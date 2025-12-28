package ClientGUI;


import java.io.IOException;

import client.ChatClient;
import common.ChatIF;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Controller class for the Client-side JavaFX Graphical User Interface.
 * <p>
 * This class handles user interactions, validates input, and communicates with the
 * {@link ChatClient} to send commands to the server. It implements the {@link ChatIF}
 * interface to receive and display messages sent back from the server.
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 * <li>Initializing the network connection.</li>
 * <li>Handling button clicks (Show, Update, Exit).</li>
 * <li>Parsing protocol messages received from the server.</li>
 * <li>Updating UI components on the JavaFX Application Thread.</li>
 * </ul>
 */
public class ClientUIController implements ChatIF {

    // UI Components

    /** Input field for entering the Reservation ID to search. */
    @FXML
    private TextField reservationIdInput;

    /** Label displaying the currently loaded Reservation ID (or status like "Not Found"). */
    @FXML
    private Label reservationIdLabel;

    /** Editable text field for the number of guests. */
    @FXML
    private TextField numberOfGuestsField;

    /** Editable text field for the reservation date (Format: yyyy-MM-dd). */
    @FXML
    private TextField reservationDateField;

    /** Editable text field for the reservation time (Format: HH:mm:ss). */
    @FXML
    private TextField reservationTimeField;

    /** Button to trigger the reservation search. */
    @FXML
    private Button showReservation;

    /** Button to submit updates for the loaded reservation. */
    @FXML
    private Button update;

    /** Button to cancel a reservation. */
    @FXML
    private Button cancelReservation;

    /** Button to close the connection and exit the application. */
    @FXML
    private Button exit;

    /** Text area for displaying logs, status messages, and reservation details. */
    @FXML
    private TextArea reservationDetailsTextArea;

    @FXML
    private void initialize() {
      applyRolePermissions();
    }
  
    // Internal variable to track current ID
    /** Stores the ID of the reservation currently being displayed/edited. */
    private String currentReservationId;

    // Client
    /** The network client instance used to communicate with the server. */
    private ChatClient chatClient;
    private String loggedInUser;
    private String loggedInRole;
    private String dbUser = "root";
    private String dbPassword = "";

    // Static holder for the current ReservationUIController (for message routing)
    private static ReservationUIController activeReservationController = null;

    public static void setActiveReservationController(ReservationUIController controller) {
        activeReservationController = controller;
    }

    public static void clearActiveReservationController() {
        activeReservationController = null;
    }

    public void setUserContext(String username, String role) {
        this.loggedInUser = username;
        this.loggedInRole = role;
        applyRolePermissions();
    }
    private void applyRolePermissions() {
//        boolean canUpdate = loggedInRole != null &&
//            ("Manager".equalsIgnoreCase(loggedInRole)
//                    || "Representative".equalsIgnoreCase(loggedInRole));
        boolean canUpdate = loggedInRole != null && !loggedInRole.isBlank();
        boolean isCustomer = loggedInRole != null && "Customer".equalsIgnoreCase(loggedInRole);

        if (newReservationButton != null) {
            newReservationButton.setVisible(isCustomer);
            newReservationButton.setManaged(isCustomer);
        }
        
        if (reservationActionsLabel != null) {
            reservationActionsLabel.setVisible(isCustomer);
            reservationActionsLabel.setManaged(isCustomer);
        }

        // only Manager / Representative can update
        if (update != null) update.setDisable(!canUpdate);
        
        if (numberOfGuestsField != null) numberOfGuestsField.setEditable(canUpdate);
        if (reservationDateField != null) reservationDateField.setEditable(canUpdate);
        if (reservationTimeField != null) reservationTimeField.setEditable(canUpdate);


        if (reservationDetailsTextArea != null && loggedInRole != null && loggedInUser != null) {
            reservationDetailsTextArea.appendText(
                    "Logged in as " + loggedInRole + " (" + loggedInUser + ")\n"
            );
        }
    }

    
    /**
     * Initializes the network client connection.
     * <p>
     * Creates a new {@link ChatClient} instance connected to the specified host and port.
     *
     * @param host The server hostname or IP address.
     * @param port The server listening port.
     */
    public void initClient(String host, int port) {
        try {
            this.chatClient = new ChatClient(host, port, this); // "this" is ChatIF
        } catch (IOException e) {
            e.printStackTrace();
            display("Could not connect to server: " + e.getMessage());
        }
    }

    /**
     * Sets the ChatClient instance manually (useful for dependency injection or testing).
     *
     * @param chatClient The ChatClient instance.
     */
    public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Event handler for the "Update" button.
     * <p>
     * Validates that a reservation is currently loaded and that all fields are populated.
     * Constructs the update protocol message {@code #UPDATE_RESERVATION} and sends it to the server.
     *
     * @param event The action event triggered by the button click.
     */
    @FXML
    private void onUpdateReservationClicked(ActionEvent event) {
        if (chatClient == null) {
            display("Client not initialized.");
            return;
        }

        // Check if a reservation is currently loaded
        String loadedId = reservationIdLabel.getText();
        if (loadedId == null || loadedId.equals("-") || loadedId.equals("Not found") || loadedId.equals("Error")) {
            display("No valid reservation loaded to update.");
            return;
        }

        String newGuests = numberOfGuestsField.getText();
        String newDate   = reservationDateField.getText();
        String newTime   = reservationTimeField.getText();

        if (newGuests.isBlank() || newDate.isBlank() || newTime.isBlank()) {
            display("Please enter Guests, Date and Time.");
            return;
        }

        // Send Update Command
        // Protocol: #UPDATE_RESERVATION <id> <guests> <date> <time>
        String msg = "#UPDATE_RESERVATION " + loadedId + " " + newGuests + " " + newDate + " " + newTime;

        reservationDetailsTextArea.appendText("\nSending update request...\n");
        chatClient.handleMessageFromClientUI(msg);
    }
    
    /**
     * Event handler for the "Show Reservation" button.
     * <p>
     * Reads the ID from the input field and initiates a search request.
     *
     * @param event The action event triggered by the button click.
     */
    @FXML
    private void onShowReservationClicked(ActionEvent event) {
        if (chatClient == null) {
            display("Client not initialized.");
            return;
        }

        String idToSearch = reservationIdInput.getText();

        if (idToSearch == null || idToSearch.isBlank()) {
            display("Please enter a Reservation ID.");
            return;
        }

        SearchReservation(idToSearch.trim());
    }
    
    /**
     * Sends a search request to the server for a specific reservation ID.
     * <p>
     * Sends the protocol message {@code #GET_RESERVATION <id>}.
     *
     * @param id The reservation ID to search for.
     */
    public void SearchReservation(String id) {
        this.currentReservationId = id;
        try {
            String msg = "#GET_RESERVATION " + id;
            chatClient.handleMessageFromClientUI(msg);

            Platform.runLater(() -> {
                reservationDetailsTextArea.setText("Searching for Reservation ID: " + id + "...");
            });

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                reservationDetailsTextArea.setText("Error sending request: " + e.getMessage());
            });
        }
    }
    
    /**
     * Displays a message in the UI.
     * <p>
     * This method is called by the {@code ChatClient} when a message is received from the server.
     * Since network operations happen on a background thread, this method uses 
     * {@link Platform#runLater(Runnable)} to safely update the JavaFX UI.
     *
     * @param message The message string received from the server.
     */
    @Override
    public void display(String message) {
        System.out.println("UI display(): " + message);
        Platform.runLater(() -> handleServerMessage(message));
    }
    
    /**
     * Parses the raw message received from the server and updates the UI components accordingly.
     * <p>
     * Handles the following protocol responses:
     * <ul>
     * <li>{@code RESERVATION|...}: Populates the fields with reservation details.</li>
     * <li>{@code RESERVATION_NOT_FOUND}: Clears fields and shows a "Not found" status.</li>
     * <li>{@code RESERVATION_UPDATED}: Notifies success and re-fetches the data.</li>
     * <li>General text messages: Appends them to the text area.</li>
     * </ul>
     *
     * @param message The raw protocol string from the server.
     */
    private void handleServerMessage(String message) {
        if (message == null) return;

        System.out.println("DEBUG: handleServerMessage received: [" + message + "]");
        System.out.println("DEBUG: activeReservationController is " + (activeReservationController != null ? "SET" : "NULL"));

        // Route reservation-specific responses to the active controller
        if ((message.startsWith("RESERVATIONS_FOR_DATE|") || message.startsWith("RESERVATION_CREATED") || message.startsWith("ERROR|BOOK")) && activeReservationController != null) {
            System.out.println("DEBUG: ✓ Routing to activeReservationController");
            if (message.startsWith("RESERVATIONS_FOR_DATE|")) {
                System.out.println("DEBUG: Calling onReservationsReceived()");
                activeReservationController.onReservationsReceived(message);
            } else if (message.startsWith("RESERVATION_CREATED")) {
                System.out.println("DEBUG: Calling onBookingResponse()");
                activeReservationController.onBookingResponse(message);
            } else {
                System.out.println("DEBUG: Calling onBookingResponse() - error");
                activeReservationController.onBookingResponse(message);
            }
            return;
        }
        
        // Route cancel responses - check both active controllers
        if ((message.startsWith("RESERVATION_CANCELED") || message.startsWith("ERROR|RESERVATION_NOT_FOUND") || message.startsWith("ERROR|CANCEL"))) {
            System.out.println("DEBUG: Routing cancel response");
            // Try active reservation controller first (if in New Reservation window)
            if (activeReservationController != null) {
                System.out.println("DEBUG: Routing cancel to activeReservationController");
                activeReservationController.onCancelResponse(message);
                return;
            }
            // Otherwise route to main client controller
            System.out.println("DEBUG: Routing cancel to main ClientUIController");
            onCancelResponseFromServer(message);
            return;
        }
        
        System.out.println("DEBUG: ✗ NOT routed - activeReservationController null or wrong message type");
        System.out.println("  activeReservationController: " + activeReservationController);
        System.out.println("  startsWithRES_FOR_DATE: " + message.startsWith("RESERVATIONS_FOR_DATE|"));
        System.out.println("  equalsRES_CREATED: " + message.equals("RESERVATION_CREATED"));
        System.out.println("  startsWithERROR: " + message.startsWith("ERROR|BOOK"));

        if (message.startsWith("RESERVATION|")) {
            // Protocol: RESERVATION|id|guests|date|time|code|subId
            String[] parts = message.split("\\|");
            
            if (parts.length >= 7) {
                String rId      = parts[1];
                String rGuests  = parts[2];
                String rDate    = parts[3];
                String rTime    = parts[4];
                String rCode    = parts[5];
                String rSubId   = parts[6];

                this.currentReservationId = rId;

                // Update UI Fields
                reservationIdLabel.setText(rId);
                numberOfGuestsField.setText(rGuests);
                reservationDateField.setText(rDate);
                reservationTimeField.setText(rTime);

                // Build Detail String
                StringBuilder sb = new StringBuilder();
                sb.append("Reservation Details\n");
                sb.append("-------------------\n");
                sb.append("Reservation ID    : ").append(rId).append("\n");
                sb.append("Guests            : ").append(rGuests).append("\n");
                sb.append("Date              : ").append(rDate).append("\n");
                sb.append("Time              : ").append(rTime).append("\n");
                sb.append("Confirmation Code : ").append(rCode).append("\n");
                sb.append("Subscriber ID     : ").append(rSubId).append("\n");

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

        } else if (message.equals("RESERVATION_UPDATED")) {
            reservationDetailsTextArea.appendText("\nSuccess: Reservation updated successfully!\nRe-loading data...");
            // Automatically refresh the data
            SearchReservation(currentReservationId);
              
        } else {
            reservationDetailsTextArea.appendText(message + "\n");
        }
    }
    
    /**
     * Event handler for the "Exit" button.
     * <p>
     * Closes the client connection gracefully and terminates the application process.
     *
     * @param event The action event triggered by the button click.
     */
    @FXML
    private void onExitClicked(ActionEvent event) {
        try {
            if (chatClient != null) chatClient.closeConnection();
        } catch (Exception e) { }
        Platform.exit();    // closes the JavaFX UI
        System.exit(0);     // kills the process completely
    }

    public void setDatabaseCredentials(String user, String password) {
        this.dbUser = user;
        this.dbPassword = password;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    /** Button to create a new reservation (opens the Reservation UI). */
    @FXML
    private Button newReservationButton;

    /** Label for "Reservation Actions" section. */
    @FXML
    private Label reservationActionsLabel;

    /**
     * Event handler for the "New Reservation" button.
     * <p>
     * Opens a new modal window for creating a reservation. Restricted to customer role only.
     *
     * @param event The action event triggered by the button click.
     */
    @FXML
    private void onNewReservationClicked(ActionEvent event) {
        // Only allow customers
        if (loggedInRole == null || !"Customer".equalsIgnoreCase(loggedInRole)) {
            display("Only customers can create reservations.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ClientGUI/ReservationUI.fxml"));
            Parent root = loader.load();

            ReservationUIController resController = loader.getController();
            // REGISTER BEFORE passing chatClient to avoid race condition
            ClientUIController.setActiveReservationController(resController);
            
            resController.setChatClient(chatClient);

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
            display("Failed to open reservation window: " + e.getMessage());
        }
    }

    /**
     * Handle cancel reservation button click.
     * Prompts user for confirmation code and cancels the reservation.
     */
    @FXML
    private void onCancelReservationClicked(ActionEvent event) {
        if (chatClient == null) {
            display("Not connected to server.");
            return;
        }

        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Cancel Reservation");
        dialog.setHeaderText("Cancel Reservation");
        dialog.setContentText("Enter confirmation code to cancel:");
        
        var result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        
        String confirmationCode = result.get().trim();
        if (confirmationCode.isEmpty()) {
            display("Please enter a confirmation code.");
            return;
        }
        
        // Send cancel command to server
        String command = "#CANCEL_RESERVATION " + confirmationCode;
        try {
            System.out.println("DEBUG: Sending cancel command for code: " + confirmationCode);
            chatClient.handleMessageFromClientUI(command);
        } catch (Exception e) {
            e.printStackTrace();
            display("Error: " + e.getMessage());
        }
    }

    /**
     * Handle cancel response from server
     */
    public void onCancelResponseFromServer(String response) {
        javafx.application.Platform.runLater(() -> {
            if (response != null && response.startsWith("RESERVATION_CANCELED")) {
                reservationDetailsTextArea.setText("Reservation successfully canceled and deleted!");
                reservationIdLabel.setText("-");
                numberOfGuestsField.setText("");
                reservationDateField.setText("");
                reservationTimeField.setText("");
                reservationIdInput.setText("");
                display("✓ Reservation canceled successfully!");
            } else if (response != null && response.startsWith("ERROR|RESERVATION_NOT_FOUND")) {
                display("✗ No reservation found with this confirmation code.");
            } else {
                display("✗ Error canceling reservation: " + response);
            }
        });
    }
}
