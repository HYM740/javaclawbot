package gui.ui.components;

import agent.tool.file.FileBackupManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;

/**
 * 文件差异回滚浮标。
 * <p>
 * Badge 头部使用 JavaFX（保持轻量交互）。
 * 下拉面板内容使用 WebView 渲染，支持文本选择与复制。
 */
public class FileDiffBadge extends VBox {

    private static final String PANEL_HTML_TEMPLATE = "<!DOCTYPE html><html style='height:100%;background:white;'>"
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
        + "</style></head><body>%s<script>"
        + "function toggleVersions(id){var el=document.getElementById('v-'+id);"
        + "var arrow=document.getElementById('a-'+id);"
        + "if(el.classList.contains('open')){el.classList.remove('open');arrow.textContent='▶';}"
        + "else{el.classList.add('open');arrow.textContent='▼';}}"
        + "function onDiff(fileIdx,verIdx){window.status='diff:'+fileIdx+':'+verIdx;}"
        + "function onRollback(fileIdx,verIdx){window.status='rollback:'+fileIdx+':'+verIdx;}"
        + "function onBatch(){window.status='batch';}"
        + "</script></body></html>";

    private final Label countLabel;
    private final Label arrowLabel;
    private boolean dropdownVisible = false;
    private WebView dropdownWebView;

    /** 文件路径 → 有序备份版本 */
    private final Map<Path, List<FileBackupManager.BackupEntry>> fileMap = new LinkedHashMap<>();
    /** 已展平的文件列表（对应 HTML 中的索引） */
    private final List<Path> fileIndex = new ArrayList<>();
    private FileBackupManager backupManager;

    public FileDiffBadge() {
        setVisible(false);
        setManaged(false);

        setStyle("-fx-background-color: white; -fx-background-radius: 16px 16px 0 0;"
                + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-width: 1px 1px 0 1px;"
                + " -fx-border-radius: 16px 16px 0 0;");
        setMinWidth(180);
        setMaxWidth(180);
        setCursor(Cursor.HAND);

        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(8, 14, 8, 14));

        Label iconCircle = new Label("\uD83D\uDCDD");
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

        arrowLabel = new Label("\u25B6");
        arrowLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(0,0,0,0.3);");

        headerRow.getChildren().addAll(iconCircle, titleLabel, countLabel, arrowLabel);
        getChildren().add(headerRow);

        setOnMouseClicked(e -> toggleDropdown());
    }

    // ===== Public API =====

    public void addModifiedFile(Path filePath, FileBackupManager.BackupEntry entry) {
        fileMap.computeIfAbsent(filePath, k -> new ArrayList<>()).add(entry);
        if (!fileIndex.contains(filePath)) {
            fileIndex.add(filePath);
        }
        int total = (int) fileMap.values().stream().mapToLong(List::size).sum();
        countLabel.setText(String.valueOf(total));
        setVisible(true);
        setManaged(true);
    }

    public void setBackupManager(FileBackupManager manager) {
        this.backupManager = manager;
    }

    public void clearFiles() {
        fileMap.clear();
        fileIndex.clear();
        countLabel.setText("0");
        setVisible(false);
        setManaged(false);
        hideDropdown();
    }

    // ===== Dropdown Logic =====

    private void toggleDropdown() {
        if (dropdownVisible) {
            hideDropdown();
        } else {
            showDropdown();
        }
    }

    private void showDropdown() {
        dropdownVisible = true;
        arrowLabel.setText("\u25BC");

        if (dropdownWebView == null) {
            dropdownWebView = new WebView();
            dropdownWebView.setContextMenuEnabled(false);
            dropdownWebView.setStyle("-fx-background-color: white;");
            dropdownWebView.setMinWidth(300);
            dropdownWebView.setMaxWidth(300);
            dropdownWebView.setPrefHeight(300);
            dropdownWebView.setMaxHeight(400);

            // Listen for status changes (JS callbacks)
            dropdownWebView.getEngine().setOnStatusChanged(event -> {
                String status = event.getData();
                handleJsCallback(status);
            });

            getChildren().add(dropdownWebView);
        }

        refreshDropdownHtml();
        dropdownWebView.setVisible(true);
        dropdownWebView.setManaged(true);
    }

    private void hideDropdown() {
        dropdownVisible = false;
        arrowLabel.setText("\u25B6");
        if (dropdownWebView != null) {
            dropdownWebView.setVisible(false);
            dropdownWebView.setManaged(false);
        }
    }

    private void refreshDropdownHtml() {
        StringBuilder html = new StringBuilder();

        int totalVersions = fileMap.values().stream().mapToInt(List::size).sum();

        // Panel header
        html.append("<div class='panel-header'>");
        html.append("<span class='panel-title'>📝 文件变更</span>");
        html.append("<span class='panel-info'>").append(fileMap.size())
             .append(" 个文件 · ").append(totalVersions).append(" 个版本</span>");
        html.append("</div>");

        if (fileMap.isEmpty()) {
            html.append("<div style='padding:16px;text-align:center;color:rgba(0,0,0,0.3);'>暂无文件变更</div>");
        } else {
            for (int fi = 0; fi < fileIndex.size(); fi++) {
                Path filePath = fileIndex.get(fi);
                List<FileBackupManager.BackupEntry> versions = fileMap.get(filePath);
                if (versions == null) continue;
                String fileName = filePath.getFileName().toString();

                html.append("<div class='file-header' onclick='toggleVersions(").append(fi).append(")'>");
                html.append("<span class='file-icon'>📄</span>");
                html.append("<span class='file-name'>").append(escapeHtmlAttr(fileName)).append("</span>");
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
        }

        String fullHtml = String.format(PANEL_HTML_TEMPLATE, html.toString());
        dropdownWebView.getEngine().load(toDataUri(fullHtml));
    }

    // ===== JS Callback Handler =====

    private void handleJsCallback(String status) {
        if (status == null) return;
        if (status.equals("batch")) {
            confirmBatchRollback();
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
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ===== Rollback Logic =====

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
                    updateCount();
                    if (fileMap.isEmpty()) {
                        setVisible(false);
                        setManaged(false);
                    }
                    if (dropdownVisible) {
                        refreshDropdownHtml();
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
                for (var entry : fileMap.entrySet()) {
                    List<FileBackupManager.BackupEntry> versions = entry.getValue();
                    if (!versions.isEmpty()) {
                        backupManager.restore(entry.getKey(), versions.get(0));
                    }
                }
                fileMap.clear();
                fileIndex.clear();
                updateCount();
                setVisible(false);
                setManaged(false);
                hideDropdown();
            }
        });
    }

    // ===== Helpers =====

    private void updateCount() {
        int total = (int) fileMap.values().stream().mapToLong(List::size).sum();
        countLabel.setText(String.valueOf(total));
    }

    private static String formatTimestamp(String ts) {
        if (ts == null || ts.isEmpty()) return "";
        String s = ts.replace("_", " ");
        return s.length() > 19 ? s.substring(0, 19) : s;
    }

    private static String escapeHtmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String toDataUri(String html) {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
        return "data:text/html;charset=UTF-8;base64," + b64;
    }
}
