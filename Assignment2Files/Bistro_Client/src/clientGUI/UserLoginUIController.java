package ClientGUI;

import java.net.URL;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller for the user login screen.
 *
 * <p>This controller handles:
 * <ul>
 *   <li>User credentials input (username, password)</li>
 *   <li>User role selection</li>
 *   <li>Optional database credentials input (kept for compatibility)</li>
 *   <li>Navigation to the appropriate UI based on the selected role</li>
 * </ul>
 * </p>
 *
 * <p><b>Networking note:</b>
 * This controller does <b>not</b> create or initialize a {@code ChatClient}.
 * The application bootstraps a single shared connection in {@link ClientUI},
 * and all controllers use {@link ClientUI#chat} to communicate with the server.</p>
 *
 * <p>Supported navigation:
 * <ul>
 *   <li>Manager / Representative → Host dashboard</li>
 *   <li>Subscriber / Customer → Client reservation UI</li>
 * </ul>
 * </p>
 *
 * @author Bistro Team
 */
public class UserLoginUIController {

    // FXML-bound UI fields

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private Label statusLabel;

    // Optional server settings section (kept for UI compatibility; not used in shared-connection mode)
    @FXML private VBox serverSettingsBox;
    @FXML private TextField hostField;
    @FXML private TextField portField;

    /**
     * Initializes the login screen.
     *
     * <p>Populates the role selection combo box and hides the advanced server settings
     * panel by default.</p>
     */
    @FXML
    private void initialize() {
        roleCombo.getItems().addAll(
                "Manager",
                "Representative",
                "Subscriber",
                "Customer"
        );

        roleCombo.getSelectionModel().select("Customer");
        statusLabel.setText("");

        if (serverSettingsBox != null) {
            serverSettingsBox.setVisible(false);
            serverSettingsBox.setManaged(false);
        }
    }

    /**
     * Toggles the visibility of the advanced server settings panel.
     *
     * <p><b>Note:</b> In the current architecture (shared connection created in {@link ClientUI}),
     * host/port fields are not used to create a new connection from this screen.
     * The panel is kept mainly for backward compatibility and future extension.</p>
     *
     * @param event the action event triggered by the toggle control
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
     * Handles the login button click.
     *
     * <p>Validates user input, reads user role, and opens the appropriate screen.
     * The shared server connection is assumed to be already created by {@link ClientUI}.</p>
     *
     * @param event the action event triggered by the login button
     */
    @FXML
    private void onLoginClicked(ActionEvent event) {
    	
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();
        String role = roleCombo.getValue();

        // Basic validation
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter username and password.");
            return;
        }

        if (role == null || role.isBlank()) {
            statusLabel.setText("Please choose a role.");
            return;
        }

        // Ensure shared connection exists
     // Ensure shared connection exists
        if (ClientUI.chat == null) {
            statusLabel.setText("Not connected to server (ClientUI.chat is null).");
            return;
        }

        // ✅ ADD THIS RIGHT HERE:
        ClientUI.chat.handleMessageFromClientUI("IDENTIFY|" + username + "|" + role);
        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // Manager / Representative dashboard
            if ("Manager".equalsIgnoreCase(role) || "Representative".equalsIgnoreCase(role)) {

                FXMLLoader loader = new FXMLLoader(getClass().getResource("HostDashboard.fxml"));
                Parent root = loader.load();

                HostDashboardController controller = loader.getController();
                controller.setUserContext(username, role);

                // No initClient(host, port) here in shared-connection mode

                stage.setTitle("Host Dashboard - " + role + " (" + username + ")");
                stage.setScene(new Scene(root));
                stage.show();
                return;
            }

            // Regular client UI
            URL fxmlLocation = getClass().getResource("ClientUIView.fxml");
            if (fxmlLocation == null) {
                statusLabel.setText("Cannot find ClientUIView.fxml.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root = loader.load();

            ClientUIController controller = loader.getController();
            controller.setUserContext(username, role);

            // No initClient(host, port) here in shared-connection mode

            stage.setTitle("Reservation Client - " + role + " (" + username + ")");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Failed to open screen: " + e.getMessage());
        }
    }

    /**
     * Handles the exit button click.
     *
     * <p>Closes the application window and terminates the JVM.</p>
     *
     * @param event the action event triggered by the exit button
     */
    @FXML
    private void onExitClicked(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
        System.exit(0);
    }
}
