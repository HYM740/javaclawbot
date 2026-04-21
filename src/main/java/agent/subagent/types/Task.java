package agent.subagent.types;

/**
 * Task 接口 - 任务定义
 */
public interface Task {
    /**
     * 任务名称
     */
    String name();

    /**
     * 任务类型
     */
    TaskType type();

    /**
     * 终止任务
     * @param taskId 任务 ID
     * @param setAppState 状态更新函数
     */
    void kill(String taskId, SetAppState setAppState);
}
