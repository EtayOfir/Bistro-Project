package ClientGUI.controller;

import ClientGUI.util.SceneUtil;
import ClientGUI.util.ViewLoader;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Controller for the Server Configuration View.
 * <p>
 * This is the entry point of the client application. It allows the user to specify
 * the IP address and Port of the backend server before attempting to log in.
 * <p>
 * This step ensures the client is dynamic and not hardcoded to "localhost".
 */
public class ServerSettingsUIController {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private Label statusLabel;

    private String host;
    private int port;
    private boolean connected = false;

    /**
     * Initializes the controller class.
     * <p>
     * Pre-fills the input fields with default values ("localhost" : "5555") 
     * for easier testing and local development.
     */
    @FXML
    private void initialize() {
        // Set default values
        hostField.setText("localhost");
        portField.setText("5555");
        statusLabel.setText("");
    }

    /**
     * Validates input and saves the server configuration.
     * <p>
     * <b>Logic Flow:</b>
     * <ol>
     * <li><b>Validation:</b> Checks that Host is not empty and Port is a valid integer between 1 and 65535.</li>
     * <li><b>Configuration:</b> Saves the valid settings into the static fields of {@code ClientUI}. 
     * This makes the settings available globally for the {@code ChatClient} later.</li>
     * <li><b>Navigation:</b> Loads the {@code UserLoginUIView.fxml} to proceed to authentication.</li>
     * </ol>
     * Note: This method does <b>not</b> open the network socket yet; it only prepares the settings.
     *
     * @param event The button click event.
     */
    @FXML
    private void onConnectClicked(ActionEvent event) {
        String hostInput = hostField.getText().trim();
        String portInput = portField.getText().trim();

        // Validate inputs
        if (hostInput.isEmpty()) {
            statusLabel.setText("Please enter a host address.");
            return;
        }

        if (portInput.isEmpty()) {
            statusLabel.setText("Please enter a port number.");
            return;
        }

        int portNum;
        try {
            portNum = Integer.parseInt(portInput);
            if (portNum < 1 || portNum > 65535) {
                statusLabel.setText("Port must be between 1 and 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            statusLabel.setText("Port must be a valid number.");
            return;
        }

        // Store the settings without connecting
        this.host = hostInput;
        this.port = portNum;
        this.connected = true;

        // Store settings in ClientUI for later use
        ClientUI.serverHost = hostInput;
        ClientUI.serverPort = portNum;

        statusLabel.setText("Settings saved!");
        statusLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 12;");

        // Navigate to Login UI
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

        javafx.application.Platform.runLater(() -> {
            try {
                FXMLLoader loader = ViewLoader.fxml("UserLoginUIView.fxml");
                Parent root = loader.load();

                stage.setTitle("Bistro – User Login");
                stage.setScene(SceneUtil.createStyledScene(root));
                stage.setResizable(true);
                stage.show();

            } catch (Exception e) {
                e.printStackTrace();
                statusLabel.setText("Failed to load Login UI: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
            }
        });
    }

    /**
     * Terminates the application.
     * <p>
     * Since this is the initial screen, clicking "Cancel" implies the user 
     * does not wish to proceed with the application at all.
     *
     * @param event The button click event.
     */
    @FXML
    private void onCancelClicked(ActionEvent event) {
        javafx.application.Platform.exit();
        System.exit(0);
    }

    /**
     * @return {@code true} if settings were successfully saved (logically connected), {@code false} otherwise.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * @return The configured host address (e.g., "127.0.0.1").
     */
    public String getHost() {
        return host;
    }

    /**
     * @return The configured port number.
     */
    public int getPort() {
        return port;
    }
}
