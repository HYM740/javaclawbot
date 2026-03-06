package providers;

import providers.startegy.FallbackStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 运行时 provider 快照
 *
 * 设计思想：
 * - 每次配置变化时，不修改旧 provider，而是创建一套新的不可变快照
 * - 一次 chat 请求始终绑定同一个 snapshot，保证配置一致性
 */
public final class ProviderRuntimeSnapshot {

    private final long version;
    private final String model;
    private final String primaryProviderName;
    private final LLMProvider primary;
    private final List<NamedProvider> fallbacks;
    private final FallbackStrategy fallbackStrategy;
    private final int maxAttempts;

    public ProviderRuntimeSnapshot(
            long version,
            String model,
            String primaryProviderName,
            LLMProvider primary,
            List<NamedProvider> fallbacks,
            FallbackStrategy fallbackStrategy,
            int maxAttempts
    ) {
        this.version = version;
        this.model = model;
        this.primaryProviderName = primaryProviderName;
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallbacks = (fallbacks != null) ? List.copyOf(fallbacks) : List.of();
        this.fallbackStrategy = Objects.requireNonNull(fallbackStrategy, "fallbackStrategy");
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public long getVersion() {
        return version;
    }

    public String getModel() {
        return model;
    }

    public String getPrimaryProviderName() {
        return primaryProviderName;
    }

    public LLMProvider getPrimary() {
        return primary;
    }

    public List<NamedProvider> getFallbacks() {
        return fallbacks;
    }

    public FallbackStrategy getFallbackStrategy() {
        return fallbackStrategy;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * 返回完整 provider/model 链：
     * primary 在第一个，后面依次为 fallback 节点
     */
    public List<NamedProvider> fullChain() {
        List<NamedProvider> chain = new ArrayList<>();
        chain.add(new NamedProvider(primaryProviderName, model, primary));
        chain.addAll(fallbacks);
        return chain;
    }

    /**
     * 带名称 + 模型 的 provider 节点
     *
     * 一个节点代表一次可执行的 LLM 调用目标：
     * (providerName, model, providerInstance)
     */
    public static final class NamedProvider {
        private final String name;
        private final String model;
        private final LLMProvider provider;

        public NamedProvider(String name, String model, LLMProvider provider) {
            this.name = name;
            this.model = model;
            this.provider = provider;
        }

        public String getName() {
            return name;
        }

        public String getModel() {
            return model;
        }

        public LLMProvider getProvider() {
            return provider;
        }
    }
}