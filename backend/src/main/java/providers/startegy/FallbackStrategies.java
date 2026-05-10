package providers.startegy;

import providers.LLMResponse;

/**
 * fallback 策略集合
 */
public final class FallbackStrategies {

    private FallbackStrategies() {}

    public static final class NoFallbackStrategy implements FallbackStrategy {
        @Override
        public boolean shouldFallback(LLMResponse response, Throwable error, int attemptIndex) {
            return false;
        }

        @Override
        public String name() {
            return "off";
        }
    }

    public static final class OnErrorFallbackStrategy implements FallbackStrategy {
        @Override
        public boolean shouldFallback(LLMResponse response, Throwable error, int attemptIndex) {
            if (error != null) return true;
            return isErrorResponse(response);
        }

        @Override
        public String name() {
            return "on_error";
        }
    }

    public static final class OnEmptyFallbackStrategy implements FallbackStrategy {
        @Override
        public boolean shouldFallback(LLMResponse response, Throwable error, int attemptIndex) {
            if (error != null) return true;
            if (isErrorResponse(response)) return true;
            return isEmptyResponse(response);
        }

        @Override
        public String name() {
            return "on_empty";
        }
    }

    public static final class OnInvalidFallbackStrategy implements FallbackStrategy {
        @Override
        public boolean shouldFallback(LLMResponse response, Throwable error, int attemptIndex) {
            if (error != null) return true;
            if (isErrorResponse(response)) return true;
            if (isEmptyResponse(response)) return true;
            return isInvalidResponse(response);
        }

        @Override
        public String name() {
            return "on_invalid";
        }
    }

    public static final class AlwaysTryNextStrategy implements FallbackStrategy {
        @Override
        public boolean shouldFallback(LLMResponse response, Throwable error, int attemptIndex) {
            if (error != null) return true;
            if (response == null) return true;
            if (isErrorResponse(response)) return true;
            return isEmptyResponse(response);
        }

        @Override
        public String name() {
            return "always_try_next";
        }
    }

    public static FallbackStrategy byMode(String mode) {
        String m = mode == null ? "" : mode.trim().toLowerCase();
        return switch (m) {
            case "off" -> new NoFallbackStrategy();
            case "on_empty" -> new OnEmptyFallbackStrategy();
            case "on_invalid" -> new OnInvalidFallbackStrategy();
            case "always_try_next" -> new AlwaysTryNextStrategy();
            case "on_error" -> new OnErrorFallbackStrategy();
            default -> new OnErrorFallbackStrategy();
        };
    }

    private static boolean isErrorResponse(LLMResponse response) {
        if (response == null) return true;

        String finishReason = response.getFinishReason();
        if (finishReason != null && "error".equalsIgnoreCase(finishReason.trim())) {
            return true;
        }

        String content = response.getContent();
        return content != null && content.startsWith("Error:");
    }

    private static boolean isEmptyResponse(LLMResponse response) {
        if (response == null) return true;
        if (response.hasToolCalls()) return false;

        String content = response.getContent();
        return content == null || content.trim().isEmpty();
    }

    private static boolean isInvalidResponse(LLMResponse response) {
        if (response == null) return true;

        String finishReason = response.getFinishReason();
        boolean invalidFinish = finishReason == null || finishReason.isBlank();

        boolean noContent = response.getContent() == null || response.getContent().trim().isEmpty();
        boolean noTools = !response.hasToolCalls();

        return invalidFinish && noContent && noTools;
    }
}