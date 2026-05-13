package gui.ui.components;

import agent.tool.file.FileBackupManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * IDEA 分屏风格差异查看器弹窗 — 全 WebView 渲染。
 * <p>
 * 左右分屏对比，白色主题，支持文本选择与复制。
 */
public class DiffViewerPopup {

    private static final double DEFAULT_WIDTH = 1000;
    private static final double DEFAULT_HEIGHT = 680;

    private static final String HTML_TEMPLATE =
        "<!DOCTYPE html><html style='height:100%;'>"
        + "<head><meta charset='UTF-8'><style>"
        + "*{margin:0;padding:0;box-sizing:border-box;}"
        + "body{font-family:'JetBrains Mono','Fira Code',monospace;font-size:12px;"
        + "background:#fff;color:rgba(0,0,0,0.7);display:flex;flex-direction:column;height:100%;}"
        + ".toolbar{display:flex;align-items:center;padding:8px 16px;background:#f8f9fa;"
        + "border-bottom:1px solid rgba(0,0,0,0.08);flex-shrink:0;}"
        + ".toolbar .file{font-size:13px;font-weight:500;margin-right:12px;}"
        + ".toolbar .ts{font-size:11px;color:rgba(0,0,0,0.4);margin-right:auto;}"
        + ".toolbar button{border:none;background:rgba(0,0,0,0.06);border-radius:4px;"
        + "padding:2px 10px;font-size:11px;cursor:pointer;margin:0 2px;color:rgba(0,0,0,0.6);}"
        + ".toolbar button.rollback{background:#ef4444;color:white;font-weight:500;padding:3px 12px;}"
        + ".toolbar .nav{font-size:11px;color:rgba(0,0,0,0.4);padding:0 4px;}"
        + ".toolbar .sep{width:1px;height:20px;background:rgba(0,0,0,0.08);margin:0 4px;}"
        + ".split{display:flex;flex:1;overflow:hidden;}"
        + ".side{flex:1;display:flex;flex-direction:column;overflow:hidden;}"
        + ".side:first-child{border-right:2px solid rgba(0,0,0,0.06);}"
        + ".side-header{display:flex;justify-content:space-between;padding:4px 12px;"
        + "background:#f8f9fa;border-bottom:1px solid rgba(0,0,0,0.06);font-size:11px;"
        + "color:rgba(0,0,0,0.5);flex-shrink:0;}"
        + ".code{flex:1;overflow-y:auto;overflow-x:auto;}"
        + ".line{display:flex;height:20px;line-height:20px;}"
        + ".line .ln{width:36px;text-align:right;padding-right:8px;"
        + "color:rgba(0,0,0,0.25);font-size:11px;user-select:none;flex-shrink:0;}"
        + ".line .txt{padding-left:4px;white-space:pre;overflow:hidden;flex:1;}"
        + ".line.removed{background:#fff0f0;}"
        + ".line.removed .ln{background:#fff0f0;}"
        + ".line.added{background:#f0fff0;}"
        + ".line.added .ln{background:#f0fff0;}"
        + ".line.empty{background:rgba(0,0,0,0.02);}"
        + ".statusbar{display:flex;justify-content:space-between;padding:4px 16px;"
        + "background:#f8f9fa;border-top:1px solid rgba(0,0,0,0.06);"
        + "font-size:11px;color:rgba(0,0,0,0.4);flex-shrink:0;}"
        + ".statusbar .del{color:#dc2626;}"
        + ".statusbar .add{color:#16a34a;}"
        + "</style></head><body>%s</body></html>";

    private enum DiffType { UNCHANGED, ADDED, REMOVED }
    private record DiffLinePair(String oldLine, String newLine, DiffType type,
                                 int oldLineNum, int newLineNum) {}

    private DiffViewerPopup() {}

    public static void show(Path originalPath, FileBackupManager.BackupEntry entry,
                            FileBackupManager backupManager) {
        String oldContent;
        String newContent;
        try {
            oldContent = Files.readString(entry.backupFilePath(), StandardCharsets.UTF_8);
            newContent = Files.exists(originalPath)
                    ? Files.readString(originalPath, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return;
        }

        List<String> oldLines = List.of(oldContent.split("\n", -1));
        List<String> newLines = List.of(newContent.split("\n", -1));
        List<DiffLinePair> diffLines = computeDiff(oldLines, newLines);

        String fileName = originalPath.getFileName().toString();
        String ts = formatTimestamp(entry.timestamp());

        long removed = diffLines.stream().filter(d -> d.type() == DiffType.REMOVED).count();
        long added = diffLines.stream().filter(d -> d.type() == DiffType.ADDED).count();

        StringBuilder html = new StringBuilder();

        // Toolbar
        html.append("<div class='toolbar'>");
        html.append("<span class='file'>📄 ").append(esc(fileName)).append("</span>");
        html.append("<span class='ts'>← v1 · ").append(ts).append("</span>");
        html.append("<button onclick='prevHunk()'>◀</button>");
        html.append("<span class='nav' id='hunk-nav'>1 / ").append(countHunks(diffLines)).append("</span>");
        html.append("<button onclick='nextHunk()'>▶</button>");
        html.append("<span class='sep'></span>");
        html.append("<button class='rollback' onclick='window.status=\"rollback\"'>↺ 回滚</button>");
        html.append("<button onclick='window.status=\"close\"' style='font-size:14px;padding:0 4px;'>✕</button>");
        html.append("</div>");

        // Split
        html.append("<div class='split'>");

        // Left side (original)
        html.append("<div class='side'>");
        html.append("<div class='side-header'><span>原始版本</span><span>v1 · ").append(ts).append("</span></div>");
        html.append("<div class='code' id='left-code'>");
        for (int i = 0; i < diffLines.size(); i++) {
            DiffLinePair d = diffLines.get(i);
            int ln = d.oldLineNum();
            String text = esc(d.oldLine());
            boolean isEmpty = d.type() == DiffType.ADDED;
            String cls = isEmpty ? "empty" : (d.type() == DiffType.REMOVED ? "removed" : "");
            html.append(renderLine(ln, isEmpty ? "" : text, cls, i));
        }
        html.append("</div></div>");

        // Right side (current)
        html.append("<div class='side'>");
        html.append("<div class='side-header'><span>当前版本</span><span>已修改</span></div>");
        html.append("<div class='code' id='right-code'>");
        for (int i = 0; i < diffLines.size(); i++) {
            DiffLinePair d = diffLines.get(i);
            int ln = d.newLineNum();
            String text = esc(d.newLine());
            boolean isEmpty = d.type() == DiffType.REMOVED;
            String cls = isEmpty ? "empty" : (d.type() == DiffType.ADDED ? "added" : "");
            html.append(renderLine(ln, isEmpty ? "" : text, cls, i));
        }
        html.append("</div></div>");

        html.append("</div>");

        // Status bar
        html.append("<div class='statusbar'>");
        html.append("<span><span class='del'>").append(removed).append(" 处删除</span>  <span class='add'>")
             .append(added).append(" 处新增</span></span>");
        html.append("<span>分屏对比</span>");
        html.append("</div>");

        // Hunk navigation script
        html.append("<script>");
        html.append("var hunks=[");
        boolean inHunk = false;
        List<Integer> hunkStarts = new ArrayList<>();
        for (int i = 0; i < diffLines.size(); i++) {
            DiffLinePair d = diffLines.get(i);
            if (d.type() != DiffType.UNCHANGED) {
                if (!inHunk) {
                    hunkStarts.add(i);
                    inHunk = true;
                }
            } else {
                inHunk = false;
            }
        }
        for (int hi = 0; hi < hunkStarts.size(); hi++) {
            if (hi > 0) html.append(",");
            html.append(hunkStarts.get(hi));
        }
        html.append("];var currentHunk=0;var totalHunks=hunks.length||1;");
        html.append("function scrollToHunk(idx){if(hunks.length===0)return;");
        html.append("var line=hunks[Math.min(idx,hunks.length-1)];");
        html.append("var left=document.getElementById('left-code');");
        html.append("var right=document.getElementById('right-code');");
        html.append("var target=left.children[line];if(target){target.scrollIntoView({block:'center'});}");
        html.append("if(right.children[line]){right.children[line].scrollIntoView({block:'center'});}");
        html.append("document.getElementById('hunk-nav').textContent=(idx+1)+' / '+totalHunks;}");
        html.append("function prevHunk(){if(currentHunk>0)currentHunk--;scrollToHunk(currentHunk);}");
        html.append("function nextHunk(){if(currentHunk<totalHunks-1)currentHunk++;scrollToHunk(currentHunk);}");
        // 同步滚动：左右分屏同时滚动
        html.append("var syncing=false;");
        html.append("document.getElementById('left-code').addEventListener('scroll',function(){");
        html.append("if(!syncing){syncing=true;document.getElementById('right-code').scrollTop=this.scrollTop;syncing=false;}});");
        html.append("document.getElementById('right-code').addEventListener('scroll',function(){");
        html.append("if(!syncing){syncing=true;document.getElementById('left-code').scrollTop=this.scrollTop;syncing=false;}});");
        html.append("</script>");

        String fullHtml = HTML_TEMPLATE.replace("%s", html.toString());

        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);

        WebView wv = new WebView();
        wv.setContextMenuEnabled(true);
        wv.setStyle("-fx-background-color: white;");
        wv.getEngine().setOnStatusChanged(event -> {
            String s = event.getData();
            if ("close".equals(s)) {
                stage.close();
            } else if ("rollback".equals(s) && backupManager != null) {
                backupManager.restore(originalPath, entry);
                stage.close();
            }
        });
        wv.getEngine().load(toDataUri(fullHtml));

        StackPane root = new StackPane(wv);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 12px;"
                + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-radius: 12px;"
                + " -fx-border-width: 1px;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 20, 0, 0, 8);");

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) stage.close(); });
        stage.setScene(scene);

        if (stage.getOwner() != null) {
            stage.setX(stage.getOwner().getX() + (stage.getOwner().getWidth() - DEFAULT_WIDTH) / 2);
            stage.setY(stage.getOwner().getY() + (stage.getOwner().getHeight() - DEFAULT_HEIGHT) / 2);
        } else {
            // 无人所有者时用屏幕居中
            double sw = javafx.stage.Screen.getPrimary().getVisualBounds().getWidth();
            double sh = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight();
            stage.setX((sw - DEFAULT_WIDTH) / 2);
            stage.setY((sh - DEFAULT_HEIGHT) / 2);
        }
        stage.show();
    }

    // ===== Diff Algorithm (LCS) =====

    private static List<DiffLinePair> computeDiff(List<String> oldLines, List<String> newLines) {
        int m = oldLines.size(), n = newLines.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++)
                if (oldLines.get(i - 1).equals(newLines.get(j - 1)))
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                else
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);

        List<DiffLinePair> reversed = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines.get(i - 1).equals(newLines.get(j - 1))) {
                reversed.add(new DiffLinePair(oldLines.get(i - 1), newLines.get(j - 1),
                        DiffType.UNCHANGED, i, j));
                i--; j--;
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
        for (int k = reversed.size() - 1; k >= 0; k--) result.add(reversed.get(k));
        return result;
    }

    private static int countHunks(List<DiffLinePair> lines) {
        int hunks = 0; boolean inHunk = false;
        for (DiffLinePair d : lines) {
            if (d.type() != DiffType.UNCHANGED) { if (!inHunk) { hunks++; inHunk = true; } }
            else { inHunk = false; }
        }
        return Math.max(hunks, 1);
    }

    // ===== Helpers =====

    private static String renderLine(int ln, String text, String cls, int idx) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='line ").append(cls).append("' id='line-").append(idx).append("'>");
        sb.append("<span class='ln'>").append(ln > 0 ? ln : "").append("</span>");
        sb.append("<span class='txt'>").append(text).append("</span>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String formatTimestamp(String ts) {
        if (ts == null || ts.isEmpty()) return "";
        String s = ts.replace("_", " ");
        return s.length() > 19 ? s.substring(0, 19) : s;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String toDataUri(String html) {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        return "data:text/html;charset=UTF-8;base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
