package agent.subagent.types;

/**
 * 任务状态枚举
 */
public enum TaskStatus {
    PENDING("pending"),      // 等待中
    RUNNING("running"),      // 运行中
    COMPLETED("completed"),  // 已完成
    FAILED("failed"),       // 失败
    KILLED("killed");       // 被终止

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 判断是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == KILLED;
    }
}
