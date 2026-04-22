package agent.subagent.team.backends.iterm2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * iTerm2 Pane
 *
 * 对应 Open-ClaudeCode: spawnMultiAgent.ts - ITerm2Pane
 */
public class ITerm2Pane {

    private static final Logger log = LoggerFactory.getLogger(ITerm2Pane.class);

    /** Pane ID */
    private final String paneId;

    /** Session ID */
    private final String sessionId;

    /** 方向: "vertical" 或 "horizontal" */
    private final String direction;

    /** 父 pane ID（如果是分割出来的） */
    private final String parentPaneId;

    /**
     * 创建 ITerm2Pane
     */
    public ITerm2Pane(String paneId, String sessionId, String direction, String parentPaneId) {
        this.paneId = paneId;
        this.sessionId = sessionId;
        this.direction = direction;
        this.parentPaneId = parentPaneId;
    }

    /**
     * 捕获 pane 内容
     *
     * 对应: it2-profi capture
     */
    public String capture() throws ITerm2Exception {
        try {
            // 使用 it2-profi 捕获 pane 内容
            ProcessResult result = execITerm2(
                "profi", "capture",
                "--session-id", sessionId
            );

            if (result.exitCode != 0) {
                throw new ITerm2Exception("Failed to capture pane: " + result.stderr);
            }

            return result.stdout;

        } catch (ITerm2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new ITerm2Exception("Failed to capture pane: " + e.getMessage(), e);
        }
    }

    /**
     * 发送文本到 pane
     *
     * 对应: it2-profi send-text
     */
    public void sendText(String text) throws ITerm2Exception {
        try {
            ProcessResult result = execITerm2(
                "profi", "send-text",
                "--session-id", sessionId,
                "--text", text
            );

            if (result.exitCode != 0) {
                throw new ITerm2Exception("Failed to send text: " + result.stderr);
            }

            log.debug("Sent text to pane {}: {}", paneId, text);

        } catch (ITerm2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new ITerm2Exception("Failed to send text: " + e.getMessage(), e);
        }
    }

    /**
     * 发送命令到 pane（自动回车）
     */
    public void sendCommand(String command) throws ITerm2Exception {
        sendText(command + "\n");
    }

    /**
     * 获取 pane ID
     */
    public String getPaneId() {
        return paneId;
    }

    /**
     * 获取 session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取方向
     */
    public String getDirection() {
        return direction;
    }

    /**
     * 获取父 pane ID
     */
    public String getParentPaneId() {
        return parentPaneId;
    }

    /**
     * 执行 it2 命令
     */
    protected static ProcessResult execITerm2(String... args) throws ITerm2Exception {
        try {
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("it2-api");
            for (String arg : args) {
                command.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) {
                        output.append("\n");
                    }
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ITerm2Exception("it2 command timed out");
            }

            return new ProcessResult(process.exitValue(), output.toString());

        } catch (ITerm2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new ITerm2Exception("Failed to execute it2: " + e.getMessage(), e);
        }
    }

    /**
     * 进程执行结果
     */
    public static class ProcessResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr != null ? stderr : stdout;
        }

        public ProcessResult(int exitCode, String stdout) {
            this(exitCode, stdout, stdout);
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

        public String getStderr() {
            return stderr;
        }
    }

}
