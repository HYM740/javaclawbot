package agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hslf.usermodel.HSLFNotes;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import utils.PathUtil;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 文件系统工具集合（对齐 MCP filesystem 的核心能力，并增强：
 *
 * 1) read_file：支持 head / tail / 指定范围行读取
 * 2) write_file：统一文件编辑工具，支持 old_string/new_string/replace_all + edits[]/dry_run
 * 3) list_dir：列目录
 * 4) read_word
 * 5) read_ppt
 * 6) read_ppt_structured：使用 Apache POI 读取 PPT 结构化内容（slide/title/body/notes）
 *
 * 说明：
 * - 路径安全：统一通过 PathUtil.resolvePath(workspace, allowedDir) 做白名单校验
 * - 文本文件按 UTF-8 处理
 * - Office 文档：
 *   - read_word / read_ppt：适合"全文读取 + 行裁剪"
 *   - read_ppt_structured：适合 agent 做结构化消费
 */
public final class FileSystemTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FileSystemTools() {}

    // ----------------------------
    // ReadFileTool — Port of Claude Code's FileReadTool
    //
    // Original sources:
    //   - tools/FileReadTool/FileReadTool.ts (main implementation)
    //   - tools/FileReadTool/prompt.ts (description)
    //   - tools/FileReadTool/limits.ts (size/token limits)
    //   - utils/readFileInRange.ts (line range reading)
    //   - utils/fileStateCache.ts (read state tracking)
    //
    // Changes from original Java implementation:
    //   - Added cat-n format output (line_number + tab + content)
    //   - Added file_path parameter (alias for path)
    //   - Added offset + limit parameters (Claude Code style)
    //   - Added FileStateCache integration (read-before-write + dedup)
    //   - Added MAX_LINES_TO_READ = 2000 default
    //   - Added file_unchanged dedup
    //   - Added BOM stripping
    //   - Updated description to match Claude Code prompt
    //   - Preserved existing head/tail/start_line/end_line for backward compat
    // ----------------------------
    public static final class ReadFileTool extends Tool {
        /** Port of Claude Code limits.ts: DEFAULT_MAX_OUTPUT_TOKENS * 2 (chars) */
        private static final int MAX_OUTPUT_CHARS = 50_000;
        /** Port of Claude Code prompt.ts: MAX_LINES_TO_READ */
        private static final int MAX_LINES_TO_READ = 2000;
        /** Port of Claude Code limits.ts: maxSizeBytes (256KB) */
        private static final long MAX_FILE_SIZE_BYTES = 256L * 1024;

        /** Port of Claude Code prompt.ts: FILE_UNCHANGED_STUB */
        private static final String FILE_UNCHANGED_STUB =
            "File unchanged since last read. The content from the earlier read_file tool_result in this conversation is still current - refer to that instead of re-reading.";

        private final Path workspace;
        private final Path allowedDir;
        private final FileStateCache fileStateCache;

        public ReadFileTool(Path workspace, Path allowedDir) {
            this(workspace, allowedDir, new FileStateCache.NoOp());
        }

        public ReadFileTool(Path workspace, Path allowedDir, FileStateCache fileStateCache) {
            this.workspace = workspace;
            this.allowedDir = allowedDir;
            this.fileStateCache = fileStateCache != null ? fileStateCache : new FileStateCache.NoOp();
        }

        @Override
        public String name() {
            return "read_file";
        }

        /**
         * Port of Claude Code's FileReadTool/prompt.ts renderPromptTemplate()
         */
        @Override
        public String description() {
            return String.join("\n", List.of(
                "Reads a file from the local filesystem. You can access any file directly by using this tool.",
                "Assume this tool is able to read all files on the machine. If the User provides a path to a file assume that path is valid. It is okay to read a file that does not exist; an error will be returned.",
                "",
                "Usage:",
                "- The file_path parameter must be an absolute path, not a relative path",
                "- By default, it reads up to " + MAX_LINES_TO_READ + " lines starting from the beginning of the file",
                "- You can optionally specify a line offset and limit (especially handy for long files), but it's recommended to read the whole file by not providing these parameters",
                "- Results are returned using cat -n format, with line numbers starting at 1",
                "- This tool can only read files, not directories. To read a directory, use an ls command via the Bash tool.",
                "- You will regularly be asked to read screenshots. If the user provides a path to a screenshot, ALWAYS use this tool to view the file at the path. This tool will work with all temporary file paths.",
                "- If you read a file that exists but has empty contents you will receive a system reminder warning in place of file contents."
            ));
        }

        /** Manages its own size limits (port of Claude Code: maxResultSizeChars = Infinity) */
        @Override
        public int maxResultSizeChars() {
            return Integer.MAX_VALUE;
        }

        @Override
        public Map<String, Object> parameters() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("file_path", Map.of("type", "string", "description", "The absolute path to the file to read"));
            // Backward compat alias
            props.put("path", Map.of("type", "string", "description", "(alias for file_path) The file path to read"));
            // Claude Code style params
            props.put("offset", Map.of("type", "number", "description", "The line number to start reading from (0-based, optional)"));
            props.put("limit", Map.of("type", "number", "description", "The number of lines to read (optional)"));
            // Legacy params (backward compat)
            props.put("head", Map.of("type", "number", "description", "First N lines (optional, legacy - use limit)"));
            props.put("tail", Map.of("type", "number", "description", "Last N lines (optional)"));
            props.put("start_line", Map.of("type", "number", "description", "Start line (1-based, optional, legacy - use offset)"));
            props.put("end_line", Map.of("type", "number", "description", "End line (1-based, inclusive, optional)"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            // Neither file_path nor path is strictly required (both are optional, at least one needed)
            schema.put("required", List.of());
            return schema;
        }

        @Override
        public CompletionStage<String> execute(Map<String, Object> args) {
            // Support both file_path (Claude Code) and path (legacy)
            String filePath = asString(args.get("file_path"));
            if (filePath == null) filePath = asString(args.get("path"));
            if (filePath == null) {
                return CompletableFuture.completedFuture("Error: file_path is required");
            }

            // Claude Code style: offset + limit
            Integer offset = asIntOrNull(args.get("offset"));
            Integer limit = asIntOrNull(args.get("limit"));

            // Legacy params
            Integer head = asIntOrNull(args.get("head"));
            Integer tail = asIntOrNull(args.get("tail"));
            Integer startLine = asIntOrNull(args.get("start_line"));
            Integer endLine = asIntOrNull(args.get("end_line"));

            String validate = validateLineReadArgs(head, tail, startLine, endLine);
            if (validate != null) {
                return CompletableFuture.completedFuture(validate);
            }

            boolean hasClaudeCodeParams = offset != null || limit != null;
            boolean hasLegacyParams = head != null || tail != null || startLine != null || endLine != null;
            boolean hasLineLimit = hasClaudeCodeParams || hasLegacyParams;

            try {
                Path resolvedPath = PathUtil.resolvePath(filePath, workspace, allowedDir);
                if (!Files.exists(resolvedPath)) {
                    return CompletableFuture.completedFuture("Error: File not found: " + filePath);
                }
                if (!Files.isRegularFile(resolvedPath)) {
                    return CompletableFuture.completedFuture("Error: Not a file: " + filePath);
                }

                // --- Port of Claude Code: file_unchanged dedup ---
                if (!hasLineLimit && fileStateCache.isUnchanged(resolvedPath)) {
                    FileStateCache.FileState state = fileStateCache.getState(resolvedPath);
                    if (state != null && !state.isPartialView) {
                        return CompletableFuture.completedFuture(FILE_UNCHANGED_STUB);
                    }
                }

                // Read pre-check: check file size when no line limit
                if (!hasLineLimit) {
                    long fileSize = Files.size(resolvedPath);
                    if (fileSize > MAX_FILE_SIZE_BYTES) {
                        long totalLines = countLinesFast(resolvedPath);
                        return CompletableFuture.completedFuture(
                                String.format("Error: File too large (%.1f KB, %d lines). " +
                                        "Use offset/limit parameters to read specific sections, " +
                                        "e.g. offset=0, limit=200 for first 200 lines.",
                                        fileSize / 1024.0, totalLines));
                    }
                }

                String content = readFileSmart(resolvedPath);

                // Strip BOM (port of Claude Code readFileInRange)
                if (content != null && content.startsWith("\uFEFF")) {
                    content = content.substring(1);
                }

                // Apply line range
                int actualOffset = -1; // track actual offset for cat-n numbering
                if (hasClaudeCodeParams) {
                    // Claude Code style: offset (0-based) + limit
                    int from = offset != null ? Math.max(0, offset) : 0;
                    actualOffset = from;
                    List<String> allLines = splitLinesPreserveNewline(content);
                    if (from >= allLines.size()) {
                        return CompletableFuture.completedFuture("");
                    }
                    int to = allLines.size();
                    if (limit != null) {
                        to = Math.min(from + limit, allLines.size());
                    }
                    // Extract lines and join (strip trailing \n from each line for display)
                    List<String> selected = allLines.subList(from, to);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < selected.size(); i++) {
                        String line = selected.get(i);
                        if (line.endsWith("\n")) line = line.substring(0, line.length() - 1);
                        if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                        sb.append(from + i + 1).append("\t").append(line);
                        if (i < selected.size() - 1) sb.append("\n");
                    }
                    content = sb.toString();
                } else if (hasLegacyParams) {
                    // Legacy style: apply line window, then format as cat-n
                    List<String> allLines = splitLinesPreserveNewline(content);
                    content = applyLineWindow(content, head, tail, startLine, endLine);
                    // For legacy mode, determine the offset for line numbering
                    if (startLine != null) {
                        actualOffset = startLine - 1;
                    } else if (head != null) {
                        actualOffset = 0;
                    } else if (tail != null) {
                        actualOffset = Math.max(0, allLines.size() - tail);
                    } else {
                        actualOffset = 0;
                    }
                    // Format as cat-n
                    content = formatCatN(content, actualOffset);
                } else {
                    // No line limit: check MAX_LINES_TO_READ
                    List<String> allLines = splitLinesPreserveNewline(content);
                    if (allLines.size() > MAX_LINES_TO_READ) {
                        // Read first MAX_LINES_TO_READ lines with cat-n format
                        actualOffset = 0;
                        List<String> selected = allLines.subList(0, MAX_LINES_TO_READ);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < selected.size(); i++) {
                            String line = selected.get(i);
                            if (line.endsWith("\n")) line = line.substring(0, line.length() - 1);
                            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                            sb.append(i + 1).append("\t").append(line);
                            if (i < selected.size() - 1) sb.append("\n");
                        }
                        content = sb.toString() + "\n\n... (" + (allLines.size() - MAX_LINES_TO_READ) +
                                " more lines below. Use offset/limit to read more.)";
                    } else {
                        // Full file in cat-n format
                        content = formatCatN(content, 0);
                    }
                }

                // Post-read check: output char count
                if (content.length() > MAX_OUTPUT_CHARS) {
                    return CompletableFuture.completedFuture(
                            String.format("Error: Selected content too large (%d chars). " +
                                    "Use offset/limit to narrow the range.",
                                    content.length()));
                }

                // --- Port of Claude Code: update FileStateCache ---
                long mtimeMs = Files.getLastModifiedTime(resolvedPath).toMillis();
                long sizeBytes = Files.size(resolvedPath);
                fileStateCache.markRead(resolvedPath, content, mtimeMs, sizeBytes);

                return CompletableFuture.completedFuture(content);

            } catch (SecurityException se) {
                return CompletableFuture.completedFuture("Error: " + se.getMessage());
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Error reading file: " + e.getMessage());
            }
        }

        /** Format content in cat-n format: line_number + tab + content. Port of Claude Code ReadTool output. */
        private static String formatCatN(String content, int offset) {
            List<String> lines = splitLinesPreserveNewline(content);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.endsWith("\n")) line = line.substring(0, line.length() - 1);
                if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                sb.append(offset + i + 1).append("\t").append(line);
                if (i < lines.size() - 1) sb.append("\n");
            }
            return sb.toString();
        }

        /** Fast line count (port of Claude Code readFileInRange stat-based) */
        private static long countLinesFast(Path filePath) throws Exception {
            try (var lines = Files.lines(filePath)) {
                return lines.count();
            }
        }
    }

    // ----------------------------
    // WriteFileTool — 统一的文件编辑工具
    //
    // 合并了原 write_file（old_string/new_string/replace_all）和 edit_file（edits[]/dryRun）
    // 支持两种调用模式：
    //   模式1（单次编辑）：file_path + old_string + new_string + replace_all（可选）
    //   模式2（批量编辑）：file_path + edits[] + dry_run（可选）
    //
    // 能力：
    // - 精确字符串替换，支持弯引号智能匹配
    // - 保留原文件编码和行尾符
    // - replace_all 全局替换
    // - edits[] 批量多次替换
    // - dry_run 预览变更（输出 unified diff）
    // - 找不到匹配时给出模糊相似提示
    // ----------------------------
    public static final class WriteFileTool extends Tool {
        private final Path workspace;
        private final Path allowedDir;

        public WriteFileTool(Path workspace, Path allowedDir) {
            this.workspace = workspace;
            this.allowedDir = allowedDir;
        }

        @Override
        public String name() {
            return "write_file";
        }

        @Override
        public String description() {
            return String.join("\n", List.of(
                "Performs exact string replacements in files.",
                "",
                "Usage:",
                "- You must use your `read_file` tool at least once in the conversation before editing. This tool will error if you attempt an edit without reading the file. ",
                "- When editing text from Read tool output, ensure you preserve the exact indentation (tabs/spaces) as it appears AFTER the line number prefix. The line number prefix format is: line number + tab. Everything after that is the actual file content to match. Never include any part of the line number prefix in the old_string or new_string.",
                "- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.",
                "- Only use emojis if the user explicitly requests it. Avoid adding emojis to files unless asked.",
                "- The edit will FAIL if `old_string` is not unique in the file. Either provide a larger string with more surrounding context to make it unique or use `replace_all` to change every instance of `old_string`.",
                "- Use `replace_all` for replacing and renaming strings across the file. This parameter is useful if you want to rename a variable for instance.",
                "- For multiple edits in one call, use the `edits` array parameter with `old_text`/`new_text` pairs.",
                "- Use `dry_run` to preview changes as a unified diff without writing to disk."
            ));
        }

        @Override
        public int maxResultSizeChars() {
            return 100_000;
        }

        @Override
        public Map<String, Object> parameters() {
            // edits[] item schema
            Map<String, Object> editItemProps = new LinkedHashMap<>();
            editItemProps.put("old_text", Map.of("type", "string", "description", "Text to search for"));
            editItemProps.put("new_text", Map.of("type", "string", "description", "Replacement text"));

            Map<String, Object> editsSchema = new LinkedHashMap<>();
            editsSchema.put("type", "array");
            editsSchema.put("description", "List of edit operations (alternative to old_string/new_string for batch edits)");
            editsSchema.put("items", Map.of("type", "object", "properties", editItemProps, "required", List.of("old_text", "new_text")));

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("file_path", Map.of(
                    "type", "string",
                    "description", "The absolute path to the file to modify"
            ));
            props.put("old_string", Map.of(
                    "type", "string",
                    "description", "The text to replace (for single edit mode)"
            ));
            props.put("new_string", Map.of(
                    "type", "string",
                    "description", "The text to replace it with (must be different from old_string)"
            ));
            props.put("replace_all", Map.of(
                    "type", "boolean",
                    "description", "Replace all occurrences of old_string (default false)"
            ));
            props.put("edits", editsSchema);
            props.put("dry_run", Map.of(
                    "type", "boolean",
                    "description", "Preview changes as unified diff without writing to disk (default false)"
            ));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("file_path"));
            return schema;
        }

        @Override
        public CompletionStage<String> execute(Map<String, Object> args) {
            String filePath = asString(args.get("file_path"));
            boolean dryRun = asBool(args.get("dry_run"), false);

            try {
                Path resolvedPath = PathUtil.resolvePath(filePath, workspace, allowedDir);

                // Determine edit mode: batch (edits[]) or single (old_string/new_string)
                Object editsObj = args.get("edits");
                boolean hasEdits = editsObj instanceof List<?> && !((List<?>) editsObj).isEmpty();

                String oldString = asString(args.get("old_string"));
                String newString = asString(args.get("new_string"));

                if (hasEdits && (oldString != null || newString != null)) {
                    return CompletableFuture.completedFuture(
                            "Error: Cannot use both old_string/new_string and edits[] in the same call. Choose one mode."
                    );
                }

                // --- Read file with encoding detection ---
                String fileContent;
                Charset fileCharset;
                String targetLineEnding;
                boolean preserveBom;

                if (Files.exists(resolvedPath)) {
                    byte[] existingBytes = Files.readAllBytes(resolvedPath);
                    fileContent = smartDecode(existingBytes);
                    fileCharset = detectCharset(existingBytes);
                    targetLineEnding = detectLineEnding(fileContent);
                    preserveBom = hasUtf8Bom(existingBytes);
                } else {
                    // New file creation — only valid with single edit mode (old_string empty)
                    if (hasEdits) {
                        return CompletableFuture.completedFuture(
                                "Error: File does not exist: " + filePath + ". Use old_string/new_string to create a new file."
                        );
                    }
                    if (oldString != null && !oldString.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                "Error: File does not exist: " + filePath
                        );
                    }
                    fileContent = "";
                    fileCharset = StandardCharsets.UTF_8;
                    targetLineEnding = "\n";
                    preserveBom = false;
                }

                // Normalize line endings to LF for matching
                String normalizedContent = fileContent.replace("\r\n", "\n").replace("\r", "\n");
                String originalForDiff = normalizedContent;

                if (hasEdits) {
                    // === Batch mode: edits[] ===
                    List<EditOp> editOps = new ArrayList<>();
                    for (Object o : (List<?>) editsObj) {
                        if (!(o instanceof Map<?, ?> m)) {
                            return CompletableFuture.completedFuture("Error: each edit must be an object");
                        }
                        editOps.add(new EditOp(asString(m.get("old_text")), asString(m.get("new_text"))));
                    }

                    String updated = normalizedContent;
                    List<String> appliedNotes = new ArrayList<>();
                    for (int i = 0; i < editOps.size(); i++) {
                        EditOp op = editOps.get(i);
                        String normalizedOld = op.oldText.replace("\r\n", "\n").replace("\r", "\n");
                        String normalizedNew = op.newText.replace("\r\n", "\n").replace("\r", "\n");

                        if (normalizedOld.isEmpty()) {
                            updated = updated + normalizedNew;
                            appliedNotes.add("Edit#" + (i + 1) + ": appended text");
                            continue;
                        }

                        // Use findActualString for smart matching (quote normalization)
                        String actualOld = findActualString(updated, normalizedOld);
                        if (actualOld == null) {
                            return CompletableFuture.completedFuture(
                                    "Error: Edit#" + (i + 1) + " — old_text not found in file.\n" + fuzzyMatchHint(normalizedOld, updated)
                            );
                        }
                        String actualNew = preserveQuoteStyle(normalizedOld, actualOld, normalizedNew);
                        int idx = updated.indexOf(actualOld);
                        updated = updated.substring(0, idx) + actualNew + updated.substring(idx + actualOld.length());
                        appliedNotes.add("Edit#" + (i + 1) + ": replaced 1 occurrence");
                    }

                    if (dryRun) {
                        String diff = unifiedDiff(
                                splitLinesPreserveNewline(originalForDiff),
                                splitLinesPreserveNewline(updated),
                                filePath + " (before)",
                                filePath + " (after)"
                        );
                        return CompletableFuture.completedFuture(
                                "DRY RUN: would apply " + editOps.size() + " edits to " + filePath + "\n"
                                        + String.join("\n", appliedNotes) + "\n\n" + diff
                        );
                    }

                    // Write back
                    String finalContent = normalizeLineEndings(updated, targetLineEnding);
                    writeFilePreserving(resolvedPath, finalContent, fileCharset, preserveBom);

                    String diff = unifiedDiff(
                            splitLinesPreserveNewline(originalForDiff),
                            splitLinesPreserveNewline(updated),
                            filePath + " (before)",
                            filePath + " (after)"
                    );
                    return CompletableFuture.completedFuture(
                            "The file " + filePath + " has been updated successfully.\n"
                                    + String.join("\n", appliedNotes) + "\n\n" + diff
                    );

                } else {
                    // === Single mode: old_string/new_string/replace_all ===
                    boolean replaceAll = asBool(args.get("replace_all"), false);

                    if (oldString == null && newString == null) {
                        return CompletableFuture.completedFuture(
                                "Error: Must provide either old_string/new_string or edits[] parameter."
                        );
                    }

                    if (oldString == null) oldString = "";
                    if (newString == null) newString = "";

                    // Check old_string === new_string
                    if (oldString.equals(newString)) {
                        return CompletableFuture.completedFuture(
                                "Error: No changes to make: old_string and new_string are exactly the same."
                        );
                    }

                    String normalizedOldString = oldString.replace("\r\n", "\n").replace("\r", "\n");
                    String normalizedNewString = newString.replace("\r\n", "\n").replace("\r", "\n");

                    // findActualString: exact match first, then quote-normalized match
                    String actualOldString = findActualString(normalizedContent, normalizedOldString);
                    if (actualOldString == null) {
                        return CompletableFuture.completedFuture(
                                "Error: String to replace not found in file.\n" + fuzzyMatchHint(normalizedOldString, normalizedContent)
                        );
                    }

                    // Check for multiple matches when replace_all is false
                    if (!replaceAll && !normalizedOldString.isEmpty()) {
                        int matches = countMatches(normalizedContent, actualOldString);
                        if (matches > 1) {
                            return CompletableFuture.completedFuture(
                                    "Error: Found " + matches + " matches of the string to replace, but replace_all is false. " +
                                            "To replace all occurrences, set replace_all to true. " +
                                            "To replace only one occurrence, please provide more context to uniquely identify the instance.\n" +
                                            "String: " + oldString
                            );
                        }
                    }

                    // preserveQuoteStyle: when file uses curly quotes but model provides straight quotes
                    String actualNewString = preserveQuoteStyle(
                            normalizedOldString, actualOldString, normalizedNewString
                    );

                    // Apply the replacement
                    String updatedContent;
                    if (normalizedOldString.isEmpty()) {
                        updatedContent = actualNewString;
                    } else if (replaceAll) {
                        updatedContent = normalizedContent.replace(actualOldString, actualNewString);
                    } else {
                        int idx = normalizedContent.indexOf(actualOldString);
                        updatedContent = normalizedContent.substring(0, idx)
                                + actualNewString
                                + normalizedContent.substring(idx + actualOldString.length());
                    }

                    if (dryRun) {
                        String diff = unifiedDiff(
                                splitLinesPreserveNewline(originalForDiff),
                                splitLinesPreserveNewline(updatedContent),
                                filePath + " (before)",
                                filePath + " (after)"
                        );
                        return CompletableFuture.completedFuture(
                                "DRY RUN: would apply edit to " + filePath + "\n\n" + diff
                        );
                    }

                    // Write back preserving original encoding and line endings
                    String finalContent = normalizeLineEndings(updatedContent, targetLineEnding);
                    writeFilePreserving(resolvedPath, finalContent, fileCharset, preserveBom);

                    if (replaceAll) {
                        return CompletableFuture.completedFuture(
                                "The file " + filePath + " has been updated. All occurrences were successfully replaced."
                        );
                    }
                    return CompletableFuture.completedFuture(
                            "The file " + filePath + " has been updated successfully."
                    );
                }

            } catch (SecurityException se) {
                return CompletableFuture.completedFuture("Error: " + se.getMessage());
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Error editing file: " + e.getMessage());
            }
        }

        // ---- inner helpers ----

        private static final class EditOp {
            final String oldText;
            final String newText;

            EditOp(String oldText, String newText) {
                this.oldText = oldText == null ? "" : oldText;
                this.newText = newText == null ? "" : newText;
            }
        }

        /** Count non-overlapping occurrences of a substring. */
        private static int countMatches(String content, String search) {
            if (search.isEmpty()) return 0;
            int count = 0;
            int idx = 0;
            while ((idx = content.indexOf(search, idx)) >= 0) {
                count++;
                idx += search.length();
            }
            return count;
        }

        /** Write file preserving BOM for UTF-8. */
        private static void writeFilePreserving(Path path, String content, Charset charset, boolean preserveBom) throws Exception {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);

            if (preserveBom && charset == StandardCharsets.UTF_8) {
                byte[] bomBytes = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
                byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
                byte[] fullBytes = new byte[bomBytes.length + contentBytes.length];
                System.arraycopy(bomBytes, 0, fullBytes, 0, bomBytes.length);
                System.arraycopy(contentBytes, 0, fullBytes, bomBytes.length, contentBytes.length);
                Files.write(path, fullBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Files.writeString(path, content, charset, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }

        /** Fuzzy match hint: find the best matching window and show diff. */
        private static String fuzzyMatchHint(String oldText, String content) {
            List<String> lines = splitLinesPreserveNewline(content);
            List<String> oldLines = splitLinesPreserveNewline(oldText);

            int window = Math.max(1, oldLines.size());
            double bestRatio = 0.0;
            int bestStart = 0;

            int maxStart = Math.max(1, lines.size() - window + 1);
            String oldJoined = String.join("", oldLines);

            for (int i = 0; i < maxStart; i++) {
                String candidate = String.join("", lines.subList(i, Math.min(i + window, lines.size())));
                double ratio = similarity(oldJoined, candidate);
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    bestStart = i;
                }
            }

            if (bestRatio > 0.5) {
                List<String> actual = lines.subList(bestStart, Math.min(bestStart + window, lines.size()));
                String diff = unifiedDiff(
                        oldLines, actual,
                        "provided text",
                        "best match (line " + (bestStart + 1) + ")"
                );
                return "Best match (" + Math.round(bestRatio * 100) + "% similar) near line " + (bestStart + 1) + ":\n" + diff;
            }

            return "No similar text found. Verify the file content.";
        }
    }

    // ----------------------------
    // ReadWordTool (Pure POI)
    // ----------------------------
    public static final class ReadWordTool extends Tool {
        private final Path workspace;
        private final Path allowedDir;

        public ReadWordTool(Path workspace, Path allowedDir) {
            this.workspace = workspace;
            this.allowedDir = allowedDir;
        }

        @Override
        public String name() {
            return "read_word";
        }

        @Override
        public String description() {
            return "Read text content from a Word document (.doc/.docx) using Apache POI. Supports head/tail/start_line/end_line.";
        }

        @Override
        public Map<String, Object> parameters() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("path", Map.of("type", "string", "description", "The Word file path (.doc or .docx)"));
            props.put("head", Map.of("type", "number", "description", "First N lines (optional)"));
            props.put("tail", Map.of("type", "number", "description", "Last N lines (optional)"));
            props.put("start_line", Map.of("type", "number", "description", "Start line (1-based, optional)"));
            props.put("end_line", Map.of("type", "number", "description", "End line (1-based, inclusive, optional)"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("path"));
            return schema;
        }

        @Override
        public CompletionStage<String> execute(Map<String, Object> args) {
            String path = asString(args.get("path"));
            Integer head = asIntOrNull(args.get("head"));
            Integer tail = asIntOrNull(args.get("tail"));
            Integer startLine = asIntOrNull(args.get("start_line"));
            Integer endLine = asIntOrNull(args.get("end_line"));

            String validate = validateLineReadArgs(head, tail, startLine, endLine);
            if (validate != null) {
                return CompletableFuture.completedFuture(validate);
            }

            try {
                Path filePath = PathUtil.resolvePath(path, workspace, allowedDir);
                if (!Files.exists(filePath)) {
                    return CompletableFuture.completedFuture("Error: File not found: " + path);
                }
                if (!Files.isRegularFile(filePath)) {
                    return CompletableFuture.completedFuture("Error: Not a file: " + path);
                }

                String lower = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
                String text;
                if (lower.endsWith(".docx")) {
                    text = readDocxFullText(filePath);
                } else if (lower.endsWith(".doc")) {
                    text = readDocFullText(filePath);
                } else {
                    return CompletableFuture.completedFuture("Error: Unsupported Word format: " + path + " (only .doc / .docx)");
                }

                text = normalizeOfficeText(text);
                if (text.isBlank()) {
                    return CompletableFuture.completedFuture("Word document is empty or no readable text found: " + path);
                }

                return CompletableFuture.completedFuture(applyLineWindow(text, head, tail, startLine, endLine));

            } catch (SecurityException se) {
                return CompletableFuture.completedFuture("Error: " + se.getMessage());
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Error reading Word file: " + e.getMessage());
            }
        }
    }

    // ----------------------------
    // ReadWordStructuredTool (.docx structured, Pure POI)
    // ----------------------------
    public static final class ReadWordStructuredTool extends Tool {
        private final Path workspace;
        private final Path allowedDir;

        public ReadWordStructuredTool(Path workspace, Path allowedDir) {
            this.workspace = workspace;
            this.allowedDir = allowedDir;
        }

        @Override
        public String name() {
            return "read_word_structured";
        }

        @Override
        public String description() {
            return "Read a Word document into structured JSON by title/heading -> content. Reliable for .docx.";
        }

        @Override
        public Map<String, Object> parameters() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("path", Map.of("type", "string", "description", "The Word file path (.docx recommended)"));
            props.put("include_empty_sections", Map.of("type", "boolean", "description", "Whether to keep headings with empty content (default false)"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("path"));
            return schema;
        }

        @Override
        public CompletionStage<String> execute(Map<String, Object> args) {
            String path = asString(args.get("path"));
            boolean includeEmptySections = asBool(args.get("include_empty_sections"), false);

            try {
                Path filePath = PathUtil.resolvePath(path, workspace, allowedDir);
                if (!Files.exists(filePath)) {
                    return CompletableFuture.completedFuture("Error: File not found: " + path);
                }
                if (!Files.isRegularFile(filePath)) {
                    return CompletableFuture.completedFuture("Error: Not a file: " + path);
                }

                String lower = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".docx")) {
                    return CompletableFuture.completedFuture(
                            "Error: read_word_structured currently supports .docx only. For .doc, use read_word for full-text extraction."
                    );
                }

                Map<String, Object> result = readDocxStructured(filePath, includeEmptySections);
                return CompletableFuture.completedFuture(
                        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result)
                );

            } catch (SecurityException se) {
                return CompletableFuture.completedFuture("Error: " + se.getMessage());
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Error reading structured Word file: " + e.getMessage());
            }
        }

        private static Map<String, Object> readDocxStructured(Path filePath, boolean includeEmptySections) throws Exception {
            try (InputStream in = Files.newInputStream(filePath);
                 XWPFDocument doc = new XWPFDocument(in)) {

                List<Map<String, Object>> sections = new ArrayList<>();
                List<String> preamble = new ArrayList<>();
                WordSection current = null;

                for (XWPFParagraph para : doc.getParagraphs()) {
                    String text = normalizeOfficeText(safe(para.getText()));
                    if (text.isBlank()) {
                        continue;
                    }

                    HeadingInfo heading = detectHeading(doc, para, text);
                    if (heading != null) {
                        if (current != null) {
                            if (includeEmptySections || !current.content.isEmpty()) {
                                sections.add(current.toMap());
                            }
                        }

                        current = new WordSection(
                                heading.title,
                                heading.level,
                                heading.styleId,
                                heading.styleName
                        );
                    } else {
                        if (current == null) {
                            preamble.add(text);
                        } else {
                            current.content.add(text);
                        }
                    }
                }

                if (current != null) {
                    if (includeEmptySections || !current.content.isEmpty()) {
                        sections.add(current.toMap());
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("file", filePath.getFileName().toString());
                result.put("section_count", sections.size());
                result.put("preamble", preamble);
                result.put("sections", sections);
                return result;
            }
        }

        private static HeadingInfo detectHeading(XWPFDocument doc, XWPFParagraph para, String text) {
            String styleId = trimToNull(para.getStyle());
            String styleName = null;

            try {
                if (styleId != null && doc.getStyles() != null) {
                    XWPFStyle style = doc.getStyles().getStyle(styleId);
                    if (style != null) {
                        styleName = trimToNull(style.getName());
                    }
                }
            } catch (Exception ignored) {
            }

            Integer level = detectHeadingLevel(styleId, styleName);
            if (level != null) {
                return new HeadingInfo(text, level, styleId, styleName);
            }
            return null;
        }

        private static Integer detectHeadingLevel(String styleId, String styleName) {
            String a = normalizeStyleKey(styleId);
            String b = normalizeStyleKey(styleName);

            Integer fromA = parseHeadingLevelFromStyleKey(a);
            if (fromA != null) return fromA;

            Integer fromB = parseHeadingLevelFromStyleKey(b);
            if (fromB != null) return fromB;

            return null;
        }

        private static Integer parseHeadingLevelFromStyleKey(String s) {
            if (s == null || s.isBlank()) return null;

            if ("title".equals(s) || s.contains("doctitle")) {
                return 0;
            }

            String digits = extractTrailingDigitsAfterHeadingLikeToken(s);
            if (digits != null) {
                try {
                    return Integer.parseInt(digits);
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        private static String extractTrailingDigitsAfterHeadingLikeToken(String s) {
            if (s == null) return null;

            String t = s.toLowerCase(Locale.ROOT)
                    .replace("标题", "heading")
                    .replace("_", "")
                    .replace("-", "")
                    .replace(" ", "");

            int idx = t.indexOf("heading");
            if (idx >= 0) {
                String tail = t.substring(idx + "heading".length());
                StringBuilder num = new StringBuilder();
                for (int i = 0; i < tail.length(); i++) {
                    char c = tail.charAt(i);
                    if (Character.isDigit(c)) {
                        num.append(c);
                    } else {
                        break;
                    }
                }
                if (num.length() > 0) {
                    return num.toString();
                }
            }
            return null;
        }

        private static String normalizeStyleKey(String s) {
            if (s == null) return null;
            return s.trim().toLowerCase(Locale.ROOT);
        }

        private static final class HeadingInfo {
            final String title;
            final int level;
            final String styleId;
            final String styleName;

            HeadingInfo(String title, int level, String styleId, String styleName) {
                this.title = title == null ? "" : title.trim();
                this.level = level;
                this.styleId = styleId;
                this.styleName = styleName;
            }
        }

        private static final class WordSection {
            final String title;
            final int level;
            final String styleId;
            final String styleName;
            final List<String> content = new ArrayList<>();

            WordSection(String title, int level, String styleId, String styleName) {
                this.title = title == null ? "" : title.trim();
                this.level = level;
                this.styleId = styleId;
                this.styleName = styleName;
            }

            Map<String, Object> toMap() {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", title);
                m.put("level", level);
                m.put("style_id", styleId == null ? "" : styleId);
                m.put("style_name", styleName == null ? "" : styleName);
                m.put("content", content);
                return m;
            }
        }
    }

    // ----------------------------
    // ReadPptTool (Pure POI)
    // ----------------------------
    public static final class ReadPptTool extends Tool {
        private final Path workspace;
        private final Path allowedDir;

        public ReadPptTool(Path workspace, Path allowedDir) {
            this.workspace = workspace;
            this.allowedDir = allowedDir;
        }

        @Override
        public String name() {
            return "read_ppt";
        }

        @Override
        public String description() {
            return "Read text content from a PowerPoint document (.ppt/.pptx) using Apache POI. Supports head/tail/start_line/end_line.";
        }

        @Override
        public Map<String, Object> parameters() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("path", Map.of("type", "string", "description", "The PowerPoint file path (.ppt or .pptx)"));
            props.put("head", Map.of("type", "number", "description", "First N lines (optional)"));
            props.put("tail", Map.of("type", "number", "description", "Last N lines (optional)"));
            props.put("start_line", Map.of("type", "number", "description", "Start line (1-based, optional)"));
            props.put("end_line", Map.of("type", "number", "description", "End line (1-based, inclusive, optional)"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("path"));
            return schema;
        }

        @Override
        public CompletionStage<String> execute(Map<String, Object> args) {
            String path = asString(args.get("path"));
            Integer head = asIntOrNull(args.get("head"));
            Integer tail = asIntOrNull(args.get("tail"));
            Integer startLine = asIntOrNull(args.get("start_line"));
            Integer endLine = asIntOrNull(args.get("end_line"));

            String validate = validateLineReadArgs(head, tail, startLine, endLine);
            if (validate != null) {
                return CompletableFuture.completedFuture(validate);
            }

            try {
                Path filePath = PathUtil.resolvePath(path, workspace, allowedDir);
                if (!Files.exists(filePath)) {
                    return CompletableFuture.completedFuture("Error: File not found: " + path);
                }
                if (!Files.isRegularFile(filePath)) {
                    return CompletableFuture.completedFuture("Error: Not a file: " + path);
                }

                String lower = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
                String text;
                if (lower.endsWith(".pptx")) {
                    text = readPptxFullText(filePath);
                } else if (lower.endsWith(".ppt")) {
                    text = readPptFullText(filePath);
                } else {
                    return CompletableFuture.completedFuture("Error: Unsupported PowerPoint format: " + path + " (only .ppt / .pptx)");
                }

                text = normalizeOfficeText(text);
                if (text.isBlank()) {
                    return CompletableFuture.completedFuture("PowerPoint document is empty or no readable text found: " + path);
                }

                return CompletableFuture.completedFuture(applyLineWindow(text, head, tail, startLine, endLine));

            } catch (SecurityException se) {
                return CompletableFuture.completedFuture("Error: " + se.getMessage());
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Error reading PowerPoint file: " + e.getMessage());
            }
        }
    }

    // ----------------------------
    // ReadPptStructuredTool (Pure POI)
    // ----------------------------
    public static final class ReadPptStructuredTool extends Tool {
        private final Path workspace;
        private final Path allowedDir;

        public ReadPptStructuredTool(Path workspace, Path allowedDir) {
            this.workspace = workspace;
            this.allowedDir = allowedDir;
        }

        @Override
        public String name() {
            return "read_ppt_structured";
        }

        @Override
        public String description() {
            return "Read a PowerPoint document (.ppt/.pptx) into structured JSON: slide/title/body/notes.";
        }

        @Override
        public Map<String, Object> parameters() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("path", Map.of("type", "string", "description", "The PowerPoint file path (.ppt or .pptx)"));
            props.put("include_notes", Map.of("type", "boolean", "description", "Whether to include notes in result (default false)"));
            props.put("slide_start", Map.of("type", "number", "description", "Start slide index (1-based, optional)"));
            props.put("slide_end", Map.of("type", "number", "description", "End slide index (1-based, inclusive, optional)"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("path"));
            return schema;
        }

        @Override
        public CompletionStage<String> execute(Map<String, Object> args) {
            String path = asString(args.get("path"));
            boolean includeNotes = asBool(args.get("include_notes"), false);
            Integer slideStart = asIntOrNull(args.get("slide_start"));
            Integer slideEnd = asIntOrNull(args.get("slide_end"));

            try {
                Path filePath = PathUtil.resolvePath(path, workspace, allowedDir);
                if (!Files.exists(filePath)) {
                    return CompletableFuture.completedFuture("Error: File not found: " + path);
                }
                if (!Files.isRegularFile(filePath)) {
                    return CompletableFuture.completedFuture("Error: Not a file: " + path);
                }

                String lower = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
                Map<String, Object> result;
                if (lower.endsWith(".pptx")) {
                    result = readPptxStructured(filePath, includeNotes, slideStart, slideEnd);
                } else if (lower.endsWith(".ppt")) {
                    result = readPptStructured(filePath, includeNotes, slideStart, slideEnd);
                } else {
                    return CompletableFuture.completedFuture("Error: Unsupported PowerPoint format: " + path + " (only .ppt / .pptx)");
                }

                return CompletableFuture.completedFuture(
                        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result)
                );

            } catch (SecurityException se) {
                return CompletableFuture.completedFuture("Error: " + se.getMessage());
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Error reading structured PowerPoint file: " + e.getMessage());
            }
        }

        private static Map<String, Object> readPptxStructured(Path filePath, boolean includeNotes, Integer slideStart, Integer slideEnd) throws Exception {
            try (InputStream in = Files.newInputStream(filePath);
                 XMLSlideShow ppt = new XMLSlideShow(in)) {

                List<XSLFSlide> slides = ppt.getSlides();
                int total = slides.size();

                int start = (slideStart == null) ? 1 : Math.max(1, slideStart);
                int end = (slideEnd == null) ? total : Math.max(1, slideEnd);
                start = Math.min(start, total == 0 ? 1 : total);
                end = Math.min(end, total == 0 ? 1 : total);

                List<Map<String, Object>> outSlides = new ArrayList<>();
                if (total > 0 && start <= end) {
                    for (int i = start - 1; i <= end - 1; i++) {
                        XSLFSlide slide = slides.get(i);

                        String title = normalizeSingleLine(safe(slide.getTitle()));
                        List<String> body = new ArrayList<>();

                        for (XSLFShape shape : slide.getShapes()) {
                            if (shape instanceof XSLFTextShape textShape) {
                                String text = normalizeOfficeText(textShape.getText());
                                if (text.isBlank()) continue;

                                String[] blocks = text.split("\\n+");
                                for (String block : blocks) {
                                    String t = block == null ? "" : block.trim();
                                    if (!t.isEmpty()) {
                                        body.add(t);
                                    }
                                }
                            }
                        }

                        if (!title.isEmpty() && !body.isEmpty() && title.equals(normalizeSingleLine(body.get(0)))) {
                            body.remove(0);
                        }

                        Map<String, Object> slideObj = new LinkedHashMap<>();
                        slideObj.put("slide", i + 1);
                        slideObj.put("title", title);
                        slideObj.put("body", dedupKeepOrder(body));

                        if (includeNotes) {
                            slideObj.put("notes", extractPptxNotes(slide));
                        }

                        outSlides.add(slideObj);
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("file", filePath.getFileName().toString());
                result.put("slide_count", total);
                result.put("returned_slide_start", total == 0 ? 0 : start);
                result.put("returned_slide_end", total == 0 ? 0 : (start <= end ? end : 0));
                result.put("slides", outSlides);
                return result;
            }
        }

        private static Map<String, Object> readPptStructured(Path filePath, boolean includeNotes, Integer slideStart, Integer slideEnd) throws Exception {
            try (InputStream in = Files.newInputStream(filePath);
                 HSLFSlideShow ppt = new HSLFSlideShow(in)) {

                List<HSLFSlide> slides = ppt.getSlides();
                int total = slides.size();

                int start = (slideStart == null) ? 1 : Math.max(1, slideStart);
                int end = (slideEnd == null) ? total : Math.max(1, slideEnd);
                start = Math.min(start, total == 0 ? 1 : total);
                end = Math.min(end, total == 0 ? 1 : total);

                List<Map<String, Object>> outSlides = new ArrayList<>();
                if (total > 0 && start <= end) {
                    for (int i = start - 1; i <= end - 1; i++) {
                        HSLFSlide slide = slides.get(i);

                        String title = normalizeSingleLine(safe(slide.getTitle()));
                        List<String> body = new ArrayList<>();

                        for (HSLFShape shape : slide.getShapes()) {
                            if (shape instanceof HSLFTextShape textShape) {
                                String raw = safe(textShape.getText());
                                raw = normalizeOfficeText(raw);
                                if (raw.isBlank()) continue;

                                String[] blocks = raw.split("\\n+");
                                for (String block : blocks) {
                                    String t = block == null ? "" : block.trim();
                                    if (!t.isEmpty()) {
                                        body.add(t);
                                    }
                                }
                            }
                        }

                        if (!title.isEmpty() && !body.isEmpty() && title.equals(normalizeSingleLine(body.get(0)))) {
                            body.remove(0);
                        }

                        Map<String, Object> slideObj = new LinkedHashMap<>();
                        slideObj.put("slide", i + 1);
                        slideObj.put("title", title);
                        slideObj.put("body", dedupKeepOrder(body));

                        if (includeNotes) {
                            slideObj.put("notes", extractPptNotes(slide));
                        }

                        outSlides.add(slideObj);
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("file", filePath.getFileName().toString());
                result.put("slide_count", total);
                result.put("returned_slide_start", total == 0 ? 0 : start);
                result.put("returned_slide_end", total == 0 ? 0 : (start <= end ? end : 0));
                result.put("slides", outSlides);
                return result;
            }
        }

        private static String extractPptxNotes(XSLFSlide slide) {
            try {
                XSLFNotes notes = slide.getNotes();
                if (notes == null) return "";

                List<String> chunks = new ArrayList<>();
                for (XSLFShape shape : notes.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = normalizeOfficeText(textShape.getText());
                        if (!text.isBlank()) {
                            chunks.add(text);
                        }
                    }
                }
                return String.join("\n", chunks).trim();
            } catch (Exception ignored) {
                return "";
            }
        }

        private static String extractPptNotes(HSLFSlide slide) {
            try {
                HSLFNotes notes = slide.getNotes();
                if (notes == null) return "";

                List<String> chunks = new ArrayList<>();
                for (List<HSLFTextParagraph> paraGroup : notes.getTextParagraphs()) {
                    if (paraGroup == null || paraGroup.isEmpty()) {
                        continue;
                    }

                    String text = HSLFTextParagraph.getText(paraGroup);
                    text = normalizeOfficeText(text);

                    if (!text.isBlank()) {
                        chunks.add(text);
                    }
                }
                return String.join("\n", chunks).trim();
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    // ----------------------------
    // ListDirTool
    // ----------------------------
    public static final class ListDirTool extends Tool {
        private final Path workspace;
        private final Path allowedDir;

        public ListDirTool(Path workspace, Path allowedDir) {
            this.workspace = workspace;
            this.allowedDir = allowedDir;
        }

        @Override
        public String name() {
            return "list_dir";
        }

        @Override
        public String description() {
            return "List the contents of a directory.";
        }

        @Override
        public Map<String, Object> parameters() {
            return schemaPathOnly("The directory path to list");
        }

        @Override
        public CompletionStage<String> execute(Map<String, Object> args) {
            String path = asString(args.get("path"));
            try {
                Path dirPath = PathUtil.resolvePath(path, workspace, allowedDir);
                if (!Files.exists(dirPath)) return CompletableFuture.completedFuture("Error: Directory not found: " + path);
                if (!Files.isDirectory(dirPath)) return CompletableFuture.completedFuture("Error: Not a directory: " + path);

                List<String> items = new ArrayList<>();
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(dirPath)) {
                    for (Path p : ds) items.add(p.getFileName().toString());
                }
                Collections.sort(items);

                if (items.isEmpty()) return CompletableFuture.completedFuture("Directory " + path + " is empty");

                List<String> out = new ArrayList<>();
                for (String name : items) {
                    Path p = dirPath.resolve(name);
                    String prefix = Files.isDirectory(p) ? "📁 " : "📄 ";
                    out.add(prefix + name);
                }
                return CompletableFuture.completedFuture(String.join("\n", out));
            } catch (SecurityException se) {
                return CompletableFuture.completedFuture("Error: " + se.getMessage());
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Error listing directory: " + e.getMessage());
            }
        }
    }

    // ----------------------------
    // schema helpers
    // ----------------------------
    private static Map<String, Object> schemaPathOnly(String pathDesc) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", pathDesc));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("path"));
        return schema;
    }

    // ----------------------------
    // common helpers
    // ----------------------------
    static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static Integer asIntOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean asBool(Object o, boolean def) {
        if (o == null) return def;
        if (o instanceof Boolean b) return b;
        String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return def;
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }


    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String normalizeSingleLine(String s) {
        String t = safe(s).replace("\r", " ").replace("\n", " ").trim();
        while (t.contains("  ")) {
            t = t.replace("  ", " ");
        }
        return t;
    }

    private static String normalizeOfficeText(String s) {
        if (s == null) return "";
        String t = s.replace("\r\n", "\n").replace("\r", "\n");
        while (t.contains("\n\n\n")) {
            t = t.replace("\n\n\n", "\n\n");
        }
        return t.trim();
    }

    private static String readDocxFullText(Path filePath) throws Exception {
        try (InputStream in = Files.newInputStream(filePath);
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static String readDocFullText(Path filePath) throws Exception {
        try (InputStream in = Files.newInputStream(filePath);
             HWPFDocument doc = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static String readPptxFullText(Path filePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = Files.newInputStream(filePath);
             XMLSlideShow ppt = new XMLSlideShow(in)) {

            List<XSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                sb.append("=== Slide ").append(i + 1).append(" ===\n");

                String title = normalizeSingleLine(safe(slide.getTitle()));
                if (!title.isEmpty()) {
                    sb.append(title).append("\n");
                }

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = normalizeOfficeText(textShape.getText());
                        if (!text.isBlank()) {
                            sb.append(text).append("\n");
                        }
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static String readPptFullText(Path filePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = Files.newInputStream(filePath);
             HSLFSlideShow ppt = new HSLFSlideShow(in)) {

            List<HSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                HSLFSlide slide = slides.get(i);
                sb.append("=== Slide ").append(i + 1).append(" ===\n");

                String title = normalizeSingleLine(safe(slide.getTitle()));
                if (!title.isEmpty()) {
                    sb.append(title).append("\n");
                }

                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        String text = normalizeOfficeText(textShape.getText());
                        if (!text.isBlank()) {
                            sb.append(text).append("\n");
                        }
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }



    /**
     * Detect the dominant line ending in the given text.
     * Returns "\r\n" if CRLF is found, "\n" otherwise.
     */
    public static String detectLineEnding(String text) {
        if (text == null || text.isEmpty()) return System.lineSeparator();
        boolean hasCRLF = text.contains("\r\n");
        if (hasCRLF) return "\r\n";
        if (text.indexOf('\n') >= 0) return "\n";
        // No newlines at all - use system default
        return System.lineSeparator();
    }

    /**
     * Normalize all line endings in text to the specified target.
     * Handles \r\n -> target, bare \n -> target, bare \r -> \n -> target.
     */
    public static String normalizeLineEndings(String text, String targetLineEnding) {
        if (text == null || text.isEmpty()) return text;
        if (targetLineEnding == null) targetLineEnding = System.lineSeparator();
        // First normalize everything to bare LF, then convert to target
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        if ("\r\n".equals(targetLineEnding)) {
            return normalized.replace("\n", "\r\n");
        }
        return normalized; // target is \n, already normalized
    }
    /** 按行切分，但保留每行末尾的 \n（最后一行可能没有） */
    public static List<String> splitLinesPreserveNewline(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;

        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                out.add(s.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < s.length()) out.add(s.substring(start));
        return out;
    }

    private static String validateLineReadArgs(Integer head, Integer tail, Integer startLine, Integer endLine) {
        boolean hasHeadTail = head != null || tail != null;
        boolean hasRange = startLine != null || endLine != null;

        if (head != null && tail != null) {
            return "Error: cannot specify both head and tail";
        }
        if (hasHeadTail && hasRange) {
            return "Error: cannot combine head/tail with start_line/end_line";
        }
        return null;
    }

    private static String applyLineWindow(String content, Integer head, Integer tail, Integer startLine, Integer endLine) {
        if (head != null) {
            return firstNLines(content, head);
        }
        if (tail != null) {
            return lastNLines(content, tail);
        }
        if (startLine != null || endLine != null) {
            return rangeLines(content, startLine, endLine);
        }
        return content;
    }

    /** 读取前 N 行 */
    public static String firstNLines(String content, int n) {
        if (n <= 0) return "";
        List<String> lines = splitLinesPreserveNewline(content);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, lines.size()); i++) {
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /** 读取后 N 行 */
    public static String lastNLines(String content, int n) {
        if (n <= 0) return "";
        List<String> lines = splitLinesPreserveNewline(content);
        int start = Math.max(0, lines.size() - n);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.size(); i++) {
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /**
     * 读取指定行范围：
     * - start_line: 1-based，默认 1
     * - end_line: 1-based，包含该行，默认最后一行
     * - 自动夹紧到合法范围
     */
    private static String rangeLines(String content, Integer startLine, Integer endLine) {
        List<String> lines = splitLinesPreserveNewline(content);
        if (lines.isEmpty()) return "";

        int start = (startLine == null) ? 1 : Math.max(1, startLine);
        int end = (endLine == null) ? lines.size() : Math.max(1, endLine);

        start = Math.min(start, lines.size());
        end = Math.min(end, lines.size());

        if (start > end) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = start - 1; i <= end - 1; i++) {
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private static List<String> dedupKeepOrder(List<String> input) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : input) {
            String t = normalizeSingleLine(s);
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * BOM 检测结果：包含编码和 BOM 长度
     */
    private static final class BomResult {
        final Charset charset;
        final int bomLength;  // BOM 字节数，用于跳过

        BomResult(Charset charset, int bomLength) {
            this.charset = charset;
            this.bomLength = bomLength;
        }
    }

    /**
     * 检测 BOM（字节顺序标记），返回编码和 BOM 长度
     *
     * BOM 标记：
     * - UTF-8:    EF BB BF       (3 bytes)
     * - UTF-16 BE: FE FF         (2 bytes)
     * - UTF-16 LE: FF FE         (2 bytes)
     * - UTF-32 BE: 00 00 FE FF   (4 bytes)
     * - UTF-32 LE: FF FE 00 00   (4 bytes)
     */
    private static BomResult detectBom(byte[] bytes) {
        if (bytes == null || bytes.length < 2) {
            return null;
        }

        // UTF-8 BOM: EF BB BF
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            return new BomResult(StandardCharsets.UTF_8, 3);
        }

        // UTF-32 BE: 00 00 FE FF (先检测，避免与 UTF-16 BE 混淆)
        if (bytes.length >= 4
                && bytes[0] == 0x00
                && bytes[1] == 0x00
                && bytes[2] == (byte) 0xFE
                && bytes[3] == (byte) 0xFF) {
            // Java 标准 Charset 没有 UTF-32，使用自定义名称
            return new BomResult(Charset.forName("UTF-32BE"), 4);
        }

        // UTF-32 LE: FF FE 00 00
        if (bytes.length >= 4
                && bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xFE
                && bytes[2] == 0x00
                && bytes[3] == 0x00) {
            return new BomResult(Charset.forName("UTF-32LE"), 4);
        }

        // UTF-16 BE: FE FF
        if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return new BomResult(StandardCharsets.UTF_16BE, 2);
        }

        // UTF-16 LE: FF FE (排除 UTF-32 LE 的情况)
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE
                && (bytes.length < 4 || bytes[2] != 0x00 || bytes[3] != 0x00)) {
            return new BomResult(StandardCharsets.UTF_16LE, 2);
        }

        return null;
    }

    /**
     * 检测文件编码（优先 BOM，再尝试 UTF-8/GBK）
     */
    static Charset detectCharset(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return StandardCharsets.UTF_8;  // 默认 UTF-8（无 BOM）
        }

        // 优先检测 BOM
        BomResult bom = detectBom(bytes);
        if (bom != null) {
            return bom.charset;
        }

        // 无 BOM：尝试 UTF-8，失败则回退 GBK
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!containsReplacementChar(utf8, bytes)) {
            return StandardCharsets.UTF_8;
        }

        // 回退到 GBK（Windows 中文默认编码）
        return Charset.forName("GBK");
    }

    /**
     * 检测文件编码
     */
    private static Charset detectFileCharset(Path filePath) throws Exception {
        if (!Files.exists(filePath)) {
            return StandardCharsets.UTF_8;  // 新文件默认 UTF-8（无 BOM）
        }
        byte[] bytes = Files.readAllBytes(filePath);
        return detectCharset(bytes);
    }

    /**
     * 检测文件是否有 UTF-8 BOM
     */
    static boolean hasUtf8Bom(byte[] bytes) {
        return bytes != null && bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF;
    }

    /**
     * 检测文件是否有 UTF-8 BOM
     */
    private static boolean hasUtf8Bom(Path filePath) throws Exception {
        if (!Files.exists(filePath)) return false;
        byte[] bytes = Files.readAllBytes(filePath);
        return hasUtf8Bom(bytes);
    }

    /**
     * 智能解码：优先 BOM，再 UTF-8，最后 GBK
     *
     * 解决 Windows 上中文编码问题：
     * - BOM 标记文件：直接使用对应编码
     * - UTF-8 无 BOM：正常解码
     * - GBK 文件：回退解码
     */
    static String smartDecode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";

        // 优先检测 BOM
        BomResult bom = detectBom(bytes);
        if (bom != null) {
            // 使用 BOM 指定的编码，跳过 BOM 字节
            byte[] contentBytes = new byte[bytes.length - bom.bomLength];
            System.arraycopy(bytes, bom.bomLength, contentBytes, 0, contentBytes.length);
            return new String(contentBytes, bom.charset);
        }

        // 无 BOM：尝试 UTF-8
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!containsReplacementChar(utf8, bytes)) {
            return utf8;
        }

        // 回退到 GBK（Windows 中文默认编码）
        return new String(bytes, Charset.forName("GBK"));
    }

    /**
     * 检测 UTF-8 解码是否产生了替换字符（说明原始字节不是有效的 UTF-8）
     */
    private static boolean containsReplacementChar(String decoded, byte[] original) {
        // 如果解码结果包含替换字符，说明 UTF-8 解码失败，可能是 GBK
        if (decoded.contains("\uFFFD")) {
            return true;
        }
        // 注意：不能通过检测 0x81-0xFE 字节来判断 GBK，因为 UTF-8 多字节编码
        // 的后续字节（0x80-0xBF）也落在这个范围内，会导致 UTF-8 中文文件被误判为 GBK。
        // 正确的做法是只依赖替换字符检测：UTF-8 解码器遇到非法字节会插入 \uFFFD
        return false;
    }

    /**
     * 智能读取文件内容
     */
    static String readFileSmart(Path filePath) throws Exception {
        byte[] bytes = Files.readAllBytes(filePath);
        return smartDecode(bytes);
    }

    // ========================================================================
    // Atomic replication of Claude Code FileEditTool/utils.ts
    //
    // Original source: src/tools/FileEditTool/utils.ts
    //
    // Ported utility functions:
    // - normalizeQuotes(): Convert curly quotes to straight quotes
    // - findActualString(): Find matching string with fallback to quote normalization
    // - preserveQuoteStyle(): Preserve curly quotes in replacement when file uses them
    // ========================================================================

    // Claude can't output curly quotes, so we define them as constants
    // (aligned with Claude Code utils.ts LEFT/RIGHT_SINGLE/CURLY_QUOTE)
    private static final char LEFT_SINGLE_CURLY_QUOTE  = '\u2018';  // '
    private static final char RIGHT_SINGLE_CURLY_QUOTE = '\u2019';  // '
    private static final char LEFT_DOUBLE_CURLY_QUOTE  = '\u201C';  // "
    private static final char RIGHT_DOUBLE_CURLY_QUOTE = '\u201D';  // "

    /**
     * Atomic replication of Claude Code utils.ts normalizeQuotes().
     *
     * Normalizes quotes in a string by converting curly quotes to straight quotes.
     *
     * Original source: src/tools/FileEditTool/utils.ts → normalizeQuotes()
     */
    private static String normalizeQuotes(String str) {
        if (str == null) return null;
        return str
                .replace(LEFT_SINGLE_CURLY_QUOTE, '\'')
                .replace(RIGHT_SINGLE_CURLY_QUOTE, '\'')
                .replace(LEFT_DOUBLE_CURLY_QUOTE, '"')
                .replace(RIGHT_DOUBLE_CURLY_QUOTE, '"');
    }

    /**
     * Atomic replication of Claude Code utils.ts findActualString().
     *
     * Finds the actual string in the file content that matches the search string,
     * accounting for quote normalization.
     *
     * Original source: src/tools/FileEditTool/utils.ts → findActualString()
     *
     * @param fileContent  The file content to search in
     * @param searchString The string to search for
     * @return The actual string found in the file, or null if not found
     */
    static String findActualString(String fileContent, String searchString) {
        // First try exact match
        if (fileContent.contains(searchString)) {
            return searchString;
        }

        // Try with normalized quotes
        String normalizedSearch = normalizeQuotes(searchString);
        String normalizedFile = normalizeQuotes(fileContent);

        int searchIndex = normalizedFile.indexOf(normalizedSearch);
        if (searchIndex != -1) {
            // Find the actual string in the file that matches
            return fileContent.substring(searchIndex, searchIndex + searchString.length());
        }

        return null;
    }

    /**
     * Atomic replication of Claude Code utils.ts preserveQuoteStyle().
     *
     * When old_string matched via quote normalization (curly quotes in file,
     * straight quotes from model), apply the same curly quote style to new_string
     * so the edit preserves the file's typography.
     *
     * Original source: src/tools/FileEditTool/utils.ts → preserveQuoteStyle()
     *
     * @param oldString      The original search string (from model, likely straight quotes)
     * @param actualOldString The actual string found in the file (may have curly quotes)
     * @param newString       The replacement string (from model)
     * @return The new string with quote style preserved
     */
    static String preserveQuoteStyle(String oldString, String actualOldString, String newString) {
        // If they're the same, no normalization happened
        if (oldString.equals(actualOldString)) {
            return newString;
        }

        // Detect which curly quote types were in the file
        boolean hasDoubleQuotes =
                actualOldString.indexOf(LEFT_DOUBLE_CURLY_QUOTE) >= 0
                        || actualOldString.indexOf(RIGHT_DOUBLE_CURLY_QUOTE) >= 0;
        boolean hasSingleQuotes =
                actualOldString.indexOf(LEFT_SINGLE_CURLY_QUOTE) >= 0
                        || actualOldString.indexOf(RIGHT_SINGLE_CURLY_QUOTE) >= 0;

        if (!hasDoubleQuotes && !hasSingleQuotes) {
            return newString;
        }

        String result = newString;
        if (hasDoubleQuotes) {
            result = applyCurlyDoubleQuotes(result);
        }
        if (hasSingleQuotes) {
            result = applyCurlySingleQuotes(result);
        }

        return result;
    }

    /**
     * Atomic replication of Claude Code utils.ts isOpeningContext().
     *
     * Determines if a quote at the given position should be treated as an opening quote.
     * A quote character preceded by whitespace, start of string, or opening punctuation
     * is treated as an opening quote; otherwise it's a closing quote.
     */
    private static boolean isOpeningContext(char[] chars, int index) {
        if (index == 0) {
            return true;
        }
        char prev = chars[index - 1];
        return prev == ' '
                || prev == '\t'
                || prev == '\n'
                || prev == '\r'
                || prev == '('
                || prev == '['
                || prev == '{'
                || prev == '\u2014'  // em dash
                || prev == '\u2013'; // en dash
    }

    /**
     * Atomic replication of Claude Code utils.ts applyCurlyDoubleQuotes().
     *
     * Converts straight double quotes to curly double quotes based on context.
     */
    private static String applyCurlyDoubleQuotes(String str) {
        char[] chars = str.toCharArray();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '"') {
                result.append(isOpeningContext(chars, i)
                        ? LEFT_DOUBLE_CURLY_QUOTE
                        : RIGHT_DOUBLE_CURLY_QUOTE);
            } else {
                result.append(chars[i]);
            }
        }
        return result.toString();
    }

    /**
     * Atomic replication of Claude Code utils.ts applyCurlySingleQuotes().
     *
     * Converts straight single quotes to curly single quotes based on context.
     * Handles apostrophes in contractions (e.g., "don't", "it's").
     */
    private static String applyCurlySingleQuotes(String str) {
        char[] chars = str.toCharArray();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '\'') {
                // Don't convert apostrophes in contractions (e.g., "don't", "it's")
                // An apostrophe between two letters is a contraction, not a quote
                Character prev = (i > 0) ? chars[i - 1] : null;
                Character next = (i < chars.length - 1) ? chars[i + 1] : null;
                boolean prevIsLetter = (prev != null) && Character.isLetter(prev);
                boolean nextIsLetter = (next != null) && Character.isLetter(next);
                if (prevIsLetter && nextIsLetter) {
                    // Apostrophe in a contraction — use right single curly quote
                    result.append(RIGHT_SINGLE_CURLY_QUOTE);
                } else {
                    result.append(isOpeningContext(chars, i)
                            ? LEFT_SINGLE_CURLY_QUOTE
                            : RIGHT_SINGLE_CURLY_QUOTE);
                }
            } else {
                result.append(chars[i]);
            }
        }
        return result.toString();
    }

    // ---- Unified diff + fuzzy matching helpers (used by WriteFileTool) ----

    /** Simplified unified diff (line-level). */
    private static String unifiedDiff(List<String> oldLines, List<String> newLines, String fromFile, String toFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(fromFile).append("\n");
        sb.append("+++ ").append(toFile).append("\n");
        sb.append("@@ -1,").append(oldLines.size()).append(" +1,").append(newLines.size()).append(" @@\n");

        int max = Math.max(oldLines.size(), newLines.size());
        for (int i = 0; i < max; i++) {
            String a = i < oldLines.size() ? oldLines.get(i) : null;
            String b = i < newLines.size() ? newLines.get(i) : null;

            if (Objects.equals(a, b)) {
                if (a != null) sb.append(" ").append(a);
            } else {
                if (a != null) sb.append("-").append(a);
                if (b != null) sb.append("+").append(b);
            }
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append("\n");
        return sb.toString();
    }

    /** Normalized Levenshtein similarity in [0,1]. */
    private static double similarity(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int dist = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return maxLen == 0 ? 1.0 : 1.0 - ((double) dist / (double) maxLen);
    }

    private static int levenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

}