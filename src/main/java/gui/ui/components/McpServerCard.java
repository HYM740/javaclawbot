package gui.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public class McpServerCard extends VBox {

    public interface Callback {
        void onEdit(String name, String command);
        void onReload(String name);
        void onDelete(String name);
    }

    public McpServerCard(String name, String command, boolean isConnected, List<String> tools, String errorMessage) {
        this(name, command, isConnected ? "已连接" : "错误", isConnected, tools, errorMessage, null);
    }

    public McpServerCard(String name, String command, String statusText, boolean isGood, List<String> tools, String errorMessage, Callback callback) {
        setSpacing(0);
        getStyleClass().add("card");
        setPadding(new Insets(20));

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("\uD83D\uDD0C");
        icon.setStyle("-fx-background-color: rgba(59, 130, 246, 0.1); -fx-background-radius: 10px; -fx-pref-width: 40px; -fx-pref-height: 40px; -fx-alignment: center;");
        icon.setMinSize(40, 40);

        VBox infoBox = new VBox(2);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500;");

        Label commandLabel = new Label(command);
        commandLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0, 0, 0, 0.5);");

        infoBox.getChildren().addAll(nameLabel, commandLabel);

        // 状态指示器
        Label statusBadge = new Label(statusText);
        String styleClass = isGood ? "running" : "error";
        statusBadge.getStyleClass().addAll("status-badge", styleClass);

        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("\u25CF");
        dot.setStyle("-fx-text-fill: " + (isGood ? "#22c55e" : "#ef4444") + "; -fx-font-size: 8px;");
        statusBox.getChildren().addAll(dot, statusBadge);

        header.getChildren().addAll(icon, infoBox, statusBox);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        getChildren().add(header);

        // 错误消息
        if (errorMessage != null && !errorMessage.isEmpty()) {
            Label errorLabel = new Label("错误: " + errorMessage);
            errorLabel.setStyle("-fx-background-color: rgba(239, 68, 68, 0.05); -fx-background-radius: 8px; -fx-padding: 12px; -fx-font-size: 13px; -fx-text-fill: #dc2626;");
            VBox.setMargin(errorLabel, new Insets(12, 0, 0, 0));
            getChildren().add(errorLabel);
        }

        // 工具列表
        if (tools != null && !tools.isEmpty()) {
            VBox toolsBox = new VBox(8);
            toolsBox.setPadding(new Insets(16, 0, 0, 0));

            Label toolsTitle = new Label("可用工具 (" + tools.size() + ")");
            toolsTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: rgba(0, 0, 0, 0.4);");

            FlowPane toolsFlow = new FlowPane(6, 6);
            for (String tool : tools) {
                Label toolTag = new Label(tool);
                toolTag.getStyleClass().add("tool-tag");
                toolsFlow.getChildren().add(toolTag);
            }

            toolsBox.getChildren().addAll(toolsTitle, toolsFlow);

            // 分隔线
            Label separator = new Label();
            separator.setStyle("-fx-background-color: rgba(0, 0, 0, 0.05); -fx-pref-height: 1px;");
            VBox.setMargin(toolsBox, new Insets(16, 0, 0, 0));
            getChildren().addAll(separator, toolsBox);
        }

        // 操作按钮
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        actionBox.setPadding(new Insets(12, 0, 0, 0));

        Button editBtn = new Button("编辑");
        editBtn.getStyleClass().add("pill-button");
        editBtn.setPrefHeight(32);

        Button reloadBtn = new Button("重新加载");
        reloadBtn.getStyleClass().add("pill-button");
        reloadBtn.setPrefHeight(32);

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().add("pill-button");
        deleteBtn.setPrefHeight(32);

        if (callback != null) {
            editBtn.setOnAction(e -> callback.onEdit(name, command));
            reloadBtn.setOnAction(e -> callback.onReload(name));
            deleteBtn.setOnAction(e -> callback.onDelete(name));
        }

        actionBox.getChildren().addAll(editBtn, reloadBtn, deleteBtn);
        getChildren().add(actionBox);
    }
}
