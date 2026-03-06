package utils;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class Retryer {

    private Retryer() {}

    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    /** 用来携带“本次失败原因 + 建议是否重试 + 备注信息(可打印)” */
    public static final class RetryDecision {
        public final boolean retry;
        public final String reason;

        private RetryDecision(boolean retry, String reason) {
            this.retry = retry;
            this.reason = reason;
        }

        public static RetryDecision retry(String reason) { return new RetryDecision(true, reason); }
        public static RetryDecision stop(String reason) { return new RetryDecision(false, reason); }
    }

    /** 重试配置 */
    public static final class RetryPolicy {
        public final int maxAttempts;          // 总尝试次数（含首次）
        public final Duration baseDelay;       // 初始等待
        public final Duration maxDelay;        // 最大等待上限
        public final double jitterRatio;       // 抖动比例：0.2 表示 ±20%

        public RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay, double jitterRatio) {
            if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
            if (jitterRatio < 0) throw new IllegalArgumentException("jitterRatio must be >= 0");
            this.maxAttempts = maxAttempts;
            this.baseDelay = baseDelay;
            this.maxDelay = maxDelay;
            this.jitterRatio = jitterRatio;
        }
    }

    /**
     * 带重试的执行器
     *
     * @param opName   操作名（日志用）
     * @param policy   重试策略
     * @param work     实际执行逻辑
     * @param decider  失败后是否重试的决策器（可按异常/返回值判定）
     * @param logger   你自己的日志函数（这里用 String -> void）
     */
    public static <T> T executeWithRetry(
            String opName,
            RetryPolicy policy,
            CheckedSupplier<T> work,
            java.util.function.Function<Throwable, RetryDecision> decider,
            java.util.function.Consumer<String> logger
    ) throws Exception {

        Throwable last = null;

        for (int attempt = 1; attempt <= policy.maxAttempts; attempt++) {
            try {
                return work.get();
            } catch (Throwable t) {
                last = t;

                RetryDecision decision = decider.apply(t);
                boolean canRetry = decision.retry && attempt < policy.maxAttempts;

                if (canRetry) {
                    Duration sleep = computeBackoff(policy, attempt);
                    logger.accept(opName + " retry " + attempt + "/" + policy.maxAttempts
                            + " reason=" + decision.reason
                            + " nextDelayMs=" + sleep.toMillis());
                    sleepQuietly(sleep);
                    continue;
                }

                // 不可重试 或 已到最后一次：抛出，让上层决定最终日志怎么打
                if (t instanceof Exception e) throw e;
                if (t instanceof Error e) throw e;
                throw new RuntimeException(t);
            }
        }

        // 理论不可达
        if (last instanceof Exception e) throw e;
        throw new RuntimeException(last);
    }

    private static Duration computeBackoff(RetryPolicy policy, int attempt) {
        // 指数退避：base * 2^(attempt-1)
        long baseMs = policy.baseDelay.toMillis();
        long raw = baseMs * (1L << Math.max(0, attempt - 1));
        long capped = Math.min(raw, policy.maxDelay.toMillis());

        if (policy.jitterRatio <= 0) return Duration.ofMillis(capped);

        // jitter: ±ratio
        double r = policy.jitterRatio;
        double min = 1.0 - r;
        double max = 1.0 + r;
        double factor = ThreadLocalRandom.current().nextDouble(min, max);
        long jittered = (long) Math.max(0, capped * factor);
        return Duration.ofMillis(jittered);
    }

    private static void sleepQuietly(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // 工业级：恢复中断标记
        }
    }
}