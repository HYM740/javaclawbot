package gui.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ToolCallCard extends VBox {

    private boolean expanded = false;
    private final VBox contentBox;
    private final Label expandIcon;

    public ToolCallCard(String toolName, String status, String params) {
        setSpacing(0);
        getStyleClass().add("tool-call-card");

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 12, 6, 12));
        header.setCursor(javafx.scene.Cursor.HAND);

        Label statusIcon = new Label("\u2713");
        statusIcon.setStyle("-fx-text-fill: #22c55e;");

        Label toolIcon = new Label("\uD83D\uDD27");
        toolIcon.setStyle("-fx-opacity: 0.6;");

        Label nameLabel = new Label(toolName);
        nameLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        expandIcon = new Label("\u25B6");
        HBox.setHgrow(expandIcon, Priority.ALWAYS);
        expandIcon.setAlignment(Pos.CENTER_RIGHT);

        header.getChildren().addAll(statusIcon, toolIcon, nameLabel, expandIcon);

        // Content (初始隐藏)
        contentBox = new VBox(8);
        contentBox.setPadding(new Insets(0, 12, 8, 12));
        contentBox.setVisible(false);
        contentBox.setManaged(false);

        if (params != null) {
            Label paramsLabel = new Label(params);
            paramsLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-background-color: rgba(0, 0, 0, 0.02); -fx-background-radius: 6px; -fx-padding: 8px;");
            paramsLabel.setWrapText(true);
            contentBox.getChildren().add(paramsLabel);
        }

        header.setOnMouseClicked(e -> toggle());

        getChildren().addAll(header, contentBox);
    }

    private void toggle() {
        expanded = !expanded;
        contentBox.setVisible(expanded);
        contentBox.setManaged(expanded);
        expandIcon.setText(expanded ? "\u25BC" : "\u25B6");
    }
}
