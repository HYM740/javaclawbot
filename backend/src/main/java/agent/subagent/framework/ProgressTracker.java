package agent.subagent.framework;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进度追踪器
 *
 * 追踪工具调用次数、Token 使用量和最近活动
 */
public class ProgressTracker {
    private static final int MAX_RECENT_ACTIVITIES = 5;

    /** 工具调用次数 */
    private final AtomicLong toolUseCount = new AtomicLong(0);

    /** 最新输入 token 数 */
    private volatile long latestInputTokens;

    /** 累计输出 token 数 */
    private final AtomicLong cumulativeOutputTokens = new AtomicLong(0);

    /** 最近活动列表 */
    private final List<ToolActivity> recentActivities = new ArrayList<>();

    /** 最后摘要 */
    private String lastSummary;

    /**
     * 添加工具调用
     */
    public void addToolUse(String toolName, Map<String, Object> input) {
        toolUseCount.incrementAndGet();
        ToolActivity activity = new ToolActivity(toolName, input);
        synchronized (recentActivities) {
            recentActivities.add(activity);
            while (recentActivities.size() > MAX_RECENT_ACTIVITIES) {
                recentActivities.remove(0);
            }
        }
    }

    /**
     * 更新 token 使用量
     */
    public void updateTokens(long inputTokens, long outputTokens) {
        this.latestInputTokens = inputTokens;
        if (outputTokens > 0) {
            cumulativeOutputTokens.addAndGet(outputTokens);
        }
    }

    /**
     * 获取总 token 数
     */
    public long getTotalTokens() {
        return latestInputTokens + cumulativeOutputTokens.get();
    }

    /**
     * 获取工具调用次数
     */
    public long getToolUseCount() {
        return toolUseCount.get();
    }

    /**
     * 获取最近活动
     */
    public synchronized List<ToolActivity> getRecentActivities() {
        return new ArrayList<>(recentActivities);
    }

    /**
     * 获取最后活动
     */
    public synchronized ToolActivity getLastActivity() {
        return recentActivities.isEmpty() ? null : recentActivities.get(recentActivities.size() - 1);
    }

    /**
     * 获取/设置最后摘要
     */
    public String getLastSummary() { return lastSummary; }
    public void setLastSummary(String summary) { this.lastSummary = summary; }

    /**
     * 工具活动记录
     */
    public static class ToolActivity {
        private final String toolName;
        private final Map<String, Object> input;
        private final String activityDescription;
        private final boolean isSearch;
        private final boolean isRead;
        private final long timestamp;

        public ToolActivity(String toolName, Map<String, Object> input) {
            this(toolName, input, null, false, false);
        }

        public ToolActivity(String toolName, Map<String, Object> input, String description,
                          boolean isSearch, boolean isRead) {
            this.toolName = toolName;
            this.input = input;
            this.activityDescription = description;
            this.isSearch = isSearch;
            this.isRead = isRead;
            this.timestamp = Instant.now().toEpochMilli();
        }

        public String getToolName() { return toolName; }
        public Map<String, Object> getInput() { return input; }
        public String getActivityDescription() { return activityDescription; }
        public boolean isSearch() { return isSearch; }
        public boolean isRead() { return isRead; }
        public long getTimestamp() { return timestamp; }
    }
}
