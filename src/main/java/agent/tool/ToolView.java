package agent.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 对多个 ToolRegistry 的组合视图。
 * <p>
 * 约定：
 * - 后面的 registry 优先级更高，会覆盖前面同名工具
 * - 因此建议传入顺序：
 * shared, mcp, local
 * 最终优先级：local > mcp > shared
 */
public interface ToolView {
    List<Map<String, Object>> getDefinitions();

    CompletionStage<String> execute(String name, Map<String, Object> args);

    Object get(String name);
}