package agent.tool;

import skills.SkillsLoader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LoadSkillTool extends Tool {

    private final SkillsLoader skillManager;

    public LoadSkillTool(SkillsLoader skillManager) {
        this.skillManager = skillManager;
    }

    @Override
    public String name() {
        return "load_skill";
    }

    @Override
    public String description() {
        return "Load a skill into the agent runtime so it can be used.";
    }

    @Override
    public Map<String, Object> parameters() {

        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> name = new LinkedHashMap<>();
        name.put("type", "string");
        name.put("description", "Name of the skill to load");

        props.put("name", name);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "object");
        out.put("properties", props);
        out.put("required", java.util.List.of("name"));

        return out;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {

        String name = (String) params.get("name");

        if (name == null || name.isBlank()) {
            return CompletableFuture.completedFuture("Error: skill name required");
        }

        String result = skillManager.agentSkillLoad(name);

        return CompletableFuture.completedFuture(result);
    }
}