package gui.ui.components;

import config.tool.DbDataSourceConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class DataSourceCard extends VBox {

    public interface Callback {
        void onEdit(String name, DbDataSourceConfig config);
        void onReconnect(String name);
        void onDelete(String name);
        void onToggle(String name, boolean enable);
    }

    public DataSourceCard(String name, String jdbcUrl, String dbType, int maxPoolSize,
                           String statusText, boolean isGood, boolean isEnabled,
                           Callback callback, DbDataSourceConfig config) {
        setSpacing(0);
        getStyleClass().add("card");
        setPadding(new Insets(20));

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        String iconChar = getDbIcon(dbType);
        Label icon = new Label(iconChar);
        icon.setStyle("-fx-background-color: rgba(59, 130, 246, 0.1); -fx-background-radius: 10px; -fx-pref-width: 40px; -fx-pref-height: 40px; -fx-alignment: center;");
        icon.setMinSize(40, 40);

        VBox infoBox = new VBox(2);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500;");

        String displayUrl = jdbcUrl;
        if (displayUrl != null && displayUrl.length() > 60) {
            displayUrl = displayUrl.substring(0, 57) + "...";
        }
        Label urlLabel = new Label(displayUrl != null ? displayUrl : "");
        urlLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0, 0, 0, 0.5);");

        infoBox.getChildren().addAll(nameLabel, urlLabel);

        // Status
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("\u25CF");
        String dotColor = isGood ? "#22c55e" : (isEnabled ? "#9ca3af" : "#f59e0b");
        dot.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 8px;");

        Label statusBadge = new Label(statusText);
        String styleClass = isGood ? "running" : (isEnabled ? "disconnected" : "error");
        statusBadge.getStyleClass().addAll("status-badge", styleClass);
        statusBox.getChildren().addAll(dot, statusBadge);

        header.getChildren().addAll(icon, infoBox, statusBox);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        getChildren().add(header);

        // Body: db type and pool size
        HBox body = new HBox(16);
        body.setPadding(new Insets(16, 0, 0, 0));

        Label typeLabel = new Label(dbType != null ? dbType : "unknown");
        typeLabel.getStyleClass().add("tool-tag");

        Label poolLabel = new Label("最大连接数: " + maxPoolSize + " · 超时: " + config.getConnectionTimeout() + "ms");
        poolLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0, 0, 0, 0.4);");

        body.getChildren().addAll(typeLabel, poolLabel);
        getChildren().add(body);

        // Separator
        Label separator = new Label();
        separator.setStyle("-fx-background-color: rgba(0, 0, 0, 0.05); -fx-pref-height: 1px;");
        VBox.setMargin(body, new Insets(0, 0, 16, 0));
        getChildren().add(separator);

        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        actionBox.setPadding(new Insets(12, 0, 0, 0));

        // Enable/Disable toggle
        Button toggleBtn = new Button(isEnabled ? "⚡ 已启用" : "⛔ 已禁用");
        toggleBtn.getStyleClass().add("pill-button");
        toggleBtn.setPrefHeight(32);
        toggleBtn.setOnAction(e -> {
            if (callback != null) callback.onToggle(name, !isEnabled);
        });

        Button editBtn = new Button("编辑");
        editBtn.getStyleClass().add("pill-button");
        editBtn.setPrefHeight(32);
        editBtn.setOnAction(e -> {
            if (callback != null) callback.onEdit(name, config);
        });

        Button reconnectBtn = new Button("重新连接");
        reconnectBtn.getStyleClass().add("pill-button");
        reconnectBtn.setPrefHeight(32);
        reconnectBtn.setDisable(!isEnabled);
        reconnectBtn.setOnAction(e -> {
            if (callback != null) callback.onReconnect(name);
        });

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().add("pill-button");
        deleteBtn.setPrefHeight(32);
        deleteBtn.setOnAction(e -> {
            if (callback != null) callback.onDelete(name);
        });

        actionBox.getChildren().addAll(toggleBtn, editBtn, reconnectBtn, deleteBtn);
        getChildren().add(actionBox);
    }

    private static String getDbIcon(String dbType) {
        if (dbType == null) return "\uD83D\uDDC4";
        switch (dbType) {
            case "mysql": return "\uD83D\uDC2C";
            case "postgresql": return "\uD83D\uDC18";
            case "mariadb": return "\uD83E\uDD8A";
            case "oracle": return "\uD83D\uDC0D";
            case "sqlserver": return "\uD83D\uDEE0";
            case "h2": return "\uD83D\uDD25";
            case "sqlite": return "\uD83D\uDCCB";
            default: return "\uD83D\uDDC4";
        }
    }
}
