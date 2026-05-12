package gui.ui.components;

import agent.tool.file.FileBackupManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件差异回滚浮标 — 连体布局的上半部分。
 * <p>
 * 展示当前会话中被修改的文件列表、版本历史和回滚操作。
 * 与 TodoFloatBadge 组成连体容器：FileDiffBadge(上) + TodoFloatBadge(下)。
 * <p>
 * 功能：
 * - 显示被修改文件数量
 * - 展开面板展示文件列表、多版本管理
 * - 点击「对比」弹出 IDEA 分屏差异查看器
 * - 点击「回滚」执行单个文件/批量回滚
 */
public class FileDiffBadge extends VBox {

    private static final DateTimeFormatter DISPLAY_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Label countLabel;
    private final VBox dropdown;
    private final HBox headerRow;
    private final Label arrowLabel;
    private boolean dropdownVisible = false;

    /** 修改文件索引: 文件路径 → 有序备份版本列表 */
    private final Map<Path, List<FileBackupManager.BackupEntry>> fileMap = new LinkedHashMap<>();
    private FileBackupManager backupManager;

    /** 差异查看器弹出回调 */
    private java.util.function.BiConsumer<Path, FileBackupManager.BackupEntry> onShowDiff;
    /** 回滚完成回调 */
    private Runnable onRollbackComplete;

    public FileDiffBadge() {
        setVisible(false);
        setManaged(false);

        // 连体容器上半部分样式：白色背景，上圆角，底部无圆角（与下部连接）
        setStyle("-fx-background-color: white; -fx-background-radius: 16px 16px 0 0;"
                + " -fx-border-color: rgba(0,0,0,0.08) rgba(0,0,0,0.08) rgba(0,0,0,0.04) rgba(0,0,0,0.08);"
                + " -fx-border-width: 1px 1px 0 1px;"
                + " -fx-border-radius: 16px 16px 0 0;");
        setMinWidth(180);
        setMaxWidth(180);
        setCursor(Cursor.HAND);

        // 头部行
        headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(8, 14, 8, 14));

        // 橙色图标圆圈
        Label iconCircle = new Label("\uD83D\uDCDD"); // 📝
        iconCircle.setStyle("-fx-background-color: rgba(245,158,11,0.12);"
                + " -fx-background-radius: 50%; -fx-pref-width: 20px; -fx-pref-height: 20px;"
                + " -fx-alignment: center; -fx-font-size: 11px;");
        iconCircle.setMinSize(20, 20);

        Label titleLabel = new Label("文件变更");
        titleLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: rgba(0,0,0,0.8);");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        countLabel = new Label("0");
        countLabel.setStyle("-fx-background-color: rgba(245,158,11,0.1);"
                + " -fx-background-radius: 10px; -fx-padding: 1px 8px;"
                + " -fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #d97706;");

        arrowLabel = new Label("\u25B6"); // ▶
        arrowLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(0,0,0,0.3);");

        headerRow.getChildren().addAll(iconCircle, titleLabel, countLabel, arrowLabel);
        getChildren().add(headerRow);

        // 下拉面板（初始隐藏）
        dropdown = createDropdownPanel();

        // 点击切换
        setOnMouseClicked(e -> toggleDropdown());

        // 外部点击关闭下拉
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, ev -> {
                    if (dropdownVisible && !isInside(ev.getSceneX(), ev.getSceneY())) {
                        hideDropdown();
                    }
                });
            }
        });
    }

    // ===== 公共方法 =====

    /**
     * 添加一个修改文件记录。
     * 由外部（ChatPage/MainStage）在收到 _file_modified 事件时调用。
     */
    public void addModifiedFile(Path filePath, FileBackupManager.BackupEntry entry) {
        fileMap.computeIfAbsent(filePath, k -> new ArrayList<>()).add(entry);
        updateCount();
        setVisible(true);
        setManaged(true);
    }

    /** 设置 FileBackupManager 引用 */
    public void setBackupManager(FileBackupManager manager) {
        this.backupManager = manager;
    }

    /** 设置差异查看回调 */
    public void setOnShowDiff(java.util.function.BiConsumer<Path, FileBackupManager.BackupEntry> callback) {
        this.onShowDiff = callback;
    }

    /** 设置回滚完成回调 */
    public void setOnRollbackComplete(Runnable callback) {
        this.onRollbackComplete = callback;
    }

    /** 获取所有修改文件 */
    public Map<Path, List<FileBackupManager.BackupEntry>> getFileMap() {
        return Collections.unmodifiableMap(fileMap);
    }

    /** 清理所有文件记录 */
    public void clearFiles() {
        fileMap.clear();
        countLabel.setText("0");
        setVisible(false);
        setManaged(false);
        hideDropdown();
    }

    // ===== 下拉面板 =====

    private VBox createDropdownPanel() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: white;"
                + " -fx-background-radius: 0 0 12px 12px;"
                + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-width: 0 1px 1px 1px;"
                + " -fx-border-radius: 0 0 12px 12px;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 12, 0, 0, 4);");
        panel.setMinWidth(300);
        panel.setMaxWidth(300);
        panel.setMaxHeight(360);
        panel.setVisible(false);
        panel.setManaged(false);
        return panel;
    }

    private void toggleDropdown() {
        if (dropdownVisible) {
            hideDropdown();
        } else {
            showDropdown();
        }
    }

    private void showDropdown() {
        refreshDropdownContent();
        dropdown.setVisible(true);
        dropdown.setManaged(true);
        dropdownVisible = true;
        arrowLabel.setText("\u25BC"); // ▼
    }

    private void hideDropdown() {
        dropdown.setVisible(false);
        dropdown.setManaged(false);
        dropdownVisible = false;
        arrowLabel.setText("\u25B6"); // ▶
    }

    private void refreshDropdownContent() {
        dropdown.getChildren().clear();

        // ---- Header ----
        HBox header = new HBox();
        header.setPadding(new Insets(12, 14, 8, 14));
        header.setStyle("-fx-border-color: transparent transparent rgba(0,0,0,0.06) transparent;"
                + " -fx-border-width: 0 0 1 0;");

        Label title = new Label("\uD83D\uDCDD 文件变更");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: rgba(0,0,0,0.8);");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        int totalVersions = fileMap.values().stream().mapToInt(List::size).sum();
        Label info = new Label(fileMap.size() + " 个文件 \u00B7 " + totalVersions + " 个版本");
        info.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(0,0,0,0.4);");

        header.getChildren().addAll(title, spacer, info);
        dropdown.getChildren().add(header);

        // ---- 文件列表 ----
        VBox listBox = new VBox(0);
        listBox.setPadding(new Insets(4, 0, 8, 0));

        if (fileMap.isEmpty()) {
            Label empty = new Label("暂无文件变更记录");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.3); -fx-padding: 16px;");
            listBox.getChildren().add(empty);
        } else {
            for (var entry : fileMap.entrySet()) {
                Path filePath = entry.getKey();
                List<FileBackupManager.BackupEntry> versions = entry.getValue();
                addFileEntry(listBox, filePath, versions);
            }

            // 批量回滚按钮
            HBox batchBox = new HBox();
            batchBox.setPadding(new Insets(8, 14, 12, 14));
            Label batchBtn = new Label("\u21BA 回滚所有文件 ("
                    + fileMap.size() + " 个, " + totalVersions + " 个版本)");
            batchBtn.setStyle("-fx-background-color: rgba(239,68,68,0.06);"
                    + " -fx-border-color: rgba(239,68,68,0.15);"
                    + " -fx-border-radius: 8px; -fx-background-radius: 8px;"
                    + " -fx-padding: 8px; -fx-font-size: 12px; -fx-text-fill: #dc2626;"
                    + " -fx-font-weight: 500; -fx-cursor: hand;");
            batchBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(batchBtn, Priority.ALWAYS);
            batchBtn.setOnMouseClicked(e -> confirmBatchRollback());
            batchBox.getChildren().add(batchBtn);
            listBox.getChildren().add(batchBox);
        }

        dropdown.getChildren().add(listBox);
    }

    private void addFileEntry(VBox parent, Path filePath, List<FileBackupManager.BackupEntry> versions) {
        String fileName = filePath.getFileName().toString();

        // 文件头部行
        HBox fileHeader = new HBox(8);
        fileHeader.setPadding(new Insets(6, 14, 6, 14));
        fileHeader.setCursor(Cursor.HAND);
        fileHeader.setStyle("-fx-border-color: transparent transparent rgba(0,0,0,0.04) transparent;"
                + " -fx-border-width: 0 0 1 0;");

        Label icon = new Label("\uD83D\uDCC4"); // 📄
        icon.setStyle("-fx-font-size: 14px;");

        Label nameLabel = new Label(fileName);
        nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: rgba(0,0,0,0.8);");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label arrow = new Label("\u25B6"); // ▶
        arrow.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(0,0,0,0.3);");

        fileHeader.getChildren().addAll(icon, nameLabel, arrow);
        parent.getChildren().add(fileHeader);

        // 版本列表（初始隐藏）
        VBox versionsBox = new VBox(0);
        versionsBox.setVisible(false);
        versionsBox.setManaged(false);

        for (int i = 0; i < versions.size(); i++) {
            FileBackupManager.BackupEntry be = versions.get(i);
            int versionNum = i + 1;
            addVersionRow(versionsBox, be, versionNum, filePath);
        }

        parent.getChildren().add(versionsBox);

        // 点击切换展开
        fileHeader.setOnMouseClicked(e -> {
            boolean show = !versionsBox.isVisible();
            versionsBox.setVisible(show);
            versionsBox.setManaged(show);
            arrow.setText(show ? "\u25BC" : "\u25B6");
        });
    }

    private void addVersionRow(VBox parent, FileBackupManager.BackupEntry be,
                                int versionNum, Path originalPath) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 14, 4, 36));
        row.setStyle("-fx-border-color: transparent transparent rgba(0,0,0,0.03) transparent;"
                + " -fx-border-width: 0 0 1 0;");

        // 版本标签
        Label vLabel = new Label("v" + versionNum);
        vLabel.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-background-radius: 3px;"
                + " -fx-padding: 1px 6px; -fx-font-size: 9px; -fx-text-fill: rgba(0,0,0,0.4);");

        // 时间戳
        String displayTs = be.timestamp() != null && !be.timestamp().isEmpty()
                ? be.timestamp().replace("_", " ")
                : "";
        if (displayTs.length() > 19) displayTs = displayTs.substring(0, 19);
        Label tsLabel = new Label(displayTs);
        tsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.6);");
        HBox.setHgrow(tsLabel, Priority.ALWAYS);

        // 对比按钮
        Label diffBtn = new Label("对比");
        diffBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white;"
                + " -fx-background-radius: 4px; -fx-padding: 2px 8px; -fx-font-size: 10px; -fx-cursor: hand;");

        // 回滚按钮
        Label rbBtn = new Label("回滚");
        rbBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;"
                + " -fx-background-radius: 4px; -fx-padding: 2px 8px; -fx-font-size: 10px; -fx-cursor: hand;");

        row.getChildren().addAll(vLabel, tsLabel, diffBtn, rbBtn);
        parent.getChildren().add(row);

        // 事件
        diffBtn.setOnMouseClicked(e -> {
            e.consume();
            if (onShowDiff != null) {
                onShowDiff.accept(originalPath, be);
            }
        });

        rbBtn.setOnMouseClicked(e -> {
            e.consume();
            confirmSingleRollback(originalPath, be);
        });
    }

    // ===== 回滚逻辑 =====

    private void confirmSingleRollback(Path originalPath, FileBackupManager.BackupEntry be) {
        String displayTs = be.timestamp() != null && !be.timestamp().isEmpty()
                ? be.timestamp().replace("_", " ") : "";
        if (displayTs.length() > 19) displayTs = displayTs.substring(0, 19);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认回滚");
        alert.setHeaderText("回滚文件: " + originalPath.getFileName());
        alert.setContentText("将文件回滚到版本 " + displayTs + "？\n此操作将覆盖当前文件内容。");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK && backupManager != null) {
                boolean ok = backupManager.restore(originalPath, be);
                if (ok) {
                    // 从索引中移除该版本
                    List<FileBackupManager.BackupEntry> versions = fileMap.get(originalPath);
                    if (versions != null) {
                        versions.remove(be);
                        if (versions.isEmpty()) {
                            fileMap.remove(originalPath);
                        }
                    }
                    updateCount();
                    if (fileMap.isEmpty()) {
                        setVisible(false);
                        setManaged(false);
                    }
                    if (onRollbackComplete != null) {
                        onRollbackComplete.run();
                    }
                }
            }
        });
    }

    private void confirmBatchRollback() {
        int totalVersions = fileMap.values().stream().mapToInt(List::size).sum();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认批量回滚");
        alert.setHeaderText("回滚所有文件");
        alert.setContentText("将 " + fileMap.size() + " 个文件共 " + totalVersions
                + " 个版本全部回滚到初始版本？\n此操作不可撤销。");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK && backupManager != null) {
                boolean allOk = true;
                for (var entry : fileMap.entrySet()) {
                    Path path = entry.getKey();
                    List<FileBackupManager.BackupEntry> versions = entry.getValue();
                    if (!versions.isEmpty()) {
                        // 回滚到第一个版本（初始版本）
                        FileBackupManager.BackupEntry first = versions.get(0);
                        if (!backupManager.restore(path, first)) {
                            allOk = false;
                        }
                    }
                }
                fileMap.clear();
                updateCount();
                setVisible(false);
                setManaged(false);
                if (onRollbackComplete != null) {
                    onRollbackComplete.run();
                }
            }
        });
    }

    // ===== 辅助方法 =====

    private void updateCount() {
        int totalVersions = (int) fileMap.values().stream().mapToLong(List::size).sum();
        countLabel.setText(String.valueOf(totalVersions));
    }

    private boolean isInside(double sceneX, double sceneY) {
        try {
            // 检查是否在 FileDiffBadge 自身或 dropdown 区域内
            javafx.geometry.Bounds badgeBounds = localToScene(getBoundsInLocal());
            if (badgeBounds.contains(sceneX, sceneY)) return true;

            if (dropdown.isVisible()) {
                javafx.geometry.Bounds ddBounds = dropdown.localToScene(dropdown.getBoundsInLocal());
                if (ddBounds.contains(sceneX, sceneY)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
