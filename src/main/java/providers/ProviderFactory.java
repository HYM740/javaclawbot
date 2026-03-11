package providers;

import config.ConfigSchema;
import providers.startegy.FallbackStrategies;
import providers.startegy.FallbackStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Provider 工厂
 *
 * 职责：
 * - 根据 ConfigSchema 创建 ProviderRuntimeSnapshot
 * - 解析 provider 配置，创建对应的 LLMProvider 实例
 * - 构建 fallback 链
 */
public final class ProviderFactory {

    public ProviderFactory() {}

    /**
     * 创建 ProviderRuntimeSnapshot
     *
     * @param config  配置对象
     * @param version 配置版本号
     * @return ProviderRuntimeSnapshot
     */
    public ProviderRuntimeSnapshot create(ConfigSchema.Config config, long version) {
        Objects.requireNonNull(config, "config");

        ConfigSchema.AgentDefaults defaults = config.getAgents().getDefaults();
        String providerName = defaults.getProvider();
        String model = defaults.getModel();
        int maxTokens = defaults.getMaxTokens();
        double temperature = defaults.getTemperature();
        String reasoningEffort = defaults.getReasoningEffort();

        // 获取 fallback 配置
        ConfigSchema.FallbackConfig fallbackConfig = defaults.getFallback();
        FallbackStrategy fallbackStrategy = FallbackStrategies.byMode(
                fallbackConfig != null ? fallbackConfig.getMode() : "on_error"
        );
        int maxAttempts = fallbackConfig != null ? fallbackConfig.getMaxAttempts() : 3;

        // 创建 primary provider
        LLMProvider primaryProvider = createProvider(config, providerName, model);
        String primaryProviderName = resolveProviderName(config, providerName, model);

        // 构建 fallback 链
        List<ProviderRuntimeSnapshot.NamedProvider> fallbacks = buildFallbackChain(config, fallbackConfig);

        return new ProviderRuntimeSnapshot(
                version,
                model,
                primaryProviderName,
                primaryProvider,
                fallbacks,
                fallbackStrategy,
                maxAttempts
        );
    }

    /**
     * 创建 primary provider（简化版，用于兼容旧代码）
     */
    public static ProviderRuntimeSnapshot createPrimaryProvider(ConfigSchema.Config config) {
        Objects.requireNonNull(config, "config");

        ConfigSchema.AgentDefaults defaults = config.getAgents().getDefaults();
        String providerName = defaults.getProvider();
        String model = defaults.getModel();

        LLMProvider provider = createProvider(config, providerName, model);
        String resolvedName = resolveProviderName(config, providerName, model);

        ConfigSchema.FallbackConfig fallbackConfig = defaults.getFallback();
        FallbackStrategy strategy = FallbackStrategies.byMode(
                fallbackConfig != null ? fallbackConfig.getMode() : "on_error"
        );
        int maxAttempts = fallbackConfig != null ? fallbackConfig.getMaxAttempts() : 3;

        List<ProviderRuntimeSnapshot.NamedProvider> fallbacks = buildFallbackChain(config, fallbackConfig);

        return new ProviderRuntimeSnapshot(
                0L,
                model,
                resolvedName,
                provider,
                fallbacks,
                strategy,
                maxAttempts
        );
    }

    /**
     * 创建 LLMProvider 实例
     */
    private static LLMProvider createProvider(ConfigSchema.Config config, String providerName, String model) {
        // 获取 provider 配置
        ConfigSchema.ProviderConfig providerConfig = resolveProviderConfig(config, providerName, model);
        String apiKey = providerConfig != null ? providerConfig.getApiKey() : null;
        String apiBase = providerConfig != null ? providerConfig.getApiBase() : null;

        // 解析实际的 provider 名称
        String resolvedName = resolveProviderName(config, providerName, model);

        // custom：强制走 OpenAI-compatible 直连
        if ("custom".equals(resolvedName)) {
            if (apiBase == null || apiBase.isBlank()) apiBase = "http://localhost:8000/v1";
            if (apiKey == null || apiKey.isBlank()) apiKey = "no-key";
            return new CustomProvider(apiKey, apiBase, model);
        }

        // azure_openai：Azure OpenAI 直连
        if ("azure_openai".equals(resolvedName) || "azure".equals(resolvedName)) {
            if (apiBase == null || apiBase.isBlank()) {
                throw new IllegalStateException("Azure OpenAI requires api_base (e.g. https://your-resource.openai.azure.com/)");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Azure OpenAI requires api_key");
            }
            return new AzureOpenAIProvider(apiKey, apiBase, model);
        }

        // 其他 provider：使用 CustomProvider（OpenAI 兼容接口）
        // 从 ProviderRegistry 获取默认 apiBase
        ProviderRegistry.ProviderSpec spec = ProviderRegistry.findByName(resolvedName);
        if (spec != null && spec.getDefaultApiBase() != null && !spec.getDefaultApiBase().isBlank()) {
            if (apiBase == null || apiBase.isBlank()) {
                apiBase = spec.getDefaultApiBase();
            }
        }

        if (apiBase == null || apiBase.isBlank()) {
            apiBase = "https://api.openai.com/v1";
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "no-key";
        }

        return new CustomProvider(apiKey, apiBase, model);
    }

    /**
     * 解析 provider 配置
     */
    private static ConfigSchema.ProviderConfig resolveProviderConfig(ConfigSchema.Config config, String providerName, String model) {
        if (providerName != null && !"auto".equals(providerName)) {
            // 显式指定 provider
            ConfigSchema.ProviderConfig pc = config.getProviders().getByName(providerName);
            if (pc != null) {
                return pc;
            }
        }

        // 根据 model 自动匹配
        return config.getProvider(model);
    }

    /**
     * 解析 provider 名称
     */
    private static String resolveProviderName(ConfigSchema.Config config, String providerName, String model) {
        if (providerName != null && !"auto".equals(providerName)) {
            return providerName;
        }

        // 根据 model 自动匹配
        String name = config.getProviderName(model);
        return name != null ? name : "custom";
    }

    /**
     * 构建 fallback 链
     */
    private static List<ProviderRuntimeSnapshot.NamedProvider> buildFallbackChain(
            ConfigSchema.Config config,
            ConfigSchema.FallbackConfig fallbackConfig
    ) {
        List<ProviderRuntimeSnapshot.NamedProvider> fallbacks = new ArrayList<>();

        if (fallbackConfig == null || !fallbackConfig.isEnabled()) {
            return fallbacks;
        }

        List<ConfigSchema.FallbackTarget> targets = fallbackConfig.getTargets();
        if (targets == null || targets.isEmpty()) {
            return fallbacks;
        }

        for (ConfigSchema.FallbackTarget target : targets) {
            if (!target.isEnabled()) {
                continue;
            }

            String targetProvider = target.getProvider();
            if (targetProvider == null || targetProvider.isBlank()) {
                continue;
            }

            List<String> models = target.getModels();
            if (models == null || models.isEmpty()) {
                continue;
            }

            // 为每个 model 创建一个 fallback 节点
            for (String targetModel : models) {
                if (targetModel == null || targetModel.isBlank()) {
                    continue;
                }

                try {
                    // 获取或创建 provider 配置
                    String apiKey = target.getApiKey();
                    String apiBase = target.getApiBase();

                    // 如果没有显式配置，从全局配置获取
                    if ((apiKey == null || apiKey.isBlank()) && (apiBase == null || apiBase.isBlank())) {
                        ConfigSchema.ProviderConfig pc = config.getProviders().getByName(targetProvider);
                        if (pc != null) {
                            if (apiKey == null || apiKey.isBlank()) apiKey = pc.getApiKey();
                            if (apiBase == null || apiBase.isBlank()) apiBase = pc.getApiBase();
                        }
                    }

                    // 创建 provider 实例
                    LLMProvider provider = createFallbackProvider(targetProvider, apiKey, apiBase, targetModel);

                    fallbacks.add(new ProviderRuntimeSnapshot.NamedProvider(
                            targetProvider,
                            targetModel,
                            provider
                    ));
                } catch (Exception e) {
                    // 忽略无效的 fallback 配置
                    System.getLogger(ProviderFactory.class.getName()).log(
                            System.Logger.Level.WARNING,
                            "Failed to create fallback provider: " + targetProvider + " / " + targetModel,
                            e
                    );
                }
            }
        }

        return fallbacks;
    }

    /**
     * 创建 fallback provider 实例
     */
    private static LLMProvider createFallbackProvider(String providerName, String apiKey, String apiBase, String model) {
        // custom：强制走 OpenAI-compatible 直连
        if ("custom".equals(providerName)) {
            if (apiBase == null || apiBase.isBlank()) apiBase = "http://localhost:8000/v1";
            if (apiKey == null || apiKey.isBlank()) apiKey = "no-key";
            return new CustomProvider(apiKey, apiBase, model);
        }

        // azure_openai：Azure OpenAI 直连
        if ("azure_openai".equals(providerName) || "azure".equals(providerName)) {
            if (apiBase == null || apiBase.isBlank()) {
                throw new IllegalStateException("Azure OpenAI fallback requires api_base");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Azure OpenAI fallback requires api_key");
            }
            return new AzureOpenAIProvider(apiKey, apiBase, model);
        }

        // 其他 provider：使用 CustomProvider（OpenAI 兼容接口）
        ProviderRegistry.ProviderSpec spec = ProviderRegistry.findByName(providerName);
        if (spec != null && spec.getDefaultApiBase() != null && !spec.getDefaultApiBase().isBlank()) {
            if (apiBase == null || apiBase.isBlank()) {
                apiBase = spec.getDefaultApiBase();
            }
        }

        if (apiBase == null || apiBase.isBlank()) {
            apiBase = "https://api.openai.com/v1";
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "no-key";
        }

        return new CustomProvider(apiKey, apiBase, model);
    }
}