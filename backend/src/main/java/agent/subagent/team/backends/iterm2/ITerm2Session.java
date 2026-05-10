package agent.subagent.team.backends.iterm2;

import agent.subagent.team.backends.iterm2.ITerm2Exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * iTerm2 会话
 *
 * 对应 Open-ClaudeCode: spawnMultiAgent.ts - ITerm2Session
 */
public class ITerm2Session {

    private static final Logger log = LoggerFactory.getLogger(ITerm2Session.class);

    /** Session ID */
    private final String sessionId;

    /** Session 名称 */
    private final String sessionName;

    /** 关联的 profile */
    private final String profile;

    /** pane 列表 */
    private final List<ITerm2Pane> panes = new ArrayList<>();

    /** 是否运行中 */
    private volatile boolean running = true;

    /**
     * 创建 ITerm2Session
     */
    public ITerm2Session(String sessionName, String profile) throws ITerm2Exception {
        this.sessionId = UUID.randomUUID().toString();
        this.sessionName = sessionName;
        this.profile = profile != null ? profile : "Default";

        // 创建 iTerm2 会话
        createSession();
    }

    /**
     * 创建 iTerm2 会话
     */
    private void createSession() throws ITerm2Exception {
        try {
            // 使用 it2-api 创建新会话
            ProcessResult result = execITerm2(
                "profi", "create-session",
                "--name", sessionName,
                "--profile", profile
            );

            if (result.exitCode != 0) {
                throw new ITerm2Exception("Failed to create session: " + result.stderr);
            }

            log.info("Created iTerm2 session: name={}, profile={}", sessionName, profile);

        } catch (ITerm2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new ITerm2Exception("Failed to create session: " + e.getMessage(), e);
        }
    }

    /**
     * 分割垂直
     *
     * 对应: splitVertical
     */
    public ITerm2Pane splitVertical(ITerm2Pane parent) throws ITerm2Exception {
        return splitPane(parent, "vertical");
    }

    /**
     * 分割水平
     *
     * 对应: splitHorizontal
     */
    public ITerm2Pane splitHorizontal(ITerm2Pane parent) throws ITerm2Exception {
        return splitPane(parent, "horizontal");
    }

    /**
     * 分割 pane
     */
    private ITerm2Pane splitPane(ITerm2Pane parent, String direction) throws ITerm2Exception {
        try {
            String paneId = UUID.randomUUID().toString();

            // 使用 it2-api 创建分屏
            ProcessResult result = execITerm2(
                "profi", "split",
                "--session-id", parent.getSessionId(),
                "--direction", direction.equals("vertical") ? "vertical" : "horizontal"
            );

            if (result.exitCode != 0) {
                throw new ITerm2Exception("Failed to split pane: " + result.stderr);
            }

            // 解析新 pane 的 session ID
            String newSessionId = parseSessionId(result.stdout);

            ITerm2Pane pane = new ITerm2Pane(paneId, newSessionId, direction, parent.getPaneId());
            panes.add(pane);

            log.info("Split pane: parent={}, direction={}, newPaneId={}", parent.getPaneId(), direction, paneId);
            return pane;

        } catch (ITerm2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new ITerm2Exception("Failed to split pane: " + e.getMessage(), e);
        }
    }

    /**
     * 发送命令
     */
    public void sendCommand(String command) throws ITerm2Exception {
        if (panes.isEmpty()) {
            return;
        }
        try {
            panes.get(0).sendCommand(command);
        } catch (ITerm2Exception e) {
            throw new ITerm2Exception(e.getMessage(), e);
        }
    }

    /**
     * 发送文本到主 pane
     */
    public void sendText(String text) throws ITerm2Exception {
        if (panes.isEmpty()) {
            return;
        }
        try {
            panes.get(0).sendText(text);
        } catch (ITerm2Exception e) {
            throw new ITerm2Exception(e.getMessage(), e);
        }
    }

    /**
     * 关闭会话
     */
    public void close() throws ITerm2Exception {
        if (!running) {
            return;
        }

        try {
            for (ITerm2Pane pane : panes) {
                execITerm2(
                    "profi", "close-session",
                    "--session-id", pane.getSessionId()
                );
            }

            running = false;
            log.info("Closed iTerm2 session: {}", sessionName);

        } catch (Exception e) {
            throw new ITerm2Exception("Failed to close session: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取 session 名称
     */
    public String getSessionName() {
        return sessionName;
    }

    /**
     * 检查是否运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取所有 pane
     */
    public List<ITerm2Pane> getPanes() {
        return new ArrayList<>(panes);
    }

    /**
     * 解析 session ID（从输出中）
     */
    private String parseSessionId(String output) {
        // 简单解析：假设输出第一行是 session ID
        if (output != null && !output.isEmpty()) {
            String[] lines = output.split("\n");
            if (lines.length > 0) {
                return lines[0].trim();
            }
        }
        return sessionId;  // 回退到 session 的 ID
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
