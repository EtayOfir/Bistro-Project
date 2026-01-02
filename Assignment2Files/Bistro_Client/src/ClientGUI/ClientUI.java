package ClientGUI;

import client.ChatClient;
import common.ChatIF;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

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
     * Starts the JavaFX application.
     * <p>
     * Initializes the client connection once, then loads the initial login view.
     *
     * @param primaryStage the primary stage provided by the JavaFX runtime
     * @throws Exception if the FXML file cannot be loaded
     */
    @Override
    public void start(Stage primaryStage) throws Exception {

        // Initialize a shared client connection (once).
        initClientConnection();

        // Load initial view
        URL fxmlLocation = ClientUI.class.getResource("UserLoginUIView.fxml");
        System.out.println("FXML URL = " + fxmlLocation);

        if (fxmlLocation == null) {
            throw new IllegalStateException(
                    "Cannot find UserLoginUIView.fxml. Ensure it is located in the ClientGUI package under src."
            );
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        primaryStage.setTitle("Bistro – User Login");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    /**
     * Initializes the shared {@link ChatClient} connection if not already initialized.
     *
     * <p>The {@link ClientUIController} acts as the {@link ChatIF} router that receives
     * all server messages via {@link ChatIF#display(String)}.
     */
    private void initClientConnection() {
        if (chat != null) return;

        try {
            chat = new ChatClient("localhost", 5555, new ClientMessageRouter());
            chat.openConnection();

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                System.out.println("ERROR: Could not connect to server.");
            });
        }
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
