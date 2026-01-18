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
 * Controller for the "Waiting List" screen.
 *
 * <p>Supports joining the waiting list and leaving the waiting list.
 * When a {@link Subscriber} context is provided (via {@link #setReservationContext(LocalDate, String, String, int, Subscriber)}),
 * the personal details pane can be hidden and the details are taken from the subscriber object.</p>
 *
 * <p><b>Networking:</b> This controller communicates with the server using {@link ChatClient}.
 * Incoming server messages are handled through the {@link ChatIF} interface and rendered to the UI using
 * {@link Platform#runLater(Runnable)} for thread safety.</p>
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
     * Controller for the "Waiting List" screen.
     *
     * <p>Supports joining the waiting list and leaving the waiting list.
     * When a {@link Subscriber} context is provided (via {@link #setReservationContext(LocalDate, String, String, int, Subscriber)}),
     * the personal details pane can be hidden and the details are taken from the subscriber object.</p>
     *
     * <p><b>Networking:</b> This controller communicates with the server using {@link ChatClient}.
     * Incoming server messages are handled through the {@link ChatIF} interface and rendered to the UI using
     * {@link Platform#runLater(Runnable)} for thread safety.</p>
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
            leaveButton.setDisable(false);
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
     * Handles the "Join Waiting List" action.
     *
     * <p>Validates required fields, builds a payload describing the requested date/time/area/guests and
     * (if no subscriber context exists) also includes name/email/phone. The payload is base64-url encoded and sent
     * to the server using {@code #ADD_WAITING_LIST}.</p>
     *
     * <p>When a subscriber context exists, the subscriber ID is appended as an additional argument
     * and manual details are not required.</p>
     *
     * <p>Upon sending, this method disables the leave button until a server response is received.</p>
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
    
    /**
     * Handles the "Leave Waiting List" action.
     *
     * <p>If the controller already has an {@link #activeConfirmationCode}, it is used directly to send
     * {@code #LEAVE_WAITING_LIST}.</p>
     *
     * <p>If there is no active code and the current user role is Representative/Manager, a dialog is shown
     * so a confirmation code can be entered manually, then sent to the server.</p>
     *
     * <p>After sending a leave request, the leave button is disabled to prevent duplicate requests until a
     * response is received.</p>
     */
    @FXML
    private void onLeave() {
        if (client == null) {
            resultArea.setText("Not connected to server.");
            return;
        }

        if (activeConfirmationCode == null || activeConfirmationCode.isBlank()) {

            if ("Representative".equalsIgnoreCase(currentUserRole) || "Manager".equalsIgnoreCase(currentUserRole)) {

                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Leave Waiting List");
                dialog.setHeaderText("Enter Confirmation Code");
                dialog.setContentText("Code:");

                dialog.showAndWait().ifPresent(code -> {
                    String entered = code == null ? "" : code.trim();
                    if (entered.isEmpty()) {
                        resultArea.appendText("\nNo code entered.\n");
                        return;
                    }

                    activeConfirmationCode = entered;
                    String cmd = "#LEAVE_WAITING_LIST " + activeConfirmationCode;

                    resultArea.appendText("\nSending leave request...\n" + cmd + "\n");
                    client.handleMessageFromClientUI(cmd);

                    if (leaveButton != null) leaveButton.setDisable(true);
                });

                return;
            }

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
     * Clears user-entered personal details and the results area.
     *
     * <p>This does not reset server state and does not change waiting-list membership.
     * It only clears the current UI text inputs.</p>
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
     * Closes the current window.
     *
     * @param event the action event triggered by the Exit button
     */
    @FXML
    private void onExit(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
    
    /**
     * Receives messages from the server and updates the UI.
     *
     * <p>All UI updates are executed via {@link Platform#runLater(Runnable)} to ensure JavaFX thread safety.</p>
     *
     * <p>Recognized messages:
     * <ul>
     *   <li>{@code WAITING_ADDED|<code>} - stores confirmation code and prints summary.</li>
     *   <li>{@code WAITING_LEFT|<code>} - clears confirmation code and shows removal message.</li>
     *   <li>{@code ERROR|...} - prints server error message.</li>
     *   <li>Any other message - displayed as-is.</li>
     * </ul>
     * </p>
     *
     * @param message raw server message
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
     * Trims a string safely.
     *
     * @param s input string (may be {@code null})
     * @return trimmed string, or empty string if {@code s} is {@code null}
     */
    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Generates a random 6-digit confirmation code (as a string).
     *
     * @return a 6-digit numeric string (100000-999999)
     */
    private static String generateCode6() {
        int n = 100000 + new Random().nextInt(900000);
        return String.valueOf(n);
    }


    /**
     * Encodes the given text as URL-safe Base64 without padding.
     *
     * @param s the text to encode (may be {@code null})
     * @return Base64-url encoded representation (never {@code null})
     */
    private static String encodeB64Url(String s) {
        if (s == null) s = "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Prefills the waiting-list screen with reservation context.
     *
     * <p>Used when a waiting list request is opened as a follow-up to a failed reservation attempt.
     * The date/time are typically locked to match the failed reservation slot.</p>
     *
     * <p>If a subscriber is provided:
     * <ul>
     *   <li>Personal detail fields are filled from the subscriber object and disabled.</li>
     *   <li>The personal details pane may be hidden.</li>
     *   <li>Date/time/area/guests are locked (not editable) to preserve reservation context.</li>
     * </ul>
     * If subscriber is {@code null}, personal details remain editable (with some fields still locked
     * according to the reservation context rules).</p>
     *
     * @param date the reservation date to prefill (may be {@code null})
     * @param time the reservation time to prefill (may be {@code null})
     * @param area the reservation area to prefill (may be {@code null})
     * @param guests the number of guests to prefill
     * @param subscriber optional subscriber context (may be {@code null})
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
