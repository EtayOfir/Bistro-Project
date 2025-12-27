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

public class ClientUIController implements ChatIF {

    // UI Components

    @FXML
    private TextField orderNumberInput;

    @FXML
    private Label orderNumber;   // we still show current order number as a label

    @FXML
    private TextField numberOfGuestsField;   // editable guests

    @FXML
    private TextField orderDateField;        // editable date

    @FXML
    private Button showReservation;

    @FXML
    private Button update;

    @FXML
    private Button exit;

    @FXML
    private TextArea reservationDetailsTextArea;

    private String orderNum;

    // Client
    private ChatClient chatClient;
    private String loggedInUser;
    private String loggedInRole;

    public void setUserContext(String username, String role) {
        this.loggedInUser = username;
        this.loggedInRole = role;
        applyRolePermissions();
    }
    private void applyRolePermissions() {
        boolean canUpdate = "Manager".equalsIgnoreCase(loggedInRole)
                         || "Representative".equalsIgnoreCase(loggedInRole);

        // רק מנהל/נציג יכולים לעדכן
        if (update != null) update.setDisable(!canUpdate);

        if (numberOfGuestsField != null) numberOfGuestsField.setEditable(canUpdate);
        if (orderDateField != null) orderDateField.setEditable(canUpdate);

        if (reservationDetailsTextArea != null && loggedInRole != null && loggedInUser != null) {
            reservationDetailsTextArea.appendText(
                    "Logged in as " + loggedInRole + " (" + loggedInUser + ")\n"
            );
        }
    }

    public void initClient(String host, int port) {
        try {
            this.chatClient = new ChatClient(host, port, this); // "this" is ChatIF
        } catch (IOException e) {
            e.printStackTrace();
            display("Could not connect to server: " + e.getMessage());
        }
    }

    public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @FXML
    private void onUpdateReservationClicked(ActionEvent event) {
        if (chatClient == null) {
            display("Client not initialized.");
            return;
        }

        // Must already have a reservation loaded
        String currentOrderNum = orderNumber.getText();
        if (currentOrderNum == null || currentOrderNum.isBlank()) {
            display("No reservation loaded to update.");
            return;
        }

        String newGuests = numberOfGuestsField.getText();
        String newDate   = orderDateField.getText();

        if (newGuests == null || newGuests.isBlank() ||
            newDate == null   || newDate.isBlank()) {
            display("Please enter both number of guests and order date (yyyy-MM-dd).");
            return;
        }

        // Optional: basic validation for number of guests
        try {
            Integer.parseInt(newGuests);
        } catch (NumberFormatException e) {
            display("Number of guests must be an integer.");
            return;
        }

        // Build update message:
        //   #UPDATE_RESERVATION <orderNum> <numGuests> <orderDate>
        String msg = "#UPDATE_RESERVATION " + currentOrderNum + " " + newGuests + " " + newDate;

        reservationDetailsTextArea.appendText("\nUpdating reservation...\n");
        chatClient.handleMessageFromClientUI(msg);
    }

    // This method is called when the "Show Reservation" button is pressed
    @FXML
    private void onShowReservationClicked(ActionEvent event) {
        if (chatClient == null) {
            display("Client not initialized.");
            return;
        }

        String orderNumToSearch = orderNumberInput.getText();

        if (orderNumToSearch == null || orderNumToSearch.isBlank()) {
            display("Please enter an order number.");
            return;
        }

        // Trim spaces and send request to server
        SearchOrderNum(orderNumToSearch.trim());
    }

    // Send message to server through ChatClient
    public void SearchOrderNum(String orderNum) {
        this.orderNum = orderNum;
        try {
            String msg = "#GET_RESERVATION " + orderNum;
            chatClient.handleMessageFromClientUI(msg);

            Platform.runLater(() -> {
                reservationDetailsTextArea.setText("Loading reservation " + orderNum + "...");
            });

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                orderNumber.setText("Error");
                // CHANGED: use the new TextFields instead of old labels
                numberOfGuestsField.setText("-");
                orderDateField.setText("-");
                reservationDetailsTextArea.setText("Error sending request: " + e.getMessage());
            });
        }
    }

    /**
     * Called by ChatClient whenever a message comes from the server
     */
    @Override
    public void display(String message) {
        System.out.println("UI display(): " + message);

        Platform.runLater(() -> handleServerMessage(message));
    }

    // Parse server message and update fields + text area
    private void handleServerMessage(String message) {
        if (message == null) return;

        if (message.startsWith("RESERVATION|")) {
            // Format from server:
            // RESERVATION|orderNum|numGuests|orderDate|confCode|subscriberId|placingDate
            String[] parts = message.split("\\|");
            if (parts.length >= 7) {
                String ordNum       = parts[1];
                String numGuests    = parts[2];
                String date         = parts[3];
                String confCode     = parts[4];
                String subscriberId = parts[5];
                String placingDate  = parts[6];

                // remember last order
                this.orderNum = ordNum;

                // Update UI fields
                orderNumber.setText(ordNum);
                numberOfGuestsField.setText(numGuests);
                orderDateField.setText(date);

                StringBuilder details = new StringBuilder();
                details.append("Reservation Details\n");
                details.append("-------------------\n");
                details.append("Order Number      : ").append(ordNum).append("\n");
                details.append("Guests            : ").append(numGuests).append("\n");
                details.append("Order Date        : ").append(date).append("\n");
                details.append("Confirmation Code : ").append(confCode).append("\n");
                details.append("Subscriber ID     : ").append(subscriberId).append("\n");
                details.append("Placing Date      : ").append(placingDate).append("\n");

                reservationDetailsTextArea.setText(details.toString());
            } else {
                orderNumber.setText("Error");
                numberOfGuestsField.setText("-");
                orderDateField.setText("-");
                reservationDetailsTextArea.setText("Invalid reservation data from server: " + message);
            }

        } else if (message.startsWith("RESERVATION_NOT_FOUND")) {
            orderNumber.setText("Not found");
            // CHANGED: use the new TextFields
            numberOfGuestsField.setText("-");
            orderDateField.setText("-");

            String ord = (orderNum != null) ? orderNum : "";
            reservationDetailsTextArea.setText(
                    "Reservation not found for order number: " + ord);

        } else if (message.equals("Connected to server") || message.equals("Disconnected from server")) {
            reservationDetailsTextArea.appendText(message + "\n");
        } else {
            reservationDetailsTextArea.appendText(message + "\n");
        }
    }
    @FXML
    private void onExitClicked(ActionEvent event) {
        try {
            if (chatClient != null) {
                chatClient.closeConnection();
            }
        } catch (Exception e) {
            // ignore errors on exit
        }

        Platform.exit();   // closes the JavaFX UI
        System.exit(0);    // kills the process completely
    }
}
