package agent.subagent.team.backends.tmux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Tmux 会话
 *
 * 对应 Open-ClaudeCode: spawnMultiAgent.ts 中的会话管理逻辑
 */
public class TmuxSession {

    private static final Logger log = LoggerFactory.getLogger(TmuxSession.class);

    private static final String TMUX_COMMAND = "tmux";

    /** 会话 ID */
    private final String sessionId;

    /** 会话名称 */
    private final String sessionName;

    /** 工作目录 */
    private final Path workdir;

    /** pane 列表 */
    private final List<TmuxPane> panes = new ArrayList<>();

    /** 是否正在运行 */
    private volatile boolean running = true;

    /**
     * 创建 tmux 会话
     *
     * 对应: ensureSession() + new-session
     */
    public TmuxSession(String sessionName, Path workdir) throws TmuxException {
        this.sessionId = UUID.randomUUID().toString();
        this.sessionName = sessionName;
        this.workdir = workdir;

        // 检查会话是否已存在
        if (hasSession(sessionName)) {
            log.info("Tmux session already exists: {}", sessionName);
        } else {
            // 创建新会话
            createSession(sessionName, workdir);
        }
    }

    /**
     * 检查会话是否存在
     *
     * 对应: hasSession()
     */
    public static boolean hasSession(String sessionName) {
        try {
            ProcessResult result = execTmux("has-session", "-t", sessionName);
            return result.exitCode == 0;
        } catch (TmuxException e) {
            return false;
        }
    }

    /**
     * 创建新会话
     */
    private static void createSession(String sessionName, Path workdir) throws TmuxException {
        ProcessResult result = execTmux(
            "new-session",
            "-d",
            "-s", sessionName,
            "-c", workdir.toString()
        );

        if (result.exitCode != 0) {
            throw new TmuxException("Failed to create tmux session: " + result.stderr);
        }
        log.info("Created tmux session: {}", sessionName);
    }

    /**
     * 创建分屏
     *
     * 对应: split-window
     *
     * @param direction "vertical" 或 "horizontal"
     * @return 创建的 pane
     */
    public TmuxPane splitWindow(String direction) throws TmuxException {
        String paneId = UUID.randomUUID().toString();

        // 执行 split-window 命令
        String[] cmd;
        if ("vertical".equals(direction)) {
            cmd = new String[]{"split-window", "-v", "-t", sessionName};
        } else {
            cmd = new String[]{"split-window", "-h", "-t", sessionName};
        }

        ProcessResult result = execTmux(cmd);
        if (result.exitCode != 0) {
            throw new TmuxException("Failed to split window: " + result.stderr);
        }

        // 获取新 pane 的 ID
        String targetPane = getTargetPane(sessionName);
        TmuxPane pane = new TmuxPane(paneId, targetPane, sessionName, direction);
        panes.add(pane);

        log.info("Split window: session={}, direction={}, paneId={}", sessionName, direction, paneId);
        return pane;
    }

    /**
     * 获取当前 target pane
     */
    private static String getTargetPane(String sessionName) throws TmuxException {
        // 获取当前 pane 的 ID
        ProcessResult result = execTmux(
            "display-message", "-p", "#{pane_id}", "-t", sessionName
        );
        if (result.exitCode == 0) {
            return result.stdout.trim();
        }
        return sessionName + ":";
    }

    /**
     * 发送按键到会话
     *
     * 对应: send-keys
     */
    public void sendKeys(String command) throws TmuxException {
        sendKeys(sessionName, command);
    }

    /**
     * 发送按键到指定目标
     */
    public void sendKeys(String target, String command) throws TmuxException {
        ProcessResult result = execTmux(
            "send-keys", "-t", target, command, "Enter"
        );
        if (result.exitCode != 0) {
            throw new TmuxException("Failed to send keys: " + result.stderr);
        }
    }

    /**
     * 发送命令到指定 pane
     */
    public void sendCommandToPane(String paneId, String command) throws TmuxException {
        sendKeys(paneId, command);
    }

    /**
     * 关闭会话
     *
     * 对应: kill-session
     */
    public void kill() throws TmuxException {
        if (!running) {
            return;
        }

        ProcessResult result = execTmux("kill-session", "-t", sessionName);
        running = false;

        if (result.exitCode != 0) {
            throw new TmuxException("Failed to kill session: " + result.stderr);
        }
        log.info("Killed tmux session: {}", sessionName);
    }

    /**
     * 获取会话名称
     */
    public String getSessionName() {
        return sessionName;
    }

    /**
     * 获取会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 检查会话是否运行
     */
    public boolean isRunning() {
        return running && hasSession(sessionName);
    }

    /**
     * 获取所有 panes
     */
    public List<TmuxPane> getPanes() {
        return new ArrayList<>(panes);
    }

    /**
     * 执行 tmux 命令
     */
    protected static ProcessResult execTmux(String... args) throws TmuxException {
        try {
            List<String> command = new ArrayList<>();
            command.add(TMUX_COMMAND);
            for (String arg : args) {
                command.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) {
                        output.append("\n");
                    }
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TmuxException("tmux command timed out");
            }

            return new ProcessResult(process.exitValue(), output.toString());

        } catch (TmuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TmuxException("Failed to execute tmux: " + e.getMessage(), e);
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

    /**
     * Tmux 异常
     */
    public static class TmuxException extends Exception {
        public TmuxException(String message) {
            super(message);
        }

        public TmuxException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
