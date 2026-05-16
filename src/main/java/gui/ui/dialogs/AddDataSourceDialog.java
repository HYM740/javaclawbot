package gui.ui.dialogs;

import config.tool.DbDataSourceConfig;
import gui.ui.BackendBridge;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.LinkedHashMap;
import java.util.Map;

public class AddDataSourceDialog extends Stage {

    private static final Map<String, String> DRIVER_MAP = new LinkedHashMap<>();
    static {
        DRIVER_MAP.put("jdbc:mysql:", "com.mysql.cj.jdbc.Driver");
        DRIVER_MAP.put("jdbc:postgresql:", "org.postgresql.Driver");
        DRIVER_MAP.put("jdbc:mariadb:", "org.mariadb.jdbc.Driver");
        DRIVER_MAP.put("jdbc:oracle:thin:", "oracle.jdbc.OracleDriver");
        DRIVER_MAP.put("jdbc:sqlserver:", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        DRIVER_MAP.put("jdbc:h2:", "org.h2.Driver");
        DRIVER_MAP.put("jdbc:sqlite:", "org.sqlite.JDBC");
    }

    public static String inferDriverClass(String jdbcUrl) {
        if (jdbcUrl == null) return null;
        for (Map.Entry<String, String> entry : DRIVER_MAP.entrySet()) {
            if (jdbcUrl.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // === 简版模式 — 数据库类型定义 ===
    public enum DbType {
        MYSQL("MySQL", "jdbc:mysql://", 3306, "com.mysql.cj.jdbc.Driver"),
        POSTGRESQL("PostgreSQL", "jdbc:postgresql://", 5432, "org.postgresql.Driver"),
        MARIADB("MariaDB", "jdbc:mariadb://", 3306, "org.mariadb.jdbc.Driver"),
        ORACLE("Oracle", "jdbc:oracle:thin:@//", 1521, "oracle.jdbc.OracleDriver"),
        SQLSERVER("SQL Server", "jdbc:sqlserver://", 1433, "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
        H2("H2", "jdbc:h2:", -1, "org.h2.Driver"),
        SQLITE("SQLite", "jdbc:sqlite:", -1, "org.sqlite.JDBC");

        public final String label;
        public final String jdbcPrefix;
        public final int defaultPort;
        public final String driverClass;

        DbType(String label, String jdbcPrefix, int defaultPort, String driverClass) {
            this.label = label;
            this.jdbcPrefix = jdbcPrefix;
            this.defaultPort = defaultPort;
            this.driverClass = driverClass;
        }

        public boolean isFileBased() {
            return this == H2 || this == SQLITE;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final java.util.Map<String, DbType> PREFIX_TO_TYPE = new java.util.LinkedHashMap<>();
    static {
        for (DbType t : DbType.values()) {
            PREFIX_TO_TYPE.put(t.jdbcPrefix, t);
        }
    }

    public static String buildJdbcUrl(DbType type, String host, String port, String dbName) {
        if (type.isFileBased()) {
            return type.jdbcPrefix + dbName;
        }
        if (type == DbType.SQLSERVER) {
            return type.jdbcPrefix + host + ":" + port + ";databaseName=" + dbName;
        }
        return type.jdbcPrefix + host + ":" + port + "/" + dbName;
    }

    /**
     * 从已有 jdbcUrl 解析出 (DbType, host, port, dbName)。返回 null 表示无法解析。
     */
    public static record ParsedUrl(DbType type, String host, String port, String dbName) {}

    public static ParsedUrl parseJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return null;
        for (DbType type : DbType.values()) {
            if (!jdbcUrl.startsWith(type.jdbcPrefix)) continue;
            String rest = jdbcUrl.substring(type.jdbcPrefix.length());
            if (type.isFileBased()) {
                return new ParsedUrl(type, "", "", rest);
            }
            if (type == DbType.SQLSERVER) {
                String[] parts = rest.split(";databaseName=", 2);
                if (parts.length != 2) return null;
                String[] hostPort = parts[0].split(":", 2);
                if (hostPort.length != 2) return null;
                return new ParsedUrl(type, hostPort[0], hostPort[1], parts[1]);
            }
            // jdbcPrefix 已包含 "//"（如 "jdbc:mysql://"），rest 直接是 host:port/dbName
            int slash = rest.indexOf('/');
            if (slash < 0) return null;
            String hostPort = rest.substring(0, slash);
            String dbName = rest.substring(slash + 1);
            String[] hp = hostPort.split(":", 2);
            if (hp.length != 2) return null;
            return new ParsedUrl(type, hp[0], hp[1], dbName);
        }
        return null;
    }

    // === 简版模式字段 ===
    private boolean useSimpleMode = true;
    private final ComboBox<DbType> dbTypeCombo;
    private final TextField hostField;
    private final TextField portField;
    private final TextField dbNameField;
    private final TextField filePathField;
    private final Button fileBrowseBtn;
    private final Label jdbcUrlPreviewLabel;
    private final VBox hostPortBox;
    private final VBox filePathBox;
    // === 原有字段 ===
    private final TextField nameField;
    private final TextField jdbcUrlField;
    private final ComboBox<String> driverCombo;
    private final Label driverLabel;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final TextField maxPoolSizeField;
    private final TextField timeoutField;
    private final Label testResultLabel;
    private final Label title;
    private final Button confirmBtn;
    private boolean confirmed = false;
    private final String editOldName;
    private final BackendBridge backendBridge;
    /** Stored old password for edit mode */
    private final String oldPassword;

    /** 新增模式 */
    public AddDataSourceDialog(Stage owner, BackendBridge bridge) {
        this(owner, bridge, null, null);
    }

    /** 编辑模式 */
    public AddDataSourceDialog(Stage owner, BackendBridge bridge, String oldName, DbDataSourceConfig existing) {
        this.editOldName = oldName;
        this.backendBridge = bridge;
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.TRANSPARENT);

        boolean isEdit = oldName != null;

        // 编辑模式：尝试解析已有 jdbcUrl
        ParsedUrl parsed = null;
        if (isEdit && existing != null) {
            parsed = parseJdbcUrl(existing.getJdbcUrl());
            if (parsed == null) {
                useSimpleMode = false;  // 解析失败 → 高级模式
            }
        }

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #faf9f5; -fx-background-radius: 16px; -fx-border-color: #e6dfd8; -fx-border-radius: 16px; -fx-border-width: 1px;");
        root.setPrefWidth(520);

        // Title
        title = new Label(isEdit ? "编辑数据源" : "添加数据源");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 500;");

        // Name
        VBox nameBox = new VBox(4);
        Label nameLabel = new Label("数据源名称");
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        nameField = new TextField(isEdit ? oldName : "");
        nameField.getStyleClass().add("input-field");
        nameField.setPrefHeight(40);
        nameField.setPromptText("例如: mydb");
        nameBox.getChildren().addAll(nameLabel, nameField);

        // ==================== 简版模式（默认） ====================

        // 数据库类型
        VBox typeBox = new VBox(4);
        Label typeLabel = new Label("数据库类型");
        typeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        dbTypeCombo = new ComboBox<>();
        dbTypeCombo.getItems().addAll(DbType.values());
        dbTypeCombo.setPrefHeight(40);
        dbTypeCombo.setMaxWidth(Double.MAX_VALUE);
        dbTypeCombo.getStyleClass().add("input-field");

        if (parsed != null) {
            dbTypeCombo.setValue(parsed.type());
        } else {
            dbTypeCombo.setValue(DbType.MYSQL);
        }
        typeBox.getChildren().addAll(typeLabel, dbTypeCombo);

        // 主机 + 端口行
        hostPortBox = new VBox(4);
        HBox hostPortRow = new HBox(12);
        VBox hostPart = new VBox(4);
        Label hostLabel = new Label("主机");
        hostLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        hostField = new TextField(parsed != null ? parsed.host() : "");
        hostField.getStyleClass().add("input-field");
        hostField.setPrefHeight(40);
        hostField.setPromptText("localhost");
        hostPart.getChildren().addAll(hostLabel, hostField);
        HBox.setHgrow(hostPart, Priority.ALWAYS);

        VBox portPart = new VBox(4);
        portPart.setPrefWidth(100);
        Label portLabel = new Label("端口");
        portLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        portField = new TextField(parsed != null ? parsed.port() : "");
        portField.getStyleClass().add("input-field");
        portField.setPrefHeight(40);
        portPart.getChildren().addAll(portLabel, portField);

        hostPortRow.getChildren().addAll(hostPart, portPart);
        hostPortBox.getChildren().add(hostPortRow);

        // 数据库名
        VBox dbNameBox = new VBox(4);
        Label dbNameLabel = new Label("数据库名");
        dbNameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        dbNameField = new TextField(parsed != null ? parsed.dbName() : "");
        dbNameField.getStyleClass().add("input-field");
        dbNameField.setPrefHeight(40);
        dbNameField.setPromptText("mydb");
        dbNameBox.getChildren().addAll(dbNameLabel, dbNameField);

        // 文件路径（H2/SQLite，初始隐藏）
        filePathBox = new VBox(4);
        Label filePathLabel = new Label("数据库文件");
        filePathLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        HBox fileRow = new HBox(8);
        filePathField = new TextField(parsed != null && parsed.type().isFileBased() ? parsed.dbName() : "");
        filePathField.getStyleClass().add("input-field");
        filePathField.setPrefHeight(40);
        filePathField.setPromptText("C:/data/mydb.db");
        fileBrowseBtn = new Button("浏览...");
        fileBrowseBtn.getStyleClass().add("pill-button");
        fileBrowseBtn.setPrefHeight(40);
        fileBrowseBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("选择数据库文件");
            java.io.File f = fc.showOpenDialog(getOwner());
            if (f != null) filePathField.setText(f.getAbsolutePath());
        });
        fileRow.getChildren().addAll(filePathField, fileBrowseBtn);
        HBox.setHgrow(filePathField, Priority.ALWAYS);
        filePathBox.getChildren().addAll(filePathLabel, fileRow);
        filePathBox.setVisible(false);
        filePathBox.setManaged(false);

        // 数据库类型切换监听：自动填充端口 + 切换字段可见性
        dbTypeCombo.valueProperty().addListener((obs, old, val) -> {
            if (val == null) return;
            if (val.defaultPort > 0) {
                portField.setText(String.valueOf(val.defaultPort));
            } else {
                portField.setText("");
            }
            boolean fileBased = val.isFileBased();
            hostPortBox.setVisible(!fileBased);
            hostPortBox.setManaged(!fileBased);
            dbNameBox.setVisible(!fileBased);
            dbNameBox.setManaged(!fileBased);
            filePathBox.setVisible(fileBased);
            filePathBox.setManaged(fileBased);
            updateJdbcUrlPreview();
        });

        // 新建模式：显式设置默认端口（listener 在 setValue 之后才添加，需手动触发）
        if (parsed == null) {
            DbType initialType = dbTypeCombo.getValue();
            if (initialType != null && initialType.defaultPort > 0) {
                portField.setText(String.valueOf(initialType.defaultPort));
            }
        }

        // 字段变更 → 更新 URL 预览
        hostField.textProperty().addListener((obs, o, n) -> updateJdbcUrlPreview());
        portField.textProperty().addListener((obs, o, n) -> updateJdbcUrlPreview());
        dbNameField.textProperty().addListener((obs, o, n) -> updateJdbcUrlPreview());
        filePathField.textProperty().addListener((obs, o, n) -> updateJdbcUrlPreview());

        // JDBC URL 实时预览
        VBox previewBox = new VBox(4);
        Label previewHeader = new Label("JDBC URL 预览");
        previewHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: 500; -fx-text-fill: #a09d96;");
        jdbcUrlPreviewLabel = new Label();
        jdbcUrlPreviewLabel.setStyle(
            "-fx-background-color: #181715; -fx-text-fill: #faf9f5; " +
            "-fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 13px; " +
            "-fx-padding: 12px 14px; -fx-background-radius: 8px;"
        );
        jdbcUrlPreviewLabel.setWrapText(true);
        jdbcUrlPreviewLabel.setMaxWidth(Double.MAX_VALUE);
        previewBox.getChildren().addAll(previewHeader, jdbcUrlPreviewLabel);

        // 简版模式容器
        VBox simpleModeBox = new VBox(8);
        simpleModeBox.getChildren().addAll(typeBox, hostPortBox, dbNameBox, filePathBox, previewBox);
        simpleModeBox.setVisible(useSimpleMode);
        simpleModeBox.setManaged(useSimpleMode);

        // ==================== 高级模式 ====================

        // JDBC URL
        VBox urlBox = new VBox(4);
        Label urlLabel = new Label("JDBC URL");
        urlLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        jdbcUrlField = new TextField(isEdit && existing != null ? existing.getJdbcUrl() : "");
        jdbcUrlField.getStyleClass().add("input-field");
        jdbcUrlField.setPrefHeight(40);
        jdbcUrlField.setPromptText("jdbc:mysql://localhost:3306/mydb");
        urlBox.getChildren().addAll(urlLabel, jdbcUrlField);

        // Driver Class
        driverCombo = new ComboBox<>();
        driverCombo.getItems().addAll(DRIVER_MAP.values());
        driverCombo.setPrefHeight(40);
        driverCombo.setMaxWidth(Double.MAX_VALUE);
        driverCombo.setVisible(false);
        driverCombo.setManaged(false);

        driverLabel = new Label("Driver Class");
        driverLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        driverLabel.setVisible(false);
        driverLabel.setManaged(false);

        VBox driverBox = new VBox(4);
        driverBox.getChildren().addAll(driverLabel, driverCombo);

        jdbcUrlField.textProperty().addListener((obs, oldVal, val) -> {
            if (useSimpleMode) return;
            String inferred = inferDriverClass(val);
            if (inferred != null) {
                driverCombo.setValue(inferred);
                driverCombo.setVisible(false);
                driverCombo.setManaged(false);
                driverLabel.setVisible(false);
                driverLabel.setManaged(false);
            } else {
                driverCombo.setVisible(true);
                driverCombo.setManaged(true);
                driverLabel.setVisible(true);
                driverLabel.setManaged(true);
            }
        });

        // 高级模式容器
        VBox advancedModeBox = new VBox(8);
        advancedModeBox.getChildren().addAll(urlBox, driverBox);
        advancedModeBox.setVisible(!useSimpleMode);
        advancedModeBox.setManaged(!useSimpleMode);

        // ==================== 模式切换链接 ====================

        Button toggleModeLink = new Button(useSimpleMode ? "切换到高级模式 →" : "← 切换到简版模式");
        toggleModeLink.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #cc785c; " +
            "-fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: hand; " +
            "-fx-padding: 0;"
        );
        toggleModeLink.setOnAction(e -> {
            useSimpleMode = !useSimpleMode;
            simpleModeBox.setVisible(useSimpleMode);
            simpleModeBox.setManaged(useSimpleMode);
            advancedModeBox.setVisible(!useSimpleMode);
            advancedModeBox.setManaged(!useSimpleMode);
            toggleModeLink.setText(useSimpleMode ? "切换到高级模式 →" : "← 切换到简版模式");
            if (useSimpleMode) {
                updateJdbcUrlPreview();
            }
        });

        // ==================== 用户名 / 密码 ====================

        VBox userBox = new VBox(4);
        Label userLabel = new Label("用户名");
        userLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        usernameField = new TextField(isEdit && existing != null ? existing.getUsername() : "root");
        usernameField.getStyleClass().add("input-field");
        usernameField.setPrefHeight(40);
        usernameField.setPromptText("root");
        userBox.getChildren().addAll(userLabel, usernameField);

        VBox passBox = new VBox(4);
        Label passLabel = new Label("密码");
        passLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        passwordField = new PasswordField();
        passwordField.getStyleClass().add("input-field");
        passwordField.setPrefHeight(40);
        if (isEdit) {
            passwordField.setText("******");
            oldPassword = existing != null ? existing.getPassword() : "";
        } else {
            oldPassword = null;
        }
        passBox.getChildren().addAll(passLabel, passwordField);

        // ==================== 最大连接数 / 超时 ====================

        VBox poolBox = new VBox(4);
        Label poolLabel = new Label("最大连接数");
        poolLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        maxPoolSizeField = new TextField(isEdit && existing != null ? String.valueOf(existing.getMaxPoolSize()) : "10");
        maxPoolSizeField.getStyleClass().add("input-field");
        maxPoolSizeField.setPrefHeight(40);
        poolBox.getChildren().addAll(poolLabel, maxPoolSizeField);

        VBox timeoutBox = new VBox(4);
        Label timeoutLabel = new Label("连接超时(ms)");
        timeoutLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        timeoutField = new TextField(isEdit && existing != null ? String.valueOf(existing.getConnectionTimeout()) : "30000");
        timeoutField.getStyleClass().add("input-field");
        timeoutField.setPrefHeight(40);
        timeoutBox.getChildren().addAll(timeoutLabel, timeoutField);

        // ==================== 测试连接 + 结果 ====================

        HBox testBox = new HBox(8);
        testBox.setAlignment(Pos.CENTER_LEFT);
        testBox.setPadding(new Insets(8, 0, 0, 0));

        Button testBtn = new Button("测试连接");
        testBtn.setStyle(
            "-fx-background-color: #faf9f5; -fx-text-fill: #141413; " +
            "-fx-border-color: #e6dfd8; -fx-border-width: 1px; " +
            "-fx-background-radius: 8px; -fx-border-radius: 8px; " +
            "-fx-font-size: 13px; -fx-font-weight: 500;"
        );
        testBtn.setPrefHeight(36);

        testResultLabel = new Label();
        testResultLabel.setStyle("-fx-font-size: 13px;");

        testBtn.setOnAction(e -> {
            testBtn.setDisable(true);
            testBtn.setText("测试中...");
            testResultLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
            testResultLabel.setText("正在连接...");

            String url;
            String driver;
            if (useSimpleMode) {
                DbType selectedType = dbTypeCombo.getValue();
                if (selectedType == null) {
                    Platform.runLater(() -> {
                        testResultLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #c64545;");
                        testResultLabel.setText("✗ 请选择数据库类型");
                        testBtn.setDisable(false);
                        testBtn.setText("测试连接");
                    });
                    return;
                }
                url = buildJdbcUrl(selectedType, hostField.getText(), portField.getText(),
                    selectedType.isFileBased() ? filePathField.getText() : dbNameField.getText());
                driver = selectedType.driverClass;
            } else {
                url = jdbcUrlField.getText();
                driver = driverCombo.isVisible() ? driverCombo.getValue() : inferDriverClass(url);
            }
            String user = usernameField.getText();
            String pass = passwordField.getText();

            new Thread(() -> {
                String result = backendBridge.testDataSourceConnection(url, user, pass, driver);
                Platform.runLater(() -> {
                    testBtn.setDisable(false);
                    testBtn.setText("测试连接");
                    if (result == null) {
                        testResultLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #5db872;");
                        testResultLabel.setText("✓ 连接成功");
                    } else {
                        testResultLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #c64545;");
                        testResultLabel.setText("✗ 连接失败: " + result);
                    }
                });
            }).start();
        });

        testBox.getChildren().addAll(testBtn, testResultLabel);

        // ==================== 底部按钮 ====================

        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("pill-button");
        cancelBtn.setPrefHeight(40);
        cancelBtn.setOnAction(e -> close());

        confirmBtn = new Button(isEdit ? "保存" : "添加");
        confirmBtn.setPrefHeight(40);
        confirmBtn.setStyle(
            "-fx-background-color: #cc785c; -fx-text-fill: white; " +
            "-fx-background-radius: 8px; -fx-border-radius: 8px; " +
            "-fx-border-color: #cc785c; -fx-border-width: 1px; " +
            "-fx-font-size: 14px; -fx-font-weight: 500;"
        );
        confirmBtn.setOnAction(e -> {
            if (nameField.getText() == null || nameField.getText().isBlank()) return;
            if (useSimpleMode) {
                DbType selectedType = dbTypeCombo.getValue();
                if (selectedType == null) return;
                if (!selectedType.isFileBased()) {
                    if (hostField.getText() == null || hostField.getText().isBlank()) return;
                    if (portField.getText() == null || portField.getText().isBlank()) return;
                    if (dbNameField.getText() == null || dbNameField.getText().isBlank()) return;
                } else {
                    if (filePathField.getText() == null || filePathField.getText().isBlank()) return;
                }
            } else {
                if (jdbcUrlField.getText() == null || jdbcUrlField.getText().isBlank()) return;
            }
            if (usernameField.getText() == null || usernameField.getText().isBlank()) return;
            confirmed = true;
            close();
        });

        buttonBox.getChildren().addAll(cancelBtn, confirmBtn);

        // ==================== 组装 ====================

        root.getChildren().addAll(
            title, nameBox, simpleModeBox, advancedModeBox, toggleModeLink,
            userBox, passBox, poolBox, timeoutBox, testBox, buttonBox
        );

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/static/css/styles/main.css").toExternalForm());
        setScene(scene);

        // 初始化 URL 预览
        if (useSimpleMode) {
            updateJdbcUrlPreview();
        }
    }

    private void updateJdbcUrlPreview() {
        DbType type = dbTypeCombo.getValue();
        if (type == null) {
            jdbcUrlPreviewLabel.setText("");
            return;
        }
        String url;
        if (type.isFileBased()) {
            url = buildJdbcUrl(type, "", "", filePathField.getText());
        } else {
            url = buildJdbcUrl(type, hostField.getText(), portField.getText(), dbNameField.getText());
        }
        jdbcUrlPreviewLabel.setText(url);
    }

    public boolean isConfirmed() { return confirmed; }
    public boolean isEditMode() { return editOldName != null; }
    public String getEditOldName() { return editOldName; }
    public String getDataSourceName() { return nameField.getText(); }

    public String getJdbcUrl() {
        if (useSimpleMode) {
            DbType type = dbTypeCombo.getValue();
            if (type == null) return "";
            if (type.isFileBased()) {
                return buildJdbcUrl(type, "", "", filePathField.getText());
            }
            return buildJdbcUrl(type, hostField.getText(), portField.getText(), dbNameField.getText());
        }
        return jdbcUrlField.getText();
    }

    public String getDriverClass() {
        if (useSimpleMode) {
            DbType type = dbTypeCombo.getValue();
            return type != null ? type.driverClass : null;
        }
        String inferred = inferDriverClass(jdbcUrlField.getText());
        return inferred != null ? inferred : driverCombo.getValue();
    }

    public String getUsername() { return usernameField.getText(); }
    public String getPassword() { return passwordField.getText(); }
    public int getMaxPoolSize() {
        try { return Integer.parseInt(maxPoolSizeField.getText()); }
        catch (NumberFormatException e) { return 10; }
    }
    public long getConnectionTimeout() {
        try { return Long.parseLong(timeoutField.getText()); }
        catch (NumberFormatException e) { return 30000; }
    }
    /** Get stored old password (for edit mode) */
    public String getOldPassword() { return oldPassword; }
}
