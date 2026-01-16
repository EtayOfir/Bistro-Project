package ClientGUI.controller;

import ClientGUI.util.ViewLoader;
import ClientGUI.util.SceneUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.Node;



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

        }else if (response.equals("RESERVATION_ALREADY_USED")) {
            showAlert(AlertType.WARNING, "Reservation Closed", "Duplicate Entry",
                    "This reservation has already been used (Status: Arrived).\nPlease contact the hostess.");
        }
        else {
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
    
 // ==========================================
    //           Navigation Logic (Back Button)
    // ==========================================

    /** The FXML file path of the screen to return to. Default is the Login screen. */
    private String returnScreenFXML = "UserLoginUIView.fxml";

    /** The title of the window for the previous screen. */
    private String returnTitle = "Login";

    /** The username of the currently logged-in user, used to restore context. */
    private String currentUserName = "";

    /** The role of the currently logged-in user. */
    private String currentUserRole = "";

    /**
     * Sets the navigation parameters required to return to the previous screen.
     * This method should be called by the calling controller before navigating to this screen.
     *
     * @param fxml  The name of the FXML file to load when 'Back' is clicked.
     * @param title The title to set for the window upon returning.
     * @param user  The username of the active user to restore context.
     * @param role  The role of the active user.
     */
    public void setReturnPath(String fxml, String title, String user, String role) {
        this.returnScreenFXML = fxml;
        this.returnTitle = title;
        this.currentUserName = user;
        this.currentUserRole = role;
    }

    /**
     * Handles the action when the "Back" button is clicked.
     * <p>
     * This method loads the FXML file specified by {@link #returnScreenFXML},
     * retrieves its controller, and attempts to restore the user session (name/role)
     * using a switch-case structure based on the controller type.
     * Finally, it switches the current scene to the previous one.
     * </p>
     *
     * @param event The {@link ActionEvent} triggered by the button click.
     */
    @FXML
    private void onBack(ActionEvent event) {
        try {
        	FXMLLoader loader = ViewLoader.fxml(returnScreenFXML);
        	Parent root = loader.load();
        	Object controller = loader.getController();


            // Using if-else to identify controller and restore context
            if (controller instanceof ManagerUIController) {
                ((ManagerUIController) controller).setManagerName(currentUserName);
            } else if (controller instanceof RepresentativeMenuUIController) {
                ((RepresentativeMenuUIController) controller).setRepresentativeName(currentUserName);
            } else if (controller instanceof RestaurantTerminalUIController) {
                // Logic for terminal if needed (e.g., reset state)
            } else {
                // Handle unknown controller types (Log or ignore)
                System.out.println("Returning to generic screen: " + controller.getClass().getSimpleName());
            }

            // Unregister this controller from the ClientUI routing map
            ClientUIController.clearActiveReceiveTableController();

            Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
            stage.setTitle(returnTitle);
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
