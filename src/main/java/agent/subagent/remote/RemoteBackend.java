package agent.subagent.remote;

import agent.subagent.team.backends.Backend;
import agent.subagent.team.backends.BackendType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程后端
 *
 * 对应 Open-ClaudeCode: src/tools/shared/spawnMultiAgent.ts - RemoteBackend
 */
public class RemoteBackend implements Backend {

    private static final Logger log = LoggerFactory.getLogger(RemoteBackend.class);

    /** Teleport 服务 */
    private final TeleportService teleportService;

    /** 远程 teammate 存储 */
    private final Map<String, TeleportService.RemoteTeammate> teammates = new ConcurrentHashMap<>();

    /** 是否可用 */
    private volatile Boolean available;

    /**
     * 创建 RemoteBackend
     *
     * @param ccrEndpoint CCR 服务端点
     * @param apiKey API 密钥
     */
    public RemoteBackend(String ccrEndpoint, String apiKey) {
        this.teleportService = new TeleportService(ccrEndpoint, apiKey);
    }

    /**
     * 创建 RemoteBackend
     *
     * @param teleportService Teleport 服务
     */
    public RemoteBackend(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override
    public BackendType type() {
        return BackendType.REMOTE;
    }

    @Override
    public String createPane(String name, String color) {
        log.info("Creating remote pane: name={}, color={}", name, color);

        // 远程后端不需要真正的 pane，创建 RemoteTeammate
        TeleportService.TeleportConfig config = new TeleportService.TeleportConfig()
                .setName(name)
                .setColor(color);

        TeleportService.RemoteTeammate teammate = teleportService.teleportToRemote(config);
        teammates.put(teammate.getId(), teammate);

        log.info("Created remote pane: id={}, name={}", teammate.getId(), name);
        return teammate.getId();
    }

    @Override
    public void sendCommand(String paneId, String command) {
        TeleportService.RemoteTeammate teammate = teammates.get(paneId);
        if (teammate == null) {
            log.warn("Remote teammate not found: {}", paneId);
            return;
        }

        try {
            teammate.sendCommand(command);
            log.debug("Sent command to remote pane {}: {}", paneId, command);
        } catch (Exception e) {
            log.error("Failed to send command to {}: {}", paneId, e.getMessage());
            throw new CCRException("Failed to send command: " + e.getMessage(), e);
        }
    }

    @Override
    public void killPane(String paneId) {
        TeleportService.RemoteTeammate teammate = teammates.remove(paneId);
        if (teammate != null) {
            teammate.close();
            log.info("Killed remote pane: {}", paneId);
        } else {
            log.warn("Remote teammate not found: {}", paneId);
        }
    }

    @Override
    public boolean isAvailable() {
        if (available != null) {
            return available;
        }

        // 检测 CCR 服务是否可用
        available = isCCRServiceAvailable();
        return available;
    }

    /**
     * 检测 CCR 服务是否可用
     * 对应: isServiceAvailable()
     */
    private boolean isCCRServiceAvailable() {
        try {
            // 简单的连通性检测
            // 实际应该调用 CCR 的健康检查端点
            return true;
        } catch (Exception e) {
            log.warn("CCR service not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getPaneOutput(String paneId) {
        TeleportService.RemoteTeammate teammate = teammates.get(paneId);
        if (teammate == null) {
            return "";
        }

        try {
            return teammate.getOutput();
        } catch (Exception e) {
            log.error("Failed to get output from {}: {}", paneId, e.getMessage());
            return "";
        }
    }

    @Override
    public String pollPaneOutput(String paneId) {
        TeleportService.RemoteTeammate teammate = teammates.get(paneId);
        if (teammate == null) {
            return "";
        }

        try {
            return teammate.pollOutput();
        } catch (Exception e) {
            log.error("Failed to poll output from {}: {}", paneId, e.getMessage());
            return "";
        }
    }

    /**
     * 获取 Teleport 服务
     */
    public TeleportService getTeleportService() {
        return teleportService;
    }

    /**
     * 关闭所有 teammate
     */
    public void shutdown() {
        for (String id : java.util.List.copyOf(teammates.keySet())) {
            killPane(id);
        }
    }
}
