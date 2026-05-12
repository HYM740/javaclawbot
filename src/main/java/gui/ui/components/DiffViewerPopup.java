package gui.ui.components;

import agent.tool.file.FileBackupManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * IDEA 分屏风格差异查看器弹窗。
 * <p>
 * 左右分屏对比，白色主题。
 * - 左屏：原始版本（红色背景标记删除行）
 * - 右屏：当前版本（绿色背景标记新增行）
 * - 顶部工具栏：文件信息 + 差异导航 + 回滚按钮
 * - 无边框弹窗，半透明遮罩
 */
public class DiffViewerPopup {

    private static final double DEFAULT_WIDTH = 700;
    private static final double DEFAULT_HEIGHT = 380;

    /** 差异行类型 */
    private enum DiffType { UNCHANGED, ADDED, REMOVED }

    /** 左右屏配对行 */
    private record DiffLinePair(String oldLine, String newLine, DiffType type,
                                 int oldLineNum, int newLineNum) {}

    private DiffViewerPopup() {}

    /**
     * 显示差异对比弹窗。
     *
     * @param originalPath  被修改的文件路径
     * @param entry         备份版本条目
     * @param backupManager 备份管理器（用于回滚操作）
     */
    public static void show(Path originalPath, FileBackupManager.BackupEntry entry,
                            FileBackupManager backupManager) {
        // 读取内容
        String oldContent;
        String newContent;
        try {
            oldContent = Files.readString(entry.backupFilePath(), StandardCharsets.UTF_8);
            newContent = Files.exists(originalPath)
                    ? Files.readString(originalPath, StandardCharsets.UTF_8)
                    : "";
        } catch (IOException e) {
            return;
        }

        List<String> oldLines = List.of(oldContent.split("\n", -1));
        List<String> newLines = List.of(newContent.split("\n", -1));

        // 计算差异
        List<DiffLinePair> diffLines = computeDiff(oldLines, newLines);

        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 12px;"
                + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-radius: 12px;"
                + " -fx-border-width: 1px;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 20, 0, 0, 8);");

        // 构建 UI
        buildUI(root, originalPath, entry, diffLines, backupManager, stage);

        // 统计变更
        long removed = diffLines.stream().filter(d -> d.type() == DiffType.REMOVED).count();
        long added = diffLines.stream().filter(d -> d.type() == DiffType.ADDED).count();

        // 底部状态栏
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(4, 16, 4, 16));
        statusBar.setStyle("-fx-background-color: #f8f9fa;"
                + " -fx-background-radius: 0 0 12px 12px;"
                + " -fx-border-color: rgba(0,0,0,0.06) transparent transparent transparent;"
                + " -fx-border-width: 1 0 0 0;");

        String stats = String.format("\u25CF <span style='color:#dc2626;'>%d 处删除</span>"
                + "  \u25CF <span style='color:#16a34a;'>%d 处新增</span>", removed, added);
        Label statsLabel = new Label(removed + " 处删除  |  " + added + " 处新增");
        statsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.4);");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        Label modeLabel = new Label("分屏对比");
        modeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.2);");

        statusBar.getChildren().addAll(statsLabel, spacer2, modeLabel);
        root.getChildren().add(statusBar);

        // Scene
        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setScene(scene);

        // 居中
        if (stage.getOwner() != null) {
            stage.setX(stage.getOwner().getX()
                    + (stage.getOwner().getWidth() - DEFAULT_WIDTH) / 2);
            stage.setY(stage.getOwner().getY()
                    + (stage.getOwner().getHeight() - DEFAULT_HEIGHT) / 2);
        }

        stage.show();
    }

    /** 构建弹窗主体 UI */
    private static void buildUI(VBox root, Path originalPath,
                                 FileBackupManager.BackupEntry entry,
                                 List<DiffLinePair> diffLines,
                                 FileBackupManager backupManager,
                                 Stage stage) {
        // ---- 顶部工具栏 ----
        HBox toolbar = new HBox();
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 16, 8, 16));
        toolbar.setStyle("-fx-background-color: #f8f9fa;"
                + " -fx-background-radius: 12px 12px 0 0;"
                + " -fx-border-color: transparent transparent rgba(0,0,0,0.08) transparent;"
                + " -fx-border-width: 0 0 1 0;");

        Label fileLabel = new Label("\uD83D\uDCC4 " + originalPath.getFileName());
        fileLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: rgba(0,0,0,0.8);");

        String displayTs = entry.timestamp() != null && !entry.timestamp().isEmpty()
                ? entry.timestamp().replace("_", " ") : "";
        if (displayTs.length() > 19) displayTs = displayTs.substring(0, 19);
        Label tsLabel = new Label("\u2190 v1 \u00B7 " + displayTs);
        tsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.4);");
        tsLabel.setPadding(new Insets(0, 0, 0, 12));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 差异导航
        Label prevBtn = new Label("\u25C0"); // ◀
        prevBtn.setStyle("-fx-background-color: rgba(0,0,0,0.06); -fx-background-radius: 4px;"
                + " -fx-padding: 2px 10px; -fx-font-size: 11px;"
                + " -fx-text-fill: rgba(0,0,0,0.6); -fx-cursor: hand;");

        Label navLabel = new Label("1 / " + countHunks(diffLines));
        navLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.4);");
        navLabel.setPadding(new Insets(0, 4, 0, 4));

        Label nextBtn = new Label("\u25B6"); // ▶
        nextBtn.setStyle("-fx-background-color: rgba(0,0,0,0.06); -fx-background-radius: 4px;"
                + " -fx-padding: 2px 10px; -fx-font-size: 11px;"
                + " -fx-text-fill: rgba(0,0,0,0.6); -fx-cursor: hand;");

        // 分隔线
        Label sep1 = new Label("|");
        sep1.setStyle("-fx-text-fill: rgba(0,0,0,0.08); -fx-font-size: 14px; -fx-padding: 0 4px;");

        // 回滚按钮
        Label rollbackBtn = new Label("\u21BA 回滚");
        rollbackBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;"
                + " -fx-background-radius: 4px; -fx-padding: 3px 12px; -fx-font-size: 11px;"
                + " -fx-cursor: hand; -fx-font-weight: 500;");

        // 关闭按钮
        Label closeBtn = new Label("\u2715"); // ✕
        closeBtn.setStyle("-fx-font-size: 14px; -fx-text-fill: rgba(0,0,0,0.3);"
                + " -fx-cursor: hand; -fx-padding: 0 4px;");

        toolbar.getChildren().addAll(fileLabel, tsLabel, spacer,
                prevBtn, navLabel, nextBtn, sep1, rollbackBtn, closeBtn);
        root.getChildren().add(toolbar);

        // ---- 左右分屏 ----
        HBox splitPane = new HBox();
        splitPane.setStyle("-fx-background-color: white;");

        // 左屏（原始版本）
        VBox leftPane = createSidePanel("原始版本", "v1 \u00B7 " + displayTs, diffLines, true);
        // 右屏（当前版本）
        VBox rightPane = createSidePanel("当前版本", "已修改", diffLines, false);

        splitPane.getChildren().addAll(leftPane, rightPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        root.getChildren().add(splitPane);

        // ---- 事件绑定 ----
        closeBtn.setOnMouseClicked(e -> stage.close());
        rollbackBtn.setOnMouseClicked(e -> {
            if (backupManager != null) {
                backupManager.restore(originalPath, entry);
                stage.close();
            }
        });
    }

    /** 创建一侧面板（左屏或右屏） */
    private static VBox createSidePanel(String title, String subtitle,
                                         List<DiffLinePair> diffLines, boolean isLeft) {
        VBox panel = new VBox(0);
        HBox.setHgrow(panel, Priority.ALWAYS);

        // 侧边边框：左屏右边界，右屏无边框
        String borderStyle = isLeft
                ? "-fx-border-color: transparent rgba(0,0,0,0.06) transparent transparent;"
                + " -fx-border-width: 0 2px 0 0;"
                : "";

        // 头部
        HBox header = new HBox();
        header.setPadding(new Insets(4, 12, 4, 12));
        header.setStyle("-fx-background-color: #f8f9fa;"
                + " -fx-border-color: transparent transparent rgba(0,0,0,0.06) transparent;"
                + " -fx-border-width: 0 0 1 0;" + borderStyle);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.5);");

        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);

        Label subLabel = new Label(subtitle);
        subLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.4);");

        header.getChildren().addAll(titleLabel, hSpacer, subLabel);
        panel.getChildren().add(header);

        // 代码区域
        VBox codeBox = new VBox(0);
        codeBox.setStyle("-fx-background-color: white; -fx-font-family: 'JetBrains Mono', 'Fira Code', monospace;"
                + " -fx-font-size: 12px;" + borderStyle);

        int lineHeight = 20;
        for (int i = 0; i < diffLines.size(); i++) {
            DiffLinePair line = diffLines.get(i);
            int lineNum = isLeft ? line.oldLineNum() : line.newLineNum();
            String text = isLeft ? line.oldLine() : line.newLine();
            boolean isEmpty = (isLeft && line.type() == DiffType.ADDED)
                    || (!isLeft && line.type() == DiffType.REMOVED);

            String bg;
            if (isEmpty) {
                bg = "rgba(0,0,0,0.02)"; // 空占位行
            } else if (line.type() == DiffType.REMOVED) {
                bg = isLeft ? "#fff0f0" : "rgba(0,0,0,0.02)"; // 删除行在左屏高亮
            } else if (line.type() == DiffType.ADDED) {
                bg = isLeft ? "rgba(0,0,0,0.02)" : "#f0fff0"; // 新增行在右屏高亮
            } else {
                bg = "white";
            }

            HBox lineRow = new HBox(0);
            lineRow.setStyle("-fx-background-color: " + bg + ";");

            // 行号
            Label numLabel = new Label(lineNum > 0 ? String.valueOf(lineNum) : "");
            numLabel.setStyle("-fx-background-color: " + bg + ";"
                    + " -fx-min-width: 32px; -fx-max-width: 32px;"
                    + " -fx-alignment: center-right;"
                    + " -fx-padding: 0 8px 0 0;"
                    + " -fx-text-fill: rgba(0,0,0,0.25);"
                    + " -fx-font-size: 11px;");
            numLabel.setMinWidth(32);
            numLabel.setPrefHeight(lineHeight);

            // 代码内容
            Label codeLabel = new Label(isEmpty ? "" : text);
            codeLabel.setStyle("-fx-background-color: " + bg + ";"
                    + " -fx-padding: 0 8px 0 4px;"
                    + " -fx-text-fill: rgba(0,0,0,0.7);"
                    + " -fx-font-size: 12px;");
            codeLabel.setPrefHeight(lineHeight);
            HBox.setHgrow(codeLabel, Priority.ALWAYS);

            lineRow.getChildren().addAll(numLabel, codeLabel);
            codeBox.getChildren().add(lineRow);
        }

        ScrollPane scrollPane = new ScrollPane(codeBox);
        scrollPane.setStyle("-fx-background-color: white; -fx-border-color: transparent;");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        panel.getChildren().add(scrollPane);
        return panel;
    }

    // ===== Diff 算法 =====

    /**
     * LCS 差异算法，返回左右分屏对齐的行列表。
     */
    private static List<DiffLinePair> computeDiff(List<String> oldLines, List<String> newLines) {
        int m = oldLines.size();
        int n = newLines.size();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines.get(i - 1).equals(newLines.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // 回溯
        List<DiffLinePair> reversed = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines.get(i - 1).equals(newLines.get(j - 1))) {
                reversed.add(new DiffLinePair(oldLines.get(i - 1), newLines.get(j - 1),
                        DiffType.UNCHANGED, i, j));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                reversed.add(new DiffLinePair("", newLines.get(j - 1),
                        DiffType.ADDED, 0, j));
                j--;
            } else {
                reversed.add(new DiffLinePair(oldLines.get(i - 1), "",
                        DiffType.REMOVED, i, 0));
                i--;
            }
        }

        List<DiffLinePair> result = new ArrayList<>(reversed.size());
        for (int k = reversed.size() - 1; k >= 0; k--) {
            result.add(reversed.get(k));
        }
        return result;
    }

    /** 统计差异块数量（用于导航） */
    private static int countHunks(List<DiffLinePair> lines) {
        int hunks = 0;
        boolean inHunk = false;
        for (DiffLinePair line : lines) {
            if (line.type() != DiffType.UNCHANGED) {
                if (!inHunk) {
                    hunks++;
                    inHunk = true;
                }
            } else {
                inHunk = false;
            }
        }
        return Math.max(hunks, 1);
    }
}
