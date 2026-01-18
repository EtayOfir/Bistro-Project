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
 * Controller for the User Registration screen.
 * <p>
 * This controller allows authorized users (Managers and Representatives) to register
 * new entities in the system.
 * <p>
 * <b>Role-Based Behavior:</b>
 * <ul>
 * <li><b>Manager:</b> Can register Managers, Representatives, and Subscribers.</li>
 * <li><b>Representative:</b> Can <i>only</i> register new Subscribers. The role selection is locked.</li>
 * </ul>
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
     * Initializes the controller with the current user's context and adjusts UI permissions.
     * <p>
     * This method dynamically configures the "Role" combo box:
     * <ul>
     * <li>If the user is a <b>Representative</b>, the list is restricted to "Subscriber" and disabled.</li>
     * <li>If the user is a <b>Manager</b>, all roles are available and the list is enabled.</li>
     * </ul>
     *
     * @param userName The username of the person performing the registration.
     * @param role     The role of the person performing the registration.
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
     * Sets the navigation return path and initializes the user context.
     * <p>
     * Allows this screen to be reused from different origin screens (e.g., Manager Dashboard
     * or Representative Menu) by storing the FXML path to return to.
     *
     * @param returnFxml  The filename of the previous screen.
     * @param returnTitle The window title for the previous screen.
     * @param userName    The active username.
     * @param role        The active user role.
     */
    public void setReturnPath(String returnFxml, String returnTitle, String userName, String role) {
        this.returnFxml = returnFxml;
        this.returnTitle = returnTitle;
        setUserContext(userName, role);
    }

    /**
     * Handles the "Register" button click.
     * <p>
     * <b>Validation Sequence:</b>
     * <ol>
     * <li>Checks that all mandatory fields are not empty.</li>
     * <li>Validates the phone number format (must match {@code XXX-XXXXXXX}).</li>
     * </ol>
     * <p>
     * <b>Protocol Construction:</b>
     * Sends a command to the server in the following pipe-delimited format:
     * <br>{@code #REGISTER <CreatorRole>|<FullName>|<Phone>|<Email>|<UserName>|<Password>|<TargetRole>}
     *
     * @param event The button click event.
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


            statusLabel.setText("Register completed");
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

    /**
     * Handles the navigation back to the previous screen.
     * <p>
     * Uses the stored {@link #returnFxml} path to load the scene.
     * It identifies the type of the target controller (Manager vs Representative)
     * via {@code instanceof} checks to correctly restore the user session/name.
     *
     * @param event The button click event.
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

    /**
     * Utility method to safely trim strings.
     *
     * @param s The input string.
     * @return The trimmed string, or an empty string if input is null.
     */
    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
