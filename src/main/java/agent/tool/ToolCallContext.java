package agent.tool;

/**
 * 工具调用上下文 — ThreadLocal 传递当前工具调用 ID。
 *
 * 用于 EditTool/WriteTool 在执行 backup() 时获取触发备份的工具调用 ID，
 * 写入 backup-index.json 的 toolCallId 字段，支持历史会话恢复时精确匹配。
 *
 * 使用方式：
 * <pre>
 *   ToolCallContext.setToolCallId(tc.getId());
 *   try {
 *       tools.execute(...);
 *   } finally {
 *       ToolCallContext.clear();
 *   }
 * </pre>
 */
public class ToolCallContext {

    private static final ThreadLocal<String> currentToolCallId = new ThreadLocal<>();

    /** 设置当前线程的工具调用 ID */
    public static void setToolCallId(String id) {
        currentToolCallId.set(id);
    }

    /** 获取当前线程的工具调用 ID，未设置返回 null */
    public static String getToolCallId() {
        return currentToolCallId.get();
    }

    /** 清除当前线程的工具调用 ID */
    public static void clear() {
        currentToolCallId.remove();
    }
}