package memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 会话文件处理工具
 *
 * 对齐 OpenClaw 的 session-files.ts
 *
 * 功能：
 * - 列出会话目录下的所有 .jsonl 文件
 * - 解析 JSONL 会话文件，提取消息文本
 * - 生成索引用的 SessionFileEntry
 */
public class SessionFiles {

    private static final Logger log = LoggerFactory.getLogger(SessionFiles.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 会话文件条目
     */
    public static class SessionFileEntry {
        /** 相对路径（如 "sessions/amber-atlas.jsonl"） */
        public final String path;

        /** 绝对路径 */
        public final String absPath;

        /** 修改时间（毫秒） */
        public final long mtimeMs;

        /** 文件大小（字节） */
        public final long size;

        /** 内容哈希 */
        public final String hash;

        /** 提取的文本内容 */
        public final String content;

        /** 行号映射：内容行索引 -> JSONL 源行号（1-based） */
        public final int[] lineMap;

        public SessionFileEntry(String path, String absPath, long mtimeMs, long size,
                                String hash, String content, int[] lineMap) {
            this.path = path;
            this.absPath = absPath;
            this.mtimeMs = mtimeMs;
            this.size = size;
            this.hash = hash;
            this.content = content;
            this.lineMap = lineMap;
        }
    }

    /**
     * 列出会话目录下的所有 .jsonl 文件
     *
     * @param sessionsDir 会话目录
     * @return 文件路径列表（绝对路径）
     */
    public static List<Path> listSessionFiles(Path sessionsDir) {
        if (!Files.exists(sessionsDir) || !Files.isDirectory(sessionsDir)) {
            return Collections.emptyList();
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionsDir, "*.jsonl")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        } catch (IOException e) {
            log.warn("列出会话文件失败: {}", sessionsDir, e);
        }

        return files;
    }

    /**
     * 构建会话文件条目
     *
     * @param absPath     文件绝对路径
     * @param sessionsDir 会话目录（用于计算相对路径）
     * @return 会话文件条目，解析失败返回 null
     */
    public static SessionFileEntry buildSessionEntry(Path absPath, Path sessionsDir) {
        try {
            // 获取文件信息
            long mtimeMs = Files.getLastModifiedTime(absPath).toMillis();
            long size = Files.size(absPath);

            // 读取文件内容
            String raw = Files.readString(absPath, StandardCharsets.UTF_8);
            String[] lines = raw.split("\n");

            // 解析 JSONL，提取消息
            List<String> collected = new ArrayList<>();
            List<Integer> lineMap = new ArrayList<>();

            for (int jsonlIdx = 0; jsonlIdx < lines.length; jsonlIdx++) {
                String line = lines[jsonlIdx].trim();
                if (line.isEmpty()) {
                    continue;
                }

                JsonNode record;
                try {
                    record = MAPPER.readTree(line);
                } catch (Exception e) {
                    // 解析失败，跳过
                    continue;
                }

                // 检查是否为消息记录
                if (!record.has("type") || !"message".equals(record.get("type").asText())) {
                    continue;
                }

                // 获取 message 对象
                JsonNode messageNode = record.get("message");
                if (messageNode == null || !messageNode.isObject()) {
                    continue;
                }

                // 获取角色
                String role = messageNode.has("role") ? messageNode.get("role").asText() : "";
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }

                // 提取文本内容
                String text = extractSessionText(messageNode.get("content"));
                if (text == null || text.isEmpty()) {
                    continue;
                }

                // 敏感信息脱敏（简化版）
                String safeText = redactSensitiveText(text);

                // 添加标签
                String label = "user".equals(role) ? "User" : "Assistant";
                collected.add(label + ": " + safeText);
                lineMap.add(jsonlIdx + 1); // JSONL 行号（1-based）
            }

            // 构建内容
            String content = String.join("\n", collected);
            int[] lineMapArray = lineMap.stream().mapToInt(Integer::intValue).toArray();

            // 计算哈希
            String hashInput = content + "\n" + Arrays.toString(lineMapArray);
            String hash = hashText(hashInput);

            // 计算相对路径
            String relativePath = "sessions/" + absPath.getFileName().toString();

            return new SessionFileEntry(
                    relativePath,
                    absPath.toString(),
                    mtimeMs,
                    size,
                    hash,
                    content,
                    lineMapArray
            );

        } catch (IOException e) {
            log.debug("读取会话文件失败: {}", absPath, e);
            return null;
        }
    }

    /**
     * 从消息内容中提取文本
     *
     * @param content 消息内容节点
     * @return 提取的文本，无内容返回 null
     */
    public static String extractSessionText(JsonNode content) {
        if (content == null) {
            return null;
        }

        // 字符串类型
        if (content.isTextual()) {
            String normalized = normalizeSessionText(content.asText());
            return normalized.isEmpty() ? null : normalized;
        }

        // 数组类型（多块内容）
        if (content.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode block : content) {
                if (!block.isObject()) {
                    continue;
                }

                String type = block.has("type") ? block.get("type").asText() : "";
                if (!"text".equals(type)) {
                    continue;
                }

                String text = block.has("text") ? block.get("text").asText() : "";
                String normalized = normalizeSessionText(text);
                if (!normalized.isEmpty()) {
                    parts.add(normalized);
                }
            }

            return parts.isEmpty() ? null : String.join(" ", parts);
        }

        return null;
    }

    /**
     * 规范化会话文本
     *
     * - 移除多余空白
     * - 合并换行
     */
    public static String normalizeSessionText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("\\s*\\n+\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 敏感信息脱敏（简化版）
     *
     * 对齐 OpenClaw 的 redactSensitiveText
     */
    public static String redactSensitiveText(String text) {
        if (text == null) {
            return null;
        }

        // 脱敏 API Key
        String result = text.replaceAll(
                "(?i)(api[_-]?key|token|secret|password|auth)\\s*[:=]\\s*['\"]?[a-zA-Z0-9_-]{10,}['\"]?",
                "$1: [REDACTED]"
        );

        // 脱敏邮箱
        result = result.replaceAll(
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
                "[EMAIL REDACTED]"
        );

        return result;
    }

    /**
     * 计算文本哈希
     */
    public static String hashText(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    /**
     * 生成会话相对路径
     *
     * @param fileName 文件名（如 "amber-atlas.jsonl"）
     * @return 相对路径（如 "sessions/amber-atlas.jsonl"）
     */
    public static String sessionPathForFile(String fileName) {
        return "sessions/" + fileName;
    }
}