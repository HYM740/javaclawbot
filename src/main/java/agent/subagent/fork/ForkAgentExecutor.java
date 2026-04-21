package agent.subagent.fork;

import agent.subagent.context.SubagentContext;
import agent.subagent.framework.ProgressTracker;
import agent.subagent.framework.TaskRegistry;
import agent.subagent.lifecycle.LocalAgentTaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import providers.LLMProvider;
import providers.LLMResponse;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fork 代理执行器
 *
 * 负责 Fork 子代理的完整执行生命周期：
 * 1. 构建隔离的上下文
 * 2. 构建 Fork 消息
 * 3. 执行 LLM 对话循环
 * 4. 追踪进度
 * 5. 处理完成和通知
 */
public class ForkAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(ForkAgentExecutor.class);

    /** 默认最大迭代次数 */
    private static final int DEFAULT_MAX_ITERATIONS = 30;

    /** LLM 提供者 */
    private final LLMProvider provider;

    /** 工作目录 */
    private final Path workspace;

    /** 最大迭代次数 */
    private final int maxIterations;

    /** Fork 执行回调 */
    private final ForkCompletionCallback completionCallback;

    /** 运行中的任务 */
    private final ConcurrentHashMap<String, CompletableFuture<ForkResult>> runningTasks = new ConcurrentHashMap<>();

    /** 终止信号 */
    private final ConcurrentHashMap<String, AtomicBoolean> terminateSignals = new ConcurrentHashMap<>();

    private final ExecutorService executor;

    public ForkAgentExecutor(
            LLMProvider provider,
            Path workspace,
            ForkCompletionCallback completionCallback
    ) {
        this(provider, workspace, DEFAULT_MAX_ITERATIONS, completionCallback);
    }

    public ForkAgentExecutor(
            LLMProvider provider,
            Path workspace,
            int maxIterations,
            ForkCompletionCallback completionCallback
    ) {
        this.provider = provider;
        this.workspace = workspace;
        this.maxIterations = maxIterations;
        this.completionCallback = completionCallback;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("fork-executor-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 执行 Fork 子代理
     */
    public CompletableFuture<ForkResult> execute(ForkContext forkContext, SubagentContext subagentContext) {
        String runId = UUID.randomUUID().toString().substring(0, 8);

        // 创建终止信号
        AtomicBoolean terminateSignal = new AtomicBoolean(false);
        terminateSignals.put(runId, terminateSignal);

        // 创建进度追踪器
        ProgressTracker progressTracker = subagentContext.getProgressTracker();

        // 注册任务
        LocalAgentTaskState taskState = LocalAgentTaskState.create(
                runId,
                forkContext.getDirective(),
                null,  // toolUseId
                forkContext.getDirective(),  // prompt
                null   // agentType
        );
        taskState.setBackgrounded(true);
        taskState.setProgressTracker(progressTracker);
        taskState.setAgentId(runId);
        TaskRegistry.getInstance().register(taskState);

        CompletableFuture<ForkResult> future = CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try {
                // 标记开始
                taskState.markStarted();
                TaskRegistry.getInstance().markStarted(runId);

                // 构建 Fork 消息
                List<Map<String, Object>> messages = ForkAgentDefinition.buildForkedMessages(
                        forkContext.getDirective(),
                        forkContext.getParentMessages()
                );

                // 构建初始消息
                List<Map<String, Object>> initialMessages = new ArrayList<>();

                // 添加系统提示词
                if (forkContext.getParentSystemPrompt() != null) {
                    initialMessages.add(Map.of("role", "system", "content", forkContext.getParentSystemPrompt()));
                }

                // 添加用户上下文
                if (forkContext.getUserContext() != null && !forkContext.getUserContext().isEmpty()) {
                    StringBuilder ucText = new StringBuilder();
                    forkContext.getUserContext().forEach((k, v) -> ucText.append(k).append(": ").append(v).append("\n"));
                    initialMessages.add(Map.of("role", "user", "content", ucText.toString()));
                }

                // 添加 Fork 消息
                initialMessages.addAll(messages);

                // 执行对话循环
                String finalResult = executeLoop(
                        runId,
                        initialMessages,
                        terminateSignal,
                        progressTracker,
                        forkContext
                );

                // 标记完成
                taskState.markCompleted(finalResult);
                TaskRegistry.getInstance().markCompleted(runId);

                ForkResult result = ForkResult.success(finalResult, progressTracker.getToolUseCount());

                // 通知完成
                if (completionCallback != null) {
                    completionCallback.onComplete(runId, forkContext.getDirective(), finalResult);
                }

                return result;

            } catch (CancellationException e) {
                log.error("Fork [{}] cancelled", runId);
                taskState.markKilled();
                TaskRegistry.getInstance().markKilled(runId);
                return ForkResult.killed();

            } catch (Exception e) {
                log.error("Fork [{}] execution error", runId, e);
                taskState.markFailed(e.getMessage());
                TaskRegistry.getInstance().markFailed(runId, e.getMessage());
                return ForkResult.error(e.getMessage());

            } finally {
                terminateSignals.remove(runId);
            }
        }, executor)
                .whenComplete((v, ex) -> runningTasks.remove(runId));

        runningTasks.put(runId, future);

        return future;
    }

    /**
     * 执行对话循环
     */
    private String executeLoop(
            String runId,
            List<Map<String, Object>> messages,
            AtomicBoolean terminateSignal,
            ProgressTracker progressTracker,
            ForkContext forkContext
    ) {
        String model = provider.getDefaultModel();

        for (int i = 0; i < maxIterations; i++) {
            // 检查终止信号
            if (terminateSignal.get()) {
                return "Terminated by request";
            }

            log.debug("Fork [{}] iteration {}, calling LLM", runId, i + 1);

            // 调用 LLM
            LLMResponse response = provider.chatWithRetry(
                    messages,
                    null, // tools 由 provider 内部处理
                    model,
                    8192,
                    0.5,
                    null
            ).toCompletableFuture().join();

            if (response.hasToolCalls()) {
                // 记录工具使用
                for (var tc : response.getToolCalls()) {
                    progressTracker.addToolUse(tc.getName(), tc.getArguments());
                }

                // 更新消息
                messages.add(buildAssistantMessage(response));

                // 执行工具调用
                for (var tc : response.getToolCalls()) {
                    if (terminateSignal.get()) break;

                    // TODO: 执行工具（需要工具注册表）
                    String result = "[Tool execution not implemented in Phase 1]";

                    messages.add(buildToolResultMessage(tc.getId(), tc.getName(), result));
                }

                continue;
            }

            // 没有工具调用，返回结果
            return response.getContent() != null ? response.getContent() : "";
        }

        return "Max iterations reached";
    }

    /**
     * 终止 Fork 任务
     */
    public boolean terminate(String runId) {
        AtomicBoolean signal = terminateSignals.get(runId);
        if (signal != null) {
            signal.set(true);
        }

        CompletableFuture<ForkResult> future = runningTasks.get(runId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            return true;
        }
        return false;
    }

    /**
     * 检查任务是否运行中
     */
    public boolean isRunning(String runId) {
        CompletableFuture<ForkResult> future = runningTasks.get(runId);
        return future != null && !future.isDone();
    }

    /**
     * 关闭执行器
     */
    public void shutdown() {
        // 终止所有运行中的任务
        runningTasks.forEach((runId, future) -> {
            if (!future.isDone()) {
                terminate(runId);
            }
        });

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    private Map<String, Object> buildAssistantMessage(LLMResponse response) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        msg.put("content", response.getContent() != null ? response.getContent() : "");

        if (response.hasToolCalls()) {
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (var tc : response.getToolCalls()) {
                toolCalls.add(Map.of(
                        "id", tc.getId(),
                        "type", "function",
                        "function", Map.of(
                                "name", tc.getName(),
                                "arguments", toJson(tc.getArguments())
                        )
                ));
            }
            msg.put("tool_calls", toolCalls);
        }

        return msg;
    }

    private Map<String, Object> buildToolResultMessage(String toolCallId, String toolName, String result) {
        return Map.of(
                "role", "tool",
                "tool_call_id", toolCallId,
                "name", toolName,
                "content", result != null ? result : ""
        );
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    /**
     * Fork 执行结果
     */
    public static class ForkResult {
        public final boolean success;
        public final boolean killed;
        public final String result;
        public final String error;
        public final long toolUseCount;

        private ForkResult(boolean success, boolean killed, String result, String error, long toolUseCount) {
            this.success = success;
            this.killed = killed;
            this.result = result;
            this.error = error;
            this.toolUseCount = toolUseCount;
        }

        public static ForkResult success(String result, long toolUseCount) {
            return new ForkResult(true, false, result, null, toolUseCount);
        }

        public static ForkResult error(String error) {
            return new ForkResult(false, false, null, error, 0);
        }

        public static ForkResult killed() {
            return new ForkResult(false, true, null, "Terminated", 0);
        }
    }

    /**
     * Fork 完成回调接口
     */
    @FunctionalInterface
    public interface ForkCompletionCallback {
        void onComplete(String runId, String directive, String result);
    }
}
