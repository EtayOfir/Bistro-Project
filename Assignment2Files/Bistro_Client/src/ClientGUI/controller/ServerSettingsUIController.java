package ClientGUI.controller;

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
 * Controller for the Server Settings window.
 * Allows users to configure the server host and port before connecting.
 */
public class ServerSettingsUIController {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private Label statusLabel;

    private String host;
    private int port;
    private boolean connected = false;

    @FXML
    private void initialize() {
        // Set default values
        hostField.setText("localhost");
        portField.setText("5555");
        statusLabel.setText("");
    }

    /**
     * Handles the Connect button click.
     * Validates the input and attempts to connect to the server.
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

        // Try to connect
        try {
            statusLabel.setText("Connecting...");
            statusLabel.setStyle("-fx-text-fill: #2563eb; -fx-font-size: 12;");
            
            // Initialize or reconnect the client
            if (ClientUI.chat != null) {
                try {
                    ClientUI.chat.closeConnection();
                } catch (Exception ex) {
                    // Ignore errors closing existing connection
                }
            }
            
            ClientUI.chat = new client.ChatClient(hostInput, portNum, new ClientMessageRouter());
            ClientUI.chat.openConnection();
            
            // Store the settings
            this.host = hostInput;
            this.port = portNum;
            this.connected = true;

            statusLabel.setText("Connected successfully!");
            statusLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 12;");

            // Navigate to Login UI after successful connection
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            javafx.application.Platform.runLater(() -> {
                try {
                	FXMLLoader loader = ViewLoader.fxml("UserLoginUIView.fxml");
                    Parent root = loader.load();

                    stage.setTitle("Bistro – User Login");
                    stage.setScene(new Scene(root));
                    stage.setResizable(true);
                    stage.show();

                } catch (Exception e) {
                    e.printStackTrace();
                    statusLabel.setText("Failed to load Login UI: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
                }
            });


        } catch (Exception e) {
            statusLabel.setText("Connection failed: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
            this.connected = false;
        }
    }

    /**
     * Handles the Cancel button click.
     * Exits the application since this is the entry screen.
     */
    @FXML
    private void onCancelClicked(ActionEvent event) {
        javafx.application.Platform.exit();
        System.exit(0);
    }

    public boolean isConnected() {
        return connected;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
