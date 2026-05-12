package gui.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ToolCallCard extends VBox {

    private boolean expanded;
    private final VBox contentBox;
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

        // Header
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

        // Content
        contentBox = new VBox(8);
        contentBox.setPadding(new Insets(0, 12, 8, 12));
        contentBox.setVisible(startExpanded);
        contentBox.setManaged(startExpanded);

        if (params != null) {
            Label paramsLabel = new Label(params);
            paramsLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-background-color: rgba(0, 0, 0, 0.02); -fx-background-radius: 6px; -fx-padding: 8px;");
            paramsLabel.setWrapText(true);
            contentBox.getChildren().add(paramsLabel);
        }

        header.setOnMouseClicked(e -> toggle());

        getChildren().addAll(header, contentBox);
    }

    private void applyStatusStyle(String status) {
        if ("running".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status)) {
            statusIcon.setText("○");
            statusIcon.setStyle("-fx-text-fill: #f59e0b;");
        } else {
            statusIcon.setText("✓");
            statusIcon.setStyle("-fx-text-fill: #22c55e;");
        }
    }

    public void addResult(String result) {
        Label resultLabel = new Label(result);
        resultLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-background-color: rgba(0, 0, 0, 0.02); -fx-background-radius: 6px; -fx-padding: 8px;");
        resultLabel.setWrapText(true);
        contentBox.getChildren().add(resultLabel);
    }

    public void addStructuredContent(javafx.scene.Node node) {
        contentBox.getChildren().add(node);
    }

    /** 更新工具卡片的状态显示 */
    public void setStatus(String status) {
        applyStatusStyle(status);
    }

    /** 设置时间戳显示 */
    public void setTimestamp(String timestamp) {
        timestampLabel.setText(timestamp);
    }

    public void setParams(String params) {
        // Replace existing params label if present
        if (!contentBox.getChildren().isEmpty()
            && contentBox.getChildren().get(0) instanceof Label first
            && first.getStyle().contains("monospace")) {
            first.setText(params);
        }
    }

    private void toggle() {
        expanded = !expanded;
        contentBox.setVisible(expanded);
        contentBox.setManaged(expanded);
        expandIcon.setText(expanded ? "▼" : "▶");
    }
}
