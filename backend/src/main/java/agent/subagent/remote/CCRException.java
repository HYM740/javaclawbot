package agent.subagent.remote;

/**
 * CCR 异常
 */
public class CCRException extends RuntimeException {
    public CCRException(String message) {
        super(message);
    }

    public CCRException(String message, Throwable cause) {
        super(message, cause);
    }
}
