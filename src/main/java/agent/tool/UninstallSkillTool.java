//package agent.tool;
//
//import skills.SkillsLoader;
//
//import java.util.LinkedHashMap;
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//
//public class UninstallSkillTool extends Tool {
//
//    private final SkillsLoader skillManager;
//
//    public UninstallSkillTool(SkillsLoader skillManager) {
//        this.skillManager = skillManager;
//    }
//
//    @Override
//    public String name() {
//        return "uninstall_skill";
//    }
//
//    @Override
//    public String description() {
//        return "Unload a skill from the agent runtime. The skill files remain on disk.";
//    }
//
//    @Override
//    public Map<String, Object> parameters() {
//
//        Map<String, Object> props = new LinkedHashMap<>();
//
//        Map<String, Object> name = new LinkedHashMap<>();
//        name.put("type", "string");
//        name.put("description", "Name of the skill to uninstall");
//
//        props.put("name", name);
//
//        Map<String, Object> schema = new LinkedHashMap<>();
//        schema.put("type", "object");
//        schema.put("properties", props);
//        schema.put("required", java.util.List.of("name"));
//
//        return schema;
//    }
//
//    @Override
//    public CompletableFuture<String> execute(Map<String, Object> params) {
//
//        String name = (String) params.get("name");
//
//        if (name == null || name.isBlank()) {
//            return CompletableFuture.completedFuture("Error: skill name required");
//        }
//
//        String result = skillManager.agentUninstallSkill(name);
//
//        return CompletableFuture.completedFuture(result);
//    }
//}