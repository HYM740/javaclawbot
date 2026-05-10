package providers.startegy;

import providers.LLMResponse;

/**
 * fallback 策略接口
 *
 * 设计模式：Strategy
 */
public interface FallbackStrategy {

    /**
     * 是否应该 fallback 到下一个 provider/model 节点
     *
     * @param response 当前响应（可能为 null）
     * @param error 当前异常（可能为 null）
     * @param attemptIndex 当前尝试序号（0=primary）
     */
    boolean shouldFallback(LLMResponse response, Throwable error, int attemptIndex);

    /**
     * 策略名，用于日志输出
     */
    String name();
}