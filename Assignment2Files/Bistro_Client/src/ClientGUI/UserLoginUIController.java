package ClientGUI;

import java.net.URL;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller for the user login screen.
 * Updated to remove Role selection (ComboBox).
 */
public class UserLoginUIController {

    // FXML-bound UI fields
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    // Optional server settings section
    @FXML private VBox serverSettingsBox;
    @FXML private TextField hostField;
    @FXML private TextField portField;

    /**
     * Initializes the login screen.
     */
    @FXML
    private void initialize() {
        statusLabel.setText("");

        if (serverSettingsBox != null) {
            serverSettingsBox.setVisible(false);
            serverSettingsBox.setManaged(false);
        }
    }

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
     * Handles the login button click.
     */
    @FXML
    private void onLoginClicked(ActionEvent event) {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();

        // 1. Hardcoded role since ComboBox was removed. 
        // Logic: If you want the server to decide the role, you need to change the protocol 
        // to send just LOGIN|user|pass and wait for a response.
        // For now, we assume "Customer" to prevent crashes.
        String role = "Customer"; 

        // Basic validation
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter username and password.");
            return;
        }

        // Ensure shared connection exists
        if (ClientUI.chat == null) {
            statusLabel.setText("Not connected to server (ClientUI.chat is null).");
            return;
        }

        // Send IDENTIFY message
        ClientUI.chat.handleMessageFromClientUI("IDENTIFY|" + username + "|" + role);

        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // Open Client UI
            URL fxmlLocation = getClass().getResource("ClientUIView.fxml");
            if (fxmlLocation == null) {
                statusLabel.setText("Cannot find ClientUIView.fxml.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root = loader.load();

            ClientUIController controller = loader.getController();
            controller.setUserContext(username, role);

            stage.setTitle("Reservation Client - " + username);
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Failed to open screen: " + e.getMessage());
        }
    }

    /**
     * Handler for the "Restaurant Terminal" button.
     */
    @FXML
    private void onTerminal(ActionEvent event) {
        System.out.println("Restaurant Terminal clicked");
        // TODO: Load HostDashboard or a specific Login for Managers here
        /*
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("HostDashboard.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Host Dashboard");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
        */
    }

    /**
     * Handler for the "Login as guest" button.
     */
    @FXML
    private void onLoginGuest(ActionEvent event) {
        System.out.println("Login as Guest clicked");
        // TODO: Implement guest logic (e.g., skip validation, set role="Guest")
    }

    @FXML
    private void onExitClicked(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
        System.exit(0);
    }
}