package ClientGUI.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller for the popup login window displayed within the Restaurant Terminal.
 * <p>
 * This controller handles the authentication of a subscriber directly from the terminal station.
 * Unlike the main client login, this is a modal/popup interaction that allows a subscriber
 * to identify themselves to the terminal to perform actions (like claiming a reservation).
 * </p>
 */
public class TerminalSubscriberLoginUIController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    /**
     * Reference to the main Terminal Controller that opened this popup.
     * Used to pass the authenticated Subscriber object back to the main screen.
     */
    private RestaurantTerminalUIController parentController;

    /**
     * Sets the parent controller instance.
     * This allows the popup to communicate the login result back to the main terminal view.
     *
     * @param parent The instance of {@link RestaurantTerminalUIController}.
     */
    public void setParentController(RestaurantTerminalUIController parent) {
        this.parentController = parent;
    }

    /**
     * Handles the "OK" / "Login" button click event.
     * <p>
     * The process flows as follows:
     * <ol>
     * <li>Validates that username and password fields are not empty.</li>
     * <li>Sends a {@code #LOGIN} command to the server via {@link ClientUI}.</li>
     * <li><b>Waits synchronously</b> for a server response.</li>
     * <li>If login is successful ({@code LOGIN_SUCCESS}):
     * <ul>
     * <li>Parses the user details (ID, Name, Role).</li>
     * <li>Updates the server with an {@code IDENTIFY} packet.</li>
     * <li>Passes the created {@link entities.Subscriber} object to the parent controller.</li>
     * <li>Closes the popup window.</li>
     * </ul>
     * </li>
     * <li>If failed, displays an error message.</li>
     * </ol>
     *
     * @param event The ActionEvent triggered by the button click.
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
     * Handles the "Cancel" button click event.
     * Closes the popup window without performing any action.
     *
     * @param event The ActionEvent triggered by the button click.
     */
    @FXML
    void onCancel(ActionEvent event) {
        closeWindow(event);
    }

    /**
     * Helper method to close the current stage (window).
     *
     * @param event The event associated with the UI element (button) that triggered the close action.
     */
    private void closeWindow(ActionEvent event) {
        Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        stage.close();
    }
}