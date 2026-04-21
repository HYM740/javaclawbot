package agent.subagent.types;

import java.util.Map;

/**
 * 应用状态
 *
 * 用于在任务框架中传递应用状态
 */
public class AppState {
    private Map<String, TaskState> tasks;
    private String mainLoopModel;
    private String agent;
    private Object toolPermissionContext;
    private Object mcp;

    public Map<String, TaskState> getTasks() {
        return tasks;
    }

    public void setTasks(Map<String, TaskState> tasks) {
        this.tasks = tasks;
    }

    public String getMainLoopModel() {
        return mainLoopModel;
    }

    public void setMainLoopModel(String mainLoopModel) {
        this.mainLoopModel = mainLoopModel;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public Object getToolPermissionContext() {
        return toolPermissionContext;
    }

    public void setToolPermissionContext(Object toolPermissionContext) {
        this.toolPermissionContext = toolPermissionContext;
    }

    public Object getMcp() {
        return mcp;
    }

    public void setMcp(Object mcp) {
        this.mcp = mcp;
    }
}
