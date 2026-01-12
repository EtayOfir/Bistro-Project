package ClientGUI.controller;

import java.io.IOException;

import ClientGUI.util.ViewLoader;
import client.ChatClient;
import common.ChatIF;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Main client-side controller (JavaFX) and central server-message router.
 *
 * <p>This controller has two roles:</p>
 * <ol>
 *   <li><b>Main Reservation Management UI</b>: show/update/cancel reservations from the primary screen.</li>
 *   <li><b>OCSF Message Router</b>: implements {@link ChatIF} and receives all server messages via
 *       {@link #display(String)}. It then routes messages to the currently active child controllers
 *       (e.g., {@link ReservationUIController}, {@link ReceiveTableUIController}).</li>
 * </ol>
 *
 * <p><b>Architecture note:</b>
 * OCSF communication is asynchronous. UI controllers should not "poll" for responses.
 * Instead, messages arrive via {@link #display(String)} and are dispatched based on message type.</p>
 *
 * <p><b>Networking note:</b>
 * This controller does not create a {@link ChatClient}. The application bootstraps the shared
 * client connection in {@code ClientUI}, and controllers send messages through {@link ClientUI#chat}.</p>
 *
 * @author Bistro Team
 */
public class ClientUIController implements ChatIF {

    // FXML UI Components (Main Screen)

    /** Input field for entering the Reservation ID to search. */
    @FXML private TextField reservationIdInput;

    /** Label displaying the currently loaded Reservation ID (or status like "Not Found"). */
    @FXML private Label reservationIdLabel;

    /** Editable text field for the number of guests. */
    @FXML private TextField numberOfGuestsField;

    /** Editable text field for the reservation date (Format: yyyy-MM-dd). */
    @FXML private TextField reservationDateField;

    /** Editable text field for the reservation time (Format: HH:mm:ss). */
    @FXML private TextField reservationTimeField;

    /** Button to trigger the reservation search. */
    @FXML private Button showReservation;

    /** Button to submit updates for the loaded reservation. */
    @FXML private Button update;

    /** Button to cancel a reservation. */
    @FXML private Button cancelReservation;

    /** Button to go back a reservation. */
    @FXML private Button backBtn;
    
    /** Button to close the connection and exit the application. */
    @FXML private Button exit;

    /** Text area for displaying logs, status messages, and reservation details. */
    @FXML private TextArea reservationDetailsTextArea;

    /** Button to create a new reservation (opens the Reservation UI). */
    @FXML private Button newReservationButton;

    /** Label for "Reservation Actions" section. */
    @FXML private Label reservationActionsLabel;

    // -------------------- Context / Permissions --------------------

    /** Stores the ID of the reservation currently being displayed/edited on the main screen. */
    private String currentReservationId;

    /** Currently logged in user name (for UI-only display). */
    private String loggedInUser;

    /** Currently logged in role (for UI-only permissions). */
    private String loggedInRole;

    // Active Controller Routing
    
    /** Active "New Reservation" window controller, if open. */
    private static volatile ReservationUIController activeReservationController;
    private static volatile StaffReservationUIController activeStaffReservationController;

    /** Active "Receive Table" window controller, if open. */
    private static volatile ReceiveTableUIController activeReceiveTableController;
    
    private static volatile ClientUIController mainController;

    public static ClientUIController getMainController() {
        return mainController;
    }
    
    public static ReservationUIController getActiveReservationController() {
        return activeReservationController;
    }

    public static ReceiveTableUIController getActiveReceiveTableController() {
        return activeReceiveTableController;
    }

    /**
     * Registers the currently active reservation controller (new reservation window).
     *
     * @param controller active reservation controller or {@code null}
     */
    public static void setActiveReservationController(ReservationUIController controller) {
        activeReservationController = controller;
    }

    /**
     * Clears the active reservation controller reference.
     * <p>Call when the reservation window closes.</p>
     */
    public static void clearActiveReservationController() {
        activeReservationController = null;
    }

    /**
     * Gets the currently active staff reservation controller (for creating customer reservations).
     */
    public static StaffReservationUIController getActiveStaffReservationController() {
        return activeStaffReservationController;
    }

    /**
     * Registers the currently active staff reservation controller.
     */
    public static void setActiveStaffReservationController(StaffReservationUIController controller) {
        activeStaffReservationController = controller;
    }

    /**
     * Clears the active staff reservation controller reference.
     */
    public static void clearActiveStaffReservationController() {
        activeStaffReservationController = null;
    }

    /**
     * Registers the currently active receive-table controller.
     *
     * @param controller active receive-table controller or {@code null}
     */
    public static void setActiveReceiveTableController(ReceiveTableUIController controller) {
        activeReceiveTableController = controller;
    }

    /**
     * Clears the active receive-table controller reference.
     * <p>Call when the receive-table window closes.</p>
     */
    public static void clearActiveReceiveTableController() {
        activeReceiveTableController = null;
    }
    
    
    // JavaFX lifecycle

    /**
     * JavaFX initialization hook.
     * Applies permissions based on the user context (if already set).
     */
    @FXML
    private void initialize() {
    	mainController = this;
        applyRolePermissions();
    }

    /**
     * Sets the logged-in user context (username + role) and updates UI permissions.
     *
     * @param username logged-in username
     * @param role logged-in role (e.g., Customer / Manager / Representative)
     */
    public void setUserContext(String username, String role) {
        this.loggedInUser = username;
        this.loggedInRole = role;
        applyRolePermissions();
    }

    /**
     * Applies role-based UI permissions.
     *
     * <p>Current policy:
     * <ul>
     *   <li>Customer: can open "New Reservation" flow.</li>
     *   <li>Any non-empty role: allowed to update reservation fields (your current simplified rule).</li>
     * </ul>
     *
     * <p>You can tighten it later to Manager/Representative only by restoring your original check.</p>
     */
    private void applyRolePermissions() {
        boolean canUpdate = loggedInRole != null && !loggedInRole.isBlank();
        boolean isSubscriber = loggedInRole != null && "Subscriber".equalsIgnoreCase(loggedInRole);

        if (newReservationButton != null) {
            newReservationButton.setVisible(isSubscriber);
            newReservationButton.setManaged(isSubscriber);
        }

        if (reservationActionsLabel != null) {
            reservationActionsLabel.setVisible(isSubscriber);
            reservationActionsLabel.setManaged(isSubscriber);
        }

        if (update != null) update.setDisable(!canUpdate);

        if (numberOfGuestsField != null) numberOfGuestsField.setEditable(canUpdate);
        if (reservationDateField != null) reservationDateField.setEditable(canUpdate);
        if (reservationTimeField != null) reservationTimeField.setEditable(canUpdate);

        if (reservationDetailsTextArea != null && loggedInRole != null && loggedInUser != null) {
        	safeAppend("Logged in as " + loggedInRole + " (" + loggedInUser + ")\n");
        }
    }

    // Networking: ChatIF entry point

    /**
     * Receives a message from the server via OCSF.
     *
     * <p>This method is called on a non-JavaFX thread, so it uses {@link Platform#runLater(Runnable)}
     * to safely update UI components and route messages to child controllers.</p>
     *
     * @param message raw server message string
     */
    @Override
    public void display(String message) {
        System.out.println("UI display(): " + message);
        Platform.runLater(() -> routeAndHandleServerMessage(message));
    }

    /**
     * Routes messages to active child controllers (if relevant), otherwise handles them
     * on the main screen.
     *
     * @param message raw server message
     */
    private void routeAndHandleServerMessage(String message) {
        if (message == null) return;

        // 1) Route "New Reservation" window messages
        ReservationUIController r = activeReservationController;
        if (r != null) {
            if (message.startsWith("RESERVATIONS_FOR_DATE|")) {
                r.onReservationsReceived(message);
                return;
            }
            if (message.startsWith("RESERVATION_CREATED")) {
                r.onBookingResponse(message);
                return;
            }
            if (message.startsWith("RESERVATION_CANCELED")
                    || message.startsWith("ERROR|RESERVATION_NOT_FOUND")
                    || message.startsWith("ERROR|CANCEL")) {
                r.onCancelResponse(message);
                return;
            }
        }

        // 2) Route "Receive Table" window messages
        ReceiveTableUIController t = activeReceiveTableController;
        if (t != null) {
            if (message.startsWith("TABLE_ASSIGNED|")
                    || "NO_TABLE_AVAILABLE".equals(message)
                    || "INVALID_CONFIRMATION_CODE".equals(message)) {
                t.onReceiveTableResponse(message);
                return;
            }
        }

        // 3) Handle main screen messages
        handleMainScreenServerMessage(message);
    }
    
    /**
     * Entry point for the central router (ClientMessageRouter).
     * Routes messages to the main screen handler, only when the main UI is loaded.
     *
     * @param message raw server message
     */
    public void handleMainScreenServerMessageFromRouter(String message) {
        handleMainScreenServerMessage(message);
    }


    /**
     * Handles server responses intended for the main management screen (show/update/cancel by ID/code).
     *
     * <p>Supported messages:
     * <ul>
     *   <li>{@code RESERVATION|id|guests|date|time|code|subId}</li>
     *   <li>{@code RESERVATION_NOT_FOUND}</li>
     *   <li>{@code RESERVATION_UPDATED}</li>
     *   <li>{@code RESERVATION_CANCELED} and {@code ERROR|RESERVATION_NOT_FOUND}</li>
     *   <li>Any other string is appended as a log line</li>
     * </ul>
     *
     * @param message raw server message
     */
    private void handleMainScreenServerMessage(String message) {

    	// If the main screen UI is not injected yet, don't touch FXML fields.
    	if (reservationDetailsTextArea == null) {
    	    System.out.println("SERVER (main UI not ready): " + message);
    	    return;
    	}

    	if (message.startsWith("RESERVATION|")) {
            // Protocol: RESERVATION|id|guests|date|time|code|subId|status|customerType
            String[] parts = message.split("\\|");

            if (parts.length >= 9) {
                String rId    = parts[1];
                String guests = parts[2];
                String date   = parts[3];
                String time   = parts[4];
                String code   = parts[5];
                String subId  = parts[6];
                String status  = parts[7];
                String customerType = parts[8];

                this.currentReservationId = rId;

                reservationIdLabel.setText(rId);
                numberOfGuestsField.setText(guests);
                reservationDateField.setText(date);
                reservationTimeField.setText(time);

                StringBuilder sb = new StringBuilder();
                sb.append("Reservation Details\n");
                sb.append("-------------------\n");
                sb.append("Reservation ID    : ").append(rId).append("\n");
                sb.append("Customer Type     : ").append(customerType).append("\n");
                sb.append("Guests            : ").append(guests).append("\n");
                sb.append("Date              : ").append(date).append("\n");
                sb.append("Time              : ").append(time).append("\n");
                sb.append("Confirmation Code : ").append(code).append("\n");
                
                // Only show Subscriber ID for Subscriber reservations
                if ("Subscriber".equalsIgnoreCase(customerType)) {
                    sb.append("Subscriber ID     : ").append(subId).append("\n");
                }
                
                sb.append("Reservation Status: ").append(status).append("\n");

                reservationDetailsTextArea.setText(sb.toString());
            } else if (parts.length >= 8) {
                // Fallback for old protocol without CustomerType
                String rId    = parts[1];
                String guests = parts[2];
                String date   = parts[3];
                String time   = parts[4];
                String code   = parts[5];
                String subId  = parts[6];
                String status  = parts[7];

                this.currentReservationId = rId;

                reservationIdLabel.setText(rId);
                numberOfGuestsField.setText(guests);
                reservationDateField.setText(date);
                reservationTimeField.setText(time);

                StringBuilder sb = new StringBuilder();
                sb.append("Reservation Details\n");
                sb.append("-------------------\n");
                sb.append("Reservation ID    : ").append(rId).append("\n");
                sb.append("Guests            : ").append(guests).append("\n");
                sb.append("Date              : ").append(date).append("\n");
                sb.append("Time              : ").append(time).append("\n");
                sb.append("Confirmation Code : ").append(code).append("\n");
                sb.append("Subscriber ID     : ").append(subId).append("\n");
                sb.append("Reservation Status: ").append(status).append("\n");

                reservationDetailsTextArea.setText(sb.toString());
            } else {
                reservationDetailsTextArea.setText("Invalid data received from server.");
            }

        } else if (message.startsWith("RESERVATION_NOT_FOUND")) {
            reservationIdLabel.setText("Not found");
            numberOfGuestsField.setText("");
            reservationDateField.setText("");
            reservationTimeField.setText("");
            reservationDetailsTextArea.setText("Reservation ID " + currentReservationId + " not found.");

        } else if ("RESERVATION_UPDATED".equals(message)) {
            reservationDetailsTextArea.appendText("\nSuccess: Reservation updated successfully!\nRe-loading data...");
            if (currentReservationId != null) {
                searchReservation(currentReservationId);
            }

        } else if (message.startsWith("RESERVATION_CANCELED")
                || message.startsWith("ERROR|RESERVATION_NOT_FOUND")
                || message.startsWith("ERROR|CANCEL")) {
            onCancelResponseFromServer(message);

        } else if (message.startsWith("DELETED_EXPIRED")) {
            // Handle expired reservations cleanup response
            // Keep this internal - don't display to user
            String[] parts = message.split("\\|");
            int count = 0;
            if (parts.length >= 2) {
                try {
                    count = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    count = 0;
                }
            }
            System.out.println("DEBUG: Cleanup completed - " + count + " expired reservations marked as Expired");

        } else if (message.startsWith("ERROR|DELETE_EXPIRED_FAILED")) {
            // Handle cleanup errors silently - don't display to user
            System.out.println("DEBUG: Cleanup encountered an error (logged on server): " + message);

        } else {
            reservationDetailsTextArea.appendText(message + "\n");
        }
    }

    // Main Screen Actions

    /**
     * Event handler for "Show Reservation".
     *
     * @param event action event
     */
    @FXML
    private void onShowReservationClicked(ActionEvent event) {
        if (ClientUI.chat == null) {
        	safeAppend("Client not initialized.\n");
            return;
        }

        String idToSearch = reservationIdInput.getText();
        if (idToSearch == null || idToSearch.isBlank()) {
        	safeAppend("Please enter a Reservation ID.\n");
            return;
        }

        searchReservation(idToSearch.trim());
    }

    /**
     * Sends a {@code #GET_RESERVATION <id>} request to the server.
     *
     * @param id reservation ID
     */
    public void searchReservation(String id) {
        this.currentReservationId = id;

        String msg = "#GET_RESERVATION " + id;
        ClientUI.chat.handleMessageFromClientUI(msg);

        safeSetText("Searching for Reservation ID: " + id + "...");
    }

    /**
     * Event handler for "Update" button.
     *
     * @param event action event
     */
    @FXML
    private void onUpdateReservationClicked(ActionEvent event) {
        if (ClientUI.chat == null) {
        	safeAppend("Client not initialized.\n");
            return;
        }

        String loadedId = reservationIdLabel.getText();
        if (loadedId == null || loadedId.equals("-") || loadedId.equalsIgnoreCase("Not found") || loadedId.equalsIgnoreCase("Error")) {
        	safeAppend("No valid reservation loaded to update.\n");
            return;
        }

        String newGuests = numberOfGuestsField.getText();
        String newDate   = reservationDateField.getText();
        String newTime   = reservationTimeField.getText();

        if (newGuests == null || newDate == null || newTime == null
                || newGuests.isBlank() || newDate.isBlank() || newTime.isBlank()) {
        	safeAppend("Please enter Guests, Date and Time.\n");
            return;
        }

        // Protocol: #UPDATE_RESERVATION <id> <guests> <date> <time>
        String msg = "#UPDATE_RESERVATION " + loadedId + " " + newGuests + " " + newDate + " " + newTime;
        safeAppend("\nSending update request...\n");
        ClientUI.chat.handleMessageFromClientUI(msg);
    }

    /**
     * Event handler for "Cancel Reservation" button.
     *
     * <p>Prompts the user for a confirmation code and sends {@code #CANCEL_RESERVATION <code>}.</p>
     *
     * @param event action event
     */
    @FXML
    private void onCancelReservationClicked(ActionEvent event) {
        if (ClientUI.chat == null) {
        	safeAppend("Not connected to server.\n");
            return;
        }

        // Trigger cleanup of expired reservations on server before canceling
        try {
            ClientUI.chat.handleMessageFromClientUI("#DELETE_EXPIRED_RESERVATIONS");
        } catch (Exception e) {
            System.err.println("Failed to trigger expired reservations cleanup: " + e.getMessage());
        }

        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Cancel Reservation");
        dialog.setHeaderText("Cancel Reservation");
        dialog.setContentText("Enter confirmation code to cancel:");

        var result = dialog.showAndWait();
        if (result.isEmpty()) return;

        String confirmationCode = result.get().trim();
        if (confirmationCode.isEmpty()) {
        	safeAppend("Please enter a confirmation code.\n");
            return;
        }

        String command = "#CANCEL_RESERVATION " + confirmationCode;
        ClientUI.chat.handleMessageFromClientUI(command);
    }

    /**
     * Opens the "New Reservation" window (customer only).
     *
     * <p>This method also asks the server to delete expired reservations before opening.</p>
     *
     * @param event action event
     */
    @FXML
    private void onNewReservationClicked(ActionEvent event) {
        if (loggedInRole == null || !"Subscriber".equalsIgnoreCase(loggedInRole)) {
        	safeAppend("Only Subscriber can create reservations.\n");
            return;
        }

        try {
            // Trigger cleanup of expired reservations on server
            if (ClientUI.chat != null) {
                ClientUI.chat.handleMessageFromClientUI("#DELETE_EXPIRED_RESERVATIONS");
            }

            FXMLLoader loader = ViewLoader.fxml("ReservationUI.fxml");
            Parent root = loader.load();

            ReservationUIController resController = loader.getController();
            // Controller registers itself in initialize(), but registering again is harmless:
            ClientUIController.setActiveReservationController(resController);

            Stage stage = new Stage();
            stage.setTitle("New Reservation");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);

            if (event != null && event.getSource() instanceof Button btn && btn.getScene() != null) {
                stage.initOwner(btn.getScene().getWindow());
            }

            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            safeAppend("Failed to open reservation window: " + e.getMessage() + "\n");
        }
    }

    /**
     * Handles cancel responses for the main screen (when no reservation window is open).
     *
     * @param response raw server response
     */
    public void onCancelResponseFromServer(String response) {
        if (response != null && response.startsWith("RESERVATION_CANCELED")) {
        	safeSetText("Reservation successfully canceled and deleted!");
            reservationIdLabel.setText("-");
            numberOfGuestsField.setText("");
            reservationDateField.setText("");
            reservationTimeField.setText("");
            reservationIdInput.setText("");
            safeAppend("\n✓ Reservation canceled successfully!\n");

        } else if (response != null && response.startsWith("ERROR|RESERVATION_NOT_FOUND")) {
        	safeAppend("✗ No reservation found with this confirmation code.\n");

        } else {
        	safeAppend("✗ Error canceling reservation: " + response + "\n");
        }
    }

    /**
     * Event handler for "Exit".
     *
     * <p>Closes the client connection gracefully (if supported by your OCSF version)
     * and terminates the JavaFX application.</p>
     *
     * @param event action event
     */
    @FXML
    private void onExitClicked(ActionEvent event) {
        try {
            // Some OCSF versions expose closeConnection(); if yours doesn't, remove this call.
            if (ClientUI.chat != null) {
                try {
                    ClientUI.chat.closeConnection();
                } catch (Exception ignored) { }
            }
        } finally {
            Platform.exit();
            System.exit(0);
        }
    }

    // DB credentials

    /**
     * Sets DB credentials (if your UI needs to forward them for admin operations).
     *
     * @param user db username
    /**
     * Safely appends text to the main text area (if injected),
     * otherwise prints to console.
     *
     * @param text text to append
     */
    private void safeAppend(String text) {
        if (reservationDetailsTextArea != null) {
            reservationDetailsTextArea.appendText(text);
        } else {
            System.out.print("UI (TextArea null): " + text);
        }
    }

    /**
     * Safely sets the content of the main text area (if injected),
     * otherwise prints to console.
     *
     * @param text text to set
     */
    private void safeSetText(String text) {
        if (reservationDetailsTextArea != null) {
            reservationDetailsTextArea.setText(text);
        } else {
            System.out.println("UI (TextArea null): " + text);
        }
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

    /** The role of the currently logged-in user (e.g., Manager, Subscriber). */
    private String currentUserRole = "";

    /**
     * Sets the navigation parameters required to return to the previous screen.
     * This method should be called by the calling controller before navigating to this screen.
     *
     * @param fxml  The name of the FXML file to load when 'Back' is clicked (e.g., "ManagerUI.fxml").
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


            // Using Switch Case (Pattern Matching) to identify controller and restore context
            switch (controller) {
                // 1. Manager
                case ManagerUIController c -> 
                    c.setManagerName(currentUserName);

                // 2. Representative
                case RepresentativeMenuUIController c -> 
                    c.setRepresentativeName(currentUserName);

                // 3. Subscriber
                case LoginSubscriberUIController c -> 
                    c.setSubscriberName(currentUserName);

                // 4. Guest (LoginGuestUI)
                case LoginGuestUIController c -> {
                    // Guest menu usually doesn't need specific user state restoration
                }

                // 5. Restaurant Terminal
                case RestaurantTerminalUIController c -> {
                    // Terminal usually doesn't need specific user state restoration
                }

                default -> {
                    // Log or handle unknown controller types if necessary
                    System.out.println("Returning to generic screen: " + controller.getClass().getSimpleName());
                }
            }

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle(returnTitle);
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
