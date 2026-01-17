package ClientGUI.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller for the small popup login window inside the terminal.
 */
public class TerminalSubscriberLoginUIController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    // הפניה לקונטרולר של הטרמינל כדי שנוכל לעדכן אותו שהתחברנו
    private RestaurantTerminalUIController parentController;

    public void setParentController(RestaurantTerminalUIController parent) {
        this.parentController = parent;
    }

    @FXML
    void onOK(ActionEvent event) {
        String user = usernameField.getText().trim();
        String pass = passwordField.getText().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            errorLabel.setText("Please enter all fields");
            return;
        }

        try {
            if (ClientUI.chat == null) {
                errorLabel.setText("No connection to server");
                return;
            }

            // 1. אימות מול השרת
            ClientUI.chat.handleMessageFromClientUI("#LOGIN " + user + " " + pass);
            String response = ClientUI.chat.waitForMessage();

            if (response != null && response.startsWith("LOGIN_SUCCESS")) {
                // פירוק התשובה: LOGIN_SUCCESS|<Role>|<ID>|<Name>
                String[] parts = response.split("\\|");
                String role = (parts.length > 1) ? parts[1] : "Subscriber";

                int id = (parts.length > 2) ? Integer.parseInt(parts[2]) : 0;
                String fullName = (parts.length > 3) ? parts[3] : user;

                // --- זה החלק שהיה חסר: יצירת אובייקט והעברתו ---
                entities.Subscriber sub = new entities.Subscriber();
                sub.setSubscriberId(id);
                sub.setUserName(user);
                sub.setFullName(fullName);
                sub.setRole(role);
                
                // 2. עדכון השרת בזהות החדשה (IDENTIFY) - מתבצע מכאן!
                // כך אנחנו לא צריכים לשנות את הפונקציה באבא
                ClientUI.chat.handleMessageFromClientUI("IDENTIFY|" + user + "|" + role);

                // 3. עדכון ה-GUI בטרמינל (באמצעות הפונקציה המקורית שלא שינינו)
                if (parentController != null) {
                    parentController.setLoggedInSubscriber(sub);
                }

                closeWindow(event);
            } else {
                errorLabel.setText("Invalid username or password");
            }

        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("Login error");
        }
    }

    @FXML
    void onCancel(ActionEvent event) {
        closeWindow(event);
    }

    private void closeWindow(ActionEvent event) {
        Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        stage.close();
    }
}