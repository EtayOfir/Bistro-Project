package ClientGUI.controller;

import ClientGUI.util.SceneUtil;
import ClientGUI.util.ViewLoader;
import client.ChatClient;
import common.ChatIF;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

/**
 * Main entry point for the Bistro Client JavaFX application.
 *
 * <p>This class is responsible for:
 * <ul>
 *   <li>Bootstrapping the JavaFX runtime</li>
 *   <li>Initializing a single shared {@link ChatClient} connection</li>
 *   <li>Loading the initial FXML view</li>
 * </ul>
 *
 * <p><b>Architecture note:</b>
 * Business logic must remain inside controllers/services. This class only provides
 * shared client connection bootstrapping and delegates incoming messages to a router
 * (the {@link ClientUIController}) via {@link ChatIF#display(String)}.
 *
 * @author Bistro Team
 */
public class ClientUI extends Application {

    /**
     * A single shared OCSF client instance used by all controllers.
     * <p>
     * Controllers may send messages using:
     * {@code ClientUI.chat.handleMessageFromClientUI("...");}
     */
    public static ChatClient chat;

    /**
     * Server host and port settings configured before login
     */
    public static String serverHost = "localhost";
    public static int serverPort = 5555;

    /**
     * Starts the JavaFX application.
     * <p>
     * Opens the Server Settings screen first, then loads the login view after connection.
     *
     * @param primaryStage the primary stage provided by the JavaFX runtime
     * @throws Exception if the FXML file cannot be loaded
     */
    @Override
    public void start(Stage primaryStage) throws Exception {

    	FXMLLoader loader = ViewLoader.fxml("ServerSettingsUI.fxml");
        Parent root = loader.load();// Load Server Settings view first
    	

        primaryStage.setTitle("Bistro – Server Settings");
        primaryStage.setScene(SceneUtil.createStyledScene(root));
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    /**
     * Application entry point.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
