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

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 16px; -fx-border-color: rgba(0, 0, 0, 0.1); -fx-border-radius: 16px; -fx-border-width: 1px;");
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

        // JDBC URL
        VBox urlBox = new VBox(4);
        Label urlLabel = new Label("JDBC URL");
        urlLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        jdbcUrlField = new TextField(isEdit && existing != null ? existing.getJdbcUrl() : "");
        jdbcUrlField.getStyleClass().add("input-field");
        jdbcUrlField.setPrefHeight(40);
        jdbcUrlField.setPromptText("jdbc:mysql://localhost:3306/mydb");
        urlBox.getChildren().addAll(urlLabel, jdbcUrlField);

        // Driver Class (hidden when inferred)
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

        // JDBC URL change listener for driver inference
        jdbcUrlField.textProperty().addListener((obs, old, val) -> {
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

        // Username
        VBox userBox = new VBox(4);
        Label userLabel = new Label("用户名");
        userLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        usernameField = new TextField(isEdit && existing != null ? existing.getUsername() : "");
        usernameField.getStyleClass().add("input-field");
        usernameField.setPrefHeight(40);
        usernameField.setPromptText("root");
        userBox.getChildren().addAll(userLabel, usernameField);

        // Password
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

        // Max pool size
        VBox poolBox = new VBox(4);
        Label poolLabel = new Label("最大连接数");
        poolLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        maxPoolSizeField = new TextField(isEdit && existing != null ? String.valueOf(existing.getMaxPoolSize()) : "10");
        maxPoolSizeField.getStyleClass().add("input-field");
        maxPoolSizeField.setPrefHeight(40);
        poolBox.getChildren().addAll(poolLabel, maxPoolSizeField);

        // Timeout
        VBox timeoutBox = new VBox(4);
        Label timeoutLabel = new Label("连接超时(ms)");
        timeoutLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
        timeoutField = new TextField(isEdit && existing != null ? String.valueOf(existing.getConnectionTimeout()) : "30000");
        timeoutField.getStyleClass().add("input-field");
        timeoutField.setPrefHeight(40);
        timeoutBox.getChildren().addAll(timeoutLabel, timeoutField);

        // Test connection button + result label
        HBox testBox = new HBox(8);
        testBox.setAlignment(Pos.CENTER_LEFT);
        testBox.setPadding(new Insets(8, 0, 0, 0));

        Button testBtn = new Button("测试连接");
        testBtn.getStyleClass().add("pill-button");
        testBtn.setPrefHeight(36);

        testResultLabel = new Label();
        testResultLabel.setStyle("-fx-font-size: 13px;");

        testBtn.setOnAction(e -> {
            testBtn.setDisable(true);
            testBtn.setText("测试中...");
            testResultLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
            testResultLabel.setText("正在连接...");

            String url = jdbcUrlField.getText();
            String user = usernameField.getText();
            String pass = passwordField.getText();
            String driver = driverCombo.isVisible() ? driverCombo.getValue() : inferDriverClass(url);

            // Run test in background
            new Thread(() -> {
                String result = backendBridge.testDataSourceConnection(url, user, pass, driver);
                Platform.runLater(() -> {
                    testBtn.setDisable(false);
                    testBtn.setText("测试连接");
                    if (result == null) {
                        testResultLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #22c55e;");
                        testResultLabel.setText("连接成功");
                    } else {
                        testResultLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #dc2626;");
                        testResultLabel.setText("连接失败: " + result);
                    }
                });
            }).start();
        });

        testBox.getChildren().addAll(testBtn, testResultLabel);

        // Buttons
        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("pill-button");
        cancelBtn.setPrefHeight(40);
        cancelBtn.setOnAction(e -> close());

        confirmBtn = new Button(isEdit ? "保存" : "添加");
        confirmBtn.getStyleClass().add("pill-button");
        confirmBtn.setPrefHeight(40);
        confirmBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 999px; -fx-border-radius: 999px; -fx-border-color: #3b82f6; -fx-border-width: 1px;");
        confirmBtn.setOnAction(e -> {
            if (nameField.getText() == null || nameField.getText().isBlank()) {
                return;
            }
            if (jdbcUrlField.getText() == null || jdbcUrlField.getText().isBlank()) {
                return;
            }
            if (usernameField.getText() == null || usernameField.getText().isBlank()) {
                return;
            }
            confirmed = true;
            close();
        });

        buttonBox.getChildren().addAll(cancelBtn, confirmBtn);

        root.getChildren().addAll(
            title, nameBox, urlBox, driverBox, userBox, passBox,
            poolBox, timeoutBox, testBox, buttonBox
        );

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/static/css/styles/main.css").toExternalForm());
        setScene(scene);
    }

    public boolean isConfirmed() { return confirmed; }
    public boolean isEditMode() { return editOldName != null; }
    public String getEditOldName() { return editOldName; }
    public String getDataSourceName() { return nameField.getText(); }
    public String getJdbcUrl() { return jdbcUrlField.getText(); }
    public String getDriverClass() {
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
