package ClientGUI.controller;

import java.io.IOException;
import java.net.URL;

import ClientGUI.util.ViewLoader;
import client.ChatClient;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller for the main User Login screen.
 * <p>
 * Handles authentication, role-based navigation, and server settings.
 * </p>
 */
public class UserLoginUIController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    // Buttons defined in FXML
    @FXML private Button resTerminal;
    @FXML private Button loginGuest;

    // Server Settings (Preserved from your local version)
    @FXML private VBox serverSettingsBox;
    @FXML private TextField hostField;
    @FXML private TextField portField;

    /**
     * Initializes the controller class.
     */
    @FXML
    private void initialize() {
        if (statusLabel != null) statusLabel.setText("");

        // Initialize Server Settings visibility
        if (serverSettingsBox != null) {
            serverSettingsBox.setVisible(false);
            serverSettingsBox.setManaged(false);
        }
        
        // Set defaults if empty
        if (hostField != null && hostField.getText().isEmpty()) hostField.setText("localhost");
        if (portField != null && portField.getText().isEmpty()) portField.setText("5555");
    }

    /**
     * Toggles the visibility of the server settings panel.
     */
    @FXML
    private void onServerSettingsToggle(ActionEvent event) {
        if (serverSettingsBox == null) return;

        boolean show = !serverSettingsBox.isVisible();
        serverSettingsBox.setVisible(show);
        serverSettingsBox.setManaged(show);

        // Adjust window size
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        stage.sizeToScene();
    }

    /**
     * Handles the main Login button click.
     * Uses #LOGIN protocol to validate user with server.
     */
    @FXML
    private void onLoginClicked(ActionEvent event) {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();

        // 1. Input Validation
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter username and password.");
            return;
        }

        // 2. Connect ONLY on Login click (if not connected yet)
        try {
            if (ClientUI.chat == null) {
                // Use settings from fields if available, otherwise default
                String host = (hostField != null && !hostField.getText().isEmpty()) ? hostField.getText() : "localhost";
                int port = 5555;
                try {
                    if (portField != null && !portField.getText().isEmpty()) {
                        port = Integer.parseInt(portField.getText());
                    }
                } catch (NumberFormatException e) {
                    showError("Invalid Port Number. Using 5555.");
                }

                ClientUI.chat = new ChatClient(host, port, new ClientMessageRouter());
                System.out.println("✅ Connected to server (" + host + ":" + port + ") on Login click");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to connect to server: " + e.getMessage());
            return;
        }

        try {
            // 3. Send Login Command (#LOGIN <user> <pass>)
            String cmd = "#LOGIN " + username + " " + password;
            ClientUI.chat.handleMessageFromClientUI(cmd);

            // 4. Wait for Server Response
            String response = ClientUI.chat.waitForMessage(); 

            // 5. Handle Response
            if (response == null) {
                showError("Server timed out. No response received.");
                return;
            }

            if (response.startsWith("LOGIN_FAILED")) {
                showError("Invalid username or password.");
                return;
            }

            if (response.startsWith("LOGIN_SUCCESS")) {
                // Format: LOGIN_SUCCESS|<Role>
                String[] parts = response.split("\\|");
                String role = (parts.length > 1) ? parts[1] : "";

                // ✅ Tell the server who logged in (for server table + logs)
                ClientUI.chat.handleMessageFromClientUI("IDENTIFY|" + username + "|" + role);

                navigateBasedOnRole(event, username, role);

            } else {
                showError(response);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error during login: " + e.getMessage());
        }
    }

    /**
     * Helper to navigate to the correct dashboard based on the Role.
     */
    private void navigateBasedOnRole(ActionEvent event, String username, String role) {
        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            FXMLLoader loader;
            Parent root;

            // --- MANAGER ---
            if ("Manager".equalsIgnoreCase(role)) {
            	loader = ViewLoader.fxml("ManagerUI.fxml");
                root = loader.load();

                // ManagerUIController controller = loader.getController();
                // controller.setManagerName(username);

                stage.setTitle("Manager Dashboard - " + username);
                stage.setScene(new Scene(root));
                stage.show();
            }
            // --- REPRESENTATIVE ---
            else if ("Representative".equalsIgnoreCase(role)) {
            	loader = ViewLoader.fxml("RepresentativeMenuUI.fxml");
                root = loader.load();

                stage.setTitle("Representative Dashboard - " + username);
                stage.setScene(new Scene(root));
                stage.show();
            }
            // --- SUBSCRIBER ---
            else if ("Subscriber".equalsIgnoreCase(role)) {
            	loader = ViewLoader.fxml("LoginSubscriberUI.fxml");
                root = loader.load();

                stage.setTitle("Subscriber Menu - " + username);
                stage.setScene(new Scene(root));
                stage.show();
            }
            // --- FALLBACK / CUSTOMER ---
            else {
            	loader = ViewLoader.fxml("ClientUIView.fxml");
                root = loader.load();

                ClientUIController controller = loader.getController();
                controller.setUserContext(username, role);

                stage.setTitle("Reservation Client - " + username);
                stage.setScene(new Scene(root));
                stage.show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Navigation failed: " + e.getMessage());
        }
    }

    /**
     * Handles the "Restaurant Terminal" button click.
     */
    @FXML
    private void onTerminal(ActionEvent event) {
        if (ClientUI.chat == null) {
            statusLabel.setText("Not connected to server.");
            // Optional: Auto-connect or show error
            return;
        }

        try {
        	FXMLLoader loader = ViewLoader.fxml("RestaurantTerminalUI.fxml");
            Parent root = loader.load();
            
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Restaurant Terminal");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Could not open Terminal. (Missing FXML?)\n" + e.getMessage());
        }
    }
    
    /**
     * Handles the "Login as guest" button click.
     */
    @FXML
    private void onLoginGuest(ActionEvent event) {
        try {
        	FXMLLoader loader = ViewLoader.fxml("LoginGuestUI.fxml");
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Guest Menu");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Could not open Guest Screen. (Missing FXML?)\n" + e.getMessage());
        }
    }
    
    
    /**
     * Helper to show a popup error message.
     */
    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Login Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    @FXML
    private void onExitClicked(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
        System.exit(0);
    }
}