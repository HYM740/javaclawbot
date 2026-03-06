package providers;

import config.ConfigReloader;
import config.ConfigSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 可热切换 Provider
 *
 * 设计模式：
 * - Proxy：对 AgentLoop 隐藏 provider 热更新与 fallback 逻辑
 * - Strategy：fallback 规则由 FallbackStrategy 决定
 * - Snapshot：每次请求绑定一个一致的 provider 快照
 *
 * 核心行为：
 * 1. 每次 chat 前检查配置文件是否变化
 * 2. 若变化则尝试重建 provider 快照
 * 3. 若新配置有问题，则保留旧快照
 * 4. primary 失败后，按 provider/model 链执行 fallback
 */
public final class HotSwappableProvider extends LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(HotSwappableProvider.class);

    private final ConfigReloader reloader;
    private final ProviderFactory factory;
    private final ReentrantLock rebuildLock = new ReentrantLock();

    /**
     * 当前生效的 provider 快照
     */
    private volatile ProviderRuntimeSnapshot activeSnapshot;

    public HotSwappableProvider(ConfigReloader reloader, ProviderFactory factory) {
        super("hot-swap", "hot-swap");
        this.reloader = Objects.requireNonNull(reloader, "reloader");
        this.factory = Objects.requireNonNull(factory, "factory");

        ConfigSchema.Config cfg = reloader.getCurrentConfig();
        long version = reloader.getVersion();
        this.activeSnapshot = factory.create(cfg, version);
    }

    @Override
    public CompletableFuture<LLMResponse> chat(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            String model,
            int maxTokens,
            double temperature,
            String reasoningEffort
    ) {
        ProviderRuntimeSnapshot snapshot = ensureLatestSnapshot();

        // 注意：这里优先使用 snapshot 中的主模型配置，而不是 AgentLoop 启动时传入的快照 model
        // 这样 config.json 修改后，下一轮请求会自动使用新模型
        return invokeChain(snapshot, messages, tools, maxTokens, temperature, reasoningEffort);
    }

    @Override
    public String getDefaultModel() {
        ProviderRuntimeSnapshot s = activeSnapshot;
        return s != null ? s.getModel() : "default";
    }

    /**
     * 确保当前快照已刷新到最新配置
     *
     * 若重建失败，则继续使用旧快照
     */
    private ProviderRuntimeSnapshot ensureLatestSnapshot() {
        boolean changed = false;
        try {
            changed = reloader.refreshIfChanged();
        } catch (Exception e) {
            log.warn("Config refresh check failed, keep previous provider snapshot: {}", e.toString());
        }

        if (!changed) {
            return activeSnapshot;
        }

        rebuildLock.lock();
        try {
            long version = reloader.getVersion();
            ProviderRuntimeSnapshot current = activeSnapshot;
            if (current != null && current.getVersion() == version) {
                return current;
            }

            try {
                ConfigSchema.Config cfg = reloader.getCurrentConfig();
                ProviderRuntimeSnapshot next = factory.create(cfg, version);
                activeSnapshot = next;

                log.info("Provider snapshot rebuilt successfully. version={}, primary={} / {}, fallback_mode={}",
                        next.getVersion(),
                        next.getPrimaryProviderName(),
                        next.getModel(),
                        next.getFallbackStrategy().name());

                return next;
            } catch (Exception e) {
                log.warn("Failed to rebuild provider snapshot, keep previous snapshot. error={}", e.toString());
                return activeSnapshot;
            }
        } finally {
            rebuildLock.unlock();
        }
    }

    /**
     * 按 provider/model 链执行：
     * primary(provider/model) -> fallback(provider/model) -> ...
     */
    private CompletableFuture<LLMResponse> invokeChain(
            ProviderRuntimeSnapshot snapshot,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            int maxTokens,
            double temperature,
            String reasoningEffort
    ) {
        List<ProviderRuntimeSnapshot.NamedProvider> chain = snapshot.fullChain();
        int maxAttempts = Math.min(snapshot.getMaxAttempts(), chain.size());
        if (maxAttempts <= 0) maxAttempts = 1;

        CompletableFuture<LLMResponse> out = new CompletableFuture<>();
        invokeAt(snapshot, chain, 0, maxAttempts, messages, tools, maxTokens, temperature, reasoningEffort, out);
        return out;
    }

    private void invokeAt(
            ProviderRuntimeSnapshot snapshot,
            List<ProviderRuntimeSnapshot.NamedProvider> chain,
            int index,
            int maxAttempts,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            int maxTokens,
            double temperature,
            String reasoningEffort,
            CompletableFuture<LLMResponse> out
    ) {
        if (index >= chain.size() || index >= maxAttempts) {
            out.complete(errorResponse("All providers/models failed or no valid provider response."));
            return;
        }

        ProviderRuntimeSnapshot.NamedProvider current = chain.get(index);
        String providerName = current.getName();
        String nodeModel = current.getModel();
        LLMProvider provider = current.getProvider();

        provider.chat(messages, tools, nodeModel, maxTokens, temperature, reasoningEffort)
                .whenComplete((resp, ex) -> {
                    boolean shouldFallback = snapshot.getFallbackStrategy().shouldFallback(resp, ex, index);

                    if (!shouldFallback) {
                        if (ex != null) {
                            out.complete(errorResponse(ex.toString()));
                        } else {
                            out.complete(resp != null ? resp : errorResponse("Provider returned null response."));
                        }
                        return;
                    }

                    // 已经没有更多 fallback 节点
                    if (index + 1 >= chain.size() || index + 1 >= maxAttempts) {
                        if (ex != null) {
                            log.warn("Provider {} / {} failed and no more fallbacks. error={}",
                                    providerName, nodeModel, ex.toString());
                            out.complete(errorResponse(ex.toString()));
                        } else {
                            log.warn("Provider {} / {} produced fallback-worthy response and no more fallbacks.",
                                    providerName, nodeModel);
                            out.complete(resp != null ? resp : errorResponse("No more fallbacks available."));
                        }
                        return;
                    }

                    ProviderRuntimeSnapshot.NamedProvider next = chain.get(index + 1);
                    String reason = ex != null ? ex.toString() : summarizeResponse(resp);

                    log.warn("Provider {} / {} failed or invalid, fallback to {} / {}. strategy={}, reason={}",
                            providerName,
                            nodeModel,
                            next.getName(),
                            next.getModel(),
                            snapshot.getFallbackStrategy().name(),
                            reason);

                    invokeAt(snapshot, chain, index + 1, maxAttempts, messages, tools, maxTokens, temperature, reasoningEffort, out);
                });
    }

    private static LLMResponse errorResponse(String message) {
        LLMResponse r = new LLMResponse();
        r.setContent("Error: " + message);
        r.setFinishReason("error");
        return r;
    }

    private static String summarizeResponse(LLMResponse resp) {
        if (resp == null) return "null_response";
        String finish = resp.getFinishReason();
        String content = resp.getContent();
        if (content != null && content.length() > 120) {
            content = content.substring(0, 120) + "...";
        }
        return "finish_reason=" + finish + ", content=" + content;
    }
}