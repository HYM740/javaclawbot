package agent.subagent.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程启动服务
 *
 * 对应 Open-ClaudeCode: src/tools/shared/spawnMultiAgent.ts - TeleportService
 *
 * 职责：
 * 1. 准备远程环境（克隆代码、安装依赖）
 * 2. 启动远程 agent
 * 3. 管理远程会话生命周期
 */
public class TeleportService {

    private static final Logger log = LoggerFactory.getLogger(TeleportService.class);

    /** CCR 客户端 */
    private final CCRClient ccrClient;

    /** 默认 Docker 镜像 */
    private final String defaultImage;

    /** 远程 teammate 存储 */
    private final Map<String, RemoteTeammate> teammates = new ConcurrentHashMap<>();

    /**
     * 创建 TeleportService
     *
     * @param ccrEndpoint CCR 服务端点
     * @param apiKey API 密钥
     */
    public TeleportService(String ccrEndpoint, String apiKey) {
        this(ccrEndpoint, apiKey, "claude-code:latest");
    }

    /**
     * 创建 TeleportService
     *
     * @param ccrEndpoint CCR 服务端点
     * @param apiKey API 密钥
     * @param defaultImage 默认 Docker 镜像
     */
    public TeleportService(String ccrEndpoint, String apiKey, String defaultImage) {
        this.ccrClient = new CCRClient(ccrEndpoint, apiKey);
        this.defaultImage = defaultImage;
    }

    /**
     * 远程启动 teammate
     * 对应: teleportToRemote()
     *
     * @param config 启动配置
     * @return 远程 teammate
     */
    public RemoteTeammate teleportToRemote(TeleportConfig config) {
        log.info("Teleporting teammate: name={}, image={}", config.name, config.image);

        // 1. 准备远程环境
        prepareRemoteEnvironment(config);

        // 2. 创建 CCR 会话
        CCRClient.CreateSessionParams params = new CCRClient.CreateSessionParams()
                .setImage(config.image != null ? config.image : defaultImage)
                .setCommand(config.command != null ? config.command : "/bin/bash")
                .setEnvironment(config.environment);

        RemoteSession session = ccrClient.createSession(params);

        // 3. 发送初始 prompt（如果有）
        if (config.prompt != null && !config.prompt.isEmpty()) {
            ccrClient.sendCommand(session.getSessionId(), config.prompt);
        }

        // 4. 创建并返回 RemoteTeammate
        RemoteTeammate teammate = new RemoteTeammate(
                session.getSessionId(),
                config.name,
                config.color,
                session,
                ccrClient
        );
        teammates.put(teammate.getId(), teammate);

        log.info("Teleported teammate: id={}, name={}", teammate.getId(), config.name);
        return teammate;
    }

    /**
     * 准备远程环境
     * 对应: prepareEnvironment()
     *
     * @param config 启动配置
     */
    private void prepareRemoteEnvironment(TeleportConfig config) {
        // 如果需要，可以在这里：
        // 1. 克隆代码仓库
        // 2. 安装依赖
        // 3. 设置环境变量
        // 4. 验证网络连接
        log.debug("Preparing remote environment for: {}", config.name);
    }

    /**
     * 获取远程 teammate
     *
     * @param id teammate ID
     * @return RemoteTeammate
     */
    public RemoteTeammate getTeammate(String id) {
        return teammates.get(id);
    }

    /**
     * 终止远程 teammate
     *
     * @param id teammate ID
     */
    public void terminateTeammate(String id) {
        RemoteTeammate teammate = teammates.remove(id);
        if (teammate != null) {
            teammate.close();
            log.info("Terminated teammate: id={}", id);
        }
    }

    /**
     * 列出所有远程 teammate
     *
     * @return teammate ID 列表
     */
    public java.util.List<String> listTeammates() {
        return new java.util.ArrayList<>(teammates.keySet());
    }

    /**
     * 关闭所有 teammate
     */
    public void shutdown() {
        for (String id : java.util.List.copyOf(teammates.keySet())) {
            terminateTeammate(id);
        }
        ccrClient.shutdown();
    }

    /**
     * 启动配置
     */
    public static class TeleportConfig {
        private String name;
        private String color;
        private String image;
        private String command;
        private String prompt;
        private String workdir;
        private Map<String, String> environment;

        public TeleportConfig() {
        }

        public String getName() { return name; }
        public TeleportConfig setName(String name) { this.name = name; return this; }
        public String getColor() { return color; }
        public TeleportConfig setColor(String color) { this.color = color; return this; }
        public String getImage() { return image; }
        public TeleportConfig setImage(String image) { this.image = image; return this; }
        public String getCommand() { return command; }
        public TeleportConfig setCommand(String command) { this.command = command; return this; }
        public String getPrompt() { return prompt; }
        public TeleportConfig setPrompt(String prompt) { this.prompt = prompt; return this; }
        public String getWorkdir() { return workdir; }
        public TeleportConfig setWorkdir(String workdir) { this.workdir = workdir; return this; }
        public Map<String, String> getEnvironment() { return environment; }
        public TeleportConfig setEnvironment(Map<String, String> environment) { this.environment = environment; return this; }
    }

    /**
     * 远程 teammate
     */
    public static class RemoteTeammate {
        private final String id;
        private final String name;
        private final String color;
        private final RemoteSession session;
        private final CCRClient ccrClient;
        private volatile boolean closed = false;

        RemoteTeammate(String id, String name, String color, RemoteSession session, CCRClient ccrClient) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.session = session;
            this.ccrClient = ccrClient;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getColor() { return color; }
        public boolean isClosed() { return closed; }

        public void sendCommand(String command) {
            if (closed) {
                throw new CCRException("Teammate is closed");
            }
            ccrClient.sendCommand(session.getSessionId(), command);
        }

        public String pollOutput() {
            if (closed) {
                return "";
            }
            return session.pollOutput();
        }

        public String getOutput() {
            if (closed) {
                return "";
            }
            return session.getAndClearOutput();
        }

        public java.util.List<String> drainOutput() {
            if (closed) {
                return new java.util.ArrayList<>();
            }
            return session.drainOutput();
        }

        public void close() {
            if (!closed) {
                closed = true;
                ccrClient.terminateSession(session.getSessionId());
            }
        }
    }
}
