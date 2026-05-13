package agent.tool.project;

import agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import providers.cli.CliAgentCommandHandler;
import providers.cli.ProjectRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 项目管理工具 - 管理项目名称到路径的绑定映射
 *
 * 支持操作:
 * - bind: 绑定项目路径到项目名称
 * - unbind: 解绑项目
 * - projects: 列出所有绑定的项目
 *
 * 注意：
 * - 此工具仅在开发者模式下可用
 * - 项目绑定按 session 隔离（CLI 通道每个 session 独立）
 */
@Slf4j
public class ProjectTool extends Tool {

    private final CliAgentCommandHandler cliAgentHandler;

    public ProjectTool(CliAgentCommandHandler cliAgentHandler) {
        this.cliAgentHandler = cliAgentHandler;
    }

    @Override
    public String name() {
        return "project";
    }

    @Override
    public String description() {
        return """
                项目管理工具 — 绑定/解绑/列出项目路径。

                支持的操作:
                - bind: 绑定项目路径到项目名称
                - unbind: 解绑项目
                - projects: 列出所有绑定的项目

                注意：
                - 此工具仅在开发者模式下可用
                - 项目绑定按 session 隔离，不同通道/会话的项目绑定互不影响
                """;
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("enum", List.of("bind", "unbind", "projects"));
        action.put("description", "操作类型");

        Map<String, Object> project = new LinkedHashMap<>();
        project.put("type", "string");
        project.put("description", "项目名称 (bind/unbind 时使用)");

        Map<String, Object> path = new LinkedHashMap<>();
        path.put("type", "string");
        path.put("description", "项目路径 (bind 时使用)");

        Map<String, Object> main = new LinkedHashMap<>();
        main.put("type", "boolean");
        main.put("description", "是否设为主项目 (bind 时使用，默认 false)");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("action", action);
        props.put("project", project);
        props.put("path", path);
        props.put("main", main);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> args) {
        String action = (String) args.get("action");

        if (action == null || action.isBlank()) {
            return CompletableFuture.completedFuture("错误: 缺少 action 参数");
        }

        return switch (action.toLowerCase()) {
            case "bind" -> handleBind(args);
            case "unbind" -> handleUnbind(args);
            case "projects" -> handleProjects();
            default -> CompletableFuture.completedFuture("错误: 未知的 action: " + action);
        };
    }

    // ==================== 各操作处理 ====================

    private CompletableFuture<String> handleBind(Map<String, Object> args) {
        String project = (String) args.get("project");
        String path = (String) args.get("path");
        Boolean main = args.containsKey("main") && Boolean.TRUE.equals(args.get("main"));

        if (project == null || project.isBlank()) {
            return CompletableFuture.completedFuture("错误: 缺少 project 参数");
        }
        if (path == null || path.isBlank()) {
            return CompletableFuture.completedFuture("错误: 缺少 path 参数");
        }

        ProjectRegistry registry = cliAgentHandler.getProjectRegistry();
        boolean success = registry.bind(project, path, main);

        if (success) {
            String mainHint = main ? " [主项目]" : "";
            return CompletableFuture.completedFuture(
                    "✅ 项目已绑定" + mainHint + ": " + project + " → " + path);
        } else {
            return CompletableFuture.completedFuture("❌ 绑定失败");
        }
    }

    private CompletableFuture<String> handleUnbind(Map<String, Object> args) {
        String project = (String) args.get("project");

        if (project == null || project.isBlank()) {
            return CompletableFuture.completedFuture("错误: 缺少 project 参数");
        }

        ProjectRegistry registry = cliAgentHandler.getProjectRegistry();
        boolean wasMain = registry.getInfo(project) != null && registry.getInfo(project).isMain();
        boolean success = registry.unbind(project);

        if (success) {
            // 停止相关的 Agent
            cliAgentHandler.getAgentPool().stopAllForProject(project);
            String mainHint = wasMain ? " (原主项目已清除)" : "";
            return CompletableFuture.completedFuture("✅ 项目已解绑" + mainHint + ": " + project);
        } else {
            return CompletableFuture.completedFuture("❌ 项目不存在: " + project);
        }
    }

    private CompletableFuture<String> handleProjects() {
        ProjectRegistry registry = cliAgentHandler.getProjectRegistry();
        Map<String, ProjectRegistry.ProjectInfo> projects = registry.listAll();

        if (projects.isEmpty()) {
            return CompletableFuture.completedFuture("📁 暂无绑定项目");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📁 已绑定项目 (").append(projects.size()).append("):\n");

        for (Map.Entry<String, ProjectRegistry.ProjectInfo> entry : projects.entrySet()) {
            ProjectRegistry.ProjectInfo info = entry.getValue();
            sb.append("  • ").append(entry.getKey());
            if (info.isMain()) {
                sb.append(" ⭐ [主项目]");
            }
            sb.append(" → ").append(info.getPath()).append("\n");
        }

        return CompletableFuture.completedFuture(sb.toString());
    }
}
