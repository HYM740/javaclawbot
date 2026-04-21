package agent.subagent.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

/**
 * 任务状态基类
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
    public String getOutputFile() { return outputFile; }
    public long getOutputOffset() { return outputOffset; }
    public boolean isNotified() { return notified; }

    // Setters
    public void setStatus(TaskStatus status) { this.status = status; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public void setNotified(boolean notified) { this.notified = notified; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    /**
     * 判断是否为终态
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
        long end = endTime > 0 ? endTime : Instant.now().toEpochMilli();
        return end - startTime;
    }
}
