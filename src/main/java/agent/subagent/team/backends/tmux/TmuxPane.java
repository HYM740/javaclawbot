package agent.subagent.team.backends.tmux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Tmux Pane
 *
 * 对应 Open-ClaudeCode: spawnMultiAgent.ts - pane 管理
 */
public class TmuxPane {

    private static final Logger log = LoggerFactory.getLogger(TmuxPane.class);

    private static final String TMUX_COMMAND = "tmux";

    /** Pane ID */
    private final String paneId;

    /** Tmux 目标 (session:window.pane) */
    private final String target;

    /** 所属会话 */
    private final String sessionName;

    /** 方向: "vertical" 或 "horizontal" */
    private final String direction;

    /** 输出缓冲 */
    private final StringBuilder outputBuffer = new StringBuilder();

    /**
     * 创建 TmuxPane
     */
    public TmuxPane(String paneId, String target, String sessionName, String direction) {
        this.paneId = paneId;
        this.target = target;
        this.sessionName = sessionName;
        this.direction = direction;
    }

    /**
     * 捕获 pane 内容
     *
     * 对应: capture-pane
     *
     * @return pane 的文本内容
     */
    public String capturePane() throws TmuxSession.TmuxException {
        return capturePane(0, -1);  // 从顶部到底部
    }

    /**
     * 捕获 pane 内容（指定范围）
     *
     * @param startLine 起始行（负数表示从底部计算）
     * @param endLine 结束行（负数表示从底部计算）
     * @return pane 的文本内容
     */
    public String capturePane(int startLine, int endLine) throws TmuxSession.TmuxException {
        TmuxSession.ProcessResult result = execTmux(
            "capture-pane",
            "-t", target,
            "-p",
            "-S", String.valueOf(startLine),
            "-E", String.valueOf(endLine)
        );

        if (result.exitCode != 0) {
            throw new TmuxSession.TmuxException("Failed to capture pane: " + result.stderr);
        }

        return result.stdout;
    }

    /**
     * 发送命令到 pane
     *
     * 对应: send-keys
     *
     * @param command 要执行的命令
     */
    public void sendCommand(String command) throws TmuxSession.TmuxException {
        TmuxSession.ProcessResult result = execTmux(
            "send-keys", "-t", target, command, "Enter"
        );

        if (result.exitCode != 0) {
            throw new TmuxSession.TmuxException("Failed to send command: " + result.stderr);
        }

        log.debug("Sent command to pane {}: {}", paneId, command);
    }

    /**
     * 发送文本（不执行）
     *
     * @param text 要发送的文本
     */
    public void sendText(String text) throws TmuxSession.TmuxException {
        TmuxSession.ProcessResult result = execTmux(
            "send-keys", "-t", target, text
        );

        if (result.exitCode != 0) {
            throw new TmuxSession.TmuxException("Failed to send text: " + result.stderr);
        }
    }

    /**
     * 调整 pane 大小
     *
     * 对应: resize-pane
     *
     * @param width 宽度
     * @param height 高度
     */
    public void resizePane(int width, int height) throws TmuxSession.TmuxException {
        TmuxSession.ProcessResult result = execTmux(
            "resize-pane", "-t", target,
            "-x", String.valueOf(width),
            "-y", String.valueOf(height)
        );

        if (result.exitCode != 0) {
            throw new TmuxSession.TmuxException("Failed to resize pane: " + result.stderr);
        }

        log.debug("Resized pane {}: {}x{}", paneId, width, height);
    }

    /**
     * 选择 pane
     *
     * @param direction 方向 (up, down, left, right)
     */
    public void selectPane(String direction) throws TmuxSession.TmuxException {
        TmuxSession.ProcessResult result = execTmux(
            "select-pane", "-t", target, "-" + direction.charAt(0)
        );

        if (result.exitCode != 0) {
            throw new TmuxSession.TmuxException("Failed to select pane: " + result.stderr);
        }
    }

    /**
     * 获取 pane ID
     */
    public String getPaneId() {
        return paneId;
    }

    /**
     * 获取 tmux 目标
     */
    public String getTarget() {
        return target;
    }

    /**
     * 获取所属会话
     */
    public String getSessionName() {
        return sessionName;
    }

    /**
     * 获取方向
     */
    public String getDirection() {
        return direction;
    }

    /**
     * 同步执行 tmux 命令
     */
    protected TmuxSession.ProcessResult execTmux(String... args) throws TmuxSession.TmuxException {
        try {
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(TMUX_COMMAND);
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
                throw new TmuxSession.TmuxException("tmux command timed out");
            }

            return new TmuxSession.ProcessResult(process.exitValue(), output.toString());

        } catch (TmuxSession.TmuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TmuxSession.TmuxException("Failed to execute tmux: " + e.getMessage(), e);
        }
    }
}
