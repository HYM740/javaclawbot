package gui.ui.components;

import agent.tool.file.FileBackupManager;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * 连体状态浮标 — 文件变更 + 任务进度，常驻展示。
 * <p>
 * 固定在右下角，两个区域上下排列，始终可见（无数据时显示 0 / 0/0）。
 * 点击任一区域弹出中间 Stage 弹窗展示详情。
 */
public class FileDiffBadge extends VBox {

    // ===== 文件面板 HTML 模板 =====
    private static final String FILE_PANEL_HTML = "<!DOCTYPE html><html style='height:100%;background:white;'>"
        + "<head><meta charset='UTF-8'><style>"
        + "*{margin:0;padding:0;box-sizing:border-box;}"
        + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
        + "font-size:12px;color:rgba(0,0,0,0.8);background:white;padding:8px 0;}"
        + ".file-header{cursor:pointer;padding:6px 14px;display:flex;align-items:center;"
        + "border-bottom:1px solid rgba(0,0,0,0.04);}"
        + ".file-header:hover{background:rgba(0,0,0,0.02);}"
        + ".file-icon{font-size:14px;margin-right:8px;}"
        + ".file-name{font-weight:500;flex:1;}"
        + ".file-arrow{color:rgba(0,0,0,0.3);font-size:10px;}"
        + ".versions{display:none;background:rgba(0,0,0,0.01);}"
        + ".versions.open{display:block;}"
        + ".version-row{display:flex;align-items:center;padding:4px 14px 4px 36px;"
        + "border-bottom:1px solid rgba(0,0,0,0.03);}"
        + ".v-badge{background:rgba(0,0,0,0.05);border-radius:3px;padding:1px 6px;"
        + "font-size:9px;color:rgba(0,0,0,0.4);margin-right:6px;}"
        + ".v-time{flex:1;font-size:11px;color:rgba(0,0,0,0.6);}"
        + ".btn-diff,.btn-rollback{padding:2px 8px;border-radius:4px;font-size:10px;"
        + "cursor:pointer;margin-left:4px;border:none;color:white;}"
        + ".btn-diff{background:#3b82f6;}"
        + ".btn-rollback{background:#ef4444;}"
        + ".batch-btn{display:block;margin:8px 14px 12px;padding:8px;text-align:center;"
        + "background:rgba(239,68,68,0.06);border:1px solid rgba(239,68,68,0.15);"
        + "border-radius:8px;color:#dc2626;font-weight:500;cursor:pointer;text-decoration:none;}"
        + ".panel-header{padding:8px 14px;border-bottom:1px solid rgba(0,0,0,0.06);"
        + "display:flex;justify-content:space-between;}"
        + ".panel-title{font-weight:600;font-size:13px;}"
        + ".panel-info{font-size:10px;color:rgba(0,0,0,0.4);}"
        + ".empty-hint{padding:24px;text-align:center;color:rgba(0,0,0,0.3);font-size:12px;}"
        + "</style></head><body>%s<script>"
        + "function toggleVersions(id){var el=document.getElementById('v-'+id);"
        + "var arrow=document.getElementById('a-'+id);"
        + "if(el.classList.contains('open')){el.classList.remove('open');arrow.textContent='▶';}"
        + "else{el.classList.add('open');arrow.textContent='▼';}}"
        + "function onDiff(fileIdx,verIdx){window.status='diff:'+fileIdx+':'+verIdx;}"
        + "function onRollback(fileIdx,verIdx){window.status='rollback:'+fileIdx+':'+verIdx;}"
        + "function onBatch(){window.status='batch';}"
        + "function onClear(){window.status='clear';}"
        + "</script></body></html>";

    // ===== 布局常量 =====
    private static final double BADGE_WIDTH = 210;
    private static final double POPUP_WIDTH = 320;
    private static final double POPUP_MAX_HEIGHT = 500;

    // ===== 文件数据 =====
    private final Map<Path, List<FileBackupManager.BackupEntry>> fileMap = new LinkedHashMap<>();
    private final List<Path> fileIndex = new ArrayList<>();
    private FileBackupManager backupManager;

    // ===== Todo 数据 =====
    private int todoCompleted = 0;
    private int todoTotal = 0;
    private List<Map<String, Object>> todoItems = new ArrayList<>();

    // ===== UI 组件 =====
    private final Label fileCountLabel;
    private final Label todoCountLabel;
    private final Label fileArrowLabel;
    private final Label todoArrowLabel;

    // ===== 弹窗追踪（防止重复打开） =====
    private Stage currentFilePopup;
    private Stage currentTodoPopup;

    public FileDiffBadge() {
        // 始终可见（常驻）
        setVisible(true);
        setManaged(true);
        setPickOnBounds(false);   // 自身不拦截点击，仅子节点（HBox行）响应

        setStyle("-fx-background-color: white; -fx-background-radius: 16px;"
                + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-width: 1px;"
                + " -fx-border-radius: 16px;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 16, 0, 0, 6);");
        setMinWidth(BADGE_WIDTH);
        setMaxWidth(BADGE_WIDTH);
        setPrefWidth(BADGE_WIDTH);
        // 固定高度：两行 + 分隔线 ≈ 82px
        setMinHeight(82);
        setMaxHeight(82);
        setPrefHeight(82);

        // ===== 上行：文件变更 =====
        HBox fileRow = createRow(
                "\uD83D\uDCDD",                              // 📝
                "rgba(245,158,11,0.12)",                     // orange bg
                "文件变更",
                fileCountLabel = createCountBadge("0", "rgba(245,158,11,0.1)", "#d97706"),
                fileArrowLabel = new Label("\u25B6")
        );
        fileRow.setCursor(Cursor.HAND);
        fileRow.setOnMouseClicked(e -> openFilePopup());
        // hover
        fileRow.setOnMouseEntered(e -> fileRow.setStyle("-fx-background-color: rgba(0,0,0,0.02);"));
        fileRow.setOnMouseExited(e -> fileRow.setStyle("-fx-background-color: transparent;"));

        // ===== 分隔线 =====
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: rgba(0,0,0,0.05);");
        sep.setPadding(new Insets(0, 14, 0, 14));

        // ===== 下行：任务进度 =====
        HBox todoRow = createRow(
                "\uD83D\uDCCB",                              // 📋
                "rgba(59,130,246,0.12)",                     // blue bg
                "任务进度",
                todoCountLabel = createCountBadge("0/0", "rgba(59,130,246,0.1)", "#2563eb"),
                todoArrowLabel = new Label("\u25B6")
        );
        todoRow.setCursor(Cursor.HAND);
        todoRow.setOnMouseClicked(e -> openTodoPopup());
        todoRow.setOnMouseEntered(e -> todoRow.setStyle("-fx-background-color: rgba(0,0,0,0.02);"));
        todoRow.setOnMouseExited(e -> todoRow.setStyle("-fx-background-color: transparent;"));

        getChildren().addAll(fileRow, sep, todoRow);
    }

    // ===== 工厂方法 =====

    private HBox createRow(String emoji, String iconBgColor, String title, Label countBadge, Label arrow) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(11, 14, 11, 14));

        // 图标圆
        Label iconCircle = new Label(emoji);
        iconCircle.setStyle("-fx-background-color: " + iconBgColor + ";"
                + " -fx-background-radius: 50%; -fx-pref-width: 22px; -fx-pref-height: 22px;"
                + " -fx-alignment: center; -fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.6);");
        iconCircle.setMinSize(22, 22);

        // 标题
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: rgba(0,0,0,0.8);");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // 箭头
        arrow.setStyle("-fx-font-size: 9px; -fx-text-fill: rgba(0,0,0,0.3);");
        arrow.setMinWidth(12);

        row.getChildren().addAll(iconCircle, titleLabel, countBadge, arrow);
        return row;
    }

    private static Label createCountBadge(String text, String bg, String textColor) {
        Label badge = new Label(text);
        badge.setStyle("-fx-background-color: " + bg + ";"
                + " -fx-background-radius: 10px; -fx-padding: 1px 8px;"
                + " -fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: " + textColor + ";");
        return badge;
    }

    // ===== 文件 API（向后兼容） =====

    public void addModifiedFile(Path filePath, FileBackupManager.BackupEntry entry) {
        fileMap.computeIfAbsent(filePath, k -> new ArrayList<>()).add(entry);
        if (!fileIndex.contains(filePath)) {
            fileIndex.add(filePath);
        }
        if (this.backupManager == null && entry != null) {
            // backupManager 通过 setBackupManager 注入，这里做个保护
        }
        refreshFileCount();
    }

    public void setBackupManager(FileBackupManager manager) {
        this.backupManager = manager;
    }

    public FileBackupManager getBackupManager() {
        return backupManager;
    }

    public void clearFiles() {
        fileMap.clear();
        fileIndex.clear();
        refreshFileCount();
    }

    /** 清除所有文件变更记录并删除持久化备份（仅手动触发） */
    public void clearFilesAndBackups() {
        clearFiles();
        if (backupManager != null) {
            backupManager.clearAll();
        }
    }

    /** 从 FileBackupManager 重新加载所有文件变更（恢复历史会话时调用） */
    public void loadFromBackupManager() {
        if (backupManager == null) return;
        fileMap.clear();
        fileIndex.clear();
        for (Path fp : backupManager.getAllModifiedFiles()) {
            List<FileBackupManager.BackupEntry> versions = backupManager.getVersions(fp);
            if (!versions.isEmpty()) {
                fileMap.put(fp, new ArrayList<>(versions));
                if (!fileIndex.contains(fp)) {
                    fileIndex.add(fp);
                }
            }
        }
        refreshFileCount();
    }

    // ===== Todo API =====

    public void updateTodoFromJson(String json) {
        if (json == null || json.isBlank()) return;
        try {
            Gson gson = new Gson();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = gson.fromJson(json, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newTodos = (List<Map<String, Object>>) root.get("newTodos");

            if (newTodos == null) newTodos = List.of();

            todoItems = new ArrayList<>(newTodos);
            todoTotal = newTodos.size();
            todoCompleted = (int) newTodos.stream()
                    .filter(t -> "completed".equals(t.get("status")))
                    .count();

            Platform.runLater(() -> todoCountLabel.setText(todoCompleted + "/" + todoTotal));
        } catch (Exception e) {
            System.err.println("[FileDiffBadge] Todo 解析失败: " + e.getMessage());
        }
    }

    // ===== 内部 =====

    private void refreshFileCount() {
        int total = (int) fileMap.values().stream().mapToLong(List::size).sum();
        Platform.runLater(() -> fileCountLabel.setText(String.valueOf(total)));
    }

    // ===== 文件弹窗（中间 Stage，防重复） =====

    private void openFilePopup() {
        // 已打开则直接聚焦
        if (currentFilePopup != null && currentFilePopup.isShowing()) {
            currentFilePopup.toFront();
            return;
        }

        Stage popup = new Stage();
        popup.initStyle(StageStyle.TRANSPARENT);
        popup.setTitle("文件变更");

        // 构建 WebView 面板
        WebView wv = new WebView();
        wv.setContextMenuEnabled(true);
        wv.setStyle("-fx-background-color: white;");

        StringBuilder html = new StringBuilder();
        int totalVersions = fileMap.values().stream().mapToInt(List::size).sum();

        html.append("<div class='panel-header'>");
        html.append("<span class='panel-title'>📝 文件变更</span>");
        html.append("<span class='panel-info'>").append(fileMap.size())
             .append(" 个文件 · ").append(totalVersions).append(" 个版本</span>");
        html.append("</div>");

        if (fileMap.isEmpty()) {
            html.append("<div class='empty-hint'>暂无文件变更</div>");
        } else {
            for (int fi = 0; fi < fileIndex.size(); fi++) {
                Path fp = fileIndex.get(fi);
                List<FileBackupManager.BackupEntry> versions = fileMap.get(fp);
                if (versions == null || versions.isEmpty()) continue;
                String fileName = fp.getFileName().toString();

                html.append("<div class='file-header' onclick='toggleVersions(").append(fi).append(")'>");
                html.append("<span class='file-icon'>📄</span>");
                html.append("<span class='file-name'>").append(escapeHtml(fileName)).append("</span>");
                html.append("<span class='file-arrow' id='a-").append(fi).append("'>▶</span>");
                html.append("</div>");

                html.append("<div class='versions' id='v-").append(fi).append("'>");
                for (int vi = 0; vi < versions.size(); vi++) {
                    FileBackupManager.BackupEntry be = versions.get(vi);
                    String ts = formatTimestamp(be.timestamp());
                    html.append("<div class='version-row'>");
                    html.append("<span class='v-badge'>v").append(vi + 1).append("</span>");
                    html.append("<span class='v-time'>").append(ts).append("</span>");
                    html.append("<button class='btn-diff' onclick='onDiff(")
                         .append(fi).append(",").append(vi).append(")'>对比</button>");
                    html.append("<button class='btn-rollback' onclick='onRollback(")
                         .append(fi).append(",").append(vi).append(")'>回滚</button>");
                    html.append("</div>");
                }
                html.append("</div>");
            }

            html.append("<a class='batch-btn' href='javascript:void(0)' onclick='onBatch()'>")
                 .append("↺ 回滚所有文件 (").append(fileMap.size())
                 .append(" 个, ").append(totalVersions).append(" 个版本)")
                 .append("</a>");
            html.append("<a class='batch-btn' href='javascript:void(0)' onclick='onClear()'"
                    + " style='background:rgba(0,0,0,0.04);border-color:rgba(0,0,0,0.1);color:rgba(0,0,0,0.5);'>")
                 .append("🗑 清除全部 (").append(fileMap.size())
                 .append(" 个文件)")
                 .append("</a>");
        }

        String fullHtml = FILE_PANEL_HTML.replace("%s", html.toString());
        wv.getEngine().load(toDataUri(fullHtml));

        // JS 回调
        wv.getEngine().setOnStatusChanged(event -> {
            handleFileJsCallback(event.getData(), popup);
        });

        // 主内容 + 底部关闭栏
        VBox contentBox = new VBox(0);
        contentBox.setStyle("-fx-background-color: white; -fx-background-radius: 12px;"
                + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-radius: 12px;"
                + " -fx-border-width: 1px;");

        VBox.setVgrow(wv, Priority.ALWAYS);
        contentBox.getChildren().add(wv);

        // 关闭按钮
        HBox footerBox = new HBox();
        footerBox.setAlignment(Pos.CENTER_RIGHT);
        footerBox.setPadding(new Insets(8, 14, 10, 14));
        footerBox.setStyle("-fx-border-color: rgba(0,0,0,0.06) transparent transparent transparent; -fx-border-width: 1px 0 0 0;");
        Label closeBtn = new Label("关闭 (ESC)");
        closeBtn.setStyle("-fx-font-size: 11px; -fx-background-color: rgba(0,0,0,0.06);"
                + " -fx-padding: 4px 16px; -fx-background-radius: 6px;"
                + " -fx-text-fill: rgba(0,0,0,0.6); -fx-cursor: hand;");
        closeBtn.setOnMouseClicked(e -> popup.close());
        footerBox.getChildren().add(closeBtn);
        contentBox.getChildren().add(footerBox);

        Scene scene = new Scene(contentBox, POPUP_WIDTH, POPUP_MAX_HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) popup.close(); });
        popup.setScene(scene);

        // 用已知宽度居中（show 前 getWidth() 为 NaN）
        centerOnOwner(popup, POPUP_WIDTH, POPUP_MAX_HEIGHT);
        popup.show();

        // 跟踪
        currentFilePopup = popup;
        popup.setOnHidden(e -> currentFilePopup = null);
    }

    // ===== Todo 弹窗（中间 Stage） =====

    private void openTodoPopup() {
        // 已打开则直接聚焦
        if (currentTodoPopup != null && currentTodoPopup.isShowing()) {
            currentTodoPopup.toFront();
            return;
        }

        Stage popup = new Stage();
        popup.initStyle(StageStyle.TRANSPARENT);
        popup.setTitle("任务进度");

        VBox content = new VBox(0);
        content.setStyle("-fx-background-color: white; -fx-background-radius: 12px;"
                + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-radius: 12px;"
                + " -fx-border-width: 1px;");

        // Header
        int pct = todoTotal > 0 ? (int) Math.round((todoCompleted / (double) todoTotal) * 100) : 0;
        VBox headerBox = new VBox(8);
        headerBox.setPadding(new Insets(12, 14, 8, 14));
        headerBox.setStyle("-fx-border-color: transparent transparent rgba(0,0,0,0.06) transparent; -fx-border-width: 0 0 1 0;");

        Label titleLabel = new Label("📋 任务进度");
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: rgba(0,0,0,0.8);");

        // 进度条
        StackPane progressBar = new StackPane();
        progressBar.setPrefHeight(4);
        progressBar.setMaxHeight(4);

        Rectangle bg = new Rectangle(POPUP_WIDTH - 28, 4);
        bg.setArcWidth(4);
        bg.setArcHeight(4);
        bg.setFill(Color.rgb(0, 0, 0, 0.06));

        Rectangle fg = new Rectangle((POPUP_WIDTH - 28) * pct / 100.0, 4);
        fg.setArcWidth(4);
        fg.setArcHeight(4);
        fg.setFill(Color.rgb(59, 130, 246));

        progressBar.getChildren().addAll(bg, fg);

        Label progressText = new Label(todoCompleted + " / " + todoTotal + " 已完成");
        progressText.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(0,0,0,0.4);");

        headerBox.getChildren().addAll(titleLabel, progressBar, progressText);
        content.getChildren().add(headerBox);

        // 任务列表（可滚动）
        VBox listBox = new VBox(4);
        listBox.setPadding(new Insets(8, 0, 8, 0));

        if (todoItems.isEmpty()) {
            Label emptyLabel = new Label("暂无任务");
            emptyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.3);"
                    + " -fx-padding: 24px 14px; -fx-alignment: center;");
            emptyLabel.setMaxWidth(Double.MAX_VALUE);
            listBox.getChildren().add(emptyLabel);
        } else {
            List<Map<String, Object>> inProgress = todoItems.stream()
                    .filter(t -> "in_progress".equals(t.get("status"))).toList();
            if (!inProgress.isEmpty()) {
                addTodoSection(listBox, "进行中", inProgress, "in_progress");
            }
            List<Map<String, Object>> pending = todoItems.stream()
                    .filter(t -> "pending".equals(t.get("status"))).toList();
            if (!pending.isEmpty()) {
                addTodoSection(listBox, "待处理", pending, "pending");
            }
            List<Map<String, Object>> completed = todoItems.stream()
                    .filter(t -> "completed".equals(t.get("status"))).toList();
            if (!completed.isEmpty()) {
                addTodoSection(listBox, "已完成", completed, "completed");
            }
        }

        ScrollPane scrollPane = new ScrollPane(listBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setMaxHeight(POPUP_MAX_HEIGHT - 140);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        content.getChildren().add(scrollPane);

        // 关闭按钮
        HBox footerBox = new HBox();
        footerBox.setAlignment(Pos.CENTER_RIGHT);
        footerBox.setPadding(new Insets(8, 14, 12, 14));
        Label closeBtn = new Label("关闭 (ESC)");
        closeBtn.setStyle("-fx-font-size: 11px; -fx-background-color: rgba(0,0,0,0.06);"
                + " -fx-padding: 4px 16px; -fx-background-radius: 6px;"
                + " -fx-text-fill: rgba(0,0,0,0.6); -fx-cursor: hand;");
        closeBtn.setOnMouseClicked(e -> popup.close());
        footerBox.getChildren().add(closeBtn);
        content.getChildren().add(footerBox);

        StackPane root = new StackPane(content);
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, POPUP_WIDTH, -1);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) popup.close(); });
        popup.setScene(scene);

        popup.sizeToScene();
        // sizeToScene 之后 scene 的高度已计算完成，直接用
        centerOnOwner(popup, POPUP_WIDTH, scene.getHeight());
        popup.show();

        currentTodoPopup = popup;
        popup.setOnHidden(e -> currentTodoPopup = null);
    }

    private void addTodoSection(VBox parent, String title, List<Map<String, Object>> items, String status) {
        Label header = new Label(title + " (" + items.size() + ")");
        header.setStyle("-fx-font-size: 9px; -fx-font-weight: 600; -fx-text-fill: rgba(0,0,0,0.4);"
                + " -fx-padding: 8px 14px 2px 14px;");
        parent.getChildren().add(header);

        for (Map<String, Object> item : items) {
            String content = (String) item.getOrDefault("content", "");
            String activeForm = (String) item.getOrDefault("activeForm", "");

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(3, 14, 3, 14));

            String icon;
            String color;
            switch (status) {
                case "completed": icon = "\u2713"; color = "#16a34a"; break;
                case "in_progress": icon = "\u25C9"; color = "#3b82f6"; break;
                default: icon = "\u25CB"; color = "rgba(0,0,0,0.3)"; break;
            }

            Label iconLabel = new Label(icon);
            iconLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + color + "; -fx-min-width: 14px;");
            iconLabel.setMinSize(14, 14);

            Label contentLabel = new Label(content);
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(240);
            if ("completed".equals(status)) {
                contentLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.4);");
            } else {
                contentLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.8);");
            }
            HBox.setHgrow(contentLabel, Priority.ALWAYS);

            Label activeLabel = new Label("in_progress".equals(status) && !activeForm.isEmpty() ? activeForm : "");
            activeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: rgba(0,0,0,0.4);");

            row.getChildren().addAll(iconLabel, contentLabel, activeLabel);
            parent.getChildren().add(row);
        }
    }

    // ===== JS 回调处理 =====

    private void handleFileJsCallback(String status, Stage popup) {
        if (status == null) return;
        if (status.equals("batch")) {
            confirmBatchRollback();
            popup.close();
            return;
        }
        if (status.equals("clear")) {
            clearFilesAndBackups();
            popup.close();
            return;
        }
        if (status.startsWith("diff:")) {
            String[] parts = status.substring(5).split(":");
            if (parts.length >= 2) {
                try {
                    int fi = Integer.parseInt(parts[0]);
                    int vi = Integer.parseInt(parts[1]);
                    Path fp = fileIndex.get(fi);
                    List<FileBackupManager.BackupEntry> vers = fileMap.get(fp);
                    if (vers != null && vi < vers.size()) {
                        DiffViewerPopup.show(fp, vers.get(vi), backupManager);
                    }
                } catch (Exception ignored) {}
            }
        }
        if (status.startsWith("rollback:")) {
            String[] parts = status.substring(9).split(":");
            if (parts.length >= 2) {
                try {
                    int fi = Integer.parseInt(parts[0]);
                    int vi = Integer.parseInt(parts[1]);
                    Path fp = fileIndex.get(fi);
                    List<FileBackupManager.BackupEntry> vers = fileMap.get(fp);
                    if (vers != null && vi < vers.size()) {
                        confirmSingleRollback(fp, vers.get(vi));
                        popup.close();
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ===== 回滚逻辑 =====

    private void confirmSingleRollback(Path originalPath, FileBackupManager.BackupEntry be) {
        String ts = formatTimestamp(be.timestamp());
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认回滚");
        alert.setHeaderText("回滚文件: " + originalPath.getFileName());
        alert.setContentText("将文件回滚到版本 " + ts + "？\n此操作将覆盖当前文件内容。");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK && backupManager != null) {
                boolean ok = backupManager.restore(originalPath, be);
                if (ok) {
                    List<FileBackupManager.BackupEntry> versions = fileMap.get(originalPath);
                    if (versions != null) {
                        versions.remove(be);
                        if (versions.isEmpty()) {
                            fileMap.remove(originalPath);
                            fileIndex.remove(originalPath);
                        }
                    }
                    refreshFileCount();
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
                for (var entry : fileMap.entrySet()) {
                    List<FileBackupManager.BackupEntry> versions = entry.getValue();
                    if (!versions.isEmpty()) {
                        backupManager.restore(entry.getKey(), versions.get(0));
                    }
                }
                fileMap.clear();
                fileIndex.clear();
                refreshFileCount();
            }
        });
    }

    // ===== 工具方法 =====

    private void centerOnOwner(Stage popup) {
        if (getScene() != null && getScene().getWindow() != null) {
            centerOnOwner(popup, popup.getWidth(), popup.getHeight());
        }
    }

    /** 用已知尺寸居中（show 前 popup.getWidth() 为 NaN） */
    private void centerOnOwner(Stage popup, double knownW, double knownH) {
        if (getScene() != null && getScene().getWindow() != null) {
            double ownerX = getScene().getWindow().getX();
            double ownerY = getScene().getWindow().getY();
            double ownerW = getScene().getWindow().getWidth();
            double ownerH = getScene().getWindow().getHeight();
            double w = knownW > 0 ? knownW : popup.getWidth();
            double h = knownH > 0 ? knownH : popup.getHeight();
            popup.setX(ownerX + (ownerW - w) / 2);
            popup.setY(ownerY + (ownerH - h) / 2);
        }
    }

    private static String formatTimestamp(String ts) {
        if (ts == null || ts.isEmpty()) return "";
        String s = ts.replace("_", " ");
        return s.length() > 19 ? s.substring(0, 19) : s;
    }

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
        return "data:text/html;charset=UTF-8;base64," + java.util.Base64.getEncoder().encodeToString(bytes);
    }
}
