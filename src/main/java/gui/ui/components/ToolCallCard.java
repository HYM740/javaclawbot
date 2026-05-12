package gui.ui.components;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

/**
 * 工具调用卡片。
 * Header 使用 JavaFX（状态图标、工具名、时间戳、展开箭头），保持交互。
 * 工具参数和结果内容使用 WebView 渲染，支持文本选择与复制。
 */
public class ToolCallCard extends VBox {

    private static final String HTML_TEMPLATE = "<!DOCTYPE html><html style='height:100%;background:transparent;'>"
        + "<head><meta charset='UTF-8'><style>"
        + "body{font-family:'JetBrains Mono','Fira Code',monospace;"
        + "font-size:11px;line-height:1.5;color:rgba(0,0,0,0.7);background:rgba(0,0,0,0.02);"
        + "margin:0;padding:8px;overflow-x:auto;white-space:pre-wrap;word-break:break-all;"
        + "border-radius:6px;}"
        + "</style></head><body>%s</body></html>";

    private boolean expanded;
    private final VBox contentBox;
    private final WebView contentWebView;
    private final Label expandIcon;
    private final Label statusIcon;
    private final Label timestampLabel;

    public ToolCallCard(String toolName, String status, String params) {
        this(toolName, status, params, false, "");
    }

    public ToolCallCard(String toolName, String status, String params, boolean startExpanded) {
        this(toolName, status, params, startExpanded, "");
    }

    public ToolCallCard(String toolName, String status, String params, boolean startExpanded, String timestamp) {
        setSpacing(0);
        getStyleClass().add("tool-call-card");
        this.expanded = startExpanded;

        // ===== Header (JavaFX) =====
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 12, 6, 12));
        header.setCursor(javafx.scene.Cursor.HAND);

        statusIcon = new Label("✓");
        applyStatusStyle(status);

        Label toolIcon = new Label("🔧");
        toolIcon.setStyle("-fx-opacity: 0.6;");

        Label nameLabel = new Label(toolName);
        nameLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        timestampLabel = new Label(timestamp);
        timestampLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(0,0,0,0.3);");

        expandIcon = new Label(startExpanded ? "▼" : "▶");
        HBox.setHgrow(expandIcon, Priority.ALWAYS);
        expandIcon.setAlignment(Pos.CENTER_RIGHT);

        header.getChildren().addAll(statusIcon, toolIcon, nameLabel, timestampLabel, expandIcon);
        header.setOnMouseClicked(e -> toggle());

        // ===== Content WebView =====
        contentWebView = new WebView();
        contentWebView.setContextMenuEnabled(true);  // enable right-click for copy
        contentWebView.setStyle("-fx-background-color: transparent;");
        contentWebView.setMaxHeight(400);

        // Wrap WebView in ScrollPane to prevent uncontrolled size growth
        ScrollPane contentScrollPane = new ScrollPane(contentWebView);
        contentScrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        contentScrollPane.setFitToWidth(true);
        contentScrollPane.setMaxHeight(400);
        contentScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        contentBox = new VBox(0);
        contentBox.getChildren().add(contentScrollPane);
        contentBox.setPadding(new Insets(0, 12, 8, 12));
        contentBox.setVisible(startExpanded);
        contentBox.setManaged(startExpanded);

        // Load initial params
        StringBuilder html = new StringBuilder();
        if (params != null && !params.isBlank()) {
            html.append(escapeHtml(params));
        }
        loadContent(html.toString());

        getChildren().addAll(header, contentBox);
    }

    // ===== Styling =====

    private void applyStatusStyle(String status) {
        if ("running".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status)) {
            statusIcon.setText("○");
            statusIcon.setStyle("-fx-text-fill: #f59e0b;");
        } else {
            statusIcon.setText("✓");
            statusIcon.setStyle("-fx-text-fill: #22c55e;");
        }
    }

    // ===== Public API =====

    /** Append tool result text */
    public void addResult(String result) {
        String current = readBodyText();
        StringBuilder sb = new StringBuilder();
        if (current != null && !current.isBlank()) {
            sb.append(current);
            sb.append("\n\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━");
            sb.append("\n\n");
        }
        sb.append(escapeHtml(result));
        loadContent(sb.toString());
    }

    /** Append structured HTML content (e.g., diff, table).
     *  For backward compatibility, also handles javafx.scene.Node. */
    public void addStructuredContent(javafx.scene.Node node) {
        contentBox.getChildren().add(node);
    }

    /** Set status */
    public void setStatus(String status) {
        applyStatusStyle(status);
    }

    /** Set timestamp */
    public void setTimestamp(String timestamp) {
        timestampLabel.setText(timestamp);
    }

    /** Replace params content */
    public void setParams(String params) {
        loadContent(escapeHtml(params));
    }

    // ===== Internal =====

    private String readBodyText() {
        try {
            Object result = contentWebView.getEngine().executeScript("document.body.innerText");
            return result instanceof String ? (String) result : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void loadContent(String bodyHtml) {
        String fullHtml = String.format(HTML_TEMPLATE,
                (bodyHtml != null && !bodyHtml.isBlank()) ? bodyHtml : "");
        contentWebView.getEngine().load(toDataUri(fullHtml));
    }

    private void toggle() {
        expanded = !expanded;
        contentBox.setVisible(expanded);
        contentBox.setManaged(expanded);
        expandIcon.setText(expanded ? "▼" : "▶");
    }

    // ===== Static helpers =====

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String toDataUri(String html) {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String b64 = Base64.getEncoder().encodeToString(bytes);
        return "data:text/html;charset=UTF-8;base64," + b64;
    }
}
