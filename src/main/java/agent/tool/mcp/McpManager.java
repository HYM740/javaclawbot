package agent.tool.mcp;

import agent.tool.Tool;
import agent.tool.ToolRegistry;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.json.JsonMapper;
import config.ConfigSchema;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * MCP 连接管理器（官方 Java SDK 版）
 *
 * 作用：
 * 1. 使用官方 Java SDK 连接所有 MCP server
 * 2. initialize 后拉取 tools/list
 * 3. 将远端工具包装成本地 Tool 并注册到 mcpTools
 * 4. 提供 snapshotRegistry() 给每次请求构建工具视图使用
 */
public class McpManager {

    private final Map<String, ConfigSchema.MCPServerConfig> mcpServers;
    private final Executor executor;

    /**
     * 专门存放 MCP 动态工具
     */
    private final ToolRegistry mcpTools = new ToolRegistry();

    /**
     * 已连接的 server 句柄
     */
    private final Map<String, ServerHandle> handles = new LinkedHashMap<>();

    private final Object connectLock = new Object();
    private volatile CompletableFuture<Void> currentConnectFuture;
    private volatile boolean connected = false;

    public McpManager(Map<String, ConfigSchema.MCPServerConfig> mcpServers, Executor executor) {
        this.mcpServers = (mcpServers == null) ? Map.of() : mcpServers;
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * 确保 MCP 已连接。
     *
     * 注意：
     * - 不会像旧版一样在“连接中”时返回假的 completedFuture
     * - 并发调用会复用同一个 connectFuture
     */
    public CompletionStage<Void> ensureConnected() {
        synchronized (connectLock) {
            if (connected || mcpServers.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            if (currentConnectFuture != null && !currentConnectFuture.isDone()) {
                return currentConnectFuture;
            }

            currentConnectFuture = CompletableFuture.runAsync(() -> {
                for (Map.Entry<String, ConfigSchema.MCPServerConfig> entry : mcpServers.entrySet()) {
                    String serverName = entry.getKey();
                    ConfigSchema.MCPServerConfig cfg = entry.getValue();

                    McpAsyncClient client = createClient(cfg);

                    // 1) initialize（你这版 SDK 是无参 initialize()）
                    client.initialize().block(Duration.ofSeconds(20));

                    // 2) list tools（你这版 SDK 也是无参 listTools()）
                    McpSchema.ListToolsResult toolsResult =
                            client.listTools().block(Duration.ofSeconds(20));

                    List<Tool> registered = new ArrayList<>();

                    if (toolsResult != null && toolsResult.tools() != null) {
                        for (McpSchema.Tool tool : toolsResult.tools()) {
                            Map<String, Object> inputSchema = convertToolSchema(tool.inputSchema());

                            Tool wrapper = new OfficialMcpToolWrapper(
                                    serverName,
                                    tool.name(),
                                    tool.description(),
                                    inputSchema,
                                    client,
                                    resolveToolTimeout(cfg)
                            );

                            mcpTools.register(wrapper);
                            registered.add(wrapper);
                        }
                    }

                    handles.put(serverName, new ServerHandle(serverName, client, registered));
                }

                connected = true;
            }, executor);

            return currentConnectFuture;
        }
    }

    /**
     * 返回 MCP 工具快照。
     * 避免直接把内部 registry 暴露出去，防止请求期误操作。
     */
    public ToolRegistry snapshotRegistry() {
        ToolRegistry copy = new ToolRegistry();
        for (String name : mcpTools.toolNames()) {
            Tool t = mcpTools.get(name);
            if (t != null) {
                copy.register(t);
            }
        }
        return copy;
    }

    public boolean isConnected() {
        return connected;
    }

    public CompletionStage<Void> closeAll() {
        return CompletableFuture.runAsync(() -> {
            for (ServerHandle handle : handles.values()) {
                for (Tool tool : handle.registeredTools()) {
                    mcpTools.unregister(tool.name());
                }

                try {
                    handle.client().closeGracefully().block(Duration.ofSeconds(5));
                } catch (Exception ignored) {
                }
            }

            handles.clear();
            connected = false;
            currentConnectFuture = null;
        }, executor);
    }

    private McpAsyncClient createClient(ConfigSchema.MCPServerConfig cfg) {
        McpClientTransport transport = createTransport(cfg);

        return McpClient.async(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .initializationTimeout(Duration.ofSeconds(20))
                .build();
    }

    private McpJsonMapper createJsonMapper() {
        return new JacksonMcpJsonMapper(JsonMapper.builder().build());
    }
    private McpClientTransport createTransport(ConfigSchema.MCPServerConfig cfg) {
        String transportType = determineTransportType(cfg);

        switch (transportType) {
            case "stdio" -> {
                ServerParameters.Builder builder = ServerParameters.builder(cfg.getCommand());

                if (cfg.getArgs() != null && !cfg.getArgs().isEmpty()) {
                    builder.args(cfg.getArgs());
                }
                if (cfg.getEnv() != null && !cfg.getEnv().isEmpty()) {
                    builder.env(cfg.getEnv());
                }

                return new StdioClientTransport(
                        builder.build(),
                        createJsonMapper()
                );
            }
            case "sse" -> {
                HttpClientSseClientTransport.Builder builder =
                        HttpClientSseClientTransport.builder(cfg.getUrl());

                if (StrUtil.isNotBlank(cfg.getUrl())) {
                    builder.sseEndpoint(cfg.getUrl());
                }

                if (cfg.getHeaders() != null && !cfg.getHeaders().isEmpty()) {
                    builder.customizeRequest(req -> {
                        for (Map.Entry<String, String> e : cfg.getHeaders().entrySet()) {
                            req.header(e.getKey(), e.getValue());
                        }
                    });
                }

                return builder.build();
            }
            case "streamableHttp", "streamable_http", "streamable-http" -> {
                // 先尽量走最小可用版本
                return HttpClientStreamableHttpTransport
                        .builder(cfg.getUrl())
                        .build();
            }
            default -> throw new IllegalArgumentException("未知 MCP transport: " + transportType);
        }
    }

    private static String determineTransportType(ConfigSchema.MCPServerConfig cfg) {
        if (cfg.getType() != null && !cfg.getType().isBlank()) {
            return cfg.getType();
        }

        if (cfg.getCommand() != null && !cfg.getCommand().isBlank()) {
            return "stdio";
        }

        if (cfg.getUrl() != null && !cfg.getUrl().isBlank()) {
            String url = cfg.getUrl().replaceAll("/$", "");
            if (url.endsWith("/sse")) {
                return "sse";
            }
            return "streamableHttp";
        }

        throw new IllegalArgumentException("MCP server 缺少 type/command/url");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertToolSchema(Object schemaObj) {
        if (schemaObj == null) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("type", "object");
            fallback.put("properties", new LinkedHashMap<String, Object>());
            return fallback;
        }

        if (schemaObj instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("type", "object");
        fallback.put("properties", new LinkedHashMap<String, Object>());
        return fallback;
    }

    private static Duration resolveToolTimeout(ConfigSchema.MCPServerConfig cfg) {
        Integer seconds = cfg.getToolTimeout();
        if (seconds == null || seconds <= 0) {
            return Duration.ofSeconds(60);
        }
        return Duration.ofSeconds(seconds);
    }

    private record ServerHandle(
            String serverName,
            McpAsyncClient client,
            List<Tool> registeredTools
    ) {
    }
}