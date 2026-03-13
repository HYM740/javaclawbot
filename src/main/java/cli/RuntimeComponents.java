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

    public RuntimeComponents(
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
}