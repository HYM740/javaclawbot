package agent.subagent.team.backends;

import agent.subagent.remote.RemoteBackend;
import agent.subagent.team.backends.iterm2.ITerm2Backend;
import agent.subagent.team.backends.tmux.TmuxBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 后端路由器
 *
 * 对应 Open-ClaudeCode: src/tools/shared/spawnMultiAgent.ts - BackendRouter
 */
public class BackendRouter {

    private static final Logger log = LoggerFactory.getLogger(BackendRouter.class);

    /** 配置的后端类型环境变量 */
    private static final String BACKEND_ENV_VAR = "JAVACLAWBOT_BACKEND";

    /** CCR 端点环境变量 */
    private static final String CCR_ENDPOINT_ENV_VAR = "JAVACLAWBOT_CCR_ENDPOINT";

    /** CCR API 密钥环境变量 */
    private static final String CCR_API_KEY_ENV_VAR = "JAVACLAWBOT_CCR_API_KEY";

    /**
     * 检测可用后端并返回
     * 对应: detectBackend()
     */
    public Backend detectBackend() {
        String configured = getConfiguredBackend();
        if (configured != null) {
            log.debug("Using configured backend: {}", configured);
            return createBackend(configured);
        }

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            if (isConPTYAvailable()) {
                log.debug("Using ConPTY backend");
                return new ConPTYBackend();
            }
            log.debug("Using InProcess backend (ConPTY not available)");
            return new InProcessBackend();
        }

        if (os.contains("mac")) {
            if (isITerm2Available()) {
                log.debug("Using ITerm2 backend");
                return new ITerm2Backend();
            }
            if (isTmuxAvailable()) {
                log.debug("Using Tmux backend");
                return new TmuxBackend();
            }
            log.debug("Using InProcess backend (tmux/iTerm2 not available)");
            return new InProcessBackend();
        }

        if (isTmuxAvailable()) {
            log.debug("Using Tmux backend");
            return new TmuxBackend();
        }
        log.debug("Using InProcess backend (tmux not available)");
        return new InProcessBackend();
    }

    /**
     * 获取配置的后端类型
     */
    private String getConfiguredBackend() {
        String configured = System.getenv(BACKEND_ENV_VAR);
        if (configured != null && !configured.isEmpty()) {
            return configured.toLowerCase();
        }
        return null;
    }

    /**
     * 创建后端实例
     */
    public Backend createBackend(String backendType) {
        return switch (backendType.toLowerCase()) {
            case "in_process", "inprocess" -> new InProcessBackend();
            case "tmux" -> new TmuxBackend();
            case "iterm2", "iterm" -> new ITerm2Backend();
            case "conpty" -> new ConPTYBackend();
            case "remote" -> createRemoteBackend();
            default -> {
                log.warn("Unknown backend type: {}, falling back to InProcessBackend", backendType);
                yield new InProcessBackend();
            }
        };
    }

    /**
     * 创建远程后端
     */
    private Backend createRemoteBackend() {
        String ccrEndpoint = System.getenv(CCR_ENDPOINT_ENV_VAR);
        String apiKey = System.getenv(CCR_API_KEY_ENV_VAR);

        if (ccrEndpoint == null || ccrEndpoint.isEmpty()) {
            log.warn("CCR endpoint not configured, falling back to InProcessBackend");
            return new InProcessBackend();
        }

        return new RemoteBackend(ccrEndpoint, apiKey);
    }

    /**
     * 检测 tmux 是否可用
     */
    public boolean isTmuxAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("tmux -V");
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            log.debug("tmux not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检测 iTerm2 是否可用
     */
    public boolean isITerm2Available() {
        try {
            Process process = Runtime.getRuntime().exec("which it2li");
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            log.debug("iTerm2 not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检测 ConPTY 是否可用
     */
    public boolean isConPTYAvailable() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            return false;
        }
        return true;
    }
}
