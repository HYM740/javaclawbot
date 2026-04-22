package agent.subagent.team.backends.tmux;

import agent.subagent.team.backends.Backend;
import agent.subagent.team.backends.BackendType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tmux 后端
 *
 * 对应 Open-ClaudeCode: spawnMultiAgent.ts - spawnTmuxTeammate()
 *
 * 职责：
 * 1. 管理 tmux 会话
 * 2. 创建分屏 pane
 * 3. 发送命令到 pane
 * 4. 收集 pane 输出
 */
public class TmuxBackend implements Backend {

    private static final Logger log = LoggerFactory.getLogger(TmuxBackend.class);

    private static final String TMUX_COMMAND = "tmux";
    private static final String SESSION_PREFIX = "claude-";

    /** 会话存储: sessionName -> TmuxSession */
    private final Map<String, TmuxSession> sessions = new ConcurrentHashMap<>();

    /** pane 存储: paneId -> TmuxPaneInfo */
    private final Map<String, TmuxPaneInfo> panes = new ConcurrentHashMap<>();

    /**
     * pane 信息
     */
    private static class TmuxPaneInfo {
        final String paneId;
        final String target;
        final String sessionName;
        final TmuxSession session;
        final TmuxPane pane;

        TmuxPaneInfo(String paneId, String target, String sessionName, TmuxSession session, TmuxPane pane) {
            this.paneId = paneId;
            this.target = target;
            this.sessionName = sessionName;
            this.session = session;
            this.pane = pane;
        }
    }

    @Override
    public BackendType type() {
        return BackendType.TMUX;
    }

    @Override
    public String createPane(String name, String color) {
        try {
            String sessionName = SESSION_PREFIX + name;

            // 获取或创建会话
            TmuxSession session = sessions.computeIfAbsent(sessionName, k -> {
                try {
                    Path workdir = Paths.get(System.getProperty("user.dir"));
                    return new TmuxSession(sessionName, workdir);
                } catch (TmuxSession.TmuxException e) {
                    log.error("Failed to create session: {}", e.getMessage());
                    throw new RuntimeException("Failed to create tmux session", e);
                }
            });

            // 创建分屏
            TmuxPane pane = session.splitWindow("horizontal");

            // 获取 target
            String target = pane.getTarget();

            // 存储 pane 信息
            TmuxPaneInfo info = new TmuxPaneInfo(pane.getPaneId(), target, sessionName, session, pane);
            panes.put(pane.getPaneId(), info);

            log.info("Created tmux pane: paneId={}, session={}, target={}", pane.getPaneId(), sessionName, target);
            return pane.getPaneId();

        } catch (TmuxSession.TmuxException e) {
            log.error("Failed to create pane: {}", e.getMessage());
            throw new RuntimeException("Failed to create tmux pane", e);
        }
    }

    @Override
    public void sendCommand(String paneId, String command) {
        TmuxPaneInfo info = panes.get(paneId);
        if (info == null) {
            log.warn("Pane not found: {}", paneId);
            return;
        }

        try {
            info.pane.sendCommand(command);
            log.debug("Sent command to pane {}: {}", paneId, command);
        } catch (TmuxSession.TmuxException e) {
            log.error("Failed to send command to pane {}: {}", paneId, e.getMessage());
            throw new RuntimeException("Failed to send command", e);
        }
    }

    @Override
    public void killPane(String paneId) {
        TmuxPaneInfo info = panes.remove(paneId);
        if (info == null) {
            log.warn("Pane not found: {}", paneId);
            return;
        }

        // 关闭 pane 所在的会话
        try {
            info.session.kill();
            sessions.remove(info.sessionName);
            log.info("Killed tmux session: {}", info.sessionName);
        } catch (TmuxSession.TmuxException e) {
            log.error("Failed to kill session {}: {}", info.sessionName, e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return isTmuxInstalled();
    }

    @Override
    public String getPaneOutput(String paneId) {
        TmuxPaneInfo info = panes.get(paneId);
        if (info == null) {
            return "";
        }

        try {
            return info.pane.capturePane();
        } catch (TmuxSession.TmuxException e) {
            log.error("Failed to capture pane {}: {}", paneId, e.getMessage());
            return "";
        }
    }

    @Override
    public String pollPaneOutput(String paneId) {
        // 捕获当前 pane 内容的增量
        TmuxPaneInfo info = panes.get(paneId);
        if (info == null) {
            return "";
        }

        try {
            // 获取完整的 pane 内容
            String content = info.pane.capturePane();

            // 简单的增量检测：返回新增的行
            // 实际实现可能需要更复杂的 diff
            return content;

        } catch (TmuxSession.TmuxException e) {
            log.error("Failed to poll pane {}: {}", paneId, e.getMessage());
            return "";
        }
    }

    /**
     * 检测 tmux 是否安装
     *
     * 对应: isTmuxAvailable
     */
    public static boolean isTmuxInstalled() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{TMUX_COMMAND, "-V"});
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取所有会话
     */
    public Map<String, TmuxSession> getSessions() {
        return new ConcurrentHashMap<>(sessions);
    }

    /**
     * 获取所有 pane
     */
    public Map<String, TmuxPaneInfo> getPanes() {
        return new ConcurrentHashMap<>(panes);
    }

    /**
     * 关闭所有会话
     */
    public void shutdown() {
        for (TmuxSession session : sessions.values()) {
            try {
                session.kill();
            } catch (TmuxSession.TmuxException e) {
                log.error("Failed to kill session: {}", e.getMessage());
            }
        }
        sessions.clear();
        panes.clear();
    }
}
