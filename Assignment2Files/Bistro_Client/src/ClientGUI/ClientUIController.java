package ClientGUI;


import java.io.IOException;

import client.ChatClient;
import common.ChatIF;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

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

    public void setUserContext(String username, String role) {
        this.loggedInUser = username;
        this.loggedInRole = role;
        applyRolePermissions();
    }
    private void applyRolePermissions() {
        boolean canUpdate = loggedInRole != null &&
            ("Manager".equalsIgnoreCase(loggedInRole)
                    || "Representative".equalsIgnoreCase(loggedInRole));


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
}