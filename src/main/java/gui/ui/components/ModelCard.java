package gui.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ModelCard extends VBox {

    public ModelCard(String name, String provider, boolean isDefault, boolean isReady) {
        this(name, provider, isDefault, isReady, false, null);
    }

    /**
     * @param name         显示名称
     * @param provider     供应商 key
     * @param isDefault    是否为默认模型
     * @param isReady      是否已配置
     * @param isBuiltin    是否为内置供应商
     * @param onDelete     删除回调（null 则不显示删除按钮）
     */
    public ModelCard(String name, String provider, boolean isDefault, boolean isReady,
                     boolean isBuiltin, Runnable onDelete) {
        setSpacing(0);
        getStyleClass().add("card");
        setPadding(new Insets(16));
        setStyle(getStyle() + "; -fx-background-color: " + (isBuiltin ? "#efe9de" : "linear-gradient(to bottom, #efe9de, #f5efe0)")
            + "; -fx-background-radius: 12px;");

        // 外层 StackPane 支持删除按钮覆盖
        StackPane cardWrapper = new StackPane();
        cardWrapper.setMinHeight(80);

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("\uD83E\uDD16");
        icon.setStyle("-fx-background-color: rgba(0, 0, 0, 0.05); -fx-background-radius: 10px; -fx-pref-width: 40px; -fx-pref-height: 40px; -fx-alignment: center;");
        icon.setMinSize(40, 40);

        VBox infoBox = new VBox(2);

        // 名称行：名称 + 徽章
        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500;");

        // 内置 / 自定义 徽章
        Label badge = new Label(isBuiltin ? "内置" : "自定义");
        badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 600; -fx-padding: 2px 8px; -fx-background-radius: 999px;"
            + (isBuiltin
                ? " -fx-background-color: rgba(0,0,0,0.06); -fx-text-fill: rgba(0,0,0,0.45);"
                : " -fx-background-color: #cc785c; -fx-text-fill: white;"));

        nameRow.getChildren().addAll(nameLabel, badge);

        Label providerLabel = new Label(provider + (isDefault ? " · 默认模型" : ""));
        providerLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0, 0, 0, 0.5);");

        infoBox.getChildren().addAll(nameRow, providerLabel);

        // 状态指示器
        Label statusBadge = new Label(isReady ? "就绪" : "错误");
        statusBadge.getStyleClass().addAll("status-badge", isReady ? "running" : "error");

        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("\u25CF");
        dot.setStyle("-fx-text-fill: " + (isReady ? "#22c55e" : "#ef4444") + "; -fx-font-size: 8px;");
        statusBox.getChildren().addAll(dot, statusBadge);

        header.getChildren().addAll(icon, infoBox, statusBox);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        cardWrapper.getChildren().add(header);

        // 删除按钮（仅自定义供应商）
        if (!isBuiltin && onDelete != null) {
            Button delBtn = new Button("✕");
            delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #c64545; -fx-font-size: 14px;"
                + " -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0 4px 2px 4px;"
                + " -fx-background-radius: 999px; -fx-min-width: 28px; -fx-min-height: 28px;");
            delBtn.setOnMouseEntered(e -> delBtn.setStyle(delBtn.getStyle()
                .replace("-fx-background-color: transparent", "-fx-background-color: #c64545")
                .replace("-fx-text-fill: #c64545", "-fx-text-fill: white")));
            delBtn.setOnMouseExited(e -> delBtn.setStyle(delBtn.getStyle()
                .replace("-fx-background-color: #c64545", "-fx-background-color: transparent")
                .replace("-fx-text-fill: white", "-fx-text-fill: #c64545")));
            // 消费鼠标事件，防止穿透到卡片的 setOnMouseClicked
            delBtn.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                e.consume();
                log.debug("删除按钮点击，触发删除回调");
                onDelete.run();
            });
            StackPane.setAlignment(delBtn, Pos.TOP_RIGHT);
            StackPane.setMargin(delBtn, new Insets(8, 8, 0, 0));
            cardWrapper.getChildren().add(delBtn);
        }

        getChildren().add(cardWrapper);
    }
}
