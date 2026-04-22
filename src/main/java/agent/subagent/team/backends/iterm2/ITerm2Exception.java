package agent.subagent.team.backends.iterm2;

/**
 * iTerm2 异常
 */
public class ITerm2Exception extends Exception {
    public ITerm2Exception(String message) {
        super(message);
    }

    public ITerm2Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
