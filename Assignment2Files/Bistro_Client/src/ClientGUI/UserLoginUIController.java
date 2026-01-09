package ClientGUI;

import java.net.URL;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/**
 * Controller for the main User Login screen.
 * <p>
 * This controller handles:
 * <ul>
 * <li>User authentication and input validation.</li>
 * <li>Navigation to specific dashboards based on role (Manager, Representative, Subscriber).</li>
 * <li>Guest login functionality.</li>
 * <li>Access to the Restaurant Terminal interface.</li>
 * </ul>
 * </p>
 *
 * @author Bistro Team
 * @version 1.3
 */
public class UserLoginUIController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @FXML private Button resTerminal;
    @FXML private Button loginGuest;
    
    @FXML private VBox serverSettingsBox;
    @FXML private TextField hostField;
    @FXML private TextField portField;

    /**
     * Initializes the controller class.
     * Sets up the role selection combo box and defaults.
     */
    @FXML
    private void initialize() {
    	
        statusLabel.setText("");

        if (serverSettingsBox != null) {
            serverSettingsBox.setVisible(false);
            serverSettingsBox.setManaged(false);
        }
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

        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        stage.sizeToScene();
    }

    /**
     * Handles the main Login button click.
     * <p>
     * Navigates to the appropriate dashboard based on the selected role:
     * <ul>
     * <li><b>Manager:</b> Navigates to {@code ManagerUI.fxml}.</li>
     * <li><b>Representative:</b> Navigates to {@code RepresentativeUI.fxml}.</li>
     * <li><b>Subscriber:</b> Navigates to {@code LoginSubscriberUI.fxml}.</li>
     * </ul>
     * </p>
     */
    @FXML
    private void onLoginClicked(ActionEvent event) {
    	String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();
        
        // Input validation
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter username and password.");
            return;
        }

        // Check server connection
        if (ClientUI.chat == null) {
            showError("Not connected to server (ClientUI.chat is null).");
            return;
        }

        try {
        	// Send Login Command to Server
            String cmd = "#LOGIN " + username + " " + password;
            ClientUI.chat.handleMessageFromClientUI(cmd);

            // Wait for Server Response
            String response = ClientUI.chat.waitForMessage();

            // Handle Response
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

                Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

                // --- Navigation Logic based on Server Response ---

                // Manager
                if ("Manager".equalsIgnoreCase(role)) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("ManagerUI.fxml"));
                    Parent root = loader.load();
                    ManagerUIController controller = loader.getController();
                    controller.setManagerName(username);
                    
                    stage.setTitle("Manager Dashboard - " + username);
                    stage.setScene(new Scene(root));
                    stage.show();
                }
                // Representative
                else if ("Representative".equalsIgnoreCase(role)) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("RepresentativeMenuUI.fxml"));
                    Parent root = loader.load();
                    RepresentativeMenuUIController controller = loader.getController();
                    controller.setRepresentativeName(username);
                    
                    stage.setTitle("Representative Dashboard - " + username);
                    stage.setScene(new Scene(root));
                    stage.show();
                }
                // Subscriber
                else if ("Subscriber".equalsIgnoreCase(role)) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("LoginSubscriberUI.fxml"));
                    Parent root = loader.load();
                    LoginSubscriberUIController controller = loader.getController();
                    if (controller != null) {
                        controller.setSubscriberName(username);
                    }
                    
                    stage.setTitle("Subscriber Menu - " + username);
                    stage.setScene(new Scene(root));
                    stage.show();
                }
                // Unknown Role
                else {
                    showError("Unknown role received: " + role);
                }

            } else {
                // Handle generic errors (e.g. ERROR|DB_NOT_READY)
            	showError(response);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error during login: " + e.getMessage());
        }
    }

    
    @FXML
    private void onExitClicked(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
        System.exit(0);
    }
    
    /**
     * Handles the "Login as guest" button click.
     * Navigates to LoginGuestUI.fxml.
     */
    @FXML
    private void onLoginGuest(ActionEvent event) {
        if (ClientUI.chat == null) {
            statusLabel.setText("Not connected to server.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("LoginGuestUI.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Guest Menu");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Failed to open guest screen: " + e.getMessage());
        }
    }

    /**
     * Handles the "Restaurant Terminal" button click.
     * Navigates to RestaurantTerminalUI.fxml.
     */
    @FXML
    private void onTerminal(ActionEvent event) {
        if (ClientUI.chat == null) {
            statusLabel.setText("Not connected to server.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("RestaurantTerminalUI.fxml"));
            Parent root = loader.load();
            
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Restaurant Terminal");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Failed to open terminal: " + e.getMessage());
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
}