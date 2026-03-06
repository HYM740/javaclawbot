package providers;

import config.ConfigSchema;
import providers.startegy.FallbackStrategies;
import providers.startegy.FallbackStrategy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Provider 工厂
 *
 * 设计模式：Factory
 *
 * 职责：
 * 1. 根据当前配置构建主 provider
 * 2. 根据 fallback 配置构建 fallback provider/model 链
 * 3. 产出 ProviderRuntimeSnapshot
 *
 * 说明：
 * - 构建逻辑集中在这里，不散落在 Commands 或 HotSwappableProvider 中
 * - fallback 节点现在支持：
 *   provider + models[] + apiBase + apiKey
 */
public final class ProviderFactory {

    /**
     * 根据配置生成完整运行时快照
     */
    public ProviderRuntimeSnapshot create(ConfigSchema.Config config, long version) {
        Objects.requireNonNull(config, "config");

        String model = config.getAgents().getDefaults().getModel();
        String providerName = config.getProviderName(model);
        if (providerName == null || providerName.isBlank()) {
            providerName = "custom";
        }

        LLMProvider primary = createPrimaryProvider(config, model, providerName);

        ConfigSchema.FallbackConfig fb = config.getAgents().getDefaults().getFallback();
        FallbackStrategy strategy = resolveStrategy(fb);
        List<ProviderRuntimeSnapshot.NamedProvider> fallbacks = buildFallbacks(config, model, providerName, fb);
        int maxAttempts = (fb != null) ? Math.max(1, fb.getMaxAttempts()) : 3;

        return new ProviderRuntimeSnapshot(
                version,
                model,
                providerName,
                primary,
                fallbacks,
                strategy,
                maxAttempts
        );
    }

    /**
     * 构建主 provider
     */
    private LLMProvider createPrimaryProvider(
            ConfigSchema.Config config,
            String model,
            String providerName
    ) {
        if ("openai_codex".equals(providerName) || (model != null && model.startsWith("openai-codex/"))) {
            throw new IllegalStateException("OpenAI Codex is not supported in this Java build.");
        }

        ConfigSchema.ProviderConfig p = config.getProvider(model);
        String apiKey = (p != null && p.getApiKey() != null) ? p.getApiKey() : null;
        String apiBase = config.getApiBase(model);

        // custom：强制走 OpenAI-compatible 直连
        if ("custom".equals(providerName)) {
            if (apiBase == null || apiBase.isBlank()) apiBase = "http://localhost:8000/v1";
            if (apiKey == null || apiKey.isBlank()) apiKey = "no-key";
            return new CustomProvider(apiKey, apiBase, model);
        }

        // 其它 provider：如果配置出了 api_base，就统一走兼容接口
        if (apiBase != null && !apiBase.isBlank()) {
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = "no-key";
            }
            return new CustomProvider(apiKey, apiBase, model);
        }

        ProviderRegistry.ProviderSpec spec = ProviderRegistry.findByName(providerName);
        boolean isOauth = spec != null && spec.isOauth();
        boolean isBedrock = model != null && model.startsWith("bedrock/");
        boolean hasKey = apiKey != null && !apiKey.isBlank();

        if (!isBedrock && !hasKey && !isOauth) {
            throw new IllegalStateException("No API key configured (and no api_base set) for provider: " + providerName);
        }

        throw new IllegalStateException(
                "Provider '" + providerName + "' is not supported in this Java build. " +
                "Tip: set api_base to an OpenAI-compatible endpoint so CustomProvider can be used."
        );
    }

    /**
     * 兼容旧版逻辑：仅按 provider 名构建 provider，并沿用指定 model
     */
    private LLMProvider createProviderByName(
            ConfigSchema.Config config,
            String model,
            String forcedProviderName
    ) {
        String providerName = normalizeProviderName(forcedProviderName);

        if ("openai_codex".equals(providerName) || (model != null && model.startsWith("openai-codex/"))) {
            throw new IllegalStateException("OpenAI Codex is not supported in this Java build.");
        }

        ConfigSchema.ProviderConfig providerCfg = config.getProviders().getByName(providerName);
        String apiKey = (providerCfg != null) ? providerCfg.getApiKey() : null;
        String apiBase = (providerCfg != null) ? providerCfg.getApiBase() : null;

        // 若 provider 自己没写 apiBase，则尝试从注册表默认值推导
        if (apiBase == null || apiBase.isBlank()) {
            ProviderRegistry.ProviderSpec spec = ProviderRegistry.findByName(providerName);
            if (spec != null && spec.getDefaultApiBase() != null && !spec.getDefaultApiBase().isBlank()) {
                apiBase = spec.getDefaultApiBase();
            }
        }

        if ("custom".equals(providerName)) {
            if (apiBase == null || apiBase.isBlank()) apiBase = "http://localhost:8000/v1";
            if (apiKey == null || apiKey.isBlank()) apiKey = "no-key";
            return new CustomProvider(apiKey, apiBase, model);
        }

        if (apiBase != null && !apiBase.isBlank()) {
            if (apiKey == null || apiKey.isBlank()) apiKey = "no-key";
            return new CustomProvider(apiKey, apiBase, model);
        }

        throw new IllegalStateException("Fallback provider '" + providerName + "' has no usable apiBase");
    }

    /**
     * 按新版 fallback target 构建单个 provider/model 节点
     *
     * @param modelForThisNode 该节点实际使用的模型
     */
    private ProviderRuntimeSnapshot.NamedProvider createProviderByTarget(
            ConfigSchema.Config config,
            ConfigSchema.FallbackTarget target,
            String modelForThisNode
    ) {
        if (target == null || !target.isEnabled()) {
            throw new IllegalArgumentException("Fallback target is null or disabled");
        }

        String providerName = normalizeProviderName(target.getProvider());
        if (providerName.isBlank()) {
            throw new IllegalArgumentException("Fallback target provider is blank");
        }

        String model = (modelForThisNode != null && !modelForThisNode.isBlank())
                ? modelForThisNode
                : config.getAgents().getDefaults().getModel();

        String apiBase = target.getApiBase();
        String apiKey = target.getApiKey();

        ConfigSchema.ProviderConfig providerCfg = config.getProviders().getByName(providerName);

        // target 未显式覆盖时，从全局 provider 配置读取
        if ((apiKey == null || apiKey.isBlank()) && providerCfg != null) {
            apiKey = providerCfg.getApiKey();
        }
        if ((apiBase == null || apiBase.isBlank()) && providerCfg != null) {
            apiBase = providerCfg.getApiBase();
        }

        // 仍没有 apiBase，则尝试注册表默认值
        if (apiBase == null || apiBase.isBlank()) {
            ProviderRegistry.ProviderSpec spec = ProviderRegistry.findByName(providerName);
            if (spec != null && spec.getDefaultApiBase() != null && !spec.getDefaultApiBase().isBlank()) {
                apiBase = spec.getDefaultApiBase();
            }
        }

        LLMProvider provider;

        if ("custom".equals(providerName)) {
            if (apiBase == null || apiBase.isBlank()) apiBase = "http://localhost:8000/v1";
            if (apiKey == null || apiKey.isBlank()) apiKey = "no-key";
            provider = new CustomProvider(apiKey, apiBase, model);
        } else {
            if (apiBase != null && !apiBase.isBlank()) {
                if (apiKey == null || apiKey.isBlank()) apiKey = "no-key";
                provider = new CustomProvider(apiKey, apiBase, model);
            } else {
                throw new IllegalStateException("Fallback target provider '" + providerName + "' has no usable apiBase");
            }
        }

        return new ProviderRuntimeSnapshot.NamedProvider(providerName, model, provider);
    }

    /**
     * 构建 fallback 链
     *
     * 优先级：
     * 1. targets（新版）
     * 2. providers（旧版兼容）
     * 3. 自动兜底
     */
    private List<ProviderRuntimeSnapshot.NamedProvider> buildFallbacks(
            ConfigSchema.Config config,
            String primaryModel,
            String primaryProviderName,
            ConfigSchema.FallbackConfig fb
    ) {
        if (fb == null || !fb.isEnabled()) {
            return List.of();
        }

        List<ProviderRuntimeSnapshot.NamedProvider> list = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // 去重 key = provider::model
        String primaryKey = normalizeProviderName(primaryProviderName) + "::" + safeModel(primaryModel);
        seen.add(primaryKey);

        // 1) 新版 targets 优先
        if (fb.getTargets() != null && !fb.getTargets().isEmpty()) {
            for (ConfigSchema.FallbackTarget target : fb.getTargets()) {
                if (target == null || !target.isEnabled()) continue;

                String providerName = normalizeProviderName(target.getProvider());
                if (providerName.isBlank()) continue;

                List<String> models = target.getModels();
                if (models == null || models.isEmpty()) {
                    models = List.of(primaryModel);
                }

                for (String model : models) {
                    String m = safeModel(model);
                    String key = providerName + "::" + m;
                    if (seen.contains(key)) continue;

                    try {
                        ProviderRuntimeSnapshot.NamedProvider np =
                                createProviderByTarget(config, target, m);
                        list.add(np);
                        seen.add(key);
                    } catch (Exception ignored) {
                    }
                }
            }
            return list;
        }

        // 2) 兼容旧版 providers：沿用 primaryModel
        /*if (fb.getProviders() != null && !fb.getProviders().isEmpty()) {
            for (String name : fb.getProviders()) {
                if (name == null || name.isBlank()) continue;

                String providerName = normalizeProviderName(name);
                String key = providerName + "::" + safeModel(primaryModel);
                if (seen.contains(key)) continue;

                try {
                    LLMProvider provider = createProviderByName(config, primaryModel, providerName);
                    list.add(new ProviderRuntimeSnapshot.NamedProvider(providerName, primaryModel, provider));
                    seen.add(key);
                } catch (Exception ignored) {
                }
            }
            return list;
        }*/

        // 3) 自动兜底：沿用 primaryModel，按注册表顺序尝试
        for (ProviderRegistry.ProviderSpec spec : ProviderRegistry.PROVIDERS) {
            String providerName = spec.getName();
            String key = providerName + "::" + safeModel(primaryModel);
            if (seen.contains(key)) continue;

            try {
                LLMProvider provider = createProviderByName(config, primaryModel, providerName);
                list.add(new ProviderRuntimeSnapshot.NamedProvider(providerName, primaryModel, provider));
                seen.add(key);
            } catch (Exception ignored) {
            }
        }

        return list;
    }

    private FallbackStrategy resolveStrategy(ConfigSchema.FallbackConfig fb) {
        if (fb == null || !fb.isEnabled()) {
            return new FallbackStrategies.NoFallbackStrategy();
        }
        return FallbackStrategies.byMode(fb.getMode());
    }

    private String normalizeProviderName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace("-", "_");
    }

    private String safeModel(String model) {
        return (model == null || model.isBlank())
                ? "default"
                : model.trim();
    }
}