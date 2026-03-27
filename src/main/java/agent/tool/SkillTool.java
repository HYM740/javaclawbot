package agent.tool;

import agent.command.CommandQueueManager;
import agent.command.ContentBlock;
import agent.command.SkillCommand;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import skills.SkillsLoader;
import utils.GsonFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
                管理提供特定领域说明和工作流程的专业技能。
                
                支持动作：
                1. load   - 加载指定技能
                2. list   - 列出指定 path 下的所有技能
                3. unload - 卸载指定名称的技能
                
                **重要提示：**
                - 可用技能会在对话的系统提醒消息中列出
                - 当技能匹配用户请求时，这是阻塞性要求：在生成任何其他关于该任务的响应之前，必须先调用相关的技能工具
                - 绝不要提到技能而不实际调用此工具
                - 不要调用已在运行中的技能
                - 如果技能名称在当前用户说明中存在,用户说明格式: 
                    用户已指定使用的技能列表: xxx,xxx
                说明技能已经加载过了,就无需使用load 具体技能说明在对话记录上下文中存在,如果不存在或者被裁剪,你需要重新加载
                
                参数说明：
                - action: load | list | unload，默认 load
                - name:   技能名称（load / unload 时使用）
                - path:   技能目录路径（list 时使用, 为空代表列出当前所有技能）
                """;
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("description", "操作类型: load | list | unload。默认 load");

        Map<String, Object> name = new LinkedHashMap<>();
        name.put("type", "string");
        name.put("description", "技能名称。load / unload 时使用");

        Map<String, Object> path = new LinkedHashMap<>();
        path.put("type", "string");
        path.put("description", "技能目录路径。list 时使用");

        props.put("action", action);
        props.put("name", name);
        props.put("path", path);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "object");
        out.put("properties", props);

        // 注意：这里不能再 required=name 了，因为 list 动作只需要 path
        // 改为运行时校验
        out.put("required", java.util.List.of());

        return out;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        String action = getString(params, "action");
        if (action == null || action.isBlank()) {
            action = "load"; // 兼容旧调用：只传 name 时默认加载
        }

        action = action.trim().toLowerCase(Locale.ROOT);

        return switch (action) {
            case "load" -> executeLoad(params);
            case "list" -> executeList(params);
            case "unload" -> executeUnload(params);
            default -> CompletableFuture.completedFuture(
                    "Error: unsupported action '" + action + "', expected one of: load, list, unload"
            );
        };
    }

    /**
     * 加载技能（保留你现有逻辑）
     */
    private CompletableFuture<String> executeLoad(Map<String, Object> params) {
        String name = getString(params, "name");

        if (name == null || name.isBlank()) {
            return CompletableFuture.completedFuture("Error: skill name required for action=load");
        }

        if (commandQueueManager.isLoaded(name)) {
            return CompletableFuture.completedFuture("技能已加载, 请勿重复加载, 请查看上下文,帮助用户说明如何使用技能");
        }

        SkillCommand skillCommand = new SkillCommand(name, name, skillsLoader);
        commandQueueManager.addSkillCommandByTool(skillCommand);

        return CompletableFuture.completedFuture(
                skillCommand.getOutput()
        );
    }

    /**
     * 列出指定 path 下的技能
     * 具体扫描逻辑放在 SkillsLoader 中
     */
    private CompletableFuture<String> executeList(Map<String, Object> params) {
        String path = getString(params, "path");
        if (StrUtil.isNotBlank(path)) {
            return CompletableFuture.completedFuture(GsonFactory.toJson(skillsLoader.listSkills(Path.of(path))));
        }

        return CompletableFuture.completedFuture(GsonFactory.toJson(skillsLoader.listSkills(true)) );
    }

    /**
     * 卸载指定技能
     * 具体卸载逻辑放在 SkillsLoader 中
     */
    private CompletableFuture<String> executeUnload(Map<String, Object> params) {
        String name = getString(params, "name");

        if (name == null || name.isBlank()) {
            return CompletableFuture.completedFuture("Error: skill name required for action=unload");
        }

        return CompletableFuture.completedFuture(
                commandQueueManager.unloadUserSkill(name)
        );
    }

    private String getString(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }
}