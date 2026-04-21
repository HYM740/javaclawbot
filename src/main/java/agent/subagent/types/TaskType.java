package agent.subagent.types;

/**
 * 任务类型枚举
 */
public enum TaskType {
    LOCAL_AGENT("local_agent"),       // 本地代理任务
    REMOTE_AGENT("remote_agent"),     // 远程代理任务
    IN_PROCESS_TEAMMATE("in_process_teammate"),  // 进程内 Teammate
    LOCAL_WORKFLOW("local_workflow");  // 本地工作流

    private final String value;

    TaskType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TaskType fromValue(String value) {
        for (TaskType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TaskType: " + value);
    }
}
