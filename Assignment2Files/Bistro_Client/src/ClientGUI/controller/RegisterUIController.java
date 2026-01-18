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
import ClientGUI.util.SceneUtil;

/**
 * Controller for the "Register" screen.
 *
 * <p>This screen enables creation of new users (typically Subscribers) by an authenticated staff user
 * such as a {@code Manager} or {@code Representative}. The controller performs client-side validation,
 * constructs the registration command, sends it to the server, and displays status feedback.</p>
 *
 * <p><b>Role rules:</b>
 * <ul>
 *   <li>A {@code Representative} can create only {@code Subscriber} users (role is forced).</li>
 *   <li>A {@code Manager} can create {@code Manager}, {@code Representative}, or {@code Subscriber} users.</li>
 * </ul>
 * </p>
 */
public class RegisterUIController {

    @FXML private TextField fullNameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private Label statusLabel;

    private String currentUserName;   // creator username
    private String currentUserRole;   // "Manager" / "Representative"

    private String returnFxml = "ManagerUI.fxml";
    private String returnTitle = "Dashboard";

    /**
     * Sets the current user context (creator identity and role) and configures the role drop-down accordingly.
     *
     * <p>Behavior:
     * <ul>
     *   <li>If creator is {@code Representative}: role list is locked to {@code Subscriber}.</li>
     *   <li>If creator is {@code Manager}: role list allows {@code Manager}, {@code Representative}, {@code Subscriber}.</li>
     *   <li>Otherwise: role list is locked to {@code Subscriber}.</li>
     * </ul>
     * </p>
     *
     * @param userName the creator username (may be {@code null})
     * @param role     the creator role (may be {@code null})
     */
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

    /**
     * Sets the navigation return path (screen + title) and also applies the user context.
     *
     * @param returnFxml  FXML file name to return to (e.g., {@code "ManagerUI.fxml"})
     * @param returnTitle title to set on the stage when returning
     * @param userName    the creator username to restore on the destination screen
     * @param role        the creator role to restore on the destination screen
     */
    public void setReturnPath(String returnFxml, String returnTitle, String userName, String role) {
        this.returnFxml = returnFxml;
        this.returnTitle = returnTitle;
        setUserContext(userName, role);
    }

    /**
     * Handles the "Register" button click.
     *
     * <p>Validates all form fields, enforces role rules (Representative -> Subscriber only),
     * builds the server command, sends it through {@code ClientUI.chat}, and updates the UI status label.</p>
     *
     * <p>Phone format validation is based on {@code ###-#######} (10 digits total).</p>
     *
     * @param event JavaFX action event
     */
    @FXML
    void onRegister(ActionEvent event) {
        String fullName = safe(fullNameField.getText());
        String phone = safe(phoneField.getText());
        String email = safe(emailField.getText());
        String username = safe(usernameField.getText());
        String password = safe(passwordField.getText());
        String targetRole = roleCombo.getValue();

        if (fullName.isEmpty()) {
            statusLabel.setText("Full Name is missing.");
            return;
        }

        if (phone.isEmpty()) {
            statusLabel.setText("Phone Number is missing.");
            return;
        }

        if (email.isEmpty()) {
            statusLabel.setText("Email is missing.");
            return;
        }

        if (username.isEmpty()) {
            statusLabel.setText("Username is missing.");
            return;
        }

        if (password.isEmpty()) {
            statusLabel.setText("Password is missing.");
            return;
        }

        if (targetRole == null) {
            statusLabel.setText("Role is not selected.");
            return;
        }

        if (!phone.matches("\\d{3}-\\d{7}")) {
            statusLabel.setText("Phone number must contain 10 digits.");
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

    /**
     * Placeholder method for sending messages to the server.
     *
     * <p><b>Note:</b> This method is not used by the current controller implementation because it uses
     * {@code ClientUI.chat.handleMessageFromClientUI(msg)} directly. Keep it only if you plan to refactor
     * the controller to inject a client/network layer.</p>
     *
     * @param msg the message/command to send
     * @throws Exception if sending fails (in real implementation)
     * @throws UnsupportedOperationException always, until connected to real networking code
     */
    private void sendToServer(String msg) throws Exception {
        // TODO: put your real client instance here
        throw new UnsupportedOperationException(
                "Connect sendToServer(msg) to your client networking class (sendToServer/accept)."
        );
    }

    /**
     * Handles the "Return" button click.
     *
     * <p>Loads the configured return screen, restores the creator name on the destination controller
     * (Manager or Representative menu), and switches the current scene back.</p>
     *
     * @param event JavaFX action event
     */
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
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
