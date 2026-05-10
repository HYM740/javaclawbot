package gui.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class HistoryItem extends HBox {

    private final Label titleLabel;
    private final Label agentBadge;
    private final Button deleteButton;
    private boolean active = false;

    public HistoryItem(String title, String agentName) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        setPadding(new Insets(6, 10, 6, 10));
        getStyleClass().add("history-item");

        titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px;");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        agentBadge = new Label(agentName != null ? agentName : "");
        agentBadge.getStyleClass().add("agent-badge");
        boolean hasAgent = agentName != null && !agentName.isEmpty();
        agentBadge.setVisible(hasAgent);
        agentBadge.setManaged(hasAgent);

        deleteButton = new Button("\uD83D\uDDD1\uFE0F");
        deleteButton.setStyle("-fx-background-color: transparent; -fx-font-size: 12px; -fx-padding: 2px 4px;");
        deleteButton.setVisible(false);

        // 悬停显示删除按钮
        setOnMouseEntered(e -> deleteButton.setVisible(true));
        setOnMouseExited(e -> deleteButton.setVisible(false));

        getChildren().addAll(titleLabel, agentBadge, deleteButton);
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            getStyleClass().add("active");
        } else {
            getStyleClass().remove("active");
        }
    }

    public Button getDeleteButton() {
        return deleteButton;
    }
}
