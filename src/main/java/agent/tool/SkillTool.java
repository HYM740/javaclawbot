package agent.tool;

import agent.command.CommandQueueManager;
import agent.command.SkillCommand;
import skills.SkillsLoader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SkillTool extends Tool {

    private final SkillsLoader skillsLoader;

    private final CommandQueueManager commandQueueManager;

    public SkillTool(CommandQueueManager commandQueueManager, SkillsLoader skillManager) {
        this.skillsLoader = skillManager;
        this.commandQueueManager = commandQueueManager;
    }

    @Override
    public String name() {
        return "skill";
    }

    @Override
    public String description() {
        return """
                在主对话中执行技能
                当用户请求执行任务时，检查是否有匹配的可用技能。技能提供专业能力和领域知识。
                当用户引用"斜杠命令"或"/<命令>"（如"/commit"、"/review-pr"）时，他们指的就是技能。使用此工具来加载它。
                重要提示：
                - 可用技能会在对话的系统提醒消息中列出
                - 当技能匹配用户请求时，这是阻塞性要求：在生成任何其他关于该任务的响应之前，必须先调用相关的技能工具
                - 绝不要提到技能而不实际调用此工具
                - 不要调用已在运行中的技能
                - 如果在当前对话轮次中看到 <command-name> 标签，说明技能已经加载过了——直接遵循指令，不要再调用此工具
                """;
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

        if (commandQueueManager.isLoaded(name)){
            return CompletableFuture.completedFuture("技能已加载, 请勿重复加载, 请查看上下文,帮助用户说明如何使用技能");
        }
        SkillCommand skillCommand = new SkillCommand(name, name, skillsLoader);
        commandQueueManager.addSkillCommand(skillCommand);

        return CompletableFuture.completedFuture("技能已成功加载,帮助用户说明如何使用技能。技能说明如下:" + skillCommand.getSkillDesc() + "\n");
    }
}