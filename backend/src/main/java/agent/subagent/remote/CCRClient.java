package agent.subagent.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CCR (Cloud Code Runtime) 客户端
 *
 * 对应 Open-ClaudeCode: src/tools/shared/spawnMultiAgent.ts - CCRClient
 */
public class CCRClient {

    private static final Logger log = LoggerFactory.getLogger(CCRClient.class);

    private final String ccrEndpoint;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Map<String, RemoteSession> sessions = new ConcurrentHashMap<>();
    private final long sessionTimeoutMs;

    public CCRClient(String ccrEndpoint, String apiKey) {
        this(ccrEndpoint, apiKey, 300000);
    }

    public CCRClient(String ccrEndpoint, String apiKey, long sessionTimeoutMs) {
        this.ccrEndpoint = ccrEndpoint;
        this.apiKey = apiKey;
        this.sessionTimeoutMs = sessionTimeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public RemoteSession createSession(CreateSessionParams params) {
        log.info("Creating CCR session: image={}, command={}", params.image, params.command);

        try {
            String requestBody = toJson(params);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ccrEndpoint + "/sessions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new CCRException("Failed to create session: HTTP " + response.statusCode());
            }

            SessionInfo info = fromJson(response.body());

            RemoteSession session = new RemoteSession(
                    info.sessionId,
                    info.endpoint,
                    ccrEndpoint,
                    apiKey,
                    sessionTimeoutMs
            );
            sessions.put(info.sessionId, session);

            log.info("Created CCR session: id={}, endpoint={}", info.sessionId, info.endpoint);
            return session;

        } catch (CCRException e) {
            throw e;
        } catch (Exception e) {
            throw new CCRException("Failed to create session: " + e.getMessage(), e);
        }
    }

    public void sendCommand(String sessionId, String command) {
        RemoteSession session = sessions.get(sessionId);
        if (session == null) {
            throw new CCRException("Session not found: " + sessionId);
        }

        try {
            String requestBody = "{\"command\": \"" + escapeJson(command) + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(session.getEndpoint() + "/input"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new CCRException("Failed to send command: HTTP " + response.statusCode());
            }

            log.debug("Sent command to session {}: {}", sessionId, command);

        } catch (CCRException e) {
            throw e;
        } catch (Exception e) {
            throw new CCRException("Failed to send command: " + e.getMessage(), e);
        }
    }

    public void terminateSession(String sessionId) {
        RemoteSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ccrEndpoint + "/sessions/" + sessionId))
                    .header("Authorization", "Bearer " + apiKey)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                log.warn("Failed to terminate session {}: HTTP {}", sessionId, response.statusCode());
            } else {
                log.info("Terminated CCR session: {}", sessionId);
            }

        } catch (Exception e) {
            log.warn("Failed to terminate session {}: {}", sessionId, e.getMessage());
        }
    }

    public RemoteSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public java.util.List<String> listSessions() {
        return new java.util.ArrayList<>(sessions.keySet());
    }

    public void shutdown() {
        for (String sessionId : java.util.List.copyOf(sessions.keySet())) {
            terminateSession(sessionId);
        }
    }

    private String toJson(CreateSessionParams params) {
        return "{\"image\": \"" + escapeJson(params.image) + "\", \"command\": \"" + escapeJson(params.command) + "\"}";
    }

    private SessionInfo fromJson(String json) {
        SessionInfo info = new SessionInfo();
        info.sessionId = extractJsonField(json, "sessionId");
        info.endpoint = extractJsonField(json, "endpoint");
        return info;
    }

    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            pattern = "\"" + field + "\": \"";
            start = json.indexOf(pattern);
        }
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static class CreateSessionParams {
        private String image;
        private String command;
        private Map<String, String> environment;

        public String getImage() { return image; }
        public CreateSessionParams setImage(String image) { this.image = image; return this; }
        public String getCommand() { return command; }
        public CreateSessionParams setCommand(String command) { this.command = command; return this; }
        public Map<String, String> getEnvironment() { return environment; }
        public CreateSessionParams setEnvironment(Map<String, String> environment) { this.environment = environment; return this; }
    }

    public static class SessionInfo {
        public String sessionId;
        public String endpoint;
    }
}
