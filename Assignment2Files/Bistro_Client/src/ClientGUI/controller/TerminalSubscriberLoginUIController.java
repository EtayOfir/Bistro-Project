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
 * <p>
 * Responsible for collecting username/password, validating input, performing a login request
 * through the shared {@code ClientUI.chat} client, and notifying the parent terminal controller
 * upon successful authentication.
 */
public class TerminalSubscriberLoginUIController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private RestaurantTerminalUIController parentController;

    /**
     * Sets the parent controller that opened this login popup.
     * <p>
     * The parent controller will be notified upon successful login so it can update the terminal UI.
     *
     * @param parent the parent {@link RestaurantTerminalUIController} that owns this popup
     */
    public void setParentController(RestaurantTerminalUIController parent) {
        this.parentController = parent;
    }

    /**
     * Handles the OK button click event.
     * <p>
     * Validates that username and password fields are not empty, then sends a login request to the server
     * using the shared client connection ({@code ClientUI.chat}). If login succeeds, parses the server response,
     * constructs a {@code Subscriber} object, identifies the client session via an {@code IDENTIFY} message,
     * updates the parent controller with the logged-in subscriber, and closes the popup window.
     * <p>
     * In case of invalid credentials, missing connection, or unexpected errors, an appropriate message is shown
     * in {@link #errorLabel}.
     *
     * @param event the action event triggered by clicking the OK button
     */
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
            ClientUI.chat.handleMessageFromClientUI("#LOGIN " + user + " " + pass);
            String response = ClientUI.chat.waitForMessage();

            if (response != null && response.startsWith("LOGIN_SUCCESS")) {
                String[] parts = response.split("\\|");
                String role = (parts.length > 1) ? parts[1] : "Subscriber";

                int id = (parts.length > 2) ? Integer.parseInt(parts[2]) : 0;
                String fullName = (parts.length > 3) ? parts[3] : user;

                entities.Subscriber sub = new entities.Subscriber();
                sub.setSubscriberId(id);
                sub.setUserName(user);
                sub.setFullName(fullName);
                sub.setRole(role);

                
                ClientUI.chat.handleMessageFromClientUI("IDENTIFY|" + user + "|" + role);

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


    /**
     * Handles the Cancel button click event by closing the login popup window.
     *
     * @param event the action event triggered by clicking the Cancel button
     */
    @FXML
    void onCancel(ActionEvent event) {
        closeWindow(event);
    }

    /**
     * Closes the current popup window.
     *
     * @param event the action event used to locate the current {@link Stage} from the event source
     */
    private void closeWindow(ActionEvent event) {
        Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        stage.close();
    }
}