package ServerGUI;

import java.net.URL;

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
    public void start(Stage primaryStage) {
        try {
            // Load the FXML file
        	URL url = getClass().getResource("ServerUIView.fxml");
        	if (url == null) {
        	    throw new IllegalStateException("Missing ServerUIView.fxml in ServerGUI package");
        	}

        	FXMLLoader loader = new FXMLLoader(url);
        	BorderPane root = loader.load();

            // Get the controller and pass the server reference	
            ServerUIController controller = loader.getController();
            echoServer = new EchoServer(EchoServer.DEFAULT_PORT);
            
            // Connect the controller and server
            controller.setEchoServer(echoServer);
            echoServer.setUIController(controller);

            // Create the scene with the size defined in FXML (1800x1200)
            Scene scene = new Scene(root, 1000, 700);
            
            // Set up the stage
            primaryStage.setTitle("Bistro Server");
            primaryStage.setScene(scene);
            
            primaryStage.setOnCloseRequest(event -> {
                controller.shutdown();
            });
            
            primaryStage.show();
            
            System.out.println("Server UI started successfully");
        } catch (Exception e) {
            System.err.println("ERROR: Failed to start Server UI");
            e.printStackTrace();
            
            // Show error dialog
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Server Startup Error");
            alert.setHeaderText("Failed to start the Bistro Server UI");
            alert.setContentText("Error: " + e.getMessage() + "\n\nPlease check the console for details.");
            alert.showAndWait();
            
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
