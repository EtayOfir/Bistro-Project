package ClientGUI; // change if needed

import client.ChatClient;
import common.ChatIF;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Random;

public class ClientWaitingListController implements ChatIF {

    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> timeCombo;
    @FXML private ComboBox<String> areaCombo;
    @FXML private Spinner<Integer> guestsSpinner;

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;

    @FXML private TextArea resultArea;

    private ChatClient client;

    @FXML
    public void initialize() {
        // defaults
        datePicker.setValue(LocalDate.now());
        timeCombo.getItems().addAll("18:00", "18:30", "19:00", "19:30", "20:00", "20:30", "21:00");
        timeCombo.getSelectionModel().selectFirst();

        areaCombo.getItems().addAll("Main Hall", "Patio", "Bar");
        areaCombo.getSelectionModel().selectFirst();

        guestsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 2));

        // connect client (same as your console client style)
        try {
            client = new ChatClient("localhost", 5555, this);
        } catch (Exception e) {
            resultArea.setText("Failed to connect to server: " + e.getMessage());
        }
    }

    @FXML
    private void onJoin() {
        if (client == null) {
            resultArea.setText("Not connected to server.");
            return;
        }

        String fullName = safe(nameField.getText());
        String email = safe(emailField.getText());
        String phone = safe(phoneField.getText());

        if (fullName.isEmpty()) {
            resultArea.setText("Please enter full name.");
            return;
        }
        if (email.isEmpty() && phone.isEmpty()) {
            resultArea.setText("Please enter at least Email or Phone.");
            return;
        }

        int guests = guestsSpinner.getValue();
        String date = String.valueOf(datePicker.getValue());
        String time = safe(timeCombo.getValue());
        String area = safe(areaCombo.getValue());

        String confirmationCode = generateCode6();

        // Put everything you want stored for manager view / mock notifications
        String contactInfo =
                "Name=" + fullName +
                ";Email=" + email +
                ";Phone=" + phone +
                ";Date=" + date +
                ";Time=" + time +
                ";Area=" + area;

        String contactInfoB64 = encodeB64Url(contactInfo);

        // IMPORTANT: keep the command name the same as your server switch case
        String cmd = "#ADD_WAITING_LIST " + guests + " " + contactInfoB64 + " " + confirmationCode;

        resultArea.setText("Sending...\n" + cmd);
        client.handleMessageFromClientUI(cmd); // sends to server :contentReference[oaicite:4]{index=4}
    }

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
     */
    @FXML
    private void onExit(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
    
    @Override
    public void display(String message) {
        Platform.runLater(() -> {
            if (message == null) return;

            if (message.startsWith("WAITING_ADDED|")) {
                String code = message.substring("WAITING_ADDED|".length());

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

                if (!email.isEmpty()) sb.append("MOCK EMAIL sent to ").append(email).append(" (code ").append(code).append(")\n");
                if (!phone.isEmpty()) sb.append("MOCK SMS sent to ").append(phone).append(" (code ").append(code).append(")\n");

                resultArea.setText(sb.toString());
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

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String generateCode6() {
        int n = 100000 + new Random().nextInt(900000);
        return String.valueOf(n);
    }

    private static String encodeB64Url(String s) {
        if (s == null) s = "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
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
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource(returnScreenFXML));
            javafx.scene.Parent root = loader.load();

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

            javafx.stage.Stage stage = (javafx.stage.Stage)((javafx.scene.Node)event.getSource()).getScene().getWindow();
            stage.setTitle(returnTitle);
            stage.setScene(new javafx.scene.Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
