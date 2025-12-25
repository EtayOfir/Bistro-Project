package ServerGUI;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import server.EchoServer;

/**
 * Main JavaFX Application class for Server UI
 * Loads the FXML file and sets up the primary stage
 */
public class ServerUI extends Application {

    private EchoServer echoServer;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load the FXML file
        FXMLLoader loader = new FXMLLoader(getClass().getResource("ServerUIView.fxml"));
        BorderPane root = loader.load();

        // Get the controller and pass the server reference	
        ServerUIController controller = loader.getController();
        echoServer = new EchoServer(EchoServer.DEFAULT_PORT);
        
        // Connect the controller and server
        controller.setEchoServer(echoServer);
        echoServer.setUIController(controller);

        // Create the scene
        Scene scene = new Scene(root, 1000, 700);
        
        // Set up the stage
        primaryStage.setTitle("Bistro Server");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            controller.shutdown();
        });
        
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
