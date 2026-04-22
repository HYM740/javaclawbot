package agent.subagent.types;

import java.util.Map;

public class AppState {
    private Map<String, TaskState> tasks;

    public Map<String, TaskState> getTasks() {
        return tasks;
    }

    public void setTasks(Map<String, TaskState> tasks) {
        this.tasks = tasks;
    }
}
