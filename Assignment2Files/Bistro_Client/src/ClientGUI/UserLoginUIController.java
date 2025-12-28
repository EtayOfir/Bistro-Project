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

public class UserLoginUIController {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5555;
    private static final String DEFAULT_DB_USER = "root";
    private static final String DEFAULT_DB_PASSWORD = "";

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private Label statusLabel;
    @FXML private VBox serverSettingsBox;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField dbUserField;
    @FXML private PasswordField dbPasswordField;

    @FXML
    private void initialize() {
        roleCombo.getItems().addAll(
                "Manager",        // מנהל
                "Representative", // נציג
                "Subscriber",     // מנוי
                "Customer"        // לקוח רגיל
        );
        roleCombo.getSelectionModel().select("Customer");
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

        // Ask the current stage to recalc its size so the panel fits.
        Node source = (event != null) ? (Node) event.getSource() : null;
        if (source != null && source.getScene() != null && source.getScene().getWindow() instanceof Stage) {
            Stage stage = (Stage) source.getScene().getWindow();
            stage.sizeToScene();
        }
    }

    @FXML
    private void onLoginClicked(ActionEvent event) {
        String username = (usernameField.getText() == null) ? "" : usernameField.getText().trim();
        String password = (passwordField.getText() == null) ? "" : passwordField.getText().trim();
        String role     = roleCombo.getValue();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter username and password.");
            return;
        }
        if (role == null || role.isBlank()) {
            statusLabel.setText("Please choose a role.");
            return;
        }

        String hostInput = hostField != null && hostField.getText() != null ? hostField.getText().trim() : "";
        String host = hostInput.isEmpty() ? DEFAULT_HOST : hostInput;

        String portInput = portField != null && portField.getText() != null ? portField.getText().trim() : "";
        int port = DEFAULT_PORT;
        if (!portInput.isEmpty()) {
            try {
                port = Integer.parseInt(portInput);
                if (port <= 0 || port > 65535) {
                    statusLabel.setText("Port must be 1-65535.");
                    return;
                }
            } catch (NumberFormatException ex) {
                statusLabel.setText("Port must be a number.");
                return;
            }
        }

        String dbUserInput = dbUserField != null && dbUserField.getText() != null ? dbUserField.getText().trim() : "";
        String dbPassInput = dbPasswordField != null && dbPasswordField.getText() != null ? dbPasswordField.getText().trim() : "";
        String dbUser = dbUserInput.isEmpty() ? DEFAULT_DB_USER : dbUserInput;
        String dbPass = dbPassInput.isEmpty() ? DEFAULT_DB_PASSWORD : dbPassInput;

        try {
            URL fxmlLocation = getClass().getResource("ClientUIView.fxml");
            if (fxmlLocation == null) {
                statusLabel.setText("Cannot find ClientUIView.fxml (check resources)");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root = loader.load();

            ClientUIController controller = loader.getController();
            controller.setUserContext(username, role);
            controller.setDatabaseCredentials(dbUser, dbPass);
            controller.initClient(host, port);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Reservation Client - " + role + " (" + username + ")");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Failed to open main screen: " + e.getMessage());
        }
    }

    @FXML
    private void onExitClicked(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
        System.exit(0);
    }
}