package agent.subagent.types;

public enum TaskType {
    LOCAL_AGENT("local_agent"),
    REMOTE_AGENT("remote_agent"),
    IN_PROCESS_TEAMMATE("in_process_teammate"),
    LOCAL_WORKFLOW("local_workflow");

    private final String value;

    TaskType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TaskType fromValue(String value) {
        for (TaskType type : TaskType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TaskType value: " + value);
    }
}
