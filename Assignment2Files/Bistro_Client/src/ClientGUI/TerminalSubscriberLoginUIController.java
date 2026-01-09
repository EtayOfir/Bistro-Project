package ClientGUI;

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
        String user = usernameField.getText();
        String pass = passwordField.getText();

        if (user.isEmpty() || pass.isEmpty()) {
            errorLabel.setText("Please enter all fields");
            return;
        }

        // כאן תוכלי להוסיף בדיקה מול השרת (Server) אם הפרטים נכונים.
        // כרגע נניח שההתחברות הצליחה:
        
        if (parentController != null) {
            // מעדכן את הטרמינל שהמשתמש התחבר
            parentController.setLoggedInSubscriber(user);
        }

        // סוגר את החלונית הקופצת
        closeWindow(event);
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