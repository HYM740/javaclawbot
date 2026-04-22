package agent.subagent.team.backends.iterm2;

import agent.subagent.team.backends.iterm2.ITerm2Exception;

import agent.subagent.team.backends.Backend;
import agent.subagent.team.backends.BackendType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * iTerm2 后端
 *
 * 对应 Open-ClaudeCode: spawnMultiAgent.ts - spawnITerm2Teammate()
 *
 * 职责：
 * 1. 管理 iTerm2 会话
 * 2. 创建分屏 pane
 * 3. 发送命令到 pane
 * 4. 收集 pane 输出
 */
public class ITerm2Backend implements Backend {

    private static final Logger log = LoggerFactory.getLogger(ITerm2Backend.class);

    private static final String IT2_COMMAND = "it2-api";

    /** Session 存储: sessionName -> ITerm2Session */
    private final Map<String, ITerm2Session> sessions = new ConcurrentHashMap<>();

    /** pane 存储: paneId -> PaneInfo */
    private final Map<String, PaneInfo> panes = new ConcurrentHashMap<>();

    /**
     * pane 信息
     */
    private static class PaneInfo {
        final String paneId;
        final String sessionName;
        final ITerm2Session session;
        final ITerm2Pane pane;

        PaneInfo(String paneId, String sessionName, ITerm2Session session, ITerm2Pane pane) {
            this.paneId = paneId;
            this.sessionName = sessionName;
            this.session = session;
            this.pane = pane;
        }
    }

    @Override
    public BackendType type() {
        return BackendType.ITERM2;
    }

    @Override
    public String createPane(String name, String color) {
        try {
            String sessionName = "claude-" + name;

            // 获取或创建会话
            ITerm2Session session = sessions.computeIfAbsent(sessionName, k -> {
                try {
                    return new ITerm2Session(sessionName, null);
                } catch (ITerm2Exception e) {
                    log.error("Failed to create session: {}", e.getMessage());
                    throw new RuntimeException("Failed to create iTerm2 session", e);
                }
            });

            // 分割主 pane（水平分割）
            ITerm2Pane mainPane = new ITerm2Pane(
                session.getSessionId() + "-main",
                session.getSessionId(),
                "horizontal",
                null
            );

            // 分割出新的 pane
            ITerm2Pane newPane = session.splitHorizontal(mainPane);

            // 存储 pane 信息
            PaneInfo info = new PaneInfo(newPane.getPaneId(), sessionName, session, newPane);
            panes.put(newPane.getPaneId(), info);

            log.info("Created iTerm2 pane: paneId={}, session={}", newPane.getPaneId(), sessionName);
            return newPane.getPaneId();

        } catch (ITerm2Exception e) {
            log.error("Failed to create pane: {}", e.getMessage());
            throw new RuntimeException("Failed to create iTerm2 pane", e);
        }
    }

    @Override
    public void sendCommand(String paneId, String command) {
        PaneInfo info = panes.get(paneId);
        if (info == null) {
            log.warn("Pane not found: {}", paneId);
            return;
        }

        try {
            info.pane.sendCommand(command);
            log.debug("Sent command to pane {}: {}", paneId, command);
        } catch (ITerm2Exception e) {
            log.error("Failed to send command to pane {}: {}", paneId, e.getMessage());
            throw new RuntimeException("Failed to send command", e);
        }
    }

    @Override
    public void killPane(String paneId) {
        PaneInfo info = panes.remove(paneId);
        if (info == null) {
            log.warn("Pane not found: {}", paneId);
            return;
        }

        // 关闭 pane 所在的会话
        try {
            info.session.close();
            sessions.remove(info.sessionName);
            log.info("Closed iTerm2 session: {}", info.sessionName);
        } catch (ITerm2Exception e) {
            log.error("Failed to close session {}: {}", info.sessionName, e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return isITerm2Installed();
    }

    @Override
    public String getPaneOutput(String paneId) {
        PaneInfo info = panes.get(paneId);
        if (info == null) {
            return "";
        }

        try {
            return info.pane.capture();
        } catch (ITerm2Exception e) {
            log.error("Failed to capture pane {}: {}", paneId, e.getMessage());
            return "";
        }
    }

    @Override
    public String pollPaneOutput(String paneId) {
        // 获取 pane 内容的增量
        PaneInfo info = panes.get(paneId);
        if (info == null) {
            return "";
        }

        try {
            return info.pane.capture();
        } catch (ITerm2Exception e) {
            log.error("Failed to poll pane {}: {}", paneId, e.getMessage());
            return "";
        }
    }

    /**
     * 检测 iTerm2 是否安装
     *
     * 对应: isITerm2Available
     */
    public static boolean isITerm2Installed() {
        try {
            // 检查 it2-api 命令
            Process process = Runtime.getRuntime().exec(new String[]{IT2_COMMAND, "--version"});
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取所有会话
     */
    public Map<String, ITerm2Session> getSessions() {
        return new ConcurrentHashMap<>(sessions);
    }

    /**
     * 获取所有 pane
     */
    public Map<String, PaneInfo> getPanes() {
        return new ConcurrentHashMap<>(panes);
    }

    /**
     * 关闭所有会话
     */
    public void shutdown() {
        for (ITerm2Session session : sessions.values()) {
            try {
                session.close();
            } catch (ITerm2Exception e) {
                log.error("Failed to close session: {}", e.getMessage());
            }
        }
        sessions.clear();
        panes.clear();
    }
}
