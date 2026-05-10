package gui.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class NavigationItem extends HBox {

    private final Label iconLabel;
    private final Label textLabel;
    private boolean active = false;

    public NavigationItem(String icon, String text) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setPadding(new Insets(8, 10, 8, 10));
        getStyleClass().add("nav-item");

        iconLabel = new Label(icon);
        iconLabel.setMinSize(18, 18);
        iconLabel.setPrefSize(18, 18);
        iconLabel.setAlignment(Pos.CENTER);

        textLabel = new Label(text);
        textLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");

        getChildren().addAll(iconLabel, textLabel);
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            getStyleClass().add("active");
        } else {
            getStyleClass().remove("active");
        }
    }

    public boolean isActive() {
        return active;
    }

    public Label getTextLabel() {
        return textLabel;
    }
}
