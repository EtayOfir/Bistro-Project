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
    @FXML private ComboBox<String> roleCombo;
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
        roleCombo.getItems().addAll(
                "Manager",
                "Representative",
                "Subscriber"
        );

        // Set default selection to Subscriber
        roleCombo.getSelectionModel().select("Subscriber");
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
        String role = roleCombo.getValue();

        // Input validation
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter username and password.");
            return;
        }

        if (role == null || role.isBlank()) {
            statusLabel.setText("Please choose a role.");
            return;
        }

        // Check server connection
        if (ClientUI.chat == null) {
            statusLabel.setText("Not connected to server (ClientUI.chat is null).");
            return;
        }

        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // 1. Manager Login
            if ("Manager".equalsIgnoreCase(role)) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("ManagerUI.fxml"));
                Parent root = loader.load();

                // Get Manager Controller and pass username
                ManagerUIController controller = loader.getController();
                controller.setManagerName(username);

                stage.setTitle("Manager Dashboard - " + username);
                stage.setScene(new Scene(root));
                stage.show();
                return;
            }

            // 2. Representative Login
            else if ("Representative".equalsIgnoreCase(role)) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("RepresentativeMenuUI.fxml"));
                Parent root = loader.load();

                // Get Representative Controller and pass username
                RepresentativeMenuUIController controller = loader.getController();
                controller.setRepresentativeName(username);

                stage.setTitle("Representative Dashboard - " + username);
                stage.setScene(new Scene(root));
                stage.show();
                return;
            }

            // 3. Subscriber Login
            else if ("Subscriber".equalsIgnoreCase(role)) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("LoginSubscriberUI.fxml"));
                Parent root = loader.load();
                
                // Get Subscriber Controller and pass username
                LoginSubscriberUIController subController = loader.getController();
                if (subController != null) {
                    subController.setSubscriberName(username);
                }

                stage.setTitle("Subscriber Menu - " + username);
                stage.setScene(new Scene(root));
                stage.show();
                return;
            }

            // 4. Fallback (should not be reached if role list is fixed, but good for safety)
            statusLabel.setText("Role not supported yet: " + role);

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Failed to open screen: " + e.getMessage());
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
}