package cli;

import agent.AgentLoop;
import bus.InboundMessage;
import bus.OutboundMessage;
import org.jline.reader.*;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AgentConsoleSession {

    private final AgentRuntime runtime;
    private final String sessionId;
    private final boolean markdown;

    public AgentConsoleSession(AgentRuntime runtime, String sessionId, boolean markdown) {
        this.runtime = runtime;
        this.sessionId = sessionId;
        this.markdown = markdown;
    }

    public void run() {
        String[] pair = splitSession(sessionId);
        String cliChannel = pair[0];
        String cliChatId = pair[1];

        Path histFile = Paths.get(System.getProperty("user.home"), ".javaclawbot", "history", "cli_history");
        try {
            Files.createDirectories(histFile.getParent());
        } catch (IOException ignored) {
        }

        Terminal terminal;
        try {
            terminal = TerminalBuilder.builder().system(true).build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to init terminal", e);
        }

        DefaultHistory history;
        try {
            history = new DefaultHistory();
            history.read(histFile, false);
        } catch (Exception e) {
            history = new DefaultHistory();
        }

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .history(history)
                .build();

        if (reader instanceof LineReaderImpl impl) {
            impl.setVariable(LineReader.HISTORY_SIZE, 10000);
        }

        System.out.println("🐱 Interactive mode (type exit or Ctrl+C to quit)\n");

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<CountDownLatch> turnLatchRef = new AtomicReference<>(new CountDownLatch(0));
        AtomicReference<String> turnResponseRef = new AtomicReference<>(null);
        // 跟踪当前是否有任务在执行
        AtomicBoolean taskRunning = new AtomicBoolean(false);

        ExecutorService outboundExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "javaclawbot-cli-outbound");
            t.setDaemon(false);
            return t;
        });

        // 独立的输入读取线程，避免阻塞
        ExecutorService inputExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "javaclawbot-cli-input");
            t.setDaemon(true);
            return t;
        });

        BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

        // 启动输入读取线程
        CompletableFuture<Void> inputTask = CompletableFuture.runAsync(() -> {
            while (running.get()) {
                try {
                    String userInput = reader.readLine("You: ");
                    if (userInput != null && !userInput.trim().isEmpty()) {
                        inputQueue.offer(userInput);
                    }
                } catch (UserInterruptException | EndOfFileException e) {
                    running.set(false);
                    inputQueue.offer("__EXIT__");
                } catch (Exception e) {
                    if (running.get()) {
                        e.printStackTrace();
                    }
                }
            }
        }, inputExecutor);

        CompletableFuture<Void> outboundTask = CompletableFuture.runAsync(() -> {
            while (running.get() && runtime.isRunning()) {
                try {
                    OutboundMessage out = runtime.consumeOutbound(1, TimeUnit.SECONDS);
                    if (out == null) continue;

                    Map<String, Object> meta = out.getMetadata() != null ? out.getMetadata() : Map.of();
                    boolean isProgress = Boolean.TRUE.equals(meta.get("_progress"));
                    boolean isResult = Boolean.TRUE.equals(meta.get("_result"));

                    if (isProgress) {
                        System.out.println("  ↳ " + (out.getContent() == null ? "" : out.getContent()));
                        continue;
                    }

                    if(isResult) {
                        System.out.println("  命令执行结果：" + (out.getContent() == null ? "" : out.getContent()));
                        continue;
                    }

                    CountDownLatch latch = turnLatchRef.get();
                    if (latch != null && latch.getCount() > 0) {
                        if (out.getContent() != null && !out.getContent().isBlank()) {
                            turnResponseRef.compareAndSet(null, out.getContent());
                        }
                        latch.countDown();
                    } else {
                        if (out.getContent() != null && !out.getContent().isBlank()) {
                            printAgentResponse(out.getContent(), markdown);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, outboundExecutor);

        try {
            while (running.get()) {
                // 非阻塞方式获取输入，允许处理系统命令
                String userInput = inputQueue.poll(200, TimeUnit.MILLISECONDS);
                if (userInput == null) continue;

                if ("__EXIT__".equals(userInput)) {
                    System.out.println("\nGoodbye!");
                    break;
                }

                if (userInput.trim().isEmpty()) continue;
                if (isExitCommand(userInput.trim())) {
                    System.out.println("\nGoodbye!");
                    break;
                }

                try {
                    history.add(userInput);
                    history.save();
                } catch (Exception ignored) {
                }

                // 检测并处理系统命令（如 /stop），立即处理不排队
                InboundMessage msg = new InboundMessage(cliChannel, "user", cliChatId, userInput, null, null);
                if (runtime.handleSystemCommand(msg, userInput)) {
                    // 系统命令已处理（如 /stop），重置任务状态
                    taskRunning.set(false);
                    turnLatchRef.set(new CountDownLatch(0));
                    continue;
                }

                // 如果有任务正在执行，跳过新消息（或排队）
                if (taskRunning.get()) {
                    System.out.println("[dim]上一条消息仍在处理中，请使用 /stop 停止后重试[/dim]");
                    continue;
                }

                turnResponseRef.set(null);
                CountDownLatch latch = new CountDownLatch(1);
                turnLatchRef.set(latch);
                taskRunning.set(true);

                // 异步发送消息，不阻塞输入线程
                CompletableFuture.runAsync(() -> {
                    try {
                        runtime.publishInbound(msg).toCompletableFuture().join();
                    } catch (Exception e) {
                        System.out.println("发送消息失败: " + e.getMessage());
                        taskRunning.set(false);
                        latch.countDown();
                    }
                });

                System.out.println("[dim]javaclawbot is thinking...[/dim]");

                // 等待响应，但定期检查输入队列是否有 /stop
                while (latch.getCount() > 0 && running.get()) {
                    // 检查是否有新的 /stop 命令
                    String pendingInput = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (pendingInput != null && !pendingInput.isEmpty()) {
                        if ("/stop".equalsIgnoreCase(pendingInput.trim())) {
                            // 直接处理 /stop
                            InboundMessage stopMsg = new InboundMessage(cliChannel, "user", cliChatId, pendingInput, null, null);
                            if (runtime.handleSystemCommand(stopMsg, pendingInput)) {
                                taskRunning.set(false);
                                latch.countDown();
                                break;
                            }
                        } else {
                            // 其他输入放回队列
                            inputQueue.offer(pendingInput);
                        }
                    }

                    // 检查响应是否已就绪
                    if (latch.await(100, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                }

                String resp = turnResponseRef.get();
                turnLatchRef.set(new CountDownLatch(0));
                taskRunning.set(false);

                if (resp != null && !resp.isBlank()) {
                    printAgentResponse(resp, markdown);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
            inputTask.cancel(true);
            outboundTask.cancel(true);
            inputExecutor.shutdownNow();
            outboundExecutor.shutdownNow();
            try {
                history.save();
            } catch (Exception ignored) {
            }
        }
    }

    private static String[] splitSession(String sessionId) {
        if (sessionId != null && sessionId.contains(":")) {
            String[] parts = sessionId.split(":", 2);
            return new String[]{parts[0], parts[1]};
        }
        return new String[]{"cli", sessionId != null ? sessionId : "direct"};
    }

    private static boolean isExitCommand(String s) {
        return "exit".equalsIgnoreCase(s) || "quit".equalsIgnoreCase(s);
    }

    private static void printAgentResponse(String content, boolean markdown) {
        String prefix = """
                🐱 javaclawbot output: \n
                """;
        System.out.println(content == null ? "" : prefix + content);
    }
}
