package gui.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ModelCard extends VBox {

    public ModelCard(String name, String provider, boolean isDefault, boolean isReady) {
        setSpacing(0);
        getStyleClass().add("card");
        setPadding(new Insets(16));

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("\uD83E\uDD16");
        icon.setStyle("-fx-background-color: rgba(0, 0, 0, 0.05); -fx-background-radius: 10px; -fx-pref-width: 40px; -fx-pref-height: 40px; -fx-alignment: center;");
        icon.setMinSize(40, 40);

        VBox infoBox = new VBox(2);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500;");

        Label providerLabel = new Label(provider + (isDefault ? " · 默认模型" : ""));
        providerLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0, 0, 0, 0.5);");

        infoBox.getChildren().addAll(nameLabel, providerLabel);

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

        getChildren().add(header);
    }
}
