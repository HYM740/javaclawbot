package gui.ui.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SkillsPage extends VBox {

    private javafx.scene.layout.GridPane skillGrid;
    private gui.ui.BackendBridge backendBridge;

    private static String toggleStyle(boolean enabled) {
        String bgColor = enabled ? "#10b981" : "#e5e7eb";
        return "-fx-background-color: " + bgColor + "; -fx-background-radius: 8px; "
            + "-fx-pref-width: 32px; -fx-pref-height: 16px; -fx-cursor: hand;";
    }

    private static String toggleKnobStyle(boolean enabled) {
        String bgColor = enabled ? "#ffffff" : "#9ca3af";
        String translateX = enabled ? "18" : "2";
        return "-fx-background-color: " + bgColor + "; -fx-background-radius: 50%; "
            + "-fx-pref-width: 12px; -fx-pref-height: 12px; "
            + "-fx-translate-x: " + translateX + "px; -fx-translate-y: 2px;";
    }

    public SkillsPage() {
        setSpacing(0);
        setStyle("-fx-background-color: #f1ede1;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        scrollPane.setFitToWidth(true);

        VBox content = new VBox(16);
        content.setPadding(new Insets(40, 24, 24, 24));
        content.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("技能管理");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("扩展 Agent 能力的技能插件");
        subtitle.setStyle("-fx-font-size: 17px; -fx-text-fill: rgba(0, 0, 0, 0.5); -fx-font-weight: 500;");

        VBox titleBox = new VBox(8);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.getChildren().addAll(title, subtitle);

        // 技能网格
        skillGrid = new GridPane();
        skillGrid.setHgap(12);
        skillGrid.setVgap(12);
        skillGrid.setMaxWidth(880);

        String[][] skills = {
            {"\uD83E\uDDE0", "brainstorming", "头脑风暴与设计", "内置"},
            {"\uD83D\uDD0D", "systematic-debugging", "系统化调试", "内置"},
            {"\uD83D\uDCDD", "writing-plans", "编写计划", "内置"},
            {"\u2705", "verification", "完成前验证", "内置"},
            {"\uD83D\uDCCA", "xlsx", "Excel 处理", "已安装"}
        };

        int col = 0, row = 0;
        for (String[] skill : skills) {
            skillGrid.add(createSkillCard(skill[0], skill[1], skill[2], skill[3], true), col, row);
            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }

        Button addBtn = new Button("+ 安装新技能");
        addBtn.getStyleClass().add("pill-button");
        addBtn.setPrefHeight(40);
        addBtn.setOnAction(e -> installSkillZip());

        Button openBtn = new Button("\uD83D\uDCC2 打开文件夹");
        openBtn.getStyleClass().add("pill-button");
        openBtn.setPrefHeight(40);
        openBtn.setOnAction(e -> openSkillsFolder());

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);
        btnRow.getChildren().addAll(addBtn, openBtn);

        content.getChildren().addAll(titleBox, skillGrid, btnRow);
        VBox.setMargin(btnRow, new Insets(24, 0, 0, 0));

        scrollPane.setContent(content);
        getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private VBox createSkillCard(String iconText, String name, String desc, String status, boolean enabled) {
        VBox card = new VBox(0);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20, 20, 18, 16));
        card.setPrefWidth(420);
        // 纯阴影风格，无彩色边框
        card.setStyle("-fx-background-color: white; "
            + "-fx-background-radius: 12px; "
            + "-fx-border-width: 1px; "
            + "-fx-border-color: rgba(0,0,0,0.08); "
            + "-fx-border-radius: 12px; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 1);");

        if (!enabled) {
            card.setOpacity(0.5);
        }

        HBox header = new HBox(14);
        header.setAlignment(Pos.TOP_LEFT);

        Label iconLabel = new Label(iconText);
        iconLabel.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-background-radius: 10px; -fx-pref-width: 44px; -fx-pref-height: 44px; -fx-alignment: center;");
        iconLabel.setMinSize(44, 44);

        VBox infoBox = new VBox(6);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 700;");
        Label descLabel = new Label(desc);
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0, 0, 0, 0.5); -fx-line-spacing: 7px;");
        descLabel.setWrapText(true);
        descLabel.setMaxHeight(38);
        descLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        if (desc != null && !desc.isBlank()) {
            Tooltip tt = new Tooltip(desc);
            tt.setWrapText(true);
            tt.setMaxWidth(380);
            tt.setStyle("-fx-font-size: 12px;");
            descLabel.setTooltip(tt);
        }
        infoBox.getChildren().addAll(nameLabel, descLabel);

        // 右侧：状态徽标 + 开关 垂直排列
        VBox rightBox = new VBox(8);
        rightBox.setAlignment(Pos.TOP_RIGHT);

        Label statusBadge = new Label(status);
        statusBadge.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-background-radius: 8px;"
            + " -fx-font-size: 11px; -fx-padding: 2px 10px; -fx-text-fill: rgba(0,0,0,0.5); -fx-font-weight: 500;");

        // Toggle Switch
        Pane toggleContainer = new Pane();
        toggleContainer.setPickOnBounds(true);
        toggleContainer.setStyle(toggleStyle(enabled));
        toggleContainer.setMinSize(32, 16);
        toggleContainer.setPrefSize(32, 16);

        Pane toggleKnob = new Pane();
        toggleKnob.setMouseTransparent(true);
        toggleKnob.setStyle(toggleKnobStyle(enabled));
        toggleKnob.setMinSize(12, 12);
        toggleKnob.setPrefSize(12, 12);
        toggleContainer.getChildren().add(toggleKnob);

        final String skillName = name;
        final boolean currentEnabled = enabled;
        toggleContainer.setOnMouseClicked(e -> {
            boolean newEnabled = !currentEnabled;
            toggleSkillEnabled(skillName, newEnabled);
        });

        HBox toggleBox = new HBox(toggleContainer);
        toggleBox.setAlignment(Pos.CENTER_RIGHT);
        toggleBox.setMinHeight(16);
        toggleBox.setMaxHeight(16);
        HBox.setHgrow(toggleBox, Priority.ALWAYS);

        rightBox.getChildren().addAll(statusBadge, toggleBox);

        header.getChildren().addAll(iconLabel, infoBox, rightBox);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        card.getChildren().add(header);
        return card;
    }

    public void setBackendBridge(gui.ui.BackendBridge bridge) {
        this.backendBridge = bridge;
        refresh();
    }

    private void refresh() {
        if (backendBridge == null) return;
        skillGrid.getChildren().clear();

        java.util.List<java.util.Map<String, String>> skills =
            backendBridge.getSkillsLoader().listSkills(false);
        int col = 0, row = 0;
        for (java.util.Map<String, String> s : skills) {
            String name = s.get("name");
            String source = s.get("source");
            String status = "builtin".equals(source) ? "内置" : "工作区";
            boolean enabled = backendBridge.getSkillsLoader().isSkillEnabled(name);
            String desc = readSkillDescription(s.get("path"));
            if (desc == null || desc.isBlank()) {
                desc = name;
            }
            skillGrid.add(createSkillCard("\u26A1", name,
                desc, status, enabled), col, row);
            col++;
            if (col >= 2) { col = 0; row++; }
        }
    }

    /** 从 SKILL.md 文件路径直接读取 description 字段 */
    private static String readSkillDescription(String filePath) {
        if (filePath == null) return null;
        try {
            String content = java.nio.file.Files.readString(Paths.get(filePath));
            if (content == null || !content.startsWith("---")) return null;

            int end = content.indexOf("---", 3);
            if (end < 0) return null;

            String frontmatter = content.substring(3, end);
            for (String line : frontmatter.split("\\n")) {
                int idx = line.indexOf(':');
                if (idx > 0 && line.substring(0, idx).trim().equals("description")) {
                    String v = line.substring(idx + 1).trim();
                    if ((v.startsWith("\"") && v.endsWith("\""))
                        || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    return v;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void installSkillZip() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择技能压缩包");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("ZIP 压缩包", "*.zip"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) return;

        Path skillsDir = backendBridge.getConfig().getWorkspacePath().resolve("skills");
        try {
            extractZip(file, skillsDir.toFile());
            refresh();
        } catch (IOException ex) {
            System.err.println("安装技能失败: " + ex.getMessage());
        }
    }

    private void openSkillsFolder() {
        if (backendBridge == null) return;
        try {
            Path skillsDir = backendBridge.getConfig().getWorkspacePath().resolve("skills");
            java.awt.Desktop.getDesktop().open(skillsDir.toFile());
        } catch (Exception ignored) {}
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        destDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buf = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());
                // 防止 Zip Slip 攻击
                if (!outFile.getCanonicalPath().startsWith(destDir.getCanonicalPath() + File.separator)
                    && !outFile.getCanonicalPath().equals(destDir.getCanonicalPath())) {
                    continue;
                }
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void toggleSkillEnabled(String skillName, boolean enabled) {
        if (backendBridge == null) return;

        skills.SkillsLoader loader = backendBridge.getSkillsLoader();
        if (loader == null) return;

        // 查找技能文件路径
        java.util.List<java.util.Map<String, String>> allSkills = loader.listSkills(false);
        Path skillFile = null;

        for (java.util.Map<String, String> s : allSkills) {
            if (skillName.equals(s.get("name"))) {
                String path = s.get("path");
                if (path != null) {
                    skillFile = Paths.get(path);
                }
                break;
            }
        }

        if (skillFile == null || !java.nio.file.Files.exists(skillFile)) {
            System.err.println("Skill file not found: " + skillName);
            return;
        }

        try {
            String content = java.nio.file.Files.readString(skillFile);
            content = updateFrontmatterEnable(content, enabled);
            java.nio.file.Files.writeString(skillFile, content);
            refresh();
        } catch (java.io.IOException e) {
            System.err.println("Failed to update skill enable status: " + e.getMessage());
        }
    }

    private String updateFrontmatterEnable(String content, boolean enabled) {
        if (content == null || !content.startsWith("---")) {
            return content;
        }

        int endOfFrontmatter = content.indexOf("---", 3);
        if (endOfFrontmatter < 0) {
            return content;
        }

        String frontmatter = content.substring(3, endOfFrontmatter);
        String body = content.substring(endOfFrontmatter + 3);

        Pattern pattern = Pattern.compile("(?m)^enable:.*$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(frontmatter);

        if (matcher.find()) {
            frontmatter = matcher.replaceFirst("enable: " + enabled);
        } else {
            frontmatter = frontmatter.trim() + "\nenable: " + enabled;
        }

        return "---\n" + frontmatter + "\n---" + body;
    }
}
