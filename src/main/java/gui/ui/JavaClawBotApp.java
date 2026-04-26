package gui.ui;

import javafx.application.Application;
import javafx.stage.Stage;

public class JavaClawBotApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainStage mainStage = new MainStage(primaryStage);
        mainStage.show();
    }

    public static void launchApp(String[] args) {
        launch(args);
    }
}
