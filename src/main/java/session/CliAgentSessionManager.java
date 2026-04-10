package session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import providers.cli.CliEvent;
import providers.cli.CliEventType;
import utils.Helpers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CLI Agent Session 管理器
 *
 * 管理 CLI Agent (Claude Code/OpenCode) 的独立会话存储，避免污染主代理上下文。
 *
 * 存放路径: {workspace}/sessions/cliagent/
 * 文件命名: {project}_{agentType}_{sessionId}.jsonl
 *
 * 功能:
 * - 保存事件到独立 session 文件
 * - 加载历史事件
 * - 支持多个 CLI Agent 会话独立管理
 */
@Slf4j
public class CliAgentSessionManager {

    @Getter
    private final Path workspace;
    private final Path sessionsDir;

    /** session key -> lock，避免并发写 */
    private final Map<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CliAgentSessionManager(Path workspace) {
        this.workspace = workspace;
        this.sessionsDir = Helpers.ensureDir(workspace.resolve("sessions").resolve("cliagent"));
        log.info("CliAgentSessionManager initialized at: {}", sessionsDir);
    }

    /**
     * 保存事件到独立 session 文件
     *
     * @param project    项目名
     * @param agentType  Agent 类型 (claude/opencode)
     * @param sessionId  会话 ID
     * @param sessionKey 通道 sessionKey (格式: channel:chatId)
     * @param event      事件
     */
    public void saveEvent(String project, String agentType, String sessionId, String sessionKey, CliEvent event) {
        if (event == null || event.type() == null) return;

        Path target = getSessionFile(project, agentType, sessionId, sessionKey);
        String key = buildKey(project, agentType, sessionId, sessionKey);

        ReentrantLock lock = sessionLocks.computeIfAbsent(key, k -> new ReentrantLock());

        try {
            lock.lock();

            // 确保目录存在
            Helpers.ensureDir(target.getParent());

            // 构建事件记录（包含 sessionKey）
            Map<String, Object> eventRecord = buildEventRecord(event, sessionKey);

            // 追加写入
            try (BufferedWriter w = Files.newBufferedWriter(
                    target,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                w.write(objectMapper.writeValueAsString(eventRecord));
                w.write("\n");
            }

            log.trace("Saved CLI event: file={}, type={}", target.getFileName(), event.type());

        } catch (Exception e) {
            log.warn("Failed to save CLI event: file={}, error={}", target, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 批量保存事件
     */
    public void saveEvents(String project, String agentType, String sessionId, String sessionKey, List<CliEvent> events) {
        if (events == null || events.isEmpty()) return;

        Path target = getSessionFile(project, agentType, sessionId, sessionKey);
        String key = buildKey(project, agentType, sessionId, sessionKey);

        ReentrantLock lock = sessionLocks.computeIfAbsent(key, k -> new ReentrantLock());

        try {
            lock.lock();

            Helpers.ensureDir(target.getParent());

            try (BufferedWriter w = Files.newBufferedWriter(
                    target,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                for (CliEvent event : events) {
                    if (event == null || event.type() == null) continue;

                    Map<String, Object> eventRecord = buildEventRecord(event, sessionKey);
                    w.write(objectMapper.writeValueAsString(eventRecord));
                    w.write("\n");
                }
            }

            log.debug("Saved {} CLI events: file={}", events.size(), target.getFileName());

        } catch (Exception e) {
            log.warn("Failed to save CLI events: file={}, error={}", target, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取 session 文件路径
     *
     * 命名规则: {channel}_{chatId}_{project}_{agentType}_{sessionId}.jsonl
     * 主代理可以通过 sessionKey (channel:chatId) 找到对应的文件
     *
     * @param project    项目名
     * @param agentType  Agent 类型
     * @param sessionId  CLI 会话 ID
     * @param sessionKey 通道 sessionKey (格式: channel:chatId)
     */
    public Path getSessionFile(String project, String agentType, String sessionId, String sessionKey) {
        // 从 sessionKey 提取 channel 和 chatId
        String channelPart = "unknown";
        String chatIdPart = "default";

        if (sessionKey != null && !sessionKey.isBlank()) {
            String[] parts = sessionKey.split(":", 2);
            if (parts.length >= 1 && parts[0] != null) {
                channelPart = Helpers.safeFilename(parts[0]);
            }
            if (parts.length >= 2 && parts[1] != null) {
                chatIdPart = Helpers.safeFilename(parts[1]);
            }
        }

        String safeProject = Helpers.safeFilename(project);
        String safeAgentType = Helpers.safeFilename(agentType);
        String safeSessionId = sessionId != null ? Helpers.safeFilename(sessionId) : "default";

        // 文件名: {channel}_{chatId}_{project}_{agentType}_{sessionId}.jsonl
        String filename = channelPart + "_" + chatIdPart + "_" +
                          safeProject + "_" + safeAgentType + "_" + safeSessionId + ".jsonl";

        return sessionsDir.resolve(filename);
    }

    /**
     * 兼容旧版本的 getSessionFile（无 sessionKey）
     */
    public Path getSessionFile(String project, String agentType, String sessionId) {
        return getSessionFile(project, agentType, sessionId, null);
    }

    /**
     * 加载历史事件
     */
    public List<Map<String, Object>> loadEvents(String project, String agentType, String sessionId) {
        Path path = getSessionFile(project, agentType, sessionId);
        if (!Files.exists(path)) {
            return List.of();
        }

        List<Map<String, Object>> events = new ArrayList<>();
        int lineNo = 0;
        int badLines = 0;

        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    Map<String, Object> data = objectMapper.readValue(
                            line,
                            new TypeReference<Map<String, Object>>() {}
                    );
                    events.add(data);
                } catch (Exception ex) {
                    badLines++;
                    log.warn("Bad line in CLI session file: path={}, line={}, error={}",
                            path, lineNo, ex.getMessage());
                }
            }

            if (badLines > 0) {
                log.warn("Loaded CLI events with bad lines: path={}, badLines={}", path, badLines);
            }

        } catch (Exception e) {
            log.warn("Failed to load CLI events: path={}, error={}", path, e.getMessage());
        }

        return events;
    }

    /**
     * 列出所有 CLI Agent 会话
     */
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> sessions = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionsDir, "*.jsonl")) {
            for (Path path : stream) {
                try {
                    String filename = path.getFileName().toString();
                    // 解析文件名: {channel}_{chatId}_{project}_{agentType}_{sessionId}.jsonl
                    String baseName = filename.replace(".jsonl", "");
                    String[] parts = baseName.split("_", 5);

                    if (parts.length >= 5) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("channel", parts[0]);
                        item.put("chat_id", parts[1]);
                        item.put("project", parts[2]);
                        item.put("agent_type", parts[3]);
                        item.put("session_id", parts[4]);
                        // 重建 sessionKey
                        item.put("session_key", parts[0] + ":" + parts[1]);
                        item.put("path", path.toString());
                        item.put("size", Files.size(path));
                        item.put("modified", Files.getLastModifiedTime(path).toString());
                        sessions.add(item);
                    }
                } catch (Exception ignore) {
                    // 跳过无法解析的文件
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list CLI sessions: {}", e.getMessage());
            return List.of();
        }

        // 按修改时间排序（最新的在前）
        sessions.sort((a, b) -> {
            String ma = String.valueOf(a.getOrDefault("modified", ""));
            String mb = String.valueOf(b.getOrDefault("modified", ""));
            return mb.compareTo(ma);
        });

        return sessions;
    }

    /**
     * 根据 sessionKey 查找会话文件
     *
     * @param sessionKey 通道 sessionKey (格式: channel:chatId)
     * @return 匹配的会话列表
     */
    public List<Map<String, Object>> findSessionsBySessionKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return List.of();
        }

        String[] parts = sessionKey.split(":", 2);
        String channel = parts[0];
        String chatId = parts.length > 1 ? parts[1] : "";

        String prefix = channel + "_" + chatId + "_";

        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> session : listSessions()) {
            String sk = (String) session.get("session_key");
            if (sessionKey.equals(sk)) {
                matched.add(session);
            }
        }

        return matched;
    }

    /**
     * 获取最新的 session 文件名（根据 sessionKey）
     *
     * @param sessionKey 通道 sessionKey (格式: channel:chatId)
     * @return 文件名（不含路径），如果没有找到返回 null
     */
    public String getLatestSessionFilename(String sessionKey) {
        List<Map<String, Object>> sessions = findSessionsBySessionKey(sessionKey);
        if (sessions.isEmpty()) {
            return null;
        }

        // listSessions 已按修改时间排序，第一个是最新的
        Path path = Path.of((String) sessions.get(0).get("path"));
        return path.getFileName().toString();
    }

    /**
     * 删除会话文件
     */
    public boolean deleteSession(String project, String agentType, String sessionId, String sessionKey) {
        Path path = getSessionFile(project, agentType, sessionId, sessionKey);
        String key = buildKey(project, agentType, sessionId, sessionKey);

        try {
            if (Files.exists(path)) {
                Files.delete(path);
                sessionLocks.remove(key);
                log.info("Deleted CLI session file: {}", path);
                return true;
            }
        } catch (Exception e) {
            log.warn("Failed to delete CLI session file: path={}, error={}", path, e.getMessage());
        }

        return false;
    }

    /**
     * 获取会话摘要
     */
    public String getSessionSummary(String project, String agentType, String sessionId, String sessionKey) {
        Path path = getSessionFile(project, agentType, sessionId, sessionKey);
        if (!Files.exists(path)) {
            return "Session file not found";
        }

        try {
            long size = Files.size(path);
            int lines = 0;

            try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                while (r.readLine() != null) lines++;
            }

            return String.format("Session: %s/%s/%s - %d events, %d bytes",
                    sessionKey, project, agentType, lines, size);

        } catch (Exception e) {
            return "Error reading session: " + e.getMessage();
        }
    }

    // ==================== 内部方法 ====================

    private String buildKey(String project, String agentType, String sessionId, String sessionKey) {
        return sessionKey + ":" + project + ":" + agentType + ":" + sessionId;
    }

    /**
     * 构建事件记录
     */
    private Map<String, Object> buildEventRecord(CliEvent event, String sessionKey) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", LocalDateTime.now().toString());
        record.put("type", event.type().name());

        // 记录通道信息（如果有的话）
        if (sessionKey != null && !sessionKey.isBlank()) {
            record.put("session_key", sessionKey);
            // 解析 sessionKey 获取 channel 和 chatId
            String[] parts = sessionKey.split(":", 2);
            if (parts.length >= 1) {
                record.put("channel", parts[0]);
            }
            if (parts.length >= 2) {
                record.put("chat_id", parts[1]);
            }
        }

        // 根据事件类型记录不同字段
        switch (event.type()) {
            case TEXT -> {
                record.put("content", event.content());
            }
            case THINKING -> {
                record.put("content", event.content());
            }
            case TOOL_USE -> {
                record.put("tool_name", event.toolName());
                record.put("tool_input", event.toolInput());
                if (event.toolInputRaw() != null) {
                    record.put("tool_input_raw", event.toolInputRaw());
                }
            }
            case TOOL_RESULT -> {
                record.put("tool_name", event.toolName());
                record.put("tool_result", truncate(event.toolResult(), 1000));
                record.put("tool_status", event.toolStatus());
                record.put("tool_success", event.toolSuccess());
            }
            case RESULT -> {
                record.put("content", truncate(event.content(), 500));
                record.put("session_id", event.sessionId());
                record.put("input_tokens", event.inputTokens());
                record.put("output_tokens", event.outputTokens());
                record.put("done", event.done());
            }
            case SESSION_ID -> {
                record.put("session_id", event.sessionId());
            }
            case ERROR -> {
                if (event.error() != null) {
                    record.put("error_message", event.error().getMessage());
                    record.put("error_class", event.error().getClass().getSimpleName());
                }
                if (event.content() != null) {
                    record.put("content", event.content());
                }
            }
            case PERMISSION_REQUEST -> {
                record.put("request_id", event.requestId());
                record.put("tool_name", event.toolName());
                record.put("tool_input", event.toolInput());
            }
        }

        return record;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}