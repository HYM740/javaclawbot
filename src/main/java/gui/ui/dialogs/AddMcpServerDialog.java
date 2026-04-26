package gui.ui.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class AddMcpServerDialog extends Stage {

    private final TextField nameField;
    private final TextField commandField;
    private boolean confirmed = false;

    public AddMcpServerDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.TRANSPARENT);

        setTitle("添加 MCP 服务器");

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 16px; -fx-border-color: rgba(0, 0, 0, 0.1); -fx-border-radius: 16px; -fx-border-width: 1px;");
        root.setPrefWidth(400);

        // 标题
        Label title = new Label("添加 MCP 服务器");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 500;");

        // 名称输入
        VBox nameBox = new VBox(4);
        Label nameLabel = new Label("服务器名称");
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");

        nameField = new TextField();
        nameField.getStyleClass().add("input-field");
        nameField.setPrefHeight(40);
        nameField.setPromptText("例如: filesystem");

        nameBox.getChildren().addAll(nameLabel, nameField);

        // 命令输入
        VBox commandBox = new VBox(4);
        Label commandLabel = new Label("启动命令");
        commandLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");

        commandField = new TextField();
        commandField.getStyleClass().add("input-field");
        commandField.setPrefHeight(40);
        commandField.setPromptText("npx -y @modelcontextprotocol/server-filesystem");

        commandBox.getChildren().addAll(commandLabel, commandField);

        // 按钮
        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("pill-button");
        cancelBtn.setPrefHeight(40);
        cancelBtn.setOnAction(e -> close());

        Button confirmBtn = new Button("添加");
        confirmBtn.getStyleClass().add("pill-button");
        confirmBtn.setPrefHeight(40);
        confirmBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 999px; -fx-border-radius: 999px; -fx-border-color: #3b82f6; -fx-border-width: 1px;");
        confirmBtn.setOnAction(e -> {
            confirmed = true;
            close();
        });

        buttonBox.getChildren().addAll(cancelBtn, confirmBtn);

        root.getChildren().addAll(title, nameBox, commandBox, buttonBox);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/gui/ui/styles/main.css").toExternalForm());
        setScene(scene);
    }

    public String getServerName() {
        return nameField.getText();
    }

    public String getCommand() {
        return commandField.getText();
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
