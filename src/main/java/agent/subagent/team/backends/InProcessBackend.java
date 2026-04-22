package agent.subagent.team.backends;

import agent.subagent.team.TeamCoordinator;
import agent.subagent.team.TeammateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 进程内后端
 *
 * 对应 Open-ClaudeCode: src/utils/swarm/spawnInProcess.ts - spawnInProcessTeammate()
 *
 * 在当前进程内启动 teammate，使用 ThreadLocal 隔离上下文
 *
 * 职责：
 * 1. 创建 TeammateContext（ThreadLocal 隔离）
 * 2. 创建 linked AbortController
 * 3. 注册 InProcessTeammateTaskState
 * 4. 返回 spawn 结果
 */
public class InProcessBackend implements Backend {

    private static final Logger log = LoggerFactory.getLogger(InProcessBackend.class);

    /** 运行中的 teammate */
    private final Map<String, InProcessTeammate> teammates = new ConcurrentHashMap<>();

    /** ThreadLocal 上下文存储 */
    private static final ThreadLocal<TeammateContext> currentContext = new ThreadLocal<>();

    @Override
    public BackendType type() {
        return BackendType.IN_PROCESS;
    }

    @Override
    public String createPane(String name, String color) {
        String paneId = UUID.randomUUID().toString();
        InProcessTeammate teammate = new InProcessTeammate(paneId, name, color);
        teammates.put(paneId, teammate);
        log.info("Created InProcessTeammate: paneId={}, name={}", paneId, name);
        return paneId;
    }

    @Override
    public void sendCommand(String paneId, String command) {
        InProcessTeammate teammate = teammates.get(paneId);
        if (teammate != null) {
            teammate.receiveCommand(command);
        } else {
            log.warn("Teammate not found: paneId={}", paneId);
        }
    }

    @Override
    public void killPane(String paneId) {
        InProcessTeammate teammate = teammates.remove(paneId);
        if (teammate != null) {
            teammate.stop();
            log.info("Stopped InProcessTeammate: paneId={}", paneId);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPaneOutput(String paneId) {
        InProcessTeammate teammate = teammates.get(paneId);
        return teammate != null ? teammate.getOutput() : "";
    }

    @Override
    public String pollPaneOutput(String paneId) {
        InProcessTeammate teammate = teammates.get(paneId);
        if (teammate != null) {
            return teammate.pollOutput();
        }
        return "";
    }

    /**
     * 获取所有运行中的 teammate
     */
    public Map<String, InProcessTeammate> getTeammates() {
        return teammates;
    }

    /**
     * 设置当前线程的上下文
     * 对应: AsyncLocalStorage.bind()
     */
    public static void setContext(TeammateContext context) {
        currentContext.set(context);
    }

    /**
     * 获取当前线程的上下文
     * 对应: AsyncLocalStorage.getStore()
     */
    public static TeammateContext getContext() {
        return currentContext.get();
    }

    /**
     * 清除当前线程的上下文
     * 对应: AsyncLocalStorage.exit()
     */
    public static void clearContext() {
        currentContext.remove();
    }

    /**
     * 使用上下文执行任务
     * 对应: AsyncLocalStorage.run()
     *
     * @param context 上下文
     * @param task 要执行的任务
     * @param <T> 返回类型
     * @return 任务结果
     */
    public static <T> T runWithContext(TeammateContext context, java.util.concurrent.Callable<T> task) throws Exception {
        try {
            setContext(context);
            return task.call();
        } finally {
            clearContext();
        }
    }

    /**
     * Teammate 上下文
     *
     * 对应 Open-ClaudeCode: createTeammateContext()
     */
    public static class TeammateContext {
        private final String agentId;
        private final String agentName;
        private final String teamName;
        private final String color;
        private final boolean planModeRequired;
        private final String parentSessionId;
        private final AbortController abortController;
        private final String taskId;
        private volatile String status = "running";
        private volatile boolean isIdle = true;
        private volatile boolean shutdownRequested = false;

        public TeammateContext(String agentId, String agentName, String teamName,
                               String color, boolean planModeRequired,
                               String parentSessionId, AbortController abortController,
                               String taskId) {
            this.agentId = agentId;
            this.agentName = agentName;
            this.teamName = teamName;
            this.color = color;
            this.planModeRequired = planModeRequired;
            this.parentSessionId = parentSessionId;
            this.abortController = abortController;
            this.taskId = taskId;
        }

        public String getAgentId() { return agentId; }
        public String getAgentName() { return agentName; }
        public String getTeamName() { return teamName; }
        public String getColor() { return color; }
        public boolean isPlanModeRequired() { return planModeRequired; }
        public String getParentSessionId() { return parentSessionId; }
        public AbortController getAbortController() { return abortController; }
        public String getTaskId() { return taskId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public boolean isIdle() { return isIdle; }
        public void setIdle(boolean idle) { this.isIdle = idle; }
        public boolean isShutdownRequested() { return shutdownRequested; }
        public void setShutdownRequested(boolean shutdownRequested) { this.shutdownRequested = shutdownRequested; }
    }

    /**
     * AbortController 模拟
     *
     * 对应 Open-ClaudeCode: createAbortController()
     */
    public static class AbortController {
        private final AtomicBoolean aborted = new AtomicBoolean(false);
        private final AtomicReference<Object> reason = new AtomicReference<>();
        private final CountDownLatch abortLatch = new CountDownLatch(1);

        public void abort() {
            abort(reason.get());
        }

        public void abort(Object reason) {
            if (aborted.compareAndSet(false, true)) {
                this.reason.set(reason);
                abortLatch.countDown();
            }
        }

        public boolean isAborted() {
            return aborted.get();
        }

        public Object getReason() {
            return reason.get();
        }

        /**
         * 等待中止信号
         * @param timeout 超时时间
         * @param unit 时间单位
         * @return 是否在超时前收到中止信号
         */
        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return abortLatch.await(timeout, unit);
        }
    }

    /**
     * 创建子 AbortController
     *
     * 对应 Open-ClaudeCode: createChildAbortController()
     *
     * 子控制器中止不影响父控制器
     */
    public static AbortController createChildAbortController(AbortController parent) {
        AbortController child = new AbortController();

        // 如果父已中止，子立即中止
        if (parent.isAborted()) {
            child.abort(parent.getReason());
            return child;
        }

        // 添加监听器，当父中止时子也中止
        Thread observer = new Thread(() -> {
            try {
                parent.await(30, TimeUnit.DAYS);
                if (!child.isAborted()) {
                    child.abort(parent.getReason());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        observer.setDaemon(true);
        observer.start();

        return child;
    }

    /**
     * 进程内 teammate
     *
     * 对应 Open-ClaudeCode: InProcessTeammateTaskState
     */
    public class InProcessTeammate {
        private final String id;
        private final String name;
        private final String color;
        private final String taskId;
        private final TeammateContext context;
        private final AbortController abortController;
        private final BlockingQueue<String> outputQueue = new LinkedBlockingQueue<>();
        private volatile boolean running = true;
        private volatile String status = "running";
        private volatile boolean awaitingPlanApproval = false;

        public InProcessTeammate(String id, String name, String color) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.taskId = generateTaskId();
            this.abortController = new AbortController();

            // 创建上下文
            this.context = new TeammateContext(
                formatAgentId(name, ""), // teamName 稍后设置
                name,
                "", // teamName 稍后设置
                color,
                false, // planModeRequired 稍后设置
                getSessionId(),
                abortController,
                taskId
            );
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getColor() { return color; }
        public String getTaskId() { return taskId; }
        public TeammateContext getContext() { return context; }
        public AbortController getAbortController() { return abortController; }
        public boolean isRunning() { return running; }
        public String getStatus() { return status; }

        public void receiveCommand(String command) {
            log.info("InProcessTeammate {} received command: {}", name, command);
            status = "processing";

            // 检查中止信号
            if (abortController.isAborted()) {
                log.info("InProcessTeammate {} aborted", name);
                status = "aborted";
                return;
            }

            try {
                // 使用上下文执行命令
                InProcessBackend.runWithContext(context, () -> {
                    // TODO: 执行 runAgent 并收集输出
                    // 这里暂时返回占位消息
                    outputQueue.offer("Executing: " + command);
                    return null;
                });
            } catch (Exception e) {
                log.error("Error executing command", e);
                outputQueue.offer("Error: " + e.getMessage());
            }

            status = "idle";
        }

        public void stop() {
            running = false;
            abortController.abort();
            status = "killed";
        }

        public String getOutput() {
            StringBuilder sb = new StringBuilder();
            outputQueue.forEach(sb::append);
            outputQueue.clear();
            return sb.toString();
        }

        public String pollOutput() {
            try {
                return outputQueue.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private String generateTaskId() {
        return "in_process_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String formatAgentId(String name, String teamName) {
        return teamName != null && !teamName.isEmpty() ? name + "@" + teamName : name;
    }

    private String getSessionId() {
        // TODO: 从 AppState 获取 sessionId
        return "session-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
