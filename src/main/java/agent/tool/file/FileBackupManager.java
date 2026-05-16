package agent.tool.file;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件备份管理器。
 * 在 EditTool/WriteTool 执行前备份原文件，支持多版本追踪、查询、回滚。
 *
 * 路径策略：
 * - 开发者模式 (restrictToWorkspace=false)：{workspace}/.backup/{sessionId}/
 * - 非开发者模式 (restrictToWorkspace=true)：{userDataDir}/backup/{sessionId}/
 *
 * 同一个文件被多次修改时，每次修改都会保留一个备份版本。
 */
@Slf4j
public class FileBackupManager {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS");
    private static final long MAX_BACKUP_FILE_SIZE = 100L * 1024 * 1024; // 100 MB
    private static final int MAX_VERSIONS_PER_FILE = 50;

    /**
     * 备份条目：一个文件的一个备份版本。
     */
    public record BackupEntry(
            Path backupFilePath,
            String timestamp,
            Path originalPath,
            long fileSize,
            String toolCallId
    ) {}

    /** 备份根目录 */
    private final Path backupRoot;
    /** 索引：被修改文件绝对路径 → 有序备份列表（最早的在前，最新的在后） */
    private final Map<Path, List<BackupEntry>> index = new LinkedHashMap<>();
    /** 索引文件路径 */
    private final Path indexPath;

    /**
     * @param backupRoot 备份根目录
     */
    public FileBackupManager(Path backupRoot) {
        this.backupRoot = backupRoot;
        this.indexPath = backupRoot.resolve(".backup_index.json");
        loadIndex();
        log.debug("FileBackupManager initialized, backupRoot={}", backupRoot);
    }

    /**
     * 获取备份根路径。
     */
    public Path getBackupRoot() {
        return backupRoot;
    }

    // ===== 备份 =====

    /**
     * 备份指定文件的内容。
     *
     * @param originalPath 原文件路径
     * @param content      原文件内容（修改前的完整内容）
     * @return 备份条目，如果跳过备份则返回 null
     */
    public synchronized BackupEntry backup(Path originalPath, String content) {
        return backup(originalPath, content, null);
    }

    /**
     * 备份指定文件的内容（带工具调用 ID）。
     *
     * @param originalPath 原文件路径
     * @param content      原文件内容（修改前的完整内容）
     * @param toolCallId   触发备份的工具调用 ID（edit_file/write_file）
     * @return 备份条目，如果跳过备份则返回 null
     */
    public synchronized BackupEntry backup(Path originalPath, String content, String toolCallId) {
        if (content == null || content.isEmpty()) return null;
        try {
            long size = content.getBytes(StandardCharsets.UTF_8).length;
            if (size > MAX_BACKUP_FILE_SIZE) {
                log.warn("文件过大跳过备份: {} ({} bytes)", originalPath, size);
                return null;
            }

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String safeName = originalPath.toString()
                    .replaceAll("[\\\\/:*?\"<>|]", "_");
            String backupFileName = safeName + "_" + timestamp + "_before"
                    + getExtension(originalPath);
            Path backupFile = backupRoot.resolve(backupFileName);

            Files.createDirectories(backupRoot);
            Files.writeString(backupFile, content, StandardCharsets.UTF_8);

            BackupEntry entry = new BackupEntry(backupFile, timestamp, originalPath.normalize(), size, toolCallId);
            index.computeIfAbsent(originalPath.normalize(), k -> new ArrayList<>()).add(entry);

            // 修剪超出版本限制的旧备份
            pruneVersions(originalPath.normalize());
            saveIndex();

            if (log.isDebugEnabled()) {
                log.debug("文件已备份: {} -> {} (toolCallId={})", originalPath, backupFile, toolCallId);
            }
            return entry;
        } catch (Exception e) {
            log.error("备份文件失败: {}", originalPath, e);
            return null;
        }
    }

    // ===== 回滚 =====

    /**
     * 回滚文件到指定备份版本。
     *
     * @param originalPath 原文件路径
     * @param backupEntry  目标备份版本
     * @return true 表示回滚成功
     */
    public synchronized boolean restore(Path originalPath, BackupEntry backupEntry) {
        try {
            if (!Files.exists(backupEntry.backupFilePath())) {
                log.error("备份文件不存在: {}", backupEntry.backupFilePath());
                return false;
            }
            String content = Files.readString(backupEntry.backupFilePath(), StandardCharsets.UTF_8);
            Files.writeString(originalPath, content, StandardCharsets.UTF_8);
            log.info("文件已回滚: {} -> 版本 {}", originalPath, backupEntry.timestamp());
            return true;
        } catch (Exception e) {
            log.error("回滚文件失败: {}", originalPath, e);
            return false;
        }
    }

    // ===== 查询 =====

    /**
     * 获取指定文件的所有备份版本（按时间升序）。
     */
    public List<BackupEntry> getVersions(Path originalPath) {
        List<BackupEntry> entries = index.get(originalPath.normalize());
        if (entries == null) return List.of();
        return Collections.unmodifiableList(entries);
    }

    /**
     * 获取当前会话所有被修改的文件路径。
     */
    public Set<Path> getAllModifiedFiles() {
        return Collections.unmodifiableSet(index.keySet());
    }

    /**
     * 获取所有备份条目（不分文件分组）。
     */
    public List<BackupEntry> getAllBackups() {
        return index.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 获取备份统计信息。
     */
    public BackupStats getStats() {
        int fileCount = index.size();
        int versionCount = (int) index.values().stream().mapToLong(List::size).sum();
        return new BackupStats(fileCount, versionCount);
    }

    public record BackupStats(int fileCount, int versionCount) {}

    /**
     * 清理所有备份条目（索引 + 文件）。
     */
    public synchronized void clearAll() {
        for (List<BackupEntry> entries : index.values()) {
            for (BackupEntry be : entries) {
                try {
                    Files.deleteIfExists(be.backupFilePath());
                } catch (IOException e) {
                    log.warn("删除备份文件失败: {}", be.backupFilePath(), e);
                }
            }
        }
        index.clear();
        try {
            Files.deleteIfExists(indexPath);
        } catch (IOException e) {
            log.warn("删除备份索引失败", e);
        }
    }

    // ===== 私有方法 =====

    private static String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    /**
     * 修剪超出版本限制的旧备份（保留最新的 MAX_VERSIONS_PER_FILE 个）。
     */
    private void pruneVersions(Path normalizedPath) {
        List<BackupEntry> entries = index.get(normalizedPath);
        if (entries != null && entries.size() > MAX_VERSIONS_PER_FILE) {
            int toRemove = entries.size() - MAX_VERSIONS_PER_FILE;
            for (int i = 0; i < toRemove; i++) {
                BackupEntry old = entries.remove(0);
                try {
                    Files.deleteIfExists(old.backupFilePath());
                } catch (IOException e) {
                    log.warn("删除旧备份失败: {}", old.backupFilePath(), e);
                }
            }
            log.info("已修剪 {} 的旧备份，保留 {} 个版本", normalizedPath, MAX_VERSIONS_PER_FILE);
        }
    }

    // ===== 索引持久化 =====

    /**
     * 保存索引到磁盘。
     * 格式：每行一个 JSON 对象。
     */
    private void saveIndex() {
        try {
            Files.createDirectories(backupRoot);
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (var entry : index.entrySet()) {
                for (BackupEntry be : entry.getValue()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append(String.format(
                            "{\"original\":\"%s\",\"backup\":\"%s\",\"timestamp\":\"%s\",\"size\":%d,\"toolCallId\":\"%s\"}",
                            escapeJson(entry.getKey().toString()),
                            escapeJson(be.backupFilePath().toString()),
                            escapeJson(be.timestamp()),
                            be.fileSize(),
                            escapeJson(be.toolCallId() != null ? be.toolCallId() : "")
                    ));
                }
            }
            json.append("]");
            Files.writeString(indexPath, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("保存备份索引失败", e);
        }
    }

    /**
     * 从磁盘加载索引。
     */
    private void loadIndex() {
        if (!Files.exists(indexPath)) return;
        try {
            String json = Files.readString(indexPath, StandardCharsets.UTF_8);
            if (json.isBlank() || json.equals("[]")) return;

            index.clear();
            String content = json.trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1);
            }
            if (content.isBlank()) return;

            // 按 "},{" 分割（简化解析）
            String[] items = content.split("\\},\\{");
            for (String item : items) {
                item = item.replaceAll("[{}]", "");
                String orig = extractJsonValue(item, "original");
                String backup = extractJsonValue(item, "backup");
                String ts = extractJsonValue(item, "timestamp");
                String sizeStr = extractJsonValue(item, "size");
                String tcId = extractJsonValue(item, "toolCallId");
                if (orig == null || backup == null || ts == null) continue;

                Path origPath = Path.of(orig).normalize();
                Path backupPath = Path.of(backup);
                long size = 0;
                try {
                    size = Long.parseLong(sizeStr != null ? sizeStr : "0");
                } catch (NumberFormatException ignored) {}

                index.computeIfAbsent(origPath, k -> new ArrayList<>())
                        .add(new BackupEntry(backupPath, ts, origPath, size,
                                (tcId != null && !tcId.isEmpty()) ? tcId : null));
            }
        } catch (IOException e) {
            log.warn("加载备份索引失败", e);
        }
    }

    /**
     * 根据工具调用 ID 查找对应的备份条目。
     * 用于历史会话恢复时精确匹配工具卡片与备份版本。
     *
     * @param originalPath 原文件路径
     * @param toolCallId   工具调用 ID
     * @return 匹配的备份条目，未找到返回 null
     */
    public BackupEntry getBackupByToolCallId(Path originalPath, String toolCallId) {
        if (toolCallId == null) return null;
        List<BackupEntry> entries = index.get(originalPath.normalize());
        if (entries == null) return null;
        for (BackupEntry be : entries) {
            if (toolCallId.equals(be.toolCallId())) return be;
        }
        return null;
    }

    /**
     * 简易 JSON 字段值提取（不含嵌套对象/数组支持）。
     */
    private static String extractJsonValue(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx);
        if (colonIdx < 0) return null;
        String after = json.substring(colonIdx + 1).trim();
        if (after.startsWith("\"")) {
            int endQuote = findJsonStringEnd(after, 1);
            return endQuote > 1 ? after.substring(1, endQuote) : null;
        }
        // 数字值
        int end = after.indexOf(',');
        if (end < 0) end = after.indexOf(']');
        if (end < 0) end = after.length();
        return after.substring(0, end).trim();
    }

    private static int findJsonStringEnd(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++; // skip escaped char
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 简易 JSON 字符串转义（仅处理反斜杠和双引号）。
     */
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String toString() {
        return "FileBackupManager{backupRoot=" + backupRoot + ", files=" + index.size() + "}";
    }
}
