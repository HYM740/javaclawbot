package agent.tool.file;

import utils.PathUtil;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import agent.tool.Tool;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static agent.tool.file.FileSystemTools.*;

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
    public final class WriteFileTool extends Tool {
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