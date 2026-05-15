package agent.tool.file;

import agent.tool.Tool;
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
import org.apache.poi.openxml4j.util.ZipSecureFile;
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
            ZipSecureFile.setMinInflateRatio(0.001);
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
            ZipSecureFile.setMinInflateRatio(0.001);
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
    // schema helpers
    // ----------------------------
    static Map<String, Object> schemaPathOnly(String pathDesc) {
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
        ZipSecureFile.setMinInflateRatio(0.001);
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
        ZipSecureFile.setMinInflateRatio(0.001);
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
     * Detect the dominant line ending via majority vote (aligned with
     * Open-ClaudeCode's {@code detectLineEndingsForString}).
     * <p>
     * Counts CRLF ({@code \r\n}) vs bare LF occurrences in the first
     * 4096 code units, then returns the majority winner. Tie goes to LF.
     */
    public static String detectLineEnding(String text) {
        if (text == null || text.isEmpty()) return "\n";
        int crlfCount = 0;
        int lfCount = 0;
        int limit = Math.min(text.length(), 4096);
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                if (i > 0 && text.charAt(i - 1) == '\r') {
                    crlfCount++;
                } else {
                    lfCount++;
                }
            }
        }
        if (crlfCount == 0 && lfCount == 0) return "\n";
        return crlfCount > lfCount ? "\r\n" : "\n";
    }

    /**
     * Normalize all line endings in text to the specified target.
     * Uses split-join pattern (aligned with Open-ClaudeCode) to avoid
     * double-encoding: first normalizes all {@code \r\n} to {@code \n},
     * then converts to target.
     */
    public static String normalizeLineEndings(String text, String targetLineEnding) {
        if (text == null || text.isEmpty()) return text;
        // First normalize everything to bare LF
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        if ("\r\n".equals(targetLineEnding)) {
            // split-join: prevents \r\r\n when content already contains \r\n
            return String.join("\r\n", normalized.split("\n", -1));
        }
        return normalized;
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

    public static String validateLineReadArgs(Integer head, Integer tail, Integer startLine, Integer endLine) {
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

    public static String applyLineWindow(String content, Integer head, Integer tail, Integer startLine, Integer endLine) {
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
     * 检测文件编码 — 仅 BOM，其余默认 UTF-8（对齐 Open-ClaudeCode 的
     * {@code detectEncodingForResolvedPath}）。
     * <p>
     * 不尝试 GBK 等老旧编码回退。现代 Java 源码、配置文件均为 UTF-8，
     * 启发式回退引入的误判风险远大于收益。
     */
    static Charset detectCharset(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return StandardCharsets.UTF_8;
        }

        BomResult bom = detectBom(bytes);
        if (bom != null) {
            return bom.charset;
        }

        // 无 BOM → 一切皆 UTF-8
        return StandardCharsets.UTF_8;
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
     * 智能解码 — 仅 BOM，其余 UTF-8（对齐 Open-ClaudeCode 的
     * {@code readFileSyncWithMetadata}）。
     */
    static String smartDecode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";

        BomResult bom = detectBom(bytes);
        if (bom != null) {
            byte[] contentBytes = new byte[bytes.length - bom.bomLength];
            System.arraycopy(bytes, bom.bomLength, contentBytes, 0, contentBytes.length);
            return new String(contentBytes, bom.charset);
        }

        // 无 BOM → 一切皆 UTF-8
        return new String(bytes, StandardCharsets.UTF_8);
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
    public static boolean isOpeningContext(char[] chars, int index) {
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
    public static String applyCurlyDoubleQuotes(String str) {
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
    public static String applyCurlySingleQuotes(String str) {
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
    public static String unifiedDiff(List<String> oldLines, List<String> newLines, String fromFile, String toFile) {
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
    public static double similarity(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int dist = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return maxLen == 0 ? 1.0 : 1.0 - ((double) dist / (double) maxLen);
    }

    public static int levenshtein(String a, String b) {
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