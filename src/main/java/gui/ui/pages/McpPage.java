package gui.ui.pages;

import config.mcp.MCPServerConfig;
import gui.ui.components.McpServerCard;
import gui.ui.dialogs.AddMcpServerDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class McpPage extends VBox {

    private final VBox serverList;
    private final Stage stage;
    private gui.ui.BackendBridge backendBridge;

    public McpPage(Stage stage) {
        this.stage = stage;
        setSpacing(0);
        setStyle("-fx-background-color: #f1ede1;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        scrollPane.setFitToWidth(true);

        VBox content = new VBox(16);
        content.setPadding(new Insets(40, 24, 24, 24));
        content.setAlignment(Pos.TOP_CENTER);

        // 页面标题
        Label title = new Label("MCP 管理");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("管理 Model Context Protocol 服务器和工具");
        subtitle.setStyle("-fx-font-size: 17px; -fx-text-fill: rgba(0, 0, 0, 0.5); -fx-font-weight: 500;");

        VBox titleBox = new VBox(8);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.getChildren().addAll(title, subtitle);

        // 服务器列表
        serverList = new VBox(12);
        serverList.setMaxWidth(800);

        // 添加按钮
        Button addBtn = new Button("+ 添加 MCP 服务器");
        addBtn.getStyleClass().add("pill-button");
        addBtn.setPrefHeight(40);
        addBtn.setOnAction(e -> {
            AddMcpServerDialog dialog = new AddMcpServerDialog(stage);
            dialog.showAndWait();
            if (dialog.isConfirmed()) {
                String name = dialog.getServerName();
                try {
                    if (dialog.isRawMode()) {
                        backendBridge.addMcpServerRaw(name, dialog.getRawJson());
                    } else {
                        backendBridge.addMcpServer(name, dialog.getCommand());
                    }
                    refresh(); // 先展示新卡片（状态：未连接）
                    // 异步触发连接，完成后再次刷新状态
                    backendBridge.refreshMcpTools()
                        .thenRun(() -> javafx.application.Platform.runLater(() -> refresh()));
                } catch (Exception ex) {
                    System.err.println("添加 MCP 服务器失败: " + ex.getMessage());
                }
            }
        });

        content.getChildren().addAll(titleBox, serverList, addBtn);
        VBox.setMargin(addBtn, new Insets(24, 0, 0, 0));

        scrollPane.setContent(content);
        getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    public void setBackendBridge(gui.ui.BackendBridge bridge) {
        this.backendBridge = bridge;
        // 先展示当前配置（可能显示未连接）
        refresh();
        // 触发连接（如果 connectMcp 尚未完成则等待，否则刷新），完成后更新状态
        bridge.refreshMcpTools()
            .thenRun(() -> javafx.application.Platform.runLater(() -> refresh()));
    }

    private McpServerCard.Callback createCardCallback() {
        return new McpServerCard.Callback() {
            @Override
            public void onEdit(String name, String command, MCPServerConfig cfg) {
                String jsonStr = null;
                if (cfg != null) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        mapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
                        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT, true);
                        jsonStr = mapper.writeValueAsString(cfg);
                    } catch (Exception ignored) {}
                }
                AddMcpServerDialog dialog = new AddMcpServerDialog(stage, name, name, command, jsonStr);
                dialog.showAndWait();
                if (dialog.isConfirmed()) {
                    String newName = dialog.getServerName();
                    try {
                        if (dialog.isRawMode()) {
                            backendBridge.updateMcpServerRaw(name, newName, dialog.getRawJson());
                        } else {
                            backendBridge.updateMcpServer(name, newName, dialog.getCommand());
                        }
                        refresh();
                        // 编辑后自动重连
                        backendBridge.refreshMcpTools()
                            .thenRun(() -> javafx.application.Platform.runLater(() -> refresh()));
                    } catch (Exception ex) {
                        System.err.println("编辑 MCP 服务器失败: " + ex.getMessage());
                    }
                }
            }

            @Override
            public void onReload(String name) {
                backendBridge.refreshMcpTools()
                    .thenRun(() -> javafx.application.Platform.runLater(() -> refresh()));
            }

            @Override
            public void onDelete(String name) {
                backendBridge.deleteMcpServer(name);
                refresh();
                // 删除后刷新连接状态
                backendBridge.refreshMcpTools()
                    .thenRun(() -> javafx.application.Platform.runLater(() -> refresh()));
            }
        };
    }

    private void refresh() {
        if (backendBridge == null) return;
        serverList.getChildren().clear();

        java.util.Map<String, MCPServerConfig> servers =
            backendBridge.getConfig().getTools().getMcpServers();
        for (java.util.Map.Entry<String, MCPServerConfig> entry : servers.entrySet()) {
            String name = entry.getKey();
            MCPServerConfig sc = entry.getValue();
            String cmd = sc.getCommand() != null && !sc.getCommand().isBlank()
                ? sc.getCommand() + " " + String.join(" ", sc.getArgs())
                : (sc.getUrl() != null ? sc.getUrl() : "");
            gui.ui.BackendBridge.McpStatus status = backendBridge.getMcpStatus(name);
            String statusText;
            boolean isGood;
            switch (status) {
                case CONNECTED:
                    statusText = "已连接";
                    isGood = true;
                    break;
                case DISABLED:
                    statusText = "已禁用";
                    isGood = false;
                    break;
                default:
                    statusText = "未连接";
                    isGood = false;
                    break;
            }
            java.util.List<String> tools = backendBridge.getMcpServerTools(name);
            serverList.getChildren().add(
                new McpServerCard(name, cmd, statusText, isGood, tools, null, createCardCallback(), entry.getValue()));
        }
    }
}
