package memory;

import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 批处理嵌入
 *
 * 对齐 OpenClaw 的 batch-*.ts
 *
 * 功能：
 * - 批量嵌入文本，提高效率
 * - 并发控制和重试
 * - 进度回调
 */
public class MemoryBatchEmbedding {

    private static final Logger log = LoggerFactory.getLogger(MemoryBatchEmbedding.class);

    // ==================== 配置 ====================

    /** 默认批大小 */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /** 默认并发数 */
    public static final int DEFAULT_CONCURRENCY = 5;

    /** 默认重试次数 */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** 默认重试延迟（毫秒） */
    public static final long DEFAULT_RETRY_DELAY_MS = 1000;

    // ==================== 批处理配置 ====================

    /**
     * 批处理配置
     */
    public static class BatchConfig {
        /** 批大小 */
        public final int batchSize;

        /** 并发数 */
        public final int concurrency;

        /** 最大重试次数 */
        public final int maxRetries;

        /** 重试延迟（毫秒） */
        public final long retryDelayMs;

        /** 是否等待完成 */
        public final boolean wait;

        public BatchConfig() {
            this(DEFAULT_BATCH_SIZE, DEFAULT_CONCURRENCY, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_DELAY_MS, true);
        }

        public BatchConfig(int batchSize, int concurrency, int maxRetries, long retryDelayMs, boolean wait) {
            this.batchSize = Math.max(1, batchSize);
            this.concurrency = Math.max(1, concurrency);
            this.maxRetries = Math.max(0, maxRetries);
            this.retryDelayMs = Math.max(0, retryDelayMs);
            this.wait = wait;
        }

        public static BatchConfig defaultConfig() {
            return new BatchConfig();
        }
    }

    // ==================== 批处理结果 ====================

    /**
     * 批处理结果
     */
    public static class BatchResult {
        /** 成功的嵌入向量 */
        public final List<float[]> embeddings;

        /** 失败的索引 */
        public final Set<Integer> failedIndices;

        /** 错误信息 */
        public final String error;

        /** 总耗时（毫秒） */
        public final long durationMs;

        public BatchResult(List<float[]> embeddings, Set<Integer> failedIndices, String error, long durationMs) {
            this.embeddings = embeddings != null ? Collections.unmodifiableList(embeddings) : Collections.emptyList();
            this.failedIndices = failedIndices != null ? Collections.unmodifiableSet(failedIndices) : Collections.emptySet();
            this.error = error;
            this.durationMs = durationMs;
        }

        public boolean isSuccess() {
            return failedIndices.isEmpty() && error == null;
        }

        public int successCount() {
            return embeddings.size() - failedIndices.size();
        }

        public int failureCount() {
            return failedIndices.size();
        }
    }

    // ==================== 进度回调 ====================

    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        void onProgress(int completed, int total, String label);
    }

    // ==================== 批处理执行 ====================

    /**
     * 批量嵌入文本
     *
     * @param provider 嵌入提供者
     * @param texts 文本列表
     * @param config 配置
     * @param callback 进度回调（可选）
     * @return 批处理结果
     */
    public static CompletableFuture<BatchResult> embedBatch(
            EmbeddingProvider provider,
            List<String> texts,
            BatchConfig config,
            ProgressCallback callback
    ) {
        if (provider == null) {
            return CompletableFuture.completedFuture(
                    new BatchResult(null, Collections.emptySet(), "Embedding provider is null", 0)
            );
        }

        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new BatchResult(Collections.emptyList(), Collections.emptySet(), null, 0)
            );
        }

        config = config != null ? config : BatchConfig.defaultConfig();

        long startTime = System.currentTimeMillis();
        int total = texts.size();
        List<float[]> allEmbeddings = new ArrayList<>(Collections.nCopies(total, null));
        Set<Integer> failedIndices = ConcurrentHashMap.newKeySet();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicReference<String> firstError = new AtomicReference<>();

        // 分批
        List<List<Integer>> batches = createBatches(total, config.batchSize);

        // 创建信号量控制并发
        Semaphore semaphore = new Semaphore(config.concurrency);
        ExecutorService executor = Executors.newFixedThreadPool(config.concurrency);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (List<Integer> batchIndices : batches) {
            BatchConfig finalConfig = config;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    try {
                        // 获取当前批次的文本
                        List<String> batchTexts = new ArrayList<>();
                        for (int idx : batchIndices) {
                            batchTexts.add(texts.get(idx));
                        }

                        // 带重试的嵌入
                        List<float[]> batchEmbeddings = embedWithRetry(
                                provider, batchTexts, finalConfig.maxRetries, finalConfig.retryDelayMs
                        );

                        // 存储结果
                        for (int i = 0; i < batchIndices.size(); i++) {
                            int originalIdx = batchIndices.get(i);
                            if (batchEmbeddings != null && i < batchEmbeddings.size()) {
                                allEmbeddings.set(originalIdx, batchEmbeddings.get(i));
                            } else {
                                failedIndices.add(originalIdx);
                            }
                        }

                        // 更新进度
                        int currentCompleted = completed.addAndGet(batchIndices.size());
                        if (callback != null) {
                            callback.onProgress(currentCompleted, total, "Embedding");
                        }
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    for (int idx : batchIndices) {
                        failedIndices.add(idx);
                    }
                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    firstError.compareAndSet(null, errorMsg);
                    for (int idx : batchIndices) {
                        failedIndices.add(idx);
                    }
                }
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, e) -> executor.shutdown())
                .thenApply(v -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    // 填充 null 为空数组
                    for (int i = 0; i < allEmbeddings.size(); i++) {
                        if (allEmbeddings.get(i) == null) {
                            allEmbeddings.set(i, new float[0]);
                        }
                    }

                    return new BatchResult(
                            allEmbeddings,
                            failedIndices,
                            firstError.get(),
                            durationMs
                    );
                });
    }

    /**
     * 带重试的嵌入
     */
    private static List<float[]> embedWithRetry(
            EmbeddingProvider provider,
            List<String> texts,
            int maxRetries,
            long retryDelayMs
    ) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return provider.embedBatch(texts);
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries) {
                    log.warn("嵌入失败，尝试重试 ({}/{}): {}", attempt + 1, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(retryDelayMs * (attempt + 1)); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("嵌入失败，已达到最大重试次数: {}", lastException != null ? lastException.getMessage() : "unknown");
        return null;
    }

    /**
     * 创建批次索引
     */
    private static List<List<Integer>> createBatches(int total, int batchSize) {
        List<List<Integer>> batches = new ArrayList<>();

        for (int i = 0; i < total; i += batchSize) {
            List<Integer> batch = new ArrayList<>();
            for (int j = i; j < Math.min(i + batchSize, total); j++) {
                batch.add(j);
            }
            batches.add(batch);
        }

        return batches;
    }

    // ==================== 同步批处理 ====================

    /**
     * 同步批量嵌入
     *
     * @param provider 嵌入提供者
     * @param texts 文本列表
     * @param config 配置
     * @return 嵌入向量列表
     */
    public static List<float[]> embedBatchSync(
            EmbeddingProvider provider,
            List<String> texts,
            BatchConfig config
    ) {
        try {
            BatchResult result = embedBatch(provider, texts, config, null).get();
            return result.embeddings;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("批处理嵌入被中断", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("批处理嵌入失败", e.getCause());
        }
    }

    // ==================== 嵌入缓存 ====================

    /**
     * 嵌入缓存
     */
    public static class EmbeddingCache {
        private final Map<String, float[]> cache = new ConcurrentHashMap<>();
        private final int maxEntries;

        public EmbeddingCache() {
            this(10000);
        }

        public EmbeddingCache(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        /**
         * 获取缓存的嵌入
         */
        public float[] get(String text) {
            String key = MemoryInternal.hashText(text);
            return cache.get(key);
        }

        /**
         * 存储嵌入到缓存
         */
        public void put(String text, float[] embedding) {
            if (cache.size() >= maxEntries) {
                // 简单的 LRU：清除一半
                Iterator<String> it = cache.keySet().iterator();
                int toRemove = maxEntries / 2;
                while (it.hasNext() && toRemove-- > 0) {
                    it.next();
                    it.remove();
                }
            }
            String key = MemoryInternal.hashText(text);
            cache.put(key, embedding);
        }

        /**
         * 检查是否包含
         */
        public boolean contains(String text) {
            String key = MemoryInternal.hashText(text);
            return cache.containsKey(key);
        }

        /**
         * 获取缓存大小
         */
        public int size() {
            return cache.size();
        }

        /**
         * 清空缓存
         */
        public void clear() {
            cache.clear();
        }
    }
}