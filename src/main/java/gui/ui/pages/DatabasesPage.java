package gui.ui.pages;

import agent.tool.db.DataSourceManager;
import config.tool.DbDataSourceConfig;
import gui.ui.BackendBridge;
import gui.ui.components.DataSourceCard;
import gui.ui.dialogs.AddDataSourceDialog;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Map;

public class DatabasesPage extends VBox {

    private final VBox dataSourceList;
    private final Stage stage;
    private BackendBridge backendBridge;

    public DatabasesPage(Stage stage) {
        this.stage = stage;
        setSpacing(0);
        setStyle("-fx-background-color: #f1ede1;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        scrollPane.setFitToWidth(true);

        VBox content = new VBox(16);
        content.setPadding(new Insets(40, 24, 24, 24));
        content.setAlignment(Pos.TOP_CENTER);

        // Title
        Label title = new Label("Databases");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("管理数据库数据源");
        subtitle.setStyle("-fx-font-size: 17px; -fx-text-fill: rgba(0, 0, 0, 0.5); -fx-font-weight: 500;");

        VBox titleBox = new VBox(8);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.getChildren().addAll(title, subtitle);

        // Data source list
        dataSourceList = new VBox(12);
        dataSourceList.setMaxWidth(800);

        // Add button
        Button addBtn = new Button("+ 添加数据源");
        addBtn.getStyleClass().add("pill-button");
        addBtn.setPrefHeight(40);
        addBtn.setOnAction(e -> {
            AddDataSourceDialog dialog = new AddDataSourceDialog(stage, backendBridge);
            dialog.showAndWait();
            if (dialog.isConfirmed()) {
                try {
                    backendBridge.addDataSource(
                        dialog.getDataSourceName(),
                        dialog.getJdbcUrl(),
                        dialog.getUsername(),
                        dialog.getPassword(),
                        dialog.getDriverClass(),
                        dialog.getMaxPoolSize(),
                        dialog.getConnectionTimeout()
                    );
                    refresh();
                    // Connect in background
                    new Thread(() -> {
                        backendBridge.reconnectDataSource(dialog.getDataSourceName());
                        Platform.runLater(() -> refresh());
                    }).start();
                } catch (Exception ex) {
                    System.err.println("添加数据源失败: " + ex.getMessage());
                }
            }
        });

        content.getChildren().addAll(titleBox, dataSourceList, addBtn);
        VBox.setMargin(addBtn, new Insets(24, 0, 0, 0));

        scrollPane.setContent(content);
        getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    public void setBackendBridge(BackendBridge bridge) {
        this.backendBridge = bridge;
        refresh();
    }

    public void refresh() {
        if (backendBridge == null) return;
        dataSourceList.getChildren().clear();

        Map<String, DbDataSourceConfig> dbs =
            backendBridge.getConfig().getTools().getDb().getDatasources();
        for (Map.Entry<String, DbDataSourceConfig> entry : dbs.entrySet()) {
            String name = entry.getKey();
            DbDataSourceConfig cfg = entry.getValue();
            String jdbcUrl = cfg.getJdbcUrl();

            String dbType = DataSourceManager.inferDbType(jdbcUrl);
            if (dbType == null) dbType = jdbcUrl != null && jdbcUrl.contains(":") ? jdbcUrl.split(":")[1] : "unknown";

            BackendBridge.DataSourceStatus status = backendBridge.getDataSourceStatus(name);
            String statusText;
            boolean isGood;
            boolean isEnabled;
            switch (status) {
                case CONNECTED:
                    statusText = "已连接";
                    isGood = true;
                    isEnabled = true;
                    break;
                case DISABLED:
                    statusText = "已禁用";
                    isGood = false;
                    isEnabled = false;
                    break;
                default:
                    statusText = "未连接";
                    isGood = false;
                    isEnabled = true;
                    break;
            }

            dataSourceList.getChildren().add(
                new DataSourceCard(name, jdbcUrl, dbType, cfg.getMaxPoolSize(),
                    statusText, isGood, isEnabled, createCardCallback(), cfg));
        }
    }

    private DataSourceCard.Callback createCardCallback() {
        return new DataSourceCard.Callback() {
            @Override
            public void onEdit(String name, DbDataSourceConfig cfg) {
                AddDataSourceDialog dialog = new AddDataSourceDialog(stage, backendBridge, name, cfg);
                dialog.showAndWait();
                if (dialog.isConfirmed()) {
                    try {
                        backendBridge.updateDataSource(
                            dialog.getEditOldName(),
                            dialog.getDataSourceName(),
                            dialog.getJdbcUrl(),
                            dialog.getUsername(),
                            dialog.getPassword(),
                            dialog.getDriverClass(),
                            dialog.getMaxPoolSize(),
                            dialog.getConnectionTimeout()
                        );
                        refresh();
                        new Thread(() -> {
                            backendBridge.reconnectDataSource(dialog.getDataSourceName());
                            Platform.runLater(() -> refresh());
                        }).start();
                    } catch (Exception ex) {
                        System.err.println("编辑数据源失败: " + ex.getMessage());
                    }
                }
            }

            @Override
            public void onReconnect(String name) {
                new Thread(() -> {
                    backendBridge.reconnectDataSource(name);
                    Platform.runLater(() -> refresh());
                }).start();
            }

            @Override
            public void onDelete(String name) {
                try {
                    backendBridge.deleteDataSource(name);
                    refresh();
                } catch (Exception ex) {
                    System.err.println("删除数据源失败: " + ex.getMessage());
                }
            }

            @Override
            public void onToggle(String name, boolean enable) {
                try {
                    backendBridge.toggleDataSource(name, enable);
                    refresh();
                } catch (Exception ex) {
                    System.err.println("切换数据源状态失败: " + ex.getMessage());
                }
            }
        };
    }
}
