package ClientGUI;

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
import javafx.stage.Stage;

public class UserLoginUIController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        roleCombo.getItems().addAll(
                "Manager",        // מנהל
                "Representative", // נציג / מארח
                "Subscriber",     // מנוי
                "Customer"        // לקוח רגיל
        );
        roleCombo.getSelectionModel().select("Customer");
        statusLabel.setText("");
    }

    @FXML
    private void onLoginClicked(ActionEvent event) {
        String username = (usernameField.getText() == null) ? "" : usernameField.getText().trim();
        String password = (passwordField.getText() == null) ? "" : passwordField.getText().trim();
        String role     = roleCombo.getValue();

        // validations (כמו שהיה)
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter username and password.");
            return;
        }
        if (role == null || role.isBlank()) {
            statusLabel.setText("Please choose a role.");
            return;
        }

        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // ✅ אם נכנס כ-Manager או Representative -> Dashboard של מארח/נציג
            if ("Manager".equalsIgnoreCase(role) || "Representative".equalsIgnoreCase(role)) {

                FXMLLoader loader = new FXMLLoader(getClass().getResource("HostDashboard.fxml"));
                Parent root = loader.load();

                HostDashboardController controller = loader.getController();
                controller.setUserContext(username, role);

                // אם בהמשך תוסיף לדשבורד חיבור לשרת, תוכל להפעיל כאן:
                // controller.initClient("localhost", 5555);

                stage.setTitle("Host Dashboard - " + role + " (" + username + ")");
                stage.setScene(new Scene(root));
                stage.show();
                return;
            }

            // ✅ כל השאר -> המסך הרגיל (כמו שהיה אצלך)
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ClientUIView.fxml"));
            Parent root = loader.load();

            ClientUIController controller = loader.getController();
            controller.setUserContext(username, role);
            controller.initClient("localhost", 5555);

            stage.setTitle("Reservation Client - " + role + " (" + username + ")");
            stage.setScene(new Scene(root));
            stage.show();

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
}
