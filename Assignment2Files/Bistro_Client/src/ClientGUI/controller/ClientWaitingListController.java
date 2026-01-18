package ClientGUI.controller; // change if needed

import ClientGUI.util.SceneUtil;

import client.ChatClient;
import common.ChatIF;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Random;

import ClientGUI.util.ViewLoader;
import entities.Subscriber;

/**
 * Controller for the Waiting List registration screen.
 * <p>
 * This screen is typically invoked when a reservation attempt fails due to lack of availability.
 * It allows users (both anonymous guests and registered subscribers) to sign up for a
 * priority queue based on their requested date, time, and area.
 * <p>
 * Implements {@link ChatIF} to handle asynchronous server responses regarding the list status.
 */
public class ClientWaitingListController implements ChatIF {

    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> timeCombo;
    @FXML private ComboBox<String> areaCombo;
    @FXML private Spinner<Integer> guestsSpinner;

    @FXML private GridPane personalDetailsPane;

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;

    @FXML private TextArea resultArea;
    
    @FXML private Button leaveButton;
    private String activeConfirmationCode = null;


    private ChatClient client;
    private Subscriber currentSubscriber = null;

    /**
     * Initializes the controller class.
     * <p>
     * Sets up default values for UI components:
     * <ul>
     * <li>Date: defaults to today.</li>
     * <li>Time/Area: populates combo boxes.</li>
     * <li>Guests: sets spinner range (1-20).</li>
     * </ul>
     * Also establishes a fresh connection to the server using {@link ChatClient}.
     */
    @FXML
    public void initialize() {
        // defaults
        datePicker.setValue(LocalDate.now());
        timeCombo.getItems().addAll("18:00", "18:30", "19:00", "19:30", "20:00", "20:30", "21:00");
        timeCombo.getSelectionModel().selectFirst();

        areaCombo.getItems().addAll("Main Hall", "Patio", "Bar");
        areaCombo.getSelectionModel().selectFirst();

        guestsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 2));
        
        if (leaveButton != null) {
            leaveButton.setDisable(true);
        }
        activeConfirmationCode = null;
        
        // connect client (same as your console client style)
        try {
            client = new ChatClient("localhost", 5555, this);
            client.openConnection();
        } catch (Exception e) {
            resultArea.setText("Failed to connect to server: " + e.getMessage());
        }
    }

    /**
     * Handles the "Join Waiting List" request.
     * <p>
     * <b>Logic Flow:</b>
     * <ol>
     * <li>Validates input fields (Name/Email/Phone are mandatory for guests, optional for subscribers).</li>
     * <li>Generates a local 6-digit confirmation code.</li>
     * <li>Constructs a contact info string (key=value format) and encodes it to Base64 (to pass as a single protocol argument).</li>
     * <li>Sends command: {@code #ADD_WAITING_LIST <guests> <encodedInfo> <code> [subscriberId]}</li>
     * </ol>
     */
    @FXML
    private void onJoin() {
        if (client == null) {
            resultArea.setText("Not connected to server.");
            return;
        }
        activeConfirmationCode = null;
        if (leaveButton != null) {
        	leaveButton.setDisable(true);
        }

        String fullName = safe(nameField.getText());
        String email = safe(emailField.getText());
        String phone = safe(phoneField.getText());

        // Validation: if subscriber is present, personal details are optional (we use Subscriber info)
        if (currentSubscriber == null) {
            if (fullName.isEmpty()) {
                resultArea.setText("Please enter full name.");
                return;
            }
            if (email.isEmpty() && phone.isEmpty()) {
                resultArea.setText("Please enter at least Email or Phone.");
                return;
            }
        }
       
        int guests = guestsSpinner.getValue();
        String date = String.valueOf(datePicker.getValue());
        String time = safe(timeCombo.getValue());
        String area = safe(areaCombo.getValue());

        String confirmationCode = generateCode6();

        // Build contact info. Do NOT duplicate SubscriberID inside contactInfo when persisting it as column.
        String contactInfo =
            "Date=" + date +
            ";Time=" + time +
            ";Area=" + area +
            ";Guests=" + guests;

        if (currentSubscriber == null) {
            contactInfo = "Name=" + fullName + ";Email=" + email + ";Phone=" + phone + ";" + contactInfo;
        }

        String contactInfoB64 = encodeB64Url(contactInfo);

        // IMPORTANT: keep the command name the same. If subscriber present, append subscriberId as an extra arg.
        String cmd = "#ADD_WAITING_LIST " + guests + " " + contactInfoB64 + " " + confirmationCode;
        if (currentSubscriber != null) cmd += " " + currentSubscriber.getSubscriberId();

        resultArea.setText("Sending...\n" + cmd);
        client.handleMessageFromClientUI(cmd); // sends to server :contentReference[oaicite:4]{index=4}
    }
    
 //Leave Waiting List button action
    /**
     * Handles the "Leave Waiting List" request.
     * <p>
     * This action is only available after a user has successfully joined the list within the current session
     * (i.e., {@link #activeConfirmationCode} is not null).
     * <p>
     * Sends command: {@code #LEAVE_WAITING_LIST <code>}
     */
    @FXML
    private void onLeave() {
        if (client == null) {
            resultArea.setText("Not connected to server.");
            return;
        }

        if (activeConfirmationCode == null || activeConfirmationCode.isBlank()) {
            resultArea.appendText("\nNo active waiting list code to cancel.\n");
            if (leaveButton != null) leaveButton.setDisable(true);
            return;
        }

        String cmd = "#LEAVE_WAITING_LIST " + activeConfirmationCode;
        resultArea.appendText("\nSending leave request...\n" + cmd + "\n");

        client.handleMessageFromClientUI(cmd);

        // prevent double click until response
        if (leaveButton != null) leaveButton.setDisable(true);
    }


    /**
     * Clears all input fields in the personal details form.
     * <p>
     * Resets the Name, Email, Phone, and Result text areas to allow for a new entry.
     */
    @FXML
    private void onClear() {
        nameField.clear();
        emailField.clear();
        phoneField.clear();
        resultArea.clear();
    }
    
    
    /**
     * Handles the Exit button click.
     * Closes the current stage (window) and returns the user to the previous context if applicable.
     *
     * @param event The action event used to retrieve the current window.
     */
    @FXML
    private void onExit(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
    
    /**
     * Processes server responses regarding the waiting list status.
     * <p>
     * This method runs on the JavaFX Application Thread to update the UI safely.
     * Supported Protocol Messages:
     * <ul>
     * <li>{@code WAITING_ADDED|code} - Indicates successful registration.
     * <ul>
     * <li>Stores the active confirmation code.</li>
     * <li>Enables the "Leave Waiting List" button.</li>
     * <li>Displays a detailed success summary including the code and sent notifications (Email/SMS).</li>
     * </ul>
     * </li>
     * <li>{@code WAITING_LEFT|code} - Indicates successful removal from the list.
     * <ul>
     * <li>Clears the active confirmation code.</li>
     * <li>Disables the "Leave Waiting List" button.</li>
     * <li>Displays a confirmation message.</li>
     * </ul>
     * </li>
     * <li>{@code ERROR|<msg>} - Displays an error message received from the server.</li>
     * </ul>
     *
     * @param message The raw protocol string received from the server.
     */
    @Override
    public void display(String message) {
        Platform.runLater(() -> {
            if (message == null) return;

            if (message.startsWith("WAITING_ADDED|")) {
                String code = message.substring("WAITING_ADDED|".length());
                
                activeConfirmationCode = code;
                if (leaveButton != null) leaveButton.setDisable(false);

                String date = String.valueOf(datePicker.getValue());
                String time = safe(timeCombo.getValue());
                String area = safe(areaCombo.getValue());
                int guests = guestsSpinner.getValue();

                String email = safe(emailField.getText());
                String phone = safe(phoneField.getText());

                StringBuilder sb = new StringBuilder();
                sb.append("Added to waiting list.\n\n");
                sb.append("Reservation Info:\n");
                sb.append("Date: ").append(date).append("\n");
                sb.append("Time: ").append(time).append("\n");
                sb.append("Area: ").append(area).append("\n");
                sb.append("Guests: ").append(guests).append("\n");
                sb.append("Confirmation Code: ").append(code).append("\n\n");

                if (!email.isEmpty()) sb.append("EMAIL sent to ").append(email).append(" (code ").append(code).append(")\n");
                if (!phone.isEmpty()) sb.append("SMS sent to ").append(phone).append(" (code ").append(code).append(")\n");

                resultArea.setText(sb.toString());
                return;
            }
            if (message.startsWith("WAITING_LEFT|")) {
                String code = message.substring("WAITING_LEFT|".length());

                activeConfirmationCode = null;
                if (leaveButton != null) leaveButton.setDisable(true);

                resultArea.setText("Removed from waiting list.\nConfirmation Code: " + code + "\n");
                return;
            }

            if (message.startsWith("ERROR|")) {
                resultArea.setText( message);
                return;
            }

            // default
            resultArea.setText(message);
        });
    }

    /**
     * Utility method to safely trim strings and handle nulls.
     *
     * @param s The input string.
     * @return A trimmed version of the string, or an empty string if input was null.
     */
    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Generates a random 6-digit numeric string.
     * <p>
     * Used to create a temporary confirmation code for the waiting list entry locally
     * before sending it to the server.
     *
     * @return A string representing a number between 100,000 and 999,999.
     */
    private static String generateCode6() {
        int n = 100000 + new Random().nextInt(900000);
        return String.valueOf(n);
    }

    /**
     * Encodes a string into a URL-safe Base64 format.
     * <p>
     * This is used to encapsulate complex strings (like contact info containing spaces,
     * colons, or special characters) into a single protocol argument, ensuring
     * the server parses the command parameters correctly.
     *
     * @param s The raw string to encode.
     * @return The Base64 encoded string without padding.
     */
    private static String encodeB64Url(String s) {
        if (s == null) s = "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Pre-fills the waiting list form based on the failed reservation attempt.
     * <p>
     * This method transfers the context from the previous screen so the user doesn't have to re-enter data.
     * <p>
     * <b>Behavior for Subscribers:</b>
     * If a {@link Subscriber} object is provided, personal detail fields (Name, Email, Phone) 
     * are populated and <b>disabled</b> (read-only) to ensure data consistency with the registered account.
     *
     * @param date       The requested reservation date.
     * @param time       The requested time slot.
     * @param area       The requested restaurant area.
     * @param guests     The number of guests.
     * @param subscriber The subscriber object (null if the user is a guest).
     */
    public void setReservationContext(LocalDate date, String time, String area, int guests, Subscriber subscriber) {
        if (date != null) datePicker.setValue(date);
        if (time != null) timeCombo.setValue(time);
        if (area != null) areaCombo.setValue(area);
        try {
            guestsSpinner.getValueFactory().setValue(guests);
        } catch (Exception ignored) {}

        this.currentSubscriber = subscriber;

        if (subscriber != null) {
            // Prefill and disable personal fields for subscribers
            if (subscriber.getFullName() != null) nameField.setText(subscriber.getFullName());
            if (subscriber.getEmail() != null) emailField.setText(subscriber.getEmail());
            if (subscriber.getPhoneNumber() != null) phoneField.setText(subscriber.getPhoneNumber());
            if (personalDetailsPane != null) {
                personalDetailsPane.setVisible(false);
                personalDetailsPane.setManaged(false);
            }
            nameField.setDisable(true);
            emailField.setDisable(true);
            phoneField.setDisable(true);
            // Date/time must come from reservation, do not allow editing
            if (datePicker != null) datePicker.setDisable(true);
            if (timeCombo != null) timeCombo.setDisable(true);
            if (areaCombo != null) areaCombo.setDisable(true);
            if (guestsSpinner != null) guestsSpinner.setDisable(true);
        }
        else {
            if (personalDetailsPane != null) {
                personalDetailsPane.setVisible(true);
                personalDetailsPane.setManaged(true);
            }
            nameField.setDisable(false);
            emailField.setDisable(false);
            phoneField.setDisable(false);
            // Guests/area remain editable for guests; date/time should still come from reservation
            if (datePicker != null) datePicker.setDisable(true);
            if (timeCombo != null) timeCombo.setDisable(true);
            if (areaCombo != null) areaCombo.setDisable(false);
            if (guestsSpinner != null) guestsSpinner.setDisable(false);
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


            // Using Switch Case (Pattern Matching) to identify controller and restore context
            switch (controller) {
                // 1. Manager Dashboard
                case ManagerUIController c -> 
                    c.setManagerName(currentUserName);

                // 2. Representative Dashboard (Menu)
                case RepresentativeMenuUIController c -> 
                    c.setRepresentativeName(currentUserName);

                // 3. Subscriber Area
                case LoginSubscriberUIController c -> 
                    c.setSubscriberName(currentUserName);

                // 4. Guest Menu (LoginGuestUI)
                case LoginGuestUIController c -> {
                    // Usually no state to restore for guest, just navigation
                }

                // 5. Restaurant Terminal
                case RestaurantTerminalUIController c -> {
                     // Terminal logic (if needed to pass user back)
                }

                default -> {
                    // Log or handle unknown controller types
                    System.out.println("Returning to generic screen: " + controller.getClass().getSimpleName());
                }
            }

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle(returnTitle);
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
