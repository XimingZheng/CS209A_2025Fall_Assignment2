package org.example.demo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private CSController controller;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("board.fxml"));

        Parent root = loader.load();
        controller = loader.getController();

        Scene scene = new Scene(root);
        stage.setTitle("QQ Farm Demo");
        stage.setScene(scene);

        controller.init("localhost", 5050);

        stage.setOnCloseRequest(e -> controller.shutdown());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
