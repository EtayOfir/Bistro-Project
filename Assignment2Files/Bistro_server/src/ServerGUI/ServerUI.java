package ServerGUI;

import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import server.EchoServer;

/**
 * Main JavaFX Application class for the Server Side of the Bistro System.
 * <p>
 * This class is responsible for:
 * <ul>
 * <li>Loading the graphical user interface (FXML) for the server.</li>
 * <li>Initializing the backend {@link EchoServer} logic.</li>
 * <li>Connecting the UI Controller with the Server logic (MVC binding).</li>
 * <li>Handling the application lifecycle (start and safe shutdown).</li>
 * </ul>
 * </p>
 */
public class ServerUI extends Application {

	/**
     * Reference to the main server logic instance.
     */
    private EchoServer echoServer;

    /**
     * The main entry point for the JavaFX application thread.
     * <p>
     * This method performs the following initialization steps:
     * <ol>
     * <li>Loads the {@code ServerUIView.fxml} layout file.</li>
     * <li>Initializes the {@link ServerUIController}.</li>
     * <li>Instantiates the {@link EchoServer} on the default port.</li>
     * <li>Links the Server instance to the Controller and vice-versa.</li>
     * <li>Sets up the primary stage (window) with specific dimensions (1000x700).</li>
     * <li>Configures the "On Close" event to ensure the server shuts down gracefully.</li>
     * </ol>
     * </p>
     *
     * @param primaryStage The primary stage for this application, onto which
     * the application scene can be set.
     */
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

    /**
     * The main entry point for all JavaFX applications.
     * <p>
     * This method calls {@link #launch(String...)} which internally calls {@link #start(Stage)}.
     * </p>
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
