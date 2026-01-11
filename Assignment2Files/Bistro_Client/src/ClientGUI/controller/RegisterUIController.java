package ClientGUI.controller;

import javafx.collections.FXCollections;
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

import java.io.IOException;

import ClientGUI.util.ViewLoader;

public class RegisterUIController {

    @FXML private TextField fullNameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;   // ✅ NEW
    @FXML private ComboBox<String> roleCombo;
    @FXML private Label statusLabel;

    private String currentUserName;   // creator username
    private String currentUserRole;   // "Manager" / "Representative"

    private String returnFxml = "ManagerUI.fxml";
    private String returnTitle = "Dashboard";

    public void setUserContext(String userName, String role) {
        this.currentUserName = userName;
        this.currentUserRole = role;

        if ("Representative".equalsIgnoreCase(role)) {
            roleCombo.setItems(FXCollections.observableArrayList("Subscriber"));
            roleCombo.setValue("Subscriber");
            roleCombo.setDisable(true);
        } else if ("Manager".equalsIgnoreCase(role)) {
            roleCombo.setItems(FXCollections.observableArrayList("Manager", "Representative", "Subscriber"));
            roleCombo.setValue("Subscriber");
            roleCombo.setDisable(false);
        } else {
            roleCombo.setItems(FXCollections.observableArrayList("Subscriber"));
            roleCombo.setValue("Subscriber");
            roleCombo.setDisable(true);
        }
    }

    public void setReturnPath(String returnFxml, String returnTitle, String userName, String role) {
        this.returnFxml = returnFxml;
        this.returnTitle = returnTitle;
        setUserContext(userName, role);
    }

    @FXML
    void onRegister(ActionEvent event) {
        String fullName = safe(fullNameField.getText());
        String phone = safe(phoneField.getText());
        String email = safe(emailField.getText());
        String username = safe(usernameField.getText());
        String password = safe(passwordField.getText());
        String targetRole = roleCombo.getValue();

        if (fullName.isEmpty() || phone.isEmpty() || email.isEmpty()
                || username.isEmpty() || password.isEmpty() || targetRole == null) {
            statusLabel.setText("Please fill all fields (including password).");
            return;
        }

        // Representative can only create Subscriber (force)
        if ("Representative".equalsIgnoreCase(currentUserRole)) {
            targetRole = "Subscriber";
        }

        // Build request for server (pipe format allows spaces in name)
        // REGISTER|creatorRole|FullName|Phone|Email|UserName|Password|TargetRole
     // #REGISTER creatorRole|FullName|Phone|Email|UserName|Password|TargetRole
        String payload = currentUserRole + "|" + fullName + "|" + phone + "|" + email + "|"
                + username + "|" + password + "|" + targetRole;

        String msg = "#REGISTER " + payload;


        try {
        	ClientUI.chat.handleMessageFromClientUI(msg);


            statusLabel.setText("Register request sent. Waiting for server response...");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Failed to send register request.");
        }
    }

    // 🔧 Connect this to your client networking class
    private void sendToServer(String msg) throws Exception {
        // TODO: put your real client instance here
        throw new UnsupportedOperationException(
                "Connect sendToServer(msg) to your client networking class (sendToServer/accept)."
        );
    }

    @FXML
    void onReturn(ActionEvent event) {
        try {
        	FXMLLoader loader = ViewLoader.fxml(returnFxml);
            Parent root = loader.load();
            Object controller = loader.getController();

            if (controller instanceof ManagerUIController m) {
                m.setManagerName(currentUserName);
            } else if (controller instanceof RepresentativeMenuUIController r) {
                r.setRepresentativeName(currentUserName);
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle(returnTitle);
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
