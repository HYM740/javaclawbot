package gui.ui.components;

import com.google.gson.Gson;
import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

/**
 * 悬浮 Todo 进度按钮 — 实时展示当前 TodoWrite 任务进度。
 *
 * - 圆形按钮，显示 completed/total 计数
 * - 有 in_progress 任务时蓝色脉冲动画
 * - 全部完成后保留显示（如 4/4），不消失
 * - 全部完成后下拉面板底部出现「关闭」按钮，用户可手动关闭
 * - 新 TodoWrite 到达时自动重新显示
 * - 可拖拽移动位置
 * - 无任务时隐藏
 */
public class TodoFloatBadge extends StackPane {

    private final Label countLabel;
    private final VBox dropdown;
    private final StackPane badgeBtn;
    private final Timeline pulseAnimation;
    private boolean dropdownVisible = false;
    private boolean hasInProgress = false;
    private boolean userDismissed = false;
    private boolean allCompleted = false;

    private double dragStartX, dragStartY;
    private double initTranslateX, initTranslateY;
    private boolean dragging = false;

    public TodoFloatBadge() {
        setVisible(false);
        setMaxSize(44, 44);
        setPrefSize(44, 44);

        badgeBtn = new StackPane();
        badgeBtn.setPrefSize(44, 44);
        badgeBtn.setMaxSize(44, 44);
        badgeBtn.setCursor(Cursor.HAND);
        Circle circle = new Circle(22, 22, 22);
        circle.setFill(Color.rgb(255, 255, 255, 0.92));
        circle.setStroke(Color.rgb(0, 0, 0, 0.12));
        circle.setStrokeWidth(1.5);
        badgeBtn.getChildren().add(circle);

        VBox badgeContent = new VBox(1);
        badgeContent.setAlignment(Pos.CENTER);
        Label iconLabel = new Label("📋"); // 📋
        iconLabel.setStyle("-fx-font-size: 10px;");
        countLabel = new Label("");
        countLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-font-family: monospace;");
        badgeContent.getChildren().addAll(iconLabel, countLabel);
        badgeBtn.getChildren().add(badgeContent);

        badgeBtn.setOnMouseClicked(e -> {
            if (!dragging) toggleDropdown();
            dragging = false;
        });

        badgeBtn.setOnMousePressed(e -> {
            dragStartX = e.getScreenX();
            dragStartY = e.getScreenY();
            initTranslateX = getTranslateX();
            initTranslateY = getTranslateY();
            dragging = false;
        });
        badgeBtn.setOnMouseDragged(e -> {
            double dx = e.getScreenX() - dragStartX;
            double dy = e.getScreenY() - dragStartY;
            if (Math.abs(dx) > 2 || Math.abs(dy) > 2) {
                dragging = true;
                badgeBtn.setCursor(Cursor.CLOSED_HAND);
                setTranslateX(initTranslateX + dx);
                setTranslateY(initTranslateY + dy);
            }
        });
        badgeBtn.setOnMouseReleased(e -> badgeBtn.setCursor(Cursor.HAND));

        badgeBtn.setOnMouseEntered(e -> {
            circle.setStroke(Color.rgb(59, 130, 246, 0.6));
            circle.setStrokeWidth(2.0);
        });
        badgeBtn.setOnMouseExited(e -> {
            if (!hasInProgress) {
                circle.setStroke(Color.rgb(0, 0, 0, 0.12));
                circle.setStrokeWidth(1.5);
            }
        });

        dropdown = new VBox(6);
        dropdown.setStyle("-fx-background-color: rgba(255,255,255,0.96);"
                + " -fx-background-radius: 12px;"
                + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-radius: 12px;"
                + " -fx-padding: 12px;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 12, 0, 0, 4);");
        dropdown.setPrefWidth(280);
        dropdown.setMaxHeight(320);
        dropdown.setVisible(false);
        dropdown.setManaged(false);

        pulseAnimation = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(circle.strokeWidthProperty(), 1.5),
                        new KeyValue(circle.strokeProperty(), circle.getStroke())),
                new KeyFrame(Duration.millis(750),
                        new KeyValue(circle.strokeWidthProperty(), 3.5),
                        new KeyValue(circle.strokeProperty(), Color.rgb(59, 130, 246, 0.6))),
                new KeyFrame(Duration.millis(1500),
                        new KeyValue(circle.strokeWidthProperty(), 1.5),
                        new KeyValue(circle.strokeProperty(), Color.rgb(59, 130, 246, 0.3)))
        );
        pulseAnimation.setCycleCount(Animation.INDEFINITE);

        badgeBtn.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, ev -> {
                    if (dropdownVisible && !isInsideBadge(ev.getSceneX(), ev.getSceneY())) {
                        hideDropdown();
                    }
                });
            }
        });

        // 按钮 + 下拉面板：均右对齐，dropdown 偏移到按钮上方
        getChildren().addAll(badgeBtn, dropdown);
        StackPane.setAlignment(badgeBtn, Pos.BOTTOM_RIGHT);
        StackPane.setAlignment(dropdown, Pos.BOTTOM_RIGHT);
        // dropdown 右对齐，translateX 让右侧对齐按钮右侧（280-44=236）
        dropdown.translateXProperty().bind(
            badgeBtn.widthProperty().subtract(dropdown.widthProperty()));
        // dropdown 底部在按钮顶部上方 6px
        dropdown.translateYProperty().bind(
            badgeBtn.translateYProperty()
                .subtract(badgeBtn.heightProperty())
                .subtract(dropdown.heightProperty())
                .subtract(6));
    }

    /** 解析 TodoWrite JSON 并更新浮标（调用方已在 JavaFX 线程） */
    public void updateFromJson(String json) {
        if (json == null || json.isBlank()) return;
        try {
            Gson gson = new Gson();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = gson.fromJson(json, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newTodos = (List<Map<String, Object>>) root.get("newTodos");
            Boolean allDone = (Boolean) root.get("allDone");

            // 新 todo 到达，清除手动关闭状态
            userDismissed = false;

            if (newTodos == null || newTodos.isEmpty()) {
                setVisible(false);
                stopPulse();
                allCompleted = false;
                return;
            }

            int total = newTodos.size();
            long completed = newTodos.stream()
                    .filter(t -> "completed".equals(t.get("status")))
                    .count();
            boolean inProgress = newTodos.stream()
                    .anyMatch(t -> "in_progress".equals(t.get("status")));
            allCompleted = (completed == total);

            setVisible(true);
            countLabel.setText(completed + "/" + total);

            if (inProgress && !hasInProgress) {
                hasInProgress = true;
                startPulse();
            } else if (!inProgress && hasInProgress) {
                hasInProgress = false;
                stopPulse();
            }

            buildDropdown(newTodos, allCompleted);
        } catch (Exception e) {
            System.err.println("[TodoFloatBadge] 解析失败: " + e.getMessage());
        }
    }

    private void buildDropdown(List<Map<String, Object>> todos, boolean allDone) {
        dropdown.getChildren().clear();
        for (Map<String, Object> item : todos) {
            String status = (String) item.getOrDefault("status", "pending");
            String content = (String) item.getOrDefault("content", "");
            String activeForm = (String) item.getOrDefault("activeForm", "");

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 3px 0;");

            String icon;
            String color;
            switch (status) {
                case "completed": icon = "✓"; color = "#16a34a"; break;
                case "in_progress": icon = "◉"; color = "#3b82f6"; break;
                default: icon = "○"; color = "rgba(0,0,0,0.3)"; break;
            }

            Label iconLabel = new Label(icon);
            iconLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + "; -fx-min-width: 16px;");
            iconLabel.setMinSize(16, 16);

            Label contentLabel = new Label(content);
            contentLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.8);");
            contentLabel.setWrapText(true);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label statusLabel = new Label(status.equals("in_progress") ? activeForm : "");
            statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(0,0,0,0.4);");

            row.getChildren().addAll(iconLabel, contentLabel, spacer, statusLabel);
            dropdown.getChildren().add(row);
        }

        // 全部完成时底部显示关闭按钮
        if (allDone) {
            Region sep = new Region();
            sep.setStyle("-fx-min-height: 1px; -fx-background-color: rgba(0,0,0,0.06);");
            dropdown.getChildren().add(sep);

            HBox dismissRow = new HBox();
            dismissRow.setAlignment(Pos.CENTER_RIGHT);
            Label dismissBtn = new Label("✕ 关闭");
            dismissBtn.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.4);"
                    + " -fx-padding: 4px 8px; -fx-cursor: hand;"
                    + " -fx-background-radius: 6px;");
            dismissBtn.setOnMouseEntered(e ->
                dismissBtn.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.7);"
                    + " -fx-padding: 4px 8px; -fx-cursor: hand;"
                    + " -fx-background-color: rgba(0,0,0,0.05); -fx-background-radius: 6px;"));
            dismissBtn.setOnMouseExited(e ->
                dismissBtn.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.4);"
                    + " -fx-padding: 4px 8px; -fx-cursor: hand;"
                    + " -fx-background-radius: 6px;"));
            dismissBtn.setOnMouseClicked(e -> {
                userDismissed = true;
                setVisible(false);
                stopPulse();
                hideDropdown();
            });
            dismissRow.getChildren().add(dismissBtn);
            dropdown.getChildren().add(dismissRow);
        }
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
        dropdown.setManaged(true);
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
        if (!badgeBtn.getChildren().isEmpty() && badgeBtn.getChildren().get(0) instanceof Circle c) {
            c.setStroke(Color.rgb(0, 0, 0, 0.12));
            c.setStrokeWidth(1.5);
        }
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
