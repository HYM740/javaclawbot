package agent.tool;

import memory.*;
import org.slf4j.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 记忆读取工具
 *
 * 对齐 OpenClaw 的 memory_get 工具
 *
 * 功能：
 * - 安全地读取 MEMORY.md 或 memory/*.md 文件的指定行
 * - 支持 from/lines 参数控制读取范围
 * - 保持上下文精简
 *
 * 使用方式：
 * - 在 memory_search 后使用，只拉取需要的行
 */
/*
public class MemoryGetTool extends Tool {

    private static final Logger log = LoggerFactory.getLogger(MemoryGetTool.class);

    // ==================== 配置 ====================

    private final Path workspaceDir;
    private MemorySearch searchTool;

    // ==================== 构造函数 ====================

    public MemoryGetTool(Path workspaceDir) {
        this.workspaceDir = Objects.requireNonNull(workspaceDir, "工作目录不能为空");
    }

    // ==================== 实现接口 ====================

    @Override
    public String name() {
        return "memory_get";
    }

    @Override
    public String description() {
        return """
            安全地读取 MEMORY.md 或 memory/*.md 文件的指定行。
            在 memory_search 后使用，只拉取需要的行，保持上下文精简。
            
            参数：
            - path: 文件路径（如 "MEMORY.md" 或 "memory/MEMORY.md"）
            - from: 起始行号（可选，从 1 开始）
            - lines: 读取行数（可选）
            
            返回：
            - 文件内容片段
            """;
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        
        Map<String, Object> properties = new LinkedHashMap<>();
        
        Map<String, Object> pathParam = new LinkedHashMap<>();
        pathParam.put("type", "string");
        pathParam.put("description", "文件路径（如 MEMORY.md 或 memory/MEMORY.md）");
        properties.put("path", pathParam);
        
        Map<String, Object> fromParam = new LinkedHashMap<>();
        fromParam.put("type", "integer");
        fromParam.put("description", "起始行号（从 1 开始）");
        properties.put("from", fromParam);
        
        Map<String, Object> linesParam = new LinkedHashMap<>();
        linesParam.put("type", "integer");
        linesParam.put("description", "读取行数");
        properties.put("lines", linesParam);
        
        params.put("properties", properties);
        params.put("required", List.of("path"));
        
        return params;
    }

    @Override
    public CompletionStage<String> execute(Map<String, Object> args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 解析参数
                String path = args.containsKey("path") ? String.valueOf(args.get("path")) : null;
                if (path == null || path.isEmpty()) {
                    return "错误：缺少 path 参数";
                }
                
                Integer from = args.containsKey("from") ? 
                    ((Number) args.get("from")).intValue() : null;
                Integer lines = args.containsKey("lines") ? 
                    ((Number) args.get("lines")).intValue() : null;
                
                // 确保搜索工具已初始化
                ensureInitialized();
                
                // 读取文件
                MemorySearch.ReadFileResult result = searchTool.readFile(path, from, lines);
                
                if (!result.success) {
                    return String.format("读取失败: %s\n错误: %s", result.path, result.error);
                }
                
                StringBuilder sb = new StringBuilder();
                sb.append("文件: ").append(result.path).append("\n");
                if (from != null) {
                    sb.append("行号: ").append(from);
                    if (lines != null) {
                        sb.append("-").append(from + lines - 1);
                    }
                    sb.append("\n");
                }
                sb.append("\n```\n").append(result.text).append("\n```\n");
                
                return sb.toString();
                
            } catch (Exception e) {
                log.error("记忆读取失败", e);
                return "记忆读取失败: " + e.getMessage();
            }
        });
    }

    // ==================== 辅助方法 ====================

    private void ensureInitialized() throws Exception {
        if (searchTool == null) {
            searchTool = new MemorySearch(workspaceDir);
            searchTool.initialize();
        }
    }

    */
/**
     * 关闭工具
     *//*

    public void close() {
        if (searchTool != null) {
            searchTool.close();
        }
    }
}*/
