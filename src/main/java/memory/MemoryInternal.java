package memory;

import org.slf4j.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.*;

/**
 * 记忆系统内部工具
 *
 * 对齐 OpenClaw 的 internal.ts
 */
public class MemoryInternal {

    private static final Logger log = LoggerFactory.getLogger(MemoryInternal.class);

    private MemoryInternal() {
        // 工具类，禁止实例化
    }

    // ==================== 文件条目 ====================

    /**
     * 记忆文件条目
     */
    public static class MemoryFileEntry {
        /** 相对路径 */
        public final String path;

        /** 绝对路径 */
        public final String absPath;

        /** 修改时间（毫秒） */
        public final long mtimeMs;

        /** 文件大小（字节） */
        public final long size;

        /** 内容哈希 */
        public final String hash;

        public MemoryFileEntry(String path, String absPath, long mtimeMs, long size, String hash) {
            this.path = path;
            this.absPath = absPath;
            this.mtimeMs = mtimeMs;
            this.size = size;
            this.hash = hash;
        }
    }

    /**
     * 记忆分块
     */
    public static class MemoryChunk {
        /** 起始行号（1-based） */
        public final int startLine;

        /** 结束行号（1-based，包含） */
        public final int endLine;

        /** 文本内容 */
        public final String text;

        /** 内容哈希 */
        public final String hash;

        public MemoryChunk(int startLine, int endLine, String text, String hash) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.text = text;
            this.hash = hash;
        }
    }

    // ==================== 路径处理 ====================

    /**
     * 规范化相对路径
     *
     * 移除前导 ./ 和 ../，统一使用 / 分隔符
     */
    public static String normalizeRelPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String trimmed = path.trim().replaceAll("^[./]+", "");
        return trimmed.replace('\\', '/');
    }

    /**
     * 检查是否为记忆路径
     *
     * 包括: MEMORY.md, memory.md, memory/*
     */
    public static boolean isMemoryPath(String relPath) {
        String normalized = normalizeRelPath(relPath);
        if (normalized.isEmpty()) {
            return false;
        }
        // MEMORY.md 或 memory.md
        if (normalized.equalsIgnoreCase("MEMORY.md") || normalized.equalsIgnoreCase("memory.md")) {
            return true;
        }
        // memory/* 路径
        return normalized.startsWith("memory/");
    }

    /**
     * 检查是否为会话路径
     */
    public static boolean isSessionPath(String relPath) {
        String normalized = normalizeRelPath(relPath);
        return normalized.startsWith("sessions/");
    }

    // ==================== 文件列表 ====================

    /**
     * 列出所有记忆文件
     *
     * 包括: MEMORY.md, memory/*.md
     *
     * @param workspaceDir 工作目录
     * @param extraPaths 额外路径（可选）
     * @return 文件绝对路径列表
     */
    public static List<Path> listMemoryFiles(Path workspaceDir, List<String> extraPaths) {
        List<Path> result = new ArrayList<>();

        // MEMORY.md
        Path memoryFile = workspaceDir.resolve("MEMORY.md");
        if (Files.isRegularFile(memoryFile)) {
            result.add(memoryFile);
        }

        // memory.md（小写备选）
        Path altMemoryFile = workspaceDir.resolve("memory.md");
        if (Files.isRegularFile(altMemoryFile)) {
            result.add(altMemoryFile);
        }

        // memory/ 目录
        Path memoryDir = workspaceDir.resolve("memory");
        if (Files.isDirectory(memoryDir)) {
            try {
                Files.walk(memoryDir)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .forEach(result::add);
            } catch (IOException e) {
                log.warn("遍历 memory 目录失败: {}", memoryDir, e);
            }
        }

        // 额外路径
        if (extraPaths != null && !extraPaths.isEmpty()) {
            for (String extraPath : extraPaths) {
                Path resolved = Paths.get(extraPath);
                if (!resolved.isAbsolute()) {
                    resolved = workspaceDir.resolve(extraPath);
                }
                if (Files.isDirectory(resolved)) {
                    try {
                        Files.walk(resolved)
                                .filter(Files::isRegularFile)
                                .filter(p -> p.getFileName().toString().endsWith(".md"))
                                .forEach(result::add);
                    } catch (IOException e) {
                        log.warn("遍历额外路径失败: {}", resolved, e);
                    }
                } else if (Files.isRegularFile(resolved) && resolved.getFileName().toString().endsWith(".md")) {
                    result.add(resolved);
                }
            }
        }

        // 去重
        Set<Path> seen = new HashSet<>();
        List<Path> deduped = new ArrayList<>();
        for (Path p : result) {
            try {
                Path real = p.toRealPath();
                if (!seen.contains(real)) {
                    seen.add(real);
                    deduped.add(p);
                }
            } catch (IOException e) {
                if (!seen.contains(p)) {
                    seen.add(p);
                    deduped.add(p);
                }
            }
        }

        return deduped;
    }

    /**
     * 列出会话文件
     *
     * @param sessionsDir 会话目录
     * @return 文件绝对路径列表
     */
    public static List<Path> listSessionFiles(Path sessionsDir) {
        if (sessionsDir == null || !Files.isDirectory(sessionsDir)) {
            return Collections.emptyList();
        }

        try {
            return Files.list(sessionsDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("遍历会话目录失败: {}", sessionsDir, e);
            return Collections.emptyList();
        }
    }

    // ==================== 文件条目构建 ====================

    /**
     * 构建文件条目
     *
     * @param absPath 绝对路径
     * @param workspaceDir 工作目录
     * @return 文件条目，失败返回 null
     */
    public static MemoryFileEntry buildFileEntry(Path absPath, Path workspaceDir) {
        try {
            if (!Files.isRegularFile(absPath)) {
                return null;
            }

            String relPath = workspaceDir.relativize(absPath).toString().replace('\\', '/');
            long mtimeMs = Files.getLastModifiedTime(absPath).toMillis();
            long size = Files.size(absPath);
            String content = Files.readString(absPath, StandardCharsets.UTF_8);
            String hash = hashText(content);

            return new MemoryFileEntry(relPath, absPath.toString(), mtimeMs, size, hash);
        } catch (IOException e) {
            log.warn("构建文件条目失败: {}", absPath, e);
            return null;
        }
    }

    // ==================== 哈希计算 ====================

    /**
     * 计算文本哈希（SHA-256）
     */
    public static String hashText(String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== 文本分块 ====================

    /**
     * 将文本分块
     *
     * @param content 文本内容
     * @param maxTokens 每块最大 token 数
     * @param overlapTokens 重叠 token 数
     * @return 分块列表
     */
    public static List<MemoryChunk> chunkText(String content, int maxTokens, int overlapTokens) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }

        // 简单估算：1 token ≈ 4 字符
        int maxChars = maxTokens * 4;
        int overlapChars = overlapTokens * 4;

        List<MemoryChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        StringBuilder currentChunk = new StringBuilder();
        int chunkStartLine = 1;
        int currentLine = 1;
        int currentChars = 0;

        for (String line : lines) {
            int lineChars = line.length() + 1; // +1 for newline

            if (currentChars + lineChars > maxChars && currentChars > 0) {
                // 保存当前块
                String chunkText = currentChunk.toString().trim();
                if (!chunkText.isEmpty()) {
                    chunks.add(new MemoryChunk(
                            chunkStartLine,
                            currentLine - 1,
                            chunkText,
                            hashText(chunkText)
                    ));
                }

                // 开始新块（考虑重叠）
                if (overlapChars > 0 && currentChunk.length() > overlapChars) {
                    // 保留最后 overlapChars 字符作为重叠
                    String overlap = currentChunk.substring(currentChunk.length() - overlapChars);
                    currentChunk = new StringBuilder(overlap);
                    currentChars = overlap.length();
                    // 估算重叠的行数
                    int overlapLines = overlap.split("\n").length;
                    chunkStartLine = currentLine - overlapLines;
                } else {
                    currentChunk = new StringBuilder();
                    currentChars = 0;
                    chunkStartLine = currentLine;
                }
            }

            currentChunk.append(line).append("\n");
            currentChars += lineChars;
            currentLine++;
        }

        // 保存最后一块
        String lastChunk = currentChunk.toString().trim();
        if (!lastChunk.isEmpty()) {
            chunks.add(new MemoryChunk(
                    chunkStartLine,
                    currentLine - 1,
                    lastChunk,
                    hashText(lastChunk)
            ));
        }

        return chunks;
    }

    // ==================== 工具方法 ====================

    /**
     * 确保目录存在
     */
    public static Path ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            // 忽略
        }
        return dir;
    }

    /**
     * 截断文本到指定字符数
     */
    public static String truncateText(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text != null ? text : "";
        }
        return text.substring(0, maxChars) + "...";
    }

    /**
     * 判断文件是否为记忆文件
     */
    public static boolean isMemoryFile(Path file, Path workspaceDir) {
        try {
            String relPath = workspaceDir.relativize(file).toString().replace('\\', '/');
            return isMemoryPath(relPath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断文件是否为会话文件
     */
    public static boolean isSessionFile(Path file, Path workspaceDir) {
        try {
            String relPath = workspaceDir.relativize(file).toString().replace('\\', '/');
            return isSessionPath(relPath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取来源类型
     */
    public static MemoryTypes.MemorySource getSource(Path file, Path workspaceDir) {
        try {
            String relPath = workspaceDir.relativize(file).toString().replace('\\', '/');
            if (isSessionPath(relPath)) {
                return MemoryTypes.MemorySource.SESSIONS;
            }
            return MemoryTypes.MemorySource.MEMORY;
        } catch (Exception e) {
            return MemoryTypes.MemorySource.MEMORY;
        }
    }
}