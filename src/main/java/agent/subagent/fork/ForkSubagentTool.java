package agent.subagent.fork;

import agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Fork 子代理工具
 *
 * 作为 Tool 接口实现，供 LLM 调用。
 * 当用户调用 Agent 工具不指定 subagent_type 时触发。
 */
public class ForkSubagentTool extends Tool {

    private static final Logger log = LoggerFactory.getLogger(ForkSubagentTool.class);

    /** Fork 执行器回调 */
    private final ForkExecutorCallback executorCallback;

    public ForkSubagentTool(ForkExecutorCallback executorCallback) {
        this.executorCallback = executorCallback;
    }

    @Override
    public String name() {
        return "sessions_spawn";
    }

    @Override
    public String description() {
        return "Fork yourself to handle a subtask. The fork inherits your full conversation context and shares your prompt cache. Use for parallel subtasks that won't pollute your context.";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> modeEnum = new LinkedHashMap<>();
        modeEnum.put("type", "string");
        modeEnum.put("enum", List.of("run", "session"));
        modeEnum.put("default", "run");

        Map<String, Object> cleanupEnum = new LinkedHashMap<>();
        cleanupEnum.put("type", "string");
        cleanupEnum.put("enum", List.of("delete", "keep"));
        cleanupEnum.put("default", "keep");

        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "task", Map.of(
                                "type", "string",
                                "description", "The task/directive for the fork to execute"
                        ),
                        "label", Map.of(
                                "type", "string",
                                "description", "Optional short label for the fork (for display)"
                        ),
                        "run_in_background", Map.of(
                                "type", "boolean",
                                "description", "Set to true to run in background (default true for forks)"
                        )
                ),
                "required", List.of("task")
        );
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> args) {
        if (args == null) args = Map.of();

        String task = getString(args, "task", null);
        if (task == null || task.isBlank()) {
            return CompletableFuture.completedFuture(
                    "{\"status\":\"error\",\"error\":\"task is required\"}"
            );
        }

        String label = getString(args, "label", null);
        Boolean runInBackground = getBoolean(args, "run_in_background", true);

        log.info("ForkSubagentTool: task={}, label={}, runInBackground={}", task, label, runInBackground);

        try {
            // 调用执行器
            ForkExecutionResult result = executorCallback.executeFork(task, label, runInBackground);

            // 返回结果
            if (runInBackground) {
                return CompletableFuture.completedFuture(String.format(
                        "{\"status\":\"async_launched\",\"agentId\":\"%s\",\"description\":\"%s\",\"prompt\":\"%s\"}",
                        result.agentId,
                        label != null ? label : truncate(task, 30),
                        escapeJson(task)
                ));
            } else {
                return CompletableFuture.completedFuture(String.format(
                        "{\"status\":\"completed\",\"result\":\"%s\"}",
                        escapeJson(result.result != null ? result.result : "")
                ));
            }
        } catch (Exception e) {
            log.error("Fork execution failed", e);
            return CompletableFuture.completedFuture(String.format(
                    "{\"status\":\"error\",\"error\":\"%s\"}",
                    escapeJson(e.getMessage())
            ));
        }
    }

    private String getString(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        if (val instanceof String s && !s.isBlank()) return s;
        return defaultValue;
    }

    private Boolean getBoolean(Map<String, Object> args, String key, Boolean defaultValue) {
        Object val = args.get(key);
        if (val instanceof Boolean b) return b;
        return defaultValue;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Fork 执行器回调接口
     */
    @FunctionalInterface
    public interface ForkExecutorCallback {
        ForkExecutionResult executeFork(String task, String label, boolean runInBackground);
    }

    /**
     * Fork 执行结果
     */
    public static class ForkExecutionResult {
        public final String agentId;
        public final String result;
        public final boolean success;
        public final String error;

        public ForkExecutionResult(String agentId, String result, boolean success, String error) {
            this.agentId = agentId;
            this.result = result;
            this.success = success;
            this.error = error;
        }

        public static ForkExecutionResult success(String agentId, String result) {
            return new ForkExecutionResult(agentId, result, true, null);
        }

        public static ForkExecutionResult failure(String error) {
            return new ForkExecutionResult(null, null, false, error);
        }
    }
}
