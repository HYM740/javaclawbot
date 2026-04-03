package agent.tool.file;

import agent.tool.Tool;
import utils.PathUtil;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Edit tool - atomic port of Claude Code's FileEditTool.
 *
 * Source: src/tools/FileEditTool/FileEditTool.ts
 *
 * Performs exact string replacements in files.
 * Enforces read-before-write via FileStateCache.
 * Checks file modification time staleness.
 * Preserves original file encoding and line endings.
 *
 * Port of:
 *   - FileEditTool.ts validateInput() + call()
 *   - prompt.ts getEditToolDescription()
 *   - types.ts inputSchema
 *   - constants.ts FILE_UNEXPECTEDLY_MODIFIED_ERROR
 */
public final class EditTool extends Tool {

    /** Port of Claude Code MAX_EDIT_FILE_SIZE = 1 GiB */
    private static final long MAX_EDIT_FILE_SIZE = 1024L * 1024 * 1024;

    /** Port of Claude Code constants.ts FILE_UNEXPECTEDLY_MODIFIED_ERROR */
    private static final String FILE_UNEXPECTEDLY_MODIFIED_ERROR =
            "File has been unexpectedly modified. Read it again before attempting to write it.";

    /** Port of Claude Code constants.ts FILE_NOT_FOUND_CWD_NOTE */
    private static final String FILE_NOT_FOUND_CWD_NOTE = "NOTE: file_path must be an absolute path.";

    private final Path workspace;
    private final Path allowedDir;
    private final FileStateCache fileStateCache;

    public EditTool(Path workspace, Path allowedDir, FileStateCache fileStateCache) {
        this.workspace = workspace;
        this.allowedDir = allowedDir;
        this.fileStateCache = fileStateCache != null ? fileStateCache : new FileStateCache.NoOp();
    }

    /** Backward-compatible constructor (no cache enforcement) */
    public EditTool(Path workspace, Path allowedDir) {
        this(workspace, allowedDir, new FileStateCache.NoOp());
    }

    @Override
    public String name() {
        return "edit_file";
    }

    /**
     * Port of Claude Code prompt.ts getEditToolDescription().
     */
    @Override
    public String description() {
        return String.join("\n", List.of(
            "Performs exact string replacements in files.",
            "",
            "Usage:",
            "- You must use your `read_file` tool at least once in the conversation before editing. This tool will error if you attempt an edit without reading the file.",
            "- When editing text from Read tool output, ensure you preserve the exact indentation (tabs/spaces) as it appears AFTER the line number prefix. The line number prefix format is: line number + tab. Everything after that is the actual file content to match. Never include any part of the line number prefix in the old_string or new_string.",
            "- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.",
            "- Only use emojis if the user explicitly requests it. Avoid adding emojis to files unless asked.",
            "- The edit will FAIL if `old_string` is not unique in the file. Either provide a larger string with more surrounding context to make it unique or use `replace_all` to change every instance of `old_string`.",
            "- Use `replace_all` for replacing and renaming strings across the file. This parameter is useful if you want to rename a variable for instance."
        ));
    }

    @Override
    public int maxResultSizeChars() {
        return 100_000;
    }

    /**
     * Port of Claude Code types.ts inputSchema.
     */
    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("file_path", Map.of(
                "type", "string",
                "description", "The absolute path to the file to modify"
        ));
        props.put("old_string", Map.of(
                "type", "string",
                "description", "The text to replace"
        ));
        props.put("new_string", Map.of(
                "type", "string",
                "description", "The text to replace it with (must be different from old_string)"
        ));
        props.put("replace_all", Map.of(
                "type", "boolean",
                "description", "Replace all occurrences of old_string (default false)"
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("file_path", "old_string", "new_string"));
        return schema;
    }

    /**
     * Port of Claude Code FileEditTool.ts validateInput() + call().
     *
     * Execution flow (mirrors the TS source):
     * 1. Extract and normalize parameters
     * 2. Validate old_string != new_string
     * 3. Resolve file path
     * 4. Check file size < 1GB (MAX_EDIT_FILE_SIZE)
     * 5. Read file content with encoding detection
     * 6. Check file existence (empty old_string = create new file)
     * 7. Enforce read-before-write via FileStateCache (validateInput errorCode 6)
     * 8. Check mtime staleness (validateInput errorCode 7)
     * 9. findActualString with quote normalization
     * 10. Check uniqueness when replace_all=false (validateInput errorCode 9)
     * 11. preserveQuoteStyle on replacement
     * 12. Apply replacement
     * 13. Write back preserving encoding/line endings/BOM
     * 14. Invalidate FileStateCache and re-mark as read
     * 15. Return result (port of mapToolResultToToolResultBlockParam)
     */
    @Override
    public CompletionStage<String> execute(Map<String, Object> args) {
        String filePath = FileSystemTools.asString(args.get("file_path"));
        String oldString = FileSystemTools.asString(args.get("old_string"));
        String newString = FileSystemTools.asString(args.get("new_string"));
        boolean replaceAll = FileSystemTools.asBool(args.get("replace_all"), false);

        if (filePath == null) {
            return CompletableFuture.completedFuture("Error: file_path is required");
        }
        if (oldString == null) oldString = "";
        if (newString == null) newString = "";

        // --- Port of validateInput: old_string === new_string check ---
        if (oldString.equals(newString)) {
            return CompletableFuture.completedFuture(
                    "Error: No changes to make: old_string and new_string are exactly the same.");
        }

        try {
            Path resolvedPath = PathUtil.resolvePath(filePath, workspace, allowedDir);

            // --- Port of validateInput: file size check (MAX_EDIT_FILE_SIZE) ---
            if (Files.exists(resolvedPath)) {
                long fileSize = Files.size(resolvedPath);
                if (fileSize > MAX_EDIT_FILE_SIZE) {
                    return CompletableFuture.completedFuture(
                            String.format("Error: File is too large to edit (%.1f MB). Maximum editable file size is 1024 MB.",
                                    fileSize / (1024.0 * 1024.0)));
                }
            }

            // --- Read file content with encoding detection (readFileForEdit) ---
            String fileContent;
            Charset fileCharset;
            String targetLineEnding;
            boolean preserveBom;
            boolean fileExists;

            if (Files.exists(resolvedPath)) {
                byte[] existingBytes = Files.readAllBytes(resolvedPath);
                fileContent = FileSystemTools.smartDecode(existingBytes);
                fileCharset = FileSystemTools.detectCharset(existingBytes);
                targetLineEnding = FileSystemTools.detectLineEnding(fileContent);
                preserveBom = FileSystemTools.hasUtf8Bom(existingBytes);
                fileExists = true;
            } else {
                fileContent = "";
                fileCharset = StandardCharsets.UTF_8;
                targetLineEnding = "\n";
                preserveBom = false;
                fileExists = false;
            }

            // --- Port of validateInput: file doesn't exist ---
            if (!fileExists) {
                if (oldString.isEmpty()) {
                    // Empty old_string on nonexistent file = new file creation (valid)
                } else {
                    return CompletableFuture.completedFuture(
                            "Error: File does not exist. " + FILE_NOT_FOUND_CWD_NOTE);
                }
            }

            // --- Port of validateInput: existing file with empty old_string ---
            if (fileExists && oldString.isEmpty()) {
                if (!fileContent.trim().isEmpty()) {
                    return CompletableFuture.completedFuture(
                            "Error: Cannot create new file - file already exists.");
                }
            }

            // --- Port of validateInput: read-before-write enforcement (errorCode 6) ---
            if (!fileStateCache.hasRead(resolvedPath)) {
                return CompletableFuture.completedFuture(
                        "Error: File has not been read yet. Read it first before writing to it. Use the read_file tool to read " + filePath);
            }

            // --- Port of validateInput: mtime staleness check (errorCode 7) ---
            if (fileExists) {
                FileStateCache.FileState readState = fileStateCache.getState(resolvedPath);
                if (readState != null) {
                    long currentMtime = Files.getLastModifiedTime(resolvedPath).toMillis();
                    if (currentMtime > readState.timestamp) {
                        // Timestamp indicates modification - check content for full reads
                        boolean isFullRead = readState.offset == null && readState.limit == null;
                        boolean contentUnchanged = isFullRead &&
                                normalizeContent(fileContent).equals(normalizeContent(readState.content));
                        if (!contentUnchanged) {
                            return CompletableFuture.completedFuture(
                                    "Error: " + FILE_UNEXPECTEDLY_MODIFIED_ERROR);
                        }
                    }
                }
            }

            // --- Normalize line endings for matching ---
            String normalizedContent = fileContent.replace("\r\n", "\n").replace("\r", "\n");
            String normalizedOldString = oldString.replace("\r\n", "\n").replace("\r", "\n");
            String normalizedNewString = newString.replace("\r\n", "\n").replace("\r", "\n");

            // --- Port of validateInput + call: findActualString with quote normalization ---
            String actualOldString = FileSystemTools.findActualString(normalizedContent, normalizedOldString);
            if (actualOldString == null) {
                return CompletableFuture.completedFuture(
                        "Error: String to replace not found in file.\nString: " + oldString);
            }

            // --- Port of validateInput: uniqueness check (errorCode 9) ---
            if (!replaceAll) {
                int matches = countMatches(normalizedContent, actualOldString);
                if (matches > 1) {
                    return CompletableFuture.completedFuture(
                            "Error: Found " + matches + " matches of the string to replace, but replace_all is false. " +
                                    "To replace all occurrences, set replace_all to true. " +
                                    "To replace only one occurrence, please provide more context to uniquely identify the instance.\n" +
                                    "String: " + oldString);
                }
            }

            // --- Port of call: preserveQuoteStyle ---
            String actualNewString = FileSystemTools.preserveQuoteStyle(
                    normalizedOldString, actualOldString, normalizedNewString);

            // --- Port of call: apply replacement ---
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

            // --- Port of call: write back preserving encoding/line endings/BOM ---
            String finalContent = FileSystemTools.normalizeLineEndings(updatedContent, targetLineEnding);
            Path parent = resolvedPath.getParent();
            if (parent != null) Files.createDirectories(parent);

            if (preserveBom && fileCharset == StandardCharsets.UTF_8) {
                byte[] bomBytes = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
                byte[] contentBytes = finalContent.getBytes(StandardCharsets.UTF_8);
                byte[] fullBytes = new byte[bomBytes.length + contentBytes.length];
                System.arraycopy(bomBytes, 0, fullBytes, 0, bomBytes.length);
                System.arraycopy(contentBytes, 0, fullBytes, bomBytes.length, contentBytes.length);
                Files.write(resolvedPath, fullBytes,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Files.writeString(resolvedPath, finalContent, fileCharset,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            // --- Port of call: update FileStateCache (re-mark as read) ---
            long newMtime = Files.getLastModifiedTime(resolvedPath).toMillis();
            long newSize = Files.size(resolvedPath);
            fileStateCache.invalidate(resolvedPath);
            fileStateCache.markRead(resolvedPath, updatedContent, newMtime, newSize);

            // --- Port of mapToolResultToToolResultBlockParam ---
            if (replaceAll) {
                return CompletableFuture.completedFuture(
                        "The file " + filePath + " has been updated. All occurrences were successfully replaced.");
            }
            return CompletableFuture.completedFuture(
                    "The file " + filePath + " has been updated successfully.");

        } catch (SecurityException se) {
            return CompletableFuture.completedFuture("Error: " + se.getMessage());
        } catch (Exception e) {
            return CompletableFuture.completedFuture("Error editing file: " + e.getMessage());
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

    /** Normalize content for comparison (strip trailing whitespace per line). */
    private static String normalizeContent(String content) {
        if (content == null) return "";
        return content;
    }
}
