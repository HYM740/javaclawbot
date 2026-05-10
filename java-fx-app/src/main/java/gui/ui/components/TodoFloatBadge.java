package gui.ui.components;

import com.google.gson.Gson;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

/**
 * 悬浮 Todo 进度按钮 — 插件同款暗色风格。
 *
 * - 固定在输入区右上方
 * - 圆形按钮 38×38，暗色背景 + 蓝色脉冲光环
 * - 下拉面板向上展开，暗色背景 280px 宽
 * - 头部进度条 + 分组任务列表
 * - 不可拖拽
 * - 无任务时隐藏
 */
public class TodoFloatBadge extends StackPane {

    private final Label countLabel;
    private final VBox dropdown;
    private final StackPane badgeBtn;
    private final Circle circle;
    private final Timeline pulseAnimation;
    private boolean dropdownVisible = false;
    private boolean hasInProgress = false;

    // 浅色主题色值
    private static final Color BG_CARD = Color.rgb(255, 255, 255);       // #ffffff
    private static final Color BORDER = Color.rgb(0, 0, 0, 0.12);
    private static final Color ACCENT = Color.rgb(59, 130, 246);         // #3b82f6
    private static final Color TEXT_PRIMARY = Color.rgb(0, 0, 0, 0.8);
    private static final Color TEXT_MUTED = Color.rgb(0, 0, 0, 0.4);
    private static final Color SUCCESS = Color.rgb(22, 163, 74);         // #16a34a

    public TodoFloatBadge() {
        setVisible(false);
        setMaxSize(38, 38);
        setPrefSize(38, 38);
        setPickOnBounds(false);

        // === 圆形按钮 ===
        circle = new Circle(19, 19, 19);
        circle.setFill(BG_CARD);
        circle.setStroke(BORDER);
        circle.setStrokeWidth(1.5);

        badgeBtn = new StackPane();
        badgeBtn.setPrefSize(38, 38);
        badgeBtn.setMaxSize(38, 38);
        badgeBtn.setCursor(Cursor.HAND);
        badgeBtn.getChildren().add(circle);

        // 按钮内容：图标 + 计数
        VBox badgeContent = new VBox(1);
        badgeContent.setAlignment(Pos.CENTER);

        Label iconLabel = new Label("📋");
        iconLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #8b8d8f;");

        countLabel = new Label("");
        countLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-font-family: monospace; -fx-text-fill: rgba(0,0,0,0.8);");

        badgeContent.getChildren().addAll(iconLabel, countLabel);
        badgeBtn.getChildren().add(badgeContent);

        // 点击展开/收起下拉
        badgeBtn.setOnMouseClicked(e -> {
            e.consume();
            toggleDropdown();
        });

        // hover 效果
        badgeBtn.setOnMouseEntered(e -> {
            if (!hasInProgress) circle.setStroke(ACCENT);
            circle.setStrokeWidth(2.0);
        });
        badgeBtn.setOnMouseExited(e -> {
            if (!hasInProgress) {
                circle.setStroke(BORDER);
                circle.setStrokeWidth(1.5);
            }
        });

        // === 脉冲动画（进行中时激活） ===
        pulseAnimation = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(circle.strokeWidthProperty(), 1.5)),
                new KeyFrame(Duration.millis(750),
                        new KeyValue(circle.strokeWidthProperty(), 3.0),
                        new KeyValue(circle.strokeProperty(), Color.rgb(59, 130, 246, 0.8))),
                new KeyFrame(Duration.millis(1500),
                        new KeyValue(circle.strokeWidthProperty(), 1.5),
                        new KeyValue(circle.strokeProperty(), Color.rgb(59, 130, 246, 0.3)))
        );
        pulseAnimation.setCycleCount(Animation.INDEFINITE);

        // === 下拉面板 ===
        dropdown = new VBox(0);
        dropdown.setStyle("-fx-background-color: #ffffff;"
                + " -fx-background-radius: 12px;"
                + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-radius: 12px;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 12, 0, 0, 4);");
        dropdown.setMinWidth(280);
        dropdown.setMaxWidth(280);
        dropdown.setMaxHeight(360);
        dropdown.setVisible(false);
        dropdown.setManaged(false);

        // 关闭外部点击
        badgeBtn.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, ev -> {
                    if (dropdownVisible && !isInsideBadge(ev.getSceneX(), ev.getSceneY())) {
                        hideDropdown();
                    }
                });
            }
        });

        getChildren().addAll(badgeBtn, dropdown);
        StackPane.setAlignment(badgeBtn, Pos.BOTTOM_RIGHT);
        StackPane.setAlignment(dropdown, Pos.BOTTOM_RIGHT);
    }

    /** 解析 TodoWrite JSON 并更新浮标 */
    public void updateFromJson(String json) {
        if (json == null || json.isBlank()) return;
        try {
            Gson gson = new Gson();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = gson.fromJson(json, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newTodos = (List<Map<String, Object>>) root.get("newTodos");

            if (newTodos == null || newTodos.isEmpty()) {
                setVisible(false);
                stopPulse();
                return;
            }

            int total = newTodos.size();
            long completed = newTodos.stream()
                    .filter(t -> "completed".equals(t.get("status")))
                    .count();
            boolean inProgress = newTodos.stream()
                    .anyMatch(t -> "in_progress".equals(t.get("status")));

            setVisible(true);
            countLabel.setText(completed + "/" + total);

            if (inProgress && !hasInProgress) {
                hasInProgress = true;
                startPulse();
            } else if (!inProgress && hasInProgress) {
                hasInProgress = false;
                stopPulse();
            }

            buildDropdown(newTodos, total, (int) completed);
        } catch (Exception e) {
            System.err.println("[TodoFloatBadge] 解析失败: " + e.getMessage());
        }
    }

    private void buildDropdown(List<Map<String, Object>> todos, int total, int completed) {
        dropdown.getChildren().clear();
        int pct = total > 0 ? (int) Math.round((completed / (double) total) * 100) : 0;

        // === Header：标题 + 关闭按钮 + 进度条 ===
        VBox headerBox = new VBox(8);
        headerBox.setPadding(new Insets(12, 14, 8, 14));
        headerBox.setStyle("-fx-border-color: transparent transparent rgba(0,0,0,0.06) transparent; -fx-border-width: 0 0 1 0;");

        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleRow, Priority.ALWAYS);

        Label titleLabel = new Label("任务");
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: rgba(0,0,0,0.8);");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label closeBtn = new Label("\u00D7"); // ×
        closeBtn.setStyle("-fx-font-size: 16px; -fx-text-fill: rgba(0,0,0,0.3); -fx-cursor: hand; -fx-padding: 0 4px;");
        closeBtn.setOnMouseClicked(e -> {
            hideDropdown();
            e.consume();
        });

        titleRow.getChildren().addAll(titleLabel, spacer, closeBtn);

        // 进度条
        StackPane progressBar = new StackPane();
        progressBar.setPrefHeight(4);
        progressBar.setMaxHeight(4);

        Rectangle bg = new Rectangle(260, 4);
        bg.setArcWidth(4);
        bg.setArcHeight(4);
        bg.setFill(Color.rgb(0, 0, 0, 0.06));

        Rectangle fg = new Rectangle(260.0 * pct / 100, 4);
        fg.setArcWidth(4);
        fg.setArcHeight(4);
        fg.setFill(ACCENT);

        progressBar.getChildren().addAll(bg, fg);

        Label progressText = new Label(completed + " / " + total + " 已完成");
        progressText.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(0,0,0,0.4);");

        headerBox.getChildren().addAll(titleRow, progressBar, progressText);
        dropdown.getChildren().add(headerBox);

        // === 分组任务列表 ===
        VBox listBox = new VBox(4);
        listBox.setPadding(new Insets(8, 0, 8, 0));
        listBox.setStyle("-fx-background-color: #ffffff;");

        // In Progress
        List<Map<String, Object>> inProgressItems = todos.stream()
                .filter(t -> "in_progress".equals(t.get("status")))
                .toList();
        if (!inProgressItems.isEmpty()) {
            addSectionHeader(listBox, "进行中", inProgressItems.size());
            for (Map<String, Object> item : inProgressItems) addTodoRow(listBox, item, "in_progress");
        }

        // Pending
        List<Map<String, Object>> pendingItems = todos.stream()
                .filter(t -> "pending".equals(t.get("status")))
                .toList();
        if (!pendingItems.isEmpty()) {
            addSectionHeader(listBox, "待处理", pendingItems.size());
            for (Map<String, Object> item : pendingItems) addTodoRow(listBox, item, "pending");
        }

        // Completed
        List<Map<String, Object>> completedItems = todos.stream()
                .filter(t -> "completed".equals(t.get("status")))
                .toList();
        if (!completedItems.isEmpty()) {
            addSectionHeader(listBox, "已完成", completedItems.size());
            for (Map<String, Object> item : completedItems) addTodoRow(listBox, item, "completed");
        }

        dropdown.getChildren().add(listBox);
    }

    private void addSectionHeader(VBox parent, String title, int count) {
        if (parent.getChildren().size() > 0) {
            // Separator before each section (except first)
            Region sep = new Region();
            sep.setStyle("-fx-min-height: 1px; -fx-background-color: rgba(0,0,0,0.06);");
            sep.setPadding(new Insets(4, 0, 4, 0));
            parent.getChildren().add(sep);
        }

        Label header = new Label(title + " (" + count + ")");
        header.setStyle("-fx-font-size: 9px; -fx-font-weight: 600; -fx-text-fill: rgba(0,0,0,0.4);"
                + " -fx-padding: 4px 14px 2px 14px;");
        parent.getChildren().add(header);
    }

    private void addTodoRow(VBox parent, Map<String, Object> item, String status) {
        String content = (String) item.getOrDefault("content", "");
        String activeForm = (String) item.getOrDefault("activeForm", "");

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 14, 3, 14));

        // 状态图标
        String icon;
        String color;
        switch (status) {
            case "completed": icon = "\u2713"; color = "#16a34a"; break;   // ✓
            case "in_progress": icon = "\u25C9"; color = "#3b82f6"; break; // ◉
            default: icon = "\u25CB"; color = "rgba(0,0,0,0.3)"; break;   // ○
        }

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + color + "; -fx-min-width: 14px;");
        iconLabel.setMinSize(14, 14);

        // 任务内容
        Label contentLabel = new Label(content);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(220);
        if ("completed".equals(status)) {
            contentLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.4);");
        } else {
            contentLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(0,0,0,0.8);");
        }
        HBox.setHgrow(contentLabel, Priority.ALWAYS);

        // 进行中描述
        Label activeLabel = new Label("in_progress".equals(status) && !activeForm.isEmpty() ? activeForm : "");
        activeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: rgba(0,0,0,0.4);");
        activeLabel.setMinWidth(Region.USE_PREF_SIZE);

        row.getChildren().addAll(iconLabel, contentLabel, activeLabel);
        parent.getChildren().add(row);
    }

    private void toggleDropdown() {
        if (dropdownVisible) {
            hideDropdown();
        } else {
            showDropdown();
        }
    }

    private void showDropdown() {
        dropdown.setVisible(true);
        dropdown.setManaged(false);
        dropdown.applyCss();
        dropdown.autosize();

        double ddH = Math.min(dropdown.prefHeight(280), dropdown.getMaxHeight());
        double ddW = dropdown.prefWidth(ddH);
        double btnW = badgeBtn.getWidth();

        // 手动定位：右对齐 + 按钮上方 8px
        dropdown.setLayoutX(btnW - ddW);
        dropdown.setLayoutY(-ddH - 8);

        /*System.out.println("[TodoFloatBadge] showDropdown:"
            + " ddH=" + ddH + " ddW=" + ddW + " btnW=" + btnW
            + " layoutX=" + (btnW - ddW) + " layoutY=" + (-ddH - 8));*/

        dropdownVisible = true;
    }

    private void hideDropdown() {
        dropdown.setVisible(false);
        dropdown.setManaged(false);
        dropdownVisible = false;
    }

    private void startPulse() {
        pulseAnimation.play();
    }

    private void stopPulse() {
        pulseAnimation.stop();
        circle.setStroke(BORDER);
        circle.setStrokeWidth(1.5);
    }

    private boolean isInsideBadge(double sceneX, double sceneY) {
        try {
            javafx.geometry.Bounds bounds = badgeBtn.localToScene(badgeBtn.getBoundsInLocal());
            return bounds.contains(sceneX, sceneY);
        } catch (Exception e) {
            return false;
        }
    }
}
