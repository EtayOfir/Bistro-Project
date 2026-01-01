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
 * <p>
 * This controller handles:
 * <ul>
 *   <li>User credentials input (username, password)</li>
 *   <li>User role selection</li>
 *   <li>Optional server and database configuration</li>
 *   <li>Navigation to the appropriate UI based on the selected role</li>
 * </ul>
 * </p>
 *
 * <p>
 * Supported navigation:
 * <ul>
 *   <li>Manager / Representative → Host dashboard</li>
 *   <li>Subscriber / Customer → Client reservation UI</li>
 * </ul>
 * </p>
 */
public class UserLoginUIController {

    /** Default server host if not provided by the user. */
    private static final String DEFAULT_HOST = "localhost";

    /** Default server port if not provided by the user. */
    private static final int DEFAULT_PORT = 5555;

    /** Default database username. */
    private static final String DEFAULT_DB_USER = "root";

    /** Default database password. */
    private static final String DEFAULT_DB_PASSWORD = "";

    // ===== FXML-bound UI fields =====

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private Label statusLabel;

    // Optional server settings section
    @FXML private VBox serverSettingsBox;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField dbUserField;
    @FXML private PasswordField dbPasswordField;

    /**
     * Initializes the login screen.
     *
     * <p>
     * Populates the role selection combo box and hides
     * the advanced server settings panel by default.
     * </p>
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
     * @param event the action event triggered by the toggle control
     */
    @FXML
    private void onServerSettingsToggle(ActionEvent event) {
        if (serverSettingsBox == null) {
            return;
        }

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
     * <p>
     * Validates user input, resolves server and database configuration,
     * and opens the appropriate screen according to the selected role.
     * </p>
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

        // Resolve server host
        String host = (hostField == null || hostField.getText().isBlank())
                ? DEFAULT_HOST
                : hostField.getText().trim();

        // Resolve server port
        int port = DEFAULT_PORT;
        if (portField != null && !portField.getText().isBlank()) {
            try {
                port = Integer.parseInt(portField.getText().trim());
                if (port <= 0 || port > 65535) {
                    statusLabel.setText("Port must be between 1 and 65535.");
                    return;
                }
            } catch (NumberFormatException e) {
                statusLabel.setText("Port must be a valid number.");
                return;
            }
        }

        // Resolve database credentials
        String dbUser = (dbUserField == null || dbUserField.getText().isBlank())
                ? DEFAULT_DB_USER
                : dbUserField.getText().trim();

        String dbPass = (dbPasswordField == null)
                ? DEFAULT_DB_PASSWORD
                : dbPasswordField.getText();

        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // === Manager / Representative dashboard ===
            if ("Manager".equalsIgnoreCase(role) || "Representative".equalsIgnoreCase(role)) {

                FXMLLoader loader = new FXMLLoader(getClass().getResource("HostDashboard.fxml"));
                Parent root = loader.load();

                HostDashboardController controller = loader.getController();
                controller.setUserContext(username, role);
                controller.initClient(host, port);

                stage.setTitle("Host Dashboard - " + role + " (" + username + ")");
                stage.setScene(new Scene(root));
                stage.show();
                return;
            }

            // === Regular client UI ===
            URL fxmlLocation = getClass().getResource("ClientUIView.fxml");
            if (fxmlLocation == null) {
                statusLabel.setText("Cannot find ClientUIView.fxml.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root = loader.load();

            ClientUIController controller = loader.getController();
            controller.setUserContext(username, role);
            controller.setDatabaseCredentials(dbUser, dbPass);
            controller.initClient(host, port);

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
     * <p>
     * Closes the application window and terminates the JVM.
     * </p>
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
