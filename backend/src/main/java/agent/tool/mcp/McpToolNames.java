package agent.tool.mcp;

/**
 * MCP 工具名辅助类
 *
 * 统一给 MCP 工具做命名空间，避免多个 server 工具重名：
 * 例如：
 * - mcp__filesystem__read_file
 * - mcp__github__search_repos
 */
public final class McpToolNames {

    private McpToolNames() {
    }

    public static String toExposedName(String serverName, String rawToolName) {
        return "mcp__" + sanitize(serverName) + "__" + sanitize(rawToolName);
    }

    public static String sanitize(String s) {
        if (s == null || s.isBlank()) {
            return "unknown";
        }
        return s.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}