package agent.tool;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class CompositeToolView implements ToolView {
    private final List<ToolRegistry> registries;

    public CompositeToolView(ToolRegistry... registries) {
        this.registries = Arrays.asList(registries);
    }

    @Override
    public List<Map<String, Object>> getDefinitions() {
        LinkedHashMap<String, Map<String, Object>> merged = new LinkedHashMap<>();

        for (ToolRegistry registry : registries) {
            if (registry == null) continue;

            for (Map<String, Object> def : registry.getDefinitions()) {
                String name = extractToolName(def);
                if (name != null) {
                    merged.put(name, def); // 后写覆盖前写
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    @Override
    public CompletionStage<String> execute(String name, Map<String, Object> args) {
        for (int i = registries.size() - 1; i >= 0; i--) {
            ToolRegistry registry = registries.get(i);
            if (registry != null && registry.get(name) != null) {
                return registry.execute(name, args);
            }
        }
        return CompletableFuture.completedFuture(
                "Error: Tool '" + name + "' not found."
        );
    }

    @Override
    public Object get(String name) {
        for (int i = registries.size() - 1; i >= 0; i--) {
            ToolRegistry registry = registries.get(i);
            if (registry != null) {
                Object tool = registry.get(name);
                if (tool != null) {
                    return tool;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractToolName(Map<String, Object> def) {
        if (def == null) return null;
        Object fn = def.get("function");
        if (fn instanceof Map<?, ?> map) {
            Object name = map.get("name");
            return name == null ? null : String.valueOf(name);
        }
        return null;
    }
}