package ClientGUI;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller for the "Receive Table" UI screen.
 *
 * <p><b>Purpose:</b> Allows a customer to enter a confirmation code and receive an assigned table
 * (if available) based on server-side logic.</p>
 *
 * <p><b>Asynchronous design note:</b>
 * Server responses arrive asynchronously via OCSF. This controller does not read a "last message"
 * from a global place. Instead, it exposes a callback method
 * {@link #onReceiveTableResponse(String)} which is invoked by {@link ClientUIController}
 * after routing the incoming server message.</p>
 *
 * <p><b>Protocol message sent:</b>
 * <ul>
 *   <li>{@code #RECEIVE_TABLE <confirmationCode>}</li>
 * </ul>
 *
 * <p><b>Expected server responses:</b>
 * <ul>
 *   <li>{@code TABLE_ASSIGNED|<tableNumber>}</li>
 *   <li>{@code NO_TABLE_AVAILABLE}</li>
 *   <li>{@code INVALID_CONFIRMATION_CODE}</li>
 * </ul>
 */
public class ReceiveTableUIController {

    @FXML
    private TextField confirmationCodeField;

    @FXML
    private Button getTableBtn;

    @FXML
    private Button lostBtn;

    @FXML
    private Button closeBtn;

    /**
     * JavaFX initialization hook.
     *
     * <p>Registers this controller as the currently active "Receive Table" screen so that
     * {@link ClientUIController} can route server responses to it.</p>
     */
    @FXML
    private void initialize() {
        ClientUIController.setActiveReceiveTableController(this);
    }

    /**
     * Triggered when the user clicks "Get a table".
     *
     * <p>Validates that the confirmation code is not empty and sends the request to the server.
     * The server response will be handled asynchronously in {@link #onReceiveTableResponse(String)}.</p>
     *
     * @param event JavaFX action event
     */
    @FXML
    void onGet(ActionEvent event) {
        String code = confirmationCodeField.getText() == null ? "" : confirmationCodeField.getText().trim();

        if (code.isEmpty()) {
            showAlert(AlertType.ERROR, "Error", "Validation Error", "Please enter a confirmation code.");
            return;
        }

        if (ClientUI.chat == null) {
            showAlert(AlertType.ERROR, "Error", "Connection Error", "Not connected to server.");
            return;
        }

        // Send request (as String protocol message)
        String request = "#RECEIVE_TABLE " + code;
        ClientUI.chat.handleMessageFromClientUI(request);
    }

    /**
     * Callback invoked by {@link ClientUIController} when a receive-table response arrives.
     *
     * <p>This method parses the server response and displays the appropriate pop-up.</p>
     *
     * @param response raw server response string
     */
    public void onReceiveTableResponse(String response) {
        if (response == null) {
            showAlert(AlertType.ERROR, "Error", "Communication Error", "No response from server.");
            return;
        }

        if (response.startsWith("TABLE_ASSIGNED|")) {
            String[] parts = response.split("\\|");
            String tableNumber = (parts.length >= 2) ? parts[1] : "?";

            showAlert(AlertType.INFORMATION, "Table Assigned", "Welcome!",
                    "Reservation found!\nYour table number is: " + tableNumber + "\nPlease follow the hostess.");

        } else if (response.equals("NO_TABLE_AVAILABLE")) {
            showAlert(AlertType.ERROR, "No Table Available", "Sorry",
                    "Currently no suitable table is available.");

        } else if (response.equals("INVALID_CONFIRMATION_CODE")) {
            showAlert(AlertType.ERROR, "Invalid Code", "Error",
                    "The confirmation code was not found.");

        } else {
            showAlert(AlertType.ERROR, "Server Error", "Error", response);
        }
    }

    /**
     * Triggered when the user clicks "Lost code".
     *
     * <p>This is a UI simulation (no server call) showing that a new code could be resent.</p>
     *
     * @param event JavaFX action event
     */
    @FXML
    void onLost(ActionEvent event) {
        showAlert(AlertType.INFORMATION, "Lost Code Simulation", "Code Resent",
                "A new confirmation code has been sent to your registered phone number/email.");
    }

    /**
     * Triggered when the user clicks "Close".
     *
     * <p>Unregisters this controller from the router and closes the window.</p>
     *
     * @param event JavaFX action event
     */
    @FXML
    void onClose(ActionEvent event) {
        ClientUIController.clearActiveReceiveTableController();
        Stage stage = (Stage) closeBtn.getScene().getWindow();
        stage.close();
    }

    /**
     * Utility method for showing pop-up alerts.
     *
     * @param type alert type
     * @param title window title
     * @param header header text
     * @param content body text
     */
    private void showAlert(AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
