package ClientGUI;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Main entry point for the Bistro Client JavaFX application.
 * <p>
 * This class is responsible only for:
 * <ul>
 *   <li>Bootstrapping the JavaFX runtime</li>
 *   <li>Loading the initial FXML view</li>
 *   <li>Displaying the primary application stage</li>
 * </ul>
 *
 * <p>
 * <b>Design note:</b><br>
 * This class must remain free of any business logic or networking logic.
 * All client–server communication is handled exclusively by the
 * relevant JavaFX controller classes.
 *
 * @author Bistro Team
 */
public class ClientUI extends Application {

    /**
     * Starts the JavaFX application.
     * <p>
     * Loads the initial user login view from the {@code ClientGUI} package
     * and displays it on the primary stage.
     *
     * @param primaryStage the primary stage provided by the JavaFX runtime
     * @throws Exception if the FXML file cannot be loaded
     */
    @Override
    public void start(Stage primaryStage) throws Exception {

        // Locate the initial FXML file within the ClientGUI package
        URL fxmlLocation = ClientUI.class.getResource("UserLoginUIView.fxml");

        // Debug output to help identify FXML loading issues during development
        System.out.println("FXML URL = " + fxmlLocation);

        if (fxmlLocation == null) {
            throw new IllegalStateException(
                "Cannot find UserLoginUIView.fxml. " +
                "Ensure it is located in the ClientGUI package under src."
            );
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        primaryStage.setTitle("Bistro – User Login");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    /**
     * Application entry point.
     * <p>
     * Delegates control to the JavaFX {@link Application} lifecycle.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
