package gui.ui;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.stage.Stage;

public class JavaClawBotApp extends Application {

    private static HostServices hostServices;

    public static HostServices getAppHostServices() {
        return hostServices;
    }

    @Override
    public void start(Stage primaryStage) {
        hostServices = getHostServices();
        MainStage mainStage = new MainStage(primaryStage);
        mainStage.show();
    }

    public static void launchApp(String[] args) {
        launch(args);
    }
}
