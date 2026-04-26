package gui.ui;

import gui.ui.components.Sidebar;
import gui.ui.pages.*;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.HashMap;
import java.util.Map;

public class MainStage {

    private static final double DEFAULT_WIDTH = 1280;
    private static final double DEFAULT_HEIGHT = 800;
    private static final double MIN_WIDTH = 960;
    private static final double MIN_HEIGHT = 600;

    private final Stage stage;
    private final BorderPane root;
    private final StackPane contentStack;
    private final Map<String, javafx.scene.Node> pages = new HashMap<>();

    public MainStage(Stage stage) {
        this.stage = stage;
        this.root = new BorderPane();
        this.contentStack = new StackPane();

        configureStage();
        loadStylesheets();
        setupPages();
        setupSidebar();
    }

    private void configureStage() {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setWidth(DEFAULT_WIDTH);
        stage.setHeight(DEFAULT_HEIGHT);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        stage.setScene(scene);
    }

    private void loadStylesheets() {
        String mainCss = getClass().getResource("/gui/ui/styles/main.css").toExternalForm();
        stage.getScene().getStylesheets().add(mainCss);
    }

    private void setupPages() {
        // 创建所有页面
        pages.put("chat", new ChatPage());
        pages.put("models", new ModelsPage());
        pages.put("agents", new AgentsPage());
        pages.put("channels", new ChannelsPage());
        pages.put("skills", new SkillsPage());
        pages.put("mcp", new McpPage(stage));
        pages.put("crontasks", new CronPage());
        pages.put("settings", new SettingsPage());

        // 添加到 StackPane
        for (javafx.scene.Node page : pages.values()) {
            contentStack.getChildren().add(page);
            page.setVisible(false);
            page.setManaged(false);
        }

        // 默认显示 Chat 页面
        showPage("chat");

        root.setCenter(contentStack);
    }

    private void setupSidebar() {
        Sidebar sidebar = new Sidebar();
        sidebar.addPageChangeListener(this::showPage);
        root.setLeft(sidebar);
    }

    private void showPage(String pageName) {
        // 标准化页面名称
        String normalized = pageName.toLowerCase().replace(" ", "");

        for (Map.Entry<String, javafx.scene.Node> entry : pages.entrySet()) {
            boolean visible = entry.getKey().equals(normalized);
            entry.getValue().setVisible(visible);
            entry.getValue().setManaged(visible);
        }
    }

    public void show() {
        stage.show();
    }

    public BorderPane getRoot() {
        return root;
    }

    public Stage getStage() {
        return stage;
    }
}
