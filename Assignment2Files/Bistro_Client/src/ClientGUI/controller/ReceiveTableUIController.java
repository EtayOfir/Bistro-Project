package ClientGUI.controller;

import ClientGUI.util.ViewLoader;
import ClientGUI.util.SceneUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import entities.Subscriber;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import java.time.LocalDate;
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
    
    @FXML private Label lblSelectRes;
    @FXML private TableView<ResItem> reservationsTable;
    @FXML private TableColumn<ResItem, String> colTime;
    @FXML private TableColumn<ResItem, Integer> colGuests;
    @FXML private TableColumn<ResItem, String> colStatus;
    @FXML private TableColumn<ResItem, String> colCode;

    /**
     * The currently logged-in subscriber (if any).
     * Used to fetch and display relevant reservations for quick selection.
     */
    private Subscriber currentSubscriber;
    
    /**
     * The data model for the reservations table view.
     */
    private ObservableList<ResItem> tableData = FXCollections.observableArrayList();

    /**
     * A lightweight data model class representing a single reservation row in the UI table.
     * <p>
     * Wraps raw data in JavaFX {@link Property} objects to enable automatic binding 
     * with the {@link TableView}.
     */
    public static class ResItem {
        private final SimpleStringProperty time;
        private final SimpleIntegerProperty guests;
        private final SimpleStringProperty status;
        private final SimpleStringProperty code;

        public ResItem(String time, int guests, String status, String code) {
            this.time = new SimpleStringProperty(time);
            this.guests = new SimpleIntegerProperty(guests);
            this.status = new SimpleStringProperty(status);
            this.code = new SimpleStringProperty(code);
        }

        public String getTime() { return time.get(); }
        public int getGuests() { return guests.get(); }
        public String getStatus() { return status.get(); }
        public String getCode() { return code.get(); }

        public SimpleStringProperty timeProperty() { return time; }
        public SimpleIntegerProperty guestsProperty() { return guests; }
        public SimpleStringProperty statusProperty() { return status; }
        public SimpleStringProperty codeProperty() { return code; }
    }
    
    /**
     * JavaFX initialization hook.
     * <p>
     * <b>Key Actions:</b>
     * <ul>
     * <li>Registers this controller instance with {@link ClientUIController} to receive asynchronous server responses.</li>
     * <li>Configures the TableView columns.</li>
     * <li>Adds a listener to the table selection model: selecting a row automatically 
     * populates the {@code confirmationCodeField}.</li>
     * <li><b>UI State:</b> Initially hides the reservations table (it is only revealed for Subscribers).</li>
     * </ul>
     */
    @FXML
    private void initialize() {
        ClientUIController.setActiveReceiveTableController(this);
        if (reservationsTable != null) {
            colTime.setCellValueFactory(cell -> cell.getValue().timeProperty());
            colGuests.setCellValueFactory(cell -> cell.getValue().guestsProperty().asObject());
            colStatus.setCellValueFactory(cell -> cell.getValue().statusProperty());
            colCode.setCellValueFactory(cell -> cell.getValue().codeProperty());

            reservationsTable.setItems(tableData);

            reservationsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    confirmationCodeField.setText(newVal.getCode());
                }
            });

            reservationsTable.setVisible(false);
            reservationsTable.setManaged(false);
            if (lblSelectRes != null) {
                lblSelectRes.setVisible(false);
                lblSelectRes.setManaged(false);
            }
        }
    }
    
    /**
     * Sets the current subscriber context and initiates data loading.
     * <p>
     * If a valid subscriber is passed:
     * <ol>
     * <li>The UI updates to reveal the reservations table and label.</li>
     * <li>A request is sent to the server ({@code #GET_SUBSCRIBER_DATA}) to fetch relevant booking history.</li>
     * </ol>
     * If the user is a guest (subscriber is null), the table remains hidden.
     *
     * @param sub The subscriber object containing ID and personal details.
     */
    public void setSubscriber(Subscriber sub) {
        this.currentSubscriber = sub;

        if (sub != null) {
            System.out.println("DEBUG: Subscriber set in ReceiveTable: " + sub.getUserName());

            if (reservationsTable != null) {
                reservationsTable.setVisible(true);
                reservationsTable.setManaged(true);
                lblSelectRes.setVisible(true);
                lblSelectRes.setManaged(true);
                reservationsTable.setPlaceholder(new Label("Loading reservations..."));
            }

            if (ClientUI.chat != null) {
                ClientUI.chat.handleMessageFromClientUI("#GET_SUBSCRIBER_DATA " + sub.getSubscriberId());
            }
        }
    }
    
    /**
     * Processes the subscriber's data received from the server.
     * <p>
     * <b>Protocol Format:</b>
     * {@code ...|ACTIVE:date,time,diners,code,status;...}
     * <p>
     * <b>Filtering Logic:</b>
     * The method parses the "ACTIVE" section and filters for reservations that match <b>today's date</b>
     * and have a valid status ("Confirmed" or "Late").
     * <p>
     * Valid reservations are added to {@link #tableData}, which updates the UI table immediately.
     *
     * @param msg The raw protocol string containing the subscriber's full history.
     */
    public void onSubscriberDataReceived(String msg) {
        Platform.runLater(() -> {
            try {
                tableData.clear();

                // Format: ...|ACTIVE:date,time,diners,code,status;...
                String[] sections = msg.split("\\|");
                String activeSection = "";

                for (String sec : sections) {
                    if (sec.startsWith("ACTIVE:")) {
                        activeSection = sec.substring(7);
                        break;
                    }
                }

                if (activeSection.equals("EMPTY") || activeSection.isEmpty()) {
                    reservationsTable.setPlaceholder(new Label("No reservations for today"));
                    return;
                }

                LocalDate today = LocalDate.now();

                String[] rows = activeSection.split(";");
                for (String row : rows) {
                    String[] cols = row.split(",");
                    // cols: [0]Date, [1]Time, [2]Diners, [3]Code, [4]Status
                    if (cols.length >= 5) {
                        LocalDate resDate = LocalDate.parse(cols[0]);
                        String status = cols[4];

                        if (resDate.equals(today) && 
                           (status.equalsIgnoreCase("Confirmed") || status.equalsIgnoreCase("Late"))) {

                            String time = cols[1].substring(0, 5); 
                            int diners = Integer.parseInt(cols[2]);
                            String code = cols[3];

                            tableData.add(new ResItem(time, diners, status, code));
                        }
                    }
                }

                if (tableData.isEmpty()) {
                    reservationsTable.setPlaceholder(new Label("No reservations for today"));
                } 

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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
        else if (response.equals("RESERVATION_NOT_FOR_TODAY")) {
            showAlert(AlertType.WARNING, "Wrong Date", "Not for Today",
                    "This reservation is not scheduled for today.");
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
