package memory;

import java.util.*;

/**
 * 记忆系统类型定义
 *
 * 对齐 OpenClaw 的 types.ts
 */
public class MemoryTypes {

    private MemoryTypes() {
        // 工具类，禁止实例化
    }

    // ==================== 枚举 ====================

    /**
     * 记忆来源
     */
    public enum MemorySource {
        /** MEMORY.md + memory/*.md */
        MEMORY("memory"),
        /** 会话历史 */
        SESSIONS("sessions");

        private final String value;

        MemorySource(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static MemorySource fromValue(String value) {
            for (MemorySource source : values()) {
                if (source.value.equalsIgnoreCase(value)) {
                    return source;
                }
            }
            return MEMORY;
        }
    }

    // ==================== 搜索结果 ====================

    /**
     * 搜索结果
     *
     * 对齐 OpenClaw 的 MemorySearchResult
     */
    public static class MemorySearchResult {
        /** 文件路径（相对路径） */
        public final String path;

        /** 起始行号（1-based） */
        public final int startLine;

        /** 结束行号（1-based，包含） */
        public final int endLine;

        /** 综合分数 */
        public final double score;

        /** 内容片段 */
        public final String snippet;

        /** 来源 */
        public final MemorySource source;

        /** 引用（格式: path#Lstart-Lend） */
        public final String citation;

        public MemorySearchResult(String path, int startLine, int endLine,
                                  double score, String snippet, MemorySource source) {
            this.path = path;
            this.startLine = startLine;
            this.endLine = endLine;
            this.score = score;
            this.snippet = snippet;
            this.source = source;
            this.citation = formatCitation(path, startLine, endLine);
        }

        public MemorySearchResult(String path, int startLine, int endLine,
                                  double score, String snippet, MemorySource source, String citation) {
            this.path = path;
            this.startLine = startLine;
            this.endLine = endLine;
            this.score = score;
            this.snippet = snippet;
            this.source = source;
            this.citation = citation != null ? citation : formatCitation(path, startLine, endLine);
        }

        /**
         * 格式化引用
         */
        public static String formatCitation(String path, int startLine, int endLine) {
            if (startLine == endLine) {
                return path + "#L" + startLine;
            }
            return path + "#L" + startLine + "-L" + endLine;
        }

        @Override
        public String toString() {
            return String.format("MemorySearchResult{path='%s', lines=%d-%d, score=%.3f, source=%s}",
                    path, startLine, endLine, score, source);
        }
    }

    // ==================== 文件读取结果 ====================

    /**
     * 文件读取结果
     */
    public static class ReadFileResult {
        /** 文件路径 */
        public final String path;

        /** 文件内容 */
        public final String text;

        /** 是否成功 */
        public final boolean success;

        /** 错误信息 */
        public final String error;

        public ReadFileResult(String path, String text) {
            this.path = path;
            this.text = text != null ? text : "";
            this.success = true;
            this.error = null;
        }

        public ReadFileResult(String path, String text, boolean success, String error) {
            this.path = path;
            this.text = text != null ? text : "";
            this.success = success;
            this.error = error;
        }

        public static ReadFileResult error(String path, String error) {
            return new ReadFileResult(path, "", false, error);
        }
    }

    // ==================== 提供者状态 ====================

    /**
     * 提供者状态
     *
     * 对齐 OpenClaw 的 MemoryProviderStatus
     */
    public static class MemoryProviderStatus {
        /** 后端类型 */
        public final String backend; // "builtin"

        /** 提供者名称 */
        public final String provider;

        /** 模型名称 */
        public final String model;

        /** 文件数 */
        public final int files;

        /** 分块数 */
        public final int chunks;

        /** 是否有未同步变更 */
        public final boolean dirty;

        /** 工作目录 */
        public final String workspaceDir;

        /** 数据库路径 */
        public final String dbPath;

        /** 额外路径 */
        public final List<String> extraPaths;

        /** 来源列表 */
        public final List<MemorySource> sources;

        /** 来源统计 */
        public final List<SourceCount> sourceCounts;

        /** 缓存状态 */
        public final CacheStatus cache;

        /** FTS 状态 */
        public final FtsStatus fts;

        /** 向量状态 */
        public final VectorStatus vector;

        /** 回退信息 */
        public final FallbackInfo fallback;

        public MemoryProviderStatus(Builder builder) {
            this.backend = builder.backend;
            this.provider = builder.provider;
            this.model = builder.model;
            this.files = builder.files;
            this.chunks = builder.chunks;
            this.dirty = builder.dirty;
            this.workspaceDir = builder.workspaceDir;
            this.dbPath = builder.dbPath;
            this.extraPaths = builder.extraPaths != null ? Collections.unmodifiableList(builder.extraPaths) : Collections.emptyList();
            this.sources = builder.sources != null ? Collections.unmodifiableList(builder.sources) : Collections.emptyList();
            this.sourceCounts = builder.sourceCounts != null ? Collections.unmodifiableList(builder.sourceCounts) : Collections.emptyList();
            this.cache = builder.cache;
            this.fts = builder.fts;
            this.vector = builder.vector;
            this.fallback = builder.fallback;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String backend = "builtin";
            private String provider;
            private String model;
            private int files;
            private int chunks;
            private boolean dirty;
            private String workspaceDir;
            private String dbPath;
            private List<String> extraPaths;
            private List<MemorySource> sources;
            private List<SourceCount> sourceCounts;
            private CacheStatus cache;
            private FtsStatus fts;
            private VectorStatus vector;
            private FallbackInfo fallback;

            public Builder backend(String backend) { this.backend = backend; return this; }
            public Builder provider(String provider) { this.provider = provider; return this; }
            public Builder model(String model) { this.model = model; return this; }
            public Builder files(int files) { this.files = files; return this; }
            public Builder chunks(int chunks) { this.chunks = chunks; return this; }
            public Builder dirty(boolean dirty) { this.dirty = dirty; return this; }
            public Builder workspaceDir(String workspaceDir) { this.workspaceDir = workspaceDir; return this; }
            public Builder dbPath(String dbPath) { this.dbPath = dbPath; return this; }
            public Builder extraPaths(List<String> extraPaths) { this.extraPaths = extraPaths; return this; }
            public Builder sources(List<MemorySource> sources) { this.sources = sources; return this; }
            public Builder sourceCounts(List<SourceCount> sourceCounts) { this.sourceCounts = sourceCounts; return this; }
            public Builder cache(CacheStatus cache) { this.cache = cache; return this; }
            public Builder fts(FtsStatus fts) { this.fts = fts; return this; }
            public Builder vector(VectorStatus vector) { this.vector = vector; return this; }
            public Builder fallback(FallbackInfo fallback) { this.fallback = fallback; return this; }

            public MemoryProviderStatus build() {
                return new MemoryProviderStatus(this);
            }
        }
    }

    /**
     * 来源统计
     */
    public static class SourceCount {
        public final MemorySource source;
        public final int files;
        public final int chunks;

        public SourceCount(MemorySource source, int files, int chunks) {
            this.source = source;
            this.files = files;
            this.chunks = chunks;
        }
    }

    /**
     * 缓存状态
     */
    public static class CacheStatus {
        public final boolean enabled;
        public final Integer entries;
        public final Integer maxEntries;

        public CacheStatus(boolean enabled, Integer entries, Integer maxEntries) {
            this.enabled = enabled;
            this.entries = entries;
            this.maxEntries = maxEntries;
        }
    }

    /**
     * FTS 状态
     */
    public static class FtsStatus {
        public final boolean enabled;
        public final boolean available;
        public final String error;

        public FtsStatus(boolean enabled, boolean available, String error) {
            this.enabled = enabled;
            this.available = available;
            this.error = error;
        }
    }

    /**
     * 向量状态
     */
    public static class VectorStatus {
        public final boolean enabled;
        public final Boolean available;
        public final String extensionPath;
        public final String loadError;
        public final Integer dims;

        public VectorStatus(boolean enabled, Boolean available, String extensionPath, String loadError, Integer dims) {
            this.enabled = enabled;
            this.available = available;
            this.extensionPath = extensionPath;
            this.loadError = loadError;
            this.dims = dims;
        }
    }

    /**
     * 回退信息
     */
    public static class FallbackInfo {
        public final String from;
        public final String reason;

        public FallbackInfo(String from, String reason) {
            this.from = from;
            this.reason = reason;
        }
    }

    // ==================== 嵌入探测结果 ====================

    /**
     * 嵌入探测结果
     */
    public static class MemoryEmbeddingProbeResult {
        public final boolean ok;
        public final String error;

        public MemoryEmbeddingProbeResult(boolean ok, String error) {
            this.ok = ok;
            this.error = error;
        }

        public static MemoryEmbeddingProbeResult ok() {
            return new MemoryEmbeddingProbeResult(true, null);
        }

        public static MemoryEmbeddingProbeResult error(String error) {
            return new MemoryEmbeddingProbeResult(false, error);
        }
    }

    // ==================== 同步进度 ====================

    /**
     * 同步进度更新
     */
    public static class MemorySyncProgressUpdate {
        public final int completed;
        public final int total;
        public final String label;

        public MemorySyncProgressUpdate(int completed, int total, String label) {
            this.completed = completed;
            this.total = total;
            this.label = label;
        }
    }
}