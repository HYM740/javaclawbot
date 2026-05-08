package agent.tool.db;

import agent.tool.Tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Reports available datasources and their database types to the LLM.
 */
public class ListDataSourceTool extends Tool {

    private final DataSourceManager dsManager;

    public ListDataSourceTool(DataSourceManager dsManager) {
        this.dsManager = dsManager;
    }

    @Override
    public String name() {
        return "list_datasource";
    }

    @Override
    public String description() {
        return String.join("\n",
                "列出当前可用的数据源及其数据库类型。",
                "",
                "返回数据源名 → 数据库类型，如:",
                "  default → mysql",
                "  analytics → postgresql",
                "",
                "使用场景：",
                "- 在执行数据库操作前，先通过此工具了解有哪些可用的数据源",
                "- 通过数据源名和数据库类型决定使用哪个数据源执行 SQL");
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public CompletionStage<String> execute(Map<String, Object> args) {
        Map<String, Map<String, String>> info = dsManager.listDataSourceInfo();
        if (info.isEmpty()) {
            return CompletableFuture.completedFuture("当前没有可用的数据源。请先通过配置添加数据源。");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("可用数据源 (").append(info.size()).append(" 个):\n");
        for (Map.Entry<String, Map<String, String>> entry : info.entrySet()) {
            String name = entry.getKey();
            Map<String, String> dsInfo = entry.getValue();
            sb.append("  ").append(name).append(" → ").append(dsInfo.get("dbType")).append("\n");
        }
        return CompletableFuture.completedFuture(sb.toString());
    }
}
