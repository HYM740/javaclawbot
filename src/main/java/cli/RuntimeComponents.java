package cli;

import config.AgentRuntimeSettings;
import config.ConfigIO;
import config.ConfigReloader;
import config.ConfigSchema;
import lombok.Getter;
import providers.HotSwappableProvider;
import providers.LLMProvider;
import providers.ProviderFactory;

import java.nio.file.Path;

@Getter
public final class RuntimeComponents {
    final ConfigReloader reloader;
    final ConfigSchema.Config config;
    final LLMProvider provider;
    final AgentRuntimeSettings runtimeSettings;

    RuntimeComponents(
            ConfigReloader reloader,
            ConfigSchema.Config config,
            LLMProvider provider,
            AgentRuntimeSettings runtimeSettings
    ) {
        this.reloader = reloader;
        this.config = config;
        this.provider = provider;
        this.runtimeSettings = runtimeSettings;
    }

    /**
     * 共享运行时组件（使用默认配置路径）
     */
    public static RuntimeComponents createRuntimeComponents() {
        return createRuntimeComponents(null, null);
    }

    /**
     * 创建运行时组件（支持自定义配置路径和 workspace 路径）
     *
     * 对齐 Python 的 _load_runtime_config(config, workspace)：
     * - config: 指定配置文件路径，null 则使用默认 ~/.nanobot/config.json
     * - workspace: 覆盖配置中的 workspace 路径
     *
     * @param configPath    自定义配置文件路径，null 则使用默认路径
     * @param workspacePath 自定义 workspace 路径，null 则使用配置中的路径
     */
    public static RuntimeComponents createRuntimeComponents(Path configPath, Path workspacePath) {
        Path effectiveConfigPath = (configPath != null) ? configPath : ConfigIO.getConfigPath();
        ConfigReloader reloader = new ConfigReloader(effectiveConfigPath);
        ConfigSchema.Config config = reloader.getCurrentConfig();

        // 覆盖 workspace 路径（对齐 Python 的 _load_runtime_config）
        if (workspacePath != null) {
            config.setWorkspacePath(workspacePath);
        }

        LLMProvider provider = new HotSwappableProvider(reloader, new ProviderFactory());
        AgentRuntimeSettings runtimeSettings = new AgentRuntimeSettings(reloader);

        return new RuntimeComponents(reloader, config, provider, runtimeSettings);
    }
}