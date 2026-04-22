package agent.subagent.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public abstract class TaskState {
    private String id;
    private TaskType type;
    private TaskStatus status;
    private String description;
    private String toolUseId;
    private Instant startTime;
    private Instant endTime;
    private String outputFile;
    private Long outputOffset;
    private boolean notified;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TaskType getType() {
        return type;
    }

    public void setType(TaskType type) {
        this.type = type;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getToolUseId() {
        return toolUseId;
    }

    public void setToolUseId(String toolUseId) {
        this.toolUseId = toolUseId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public Long getOutputOffset() {
        return outputOffset;
    }

    public void setOutputOffset(Long outputOffset) {
        this.outputOffset = outputOffset;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    @JsonIgnore
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    @JsonIgnore
    public long getRuntimeMs() {
        if (startTime == null) {
            return 0;
        }
        Instant end = (endTime != null) ? endTime : Instant.now();
        return end.toEpochMilli() - startTime.toEpochMilli();
    }
}
