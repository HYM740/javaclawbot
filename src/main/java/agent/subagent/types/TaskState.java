package agent.subagent.types;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 任务状态基类
 *
 * 对应 Open-ClaudeCode: src/Task.ts - TaskStateBase
 *
 * 所有任务状态类型的基类，包含共享字段
 */
public abstract class TaskState {
    /** 任务 ID */
    protected String id;

    /** 任务类型 */
    protected TaskType type;

    /** 任务状态 */
    protected TaskStatus status;

    /** 任务描述 */
    protected String description;

    /** 关联的 tool_use ID */
    protected String toolUseId;

    /** 开始时间 */
    protected long startTime;

    /** 结束时间 */
    protected long endTime;

    /** 总暂停时间（毫秒） */
    protected long totalPausedMs;

    /** 输出文件路径 */
    protected String outputFile;

    /** 输出偏移量 */
    protected long outputOffset;

    /** 是否已通知 */
    protected boolean notified;

    // Getters
    public String getId() { return id; }
    public TaskType getType() { return type; }
    public TaskStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getToolUseId() { return toolUseId; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public long getTotalPausedMs() { return totalPausedMs; }
    public String getOutputFile() { return outputFile; }
    public long getOutputOffset() { return outputOffset; }
    public boolean isNotified() { return notified; }

    // Setters
    public void setStatus(TaskStatus status) { this.status = status; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public void setNotified(boolean notified) { this.notified = notified; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public void setTotalPausedMs(long totalPausedMs) { this.totalPausedMs = totalPausedMs; }
    public void setOutputOffset(long outputOffset) { this.outputOffset = outputOffset; }

    /**
     * 判断是否为终态
     * 对应 Open-ClaudeCode: isTerminalTaskStatus()
     */
    @JsonIgnore
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /**
     * 获取运行时长（毫秒）
     */
    @JsonIgnore
    public long getRuntimeMs() {
        if (startTime == 0) return 0;
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return end - startTime;
    }
}
