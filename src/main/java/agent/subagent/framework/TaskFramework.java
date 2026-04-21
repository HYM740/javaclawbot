package agent.subagent.framework;

import agent.subagent.types.AppState;
import agent.subagent.types.SetAppState;
import agent.subagent.types.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 任务框架核心
 *
 * 负责：
 * 1. 任务注册和生命周期管理
 * 2. 任务轮询和状态更新
 * 3. 生成任务附件（通知）
 * 4. 清理过期任务
 */
public class TaskFramework {
    private static final Logger log = LoggerFactory.getLogger(TaskFramework.class);

    /** 轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 1000;

    /** 任务过期时间（毫秒），默认 5 分钟 */
    private static final long TASK_EXPIRY_MS = 5 * 60 * 1000;

    /** 清理间隔（毫秒），默认 1 分钟 */
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000;

    private final TaskRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final SetAppState setAppState;
    private final TaskFramework.AppStateGetter getAppState;

    private volatile boolean running = false;

    /**
     * 创建任务框架
     */
    public TaskFramework(TaskRegistry registry, SetAppState setAppState, TaskFramework.AppStateGetter getAppState) {
        this.registry = registry;
        this.setAppState = setAppState;
        this.getAppState = getAppState;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("task-framework-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动任务框架
     */
    public void start() {
        if (running) return;
        running = true;

        // 启动轮询任务
        scheduler.scheduleAtFixedRate(
                this::pollTasks,
                0,
                POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        // 启动清理任务
        scheduler.scheduleAtFixedRate(
                this::cleanupTasks,
                CLEANUP_INTERVAL_MS,
                CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        log.info("TaskFramework started");
    }

    /**
     * 停止任务框架
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        log.info("TaskFramework stopped");
    }

    /**
     * 轮询任务
     */
    private void pollTasks() {
        if (!running) return;

        try {
            List<TaskState> runningTasks = registry.getRunningTasks();
            for (TaskState task : runningTasks) {
                pollTask(task);
            }
        } catch (Exception e) {
            log.error("Error polling tasks", e);
        }
    }

    /**
     * 轮询单个任务
     *
     * 子类可以重写此方法来实现自定义轮询逻辑
     */
    protected void pollTask(TaskState task) {
        // 默认实现：检查任务超时
        if (task.getStartTime() > 0) {
            long elapsed = Instant.now().toEpochMilli() - task.getStartTime();
            if (elapsed > TASK_EXPIRY_MS) {
                log.warn("Task {} has been running for too long, marking as failed", task.getId());
                registry.markFailed(task.getId(), "Task timed out");
            }
        }
    }

    /**
     * 清理过期任务
     */
    private void cleanupTasks() {
        if (!running) return;

        try {
            int cleaned = registry.cleanupCompleted(TASK_EXPIRY_MS);
            if (cleaned > 0) {
                log.info("Cleaned up {} completed tasks", cleaned);
            }
        } catch (Exception e) {
            log.error("Error cleaning up tasks", e);
        }
    }

    /**
     * 生成任务附件（用于通知）
     */
    public List<TaskFramework.TaskAttachment> generateAttachments() {
        return registry.getRunningTasks().stream()
                .map(this::createAttachment)
                .collect(Collectors.toList());
    }

    /**
     * 为单个任务创建附件
     */
    protected TaskFramework.TaskAttachment createAttachment(TaskState task) {
        TaskFramework.TaskAttachment attachment = new TaskFramework.TaskAttachment();
        attachment.taskId = task.getId();
        attachment.taskType = task.getType().getValue();
        attachment.status = task.getStatus().getValue();
        attachment.description = task.getDescription();
        attachment.deltaSummary = null;
        return attachment;
    }

    /**
     * 获取运行中的任务数
     */
    public int getRunningTaskCount() {
        return registry.getRunningTasks().size();
    }

    /**
     * 获取任务注册表
     */
    public TaskRegistry getRegistry() {
        return registry;
    }

    /**
     * AppState 获取器接口
     */
    @FunctionalInterface
    public interface AppStateGetter {
        AppState get();
    }

    /**
     * 任务附件（用于通知）
     */
    public static class TaskAttachment {
        public String taskId;
        public String taskType;
        public String status;
        public String description;
        public String deltaSummary;
        public String toolUseId;
    }
}
