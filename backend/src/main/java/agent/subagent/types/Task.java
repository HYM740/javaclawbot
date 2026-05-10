package agent.subagent.types;

import java.util.Set;

public interface Task {
    String name();
    TaskType type();
    void kill(String taskId, SetAppState setAppState);
}
