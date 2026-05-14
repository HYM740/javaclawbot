package providers;

import config.Config;
import config.agent.AgentDefaults;
import config.provider.ProviderConfig;

import java.util.Objects;

/**
 * Provider 工厂
 *
 * 职责：
 * - 根据 provider 名称和模型创建 LLMProvider 实例
 * - 解析 provider 配置
 *
 * 注意：fallback 链构建已移至 ModelFallbackManager
 */
public final class ProviderFactory {

    public ProviderFactory() {}

    /**
     * 创建 LLMProvider 实例
     *
     * @param config       配置对象
     * @param providerName provider 名称（可为 null 或 "auto"）
     * @param model        模型名称
     * @return LLMProvider 实例
     */
    public static LLMProvider createProvider(Config config, String providerName, String model) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(model, "model");

        // 获取 provider 配置
        ProviderConfig providerConfig = resolveProviderConfig(config, providerName, model);
        String apiKey = providerConfig != null ? providerConfig.getApiKey() : null;
        String apiBase = providerConfig != null ? providerConfig.getApiBase() : null;

        // 获取超时配置（从 AgentDefaults）
        int timeoutSeconds = getTimeoutSeconds(config);

        // 解析实际的 provider 名称
        String resolvedName = resolveProviderName(config, providerName, model);

        return createProviderWithConfig(resolvedName, apiKey, apiBase, model, timeoutSeconds);
    }

    /**
     * 获取超时秒数配置
     */
    private static int getTimeoutSeconds(Config config) {
        AgentDefaults defaults = config.getAgents() != null ? config.getAgents().getDefaults() : null;
        return defaults != null ? defaults.getTimeoutSeconds() : 120;
    }

    /**
     * 使用显式配置创建 LLMProvider 实例
     *
     * @param providerName provider 名称
     * @param apiKey       API Key（可为 null）
     * @param apiBase      API Base URL（可为 null）
     * @param model        模型名称
     * @param timeoutSeconds 超时秒数
     * @return LLMProvider 实例
     */
    public static LLMProvider createProviderWithConfig(String providerName, String apiKey, String apiBase, String model, int timeoutSeconds) {
        Objects.requireNonNull(providerName, "providerName");
        Objects.requireNonNull(model, "model");

        // 默认: openai_compatible → CustomProvider
        if (apiBase == null || apiBase.isBlank()) {
            ProviderRegistry.ProviderSpec spec = ProviderRegistry.findByName(providerName);
            if (spec != null && spec.getDefaultApiBase() != null && !spec.getDefaultApiBase().isBlank()) {
                apiBase = spec.getDefaultApiBase();
            }
        }

        if (apiBase == null || apiBase.isBlank()) apiBase = "https://api.openai.com/v1";
        if (apiKey == null || apiKey.isBlank()) apiKey = "no-key";

        return new CustomProvider(apiKey, apiBase, model, timeoutSeconds);
    }

    /**
     * 解析 provider 配置
     */
    public static ProviderConfig resolveProviderConfig(Config config, String providerName, String model) {
        if (providerName != null && !"auto".equals(providerName)) {
            // 显式指定 provider
            ProviderConfig pc = config.getProviders().getByName(providerName);
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
    public static String resolveProviderName(Config config, String providerName, String model) {
        if (providerName != null && !"auto".equals(providerName)) {
            return providerName;
        }

        // 根据 model 自动匹配
        String name = config.getProviderName(model);
        return name != null ? name : "custom";
    }
}