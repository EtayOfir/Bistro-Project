package ClientGUI.controller;

import ClientGUI.util.SceneUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

/**
 * Controller for Staff Reservation UI (Manager/Representative creating reservations for customers).
 * 
 * Similar to ReservationUIController but:
 * - Creates reservations for customers (guests or subscribers)
 * - Not for themselves
 * - Has customer type selector (Guest/Subscriber)
 * - Phone and email are for the customer, not the staff member
 */
public class StaffReservationUIController {

    // FXML Fields
    @FXML private ComboBox<String> customerTypeCombo;
    @FXML private TextField subscriberIdField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private DatePicker datePicker;
    @FXML private Spinner<Integer> hourSpinner;
    @FXML private Spinner<Integer> minuteSpinner;
    @FXML private Spinner<Integer> guestSpinner;
    @FXML private TextField reservationIdField;
    @FXML private TextField confirmationField;
    @FXML private ListView<String> availableTables;

    // Constants
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_WITH_SECONDS_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int RES_DURATION_MIN = 90; // Reservation duration in minutes

    // Instance state
    private String currentConfirmationCode = "";
    private int currentReservationId = -1;
    private Set<LocalTime> bookedTimesForDate = new HashSet<>();

    // Navigation context
    private String returnScreenFXML = "ManagerUI.fxml";
    private String returnTitle = "Manager Dashboard";
    private String currentUserName = "";
    private String currentUserRole = "";

    /**
     * Initializes the controller.
     * Sets up spinners, date picker, and customer type combo box.
     */
    @FXML
    public void initialize() {
        // Register as active controller for routing server messages
        ClientUIController.setActiveStaffReservationController(this);

        // Setup spinners
        hourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(9, 22, 12));
        minuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 45, 0, 15));
        guestSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 2));

        // Setup date picker
        datePicker.setValue(LocalDate.now());
        datePicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                fetchExistingReservations(newV);
            }
        });

        // Setup customer type combo box with items
        customerTypeCombo.setItems(FXCollections.observableArrayList("Casual", "Subscriber"));
        customerTypeCombo.setValue("Casual");
        customerTypeCombo.setOnAction(e -> {
            String type = customerTypeCombo.getValue();
            boolean isSubscriber = "Subscriber".equalsIgnoreCase(type);

            System.out.println("DEBUG: Customer type selected: " + type);

            // SubscriberID enabled only for Subscriber
            subscriberIdField.setDisable(!isSubscriber);
            subscriberIdField.setStyle(isSubscriber ? "-fx-opacity: 1.0;" : "-fx-opacity: 0.6;");
            if (!isSubscriber) {
                subscriberIdField.clear();
            }

            // Phone/Email enabled only for Casual
            phoneField.setDisable(isSubscriber);
            emailField.setDisable(isSubscriber);
            phoneField.setStyle(isSubscriber ? "-fx-opacity: 0.6;" : "-fx-opacity: 1.0;");
            emailField.setStyle(isSubscriber ? "-fx-opacity: 0.6;" : "-fx-opacity: 1.0;");

            // Clear irrelevant fields on switch
            if (isSubscriber) {
                phoneField.clear();
                emailField.clear();
            } else {
                // switching to Casual
                // (SubscriberID already cleared above)
                // keep phone/email empty so user fills customer data
                phoneField.clear();
                emailField.clear();
            }
        });

        // Initially disable subscriber ID field (since Casual is default)
        subscriberIdField.setDisable(true);
        subscriberIdField.setStyle("-fx-opacity: 0.6;");

        // Fetch reservations for today
        fetchExistingReservations(LocalDate.now());

        System.out.println("DEBUG StaffReservationUIController initialized");
    }

    /**
     * Fetches existing reservations for a given date to check availability.
     */
    private void fetchExistingReservations(LocalDate date) {
        if (ClientUI.chat == null) {
            showAlert(AlertType.ERROR, "Connection Error", "Not connected to server.");
            return;
        }

        String command = "#GET_RESERVATIONS_BY_DATE " + date.format(DATE_FMT);
        System.out.println("DEBUG: Fetching reservations for date: " + date);
        ClientUI.chat.handleMessageFromClientUI(command);
    }

    /**
     * Handler for checking availability.
     */
    @FXML
    private void onCheckAvailability(ActionEvent event) {
        LocalDate date = datePicker.getValue();
        int hour = hourSpinner.getValue();
        int minute = minuteSpinner.getValue();
        Integer guests = guestSpinner.getValue();

        if (date == null) {
            showAlert(AlertType.ERROR, "Invalid Input", "Please select a date.");
            return;
        }

        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(hour, minute));
        LocalDateTime end = start.plusMinutes(RES_DURATION_MIN);

        System.out.println("DEBUG: Checking availability for " + guests + " guests from " + start + " to " + end);

        // Check if slot is available
        boolean available = isAvailable(start, end);
        if (available) {
            showAlert(AlertType.INFORMATION, "Availability", "The selected time slot is available!");
        } else {
            showAlert(AlertType.WARNING, "Not Available", "The selected time slot is not available.");
        }

        fetchExistingReservations(date);
    }

    /**
     * Checks if a time slot is available.
     */
    private boolean isAvailable(LocalDateTime start, LocalDateTime end) {
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();

        // Check if any booked time falls within the reservation window
        for (LocalTime bookedTime : bookedTimesForDate) {
            if (!bookedTime.isBefore(startTime) && bookedTime.isBefore(endTime)) {
                return false; // Conflict found
            }
        }
        return true;
    }

    /**
     * Handler for booking a reservation.
     */
    @FXML
    private void onBook(ActionEvent event) {
        LocalDate date = datePicker.getValue();
        int hour = hourSpinner.getValue();
        int minute = minuteSpinner.getValue();
        Integer guests = guestSpinner.getValue();

        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String customerType = customerTypeCombo.getValue();

        // normalize ONLY for Casual
        if ("Casual".equalsIgnoreCase(customerType)) {
            phone = phone.replaceAll("\\D", "");
        }

        if (date == null) {
            showAlert(AlertType.ERROR, "Invalid Input", "Please select a date.");
            return;
        }

        if (customerType == null || customerType.isEmpty()) {
            showAlert(AlertType.ERROR, "Invalid Input", "Please select customer type.");
            return;
        }

        // For Subscriber type, subscriber ID is required
        int subscriberId = 0;
        if ("Subscriber".equalsIgnoreCase(customerType)) {
            String subIdStr = subscriberIdField.getText() == null ? "" : subscriberIdField.getText().trim();
            if (subIdStr.isEmpty()) {
                showAlert(AlertType.ERROR, "Missing Subscriber ID", "Please enter subscriber ID for subscriber reservations.");
                return;
            }
            try {
                subscriberId = Integer.parseInt(subIdStr);
                if (subscriberId <= 0) {
                    showAlert(AlertType.ERROR, "Invalid Subscriber ID", "Subscriber ID must be a positive number.");
                    return;
                }
            } catch (NumberFormatException e) {
                showAlert(AlertType.ERROR, "Invalid Subscriber ID", "Please enter a valid subscriber ID (numbers only).");
                return;
            }
        }

     // Only require phone/email for Casual reservations
        if ("Casual".equalsIgnoreCase(customerType)) {

            if (phone.isEmpty()) {
                showAlert(AlertType.ERROR, "Missing Phone", "Phone number is required.");
                return;
            }

            // Validate phone number - must be exactly 10 digits and start with 0
            if (!phone.matches("0\\d{9}")) {
                showAlert(AlertType.ERROR, "Invalid Phone",
                        "Phone number must contain exactly 10 digits and start with 0.");
                return;
            }

            if (email.isEmpty()) {
                showAlert(AlertType.ERROR, "Missing Email", "Email address is required.");
                return;
            }

            // Validate email format - must end with @provider.com or @provider.co.il
            if (!isValidEmail(email)) {
                showAlert(AlertType.ERROR, "Invalid Email",
                        "Email must be in format: name@provider.com or name@provider.co.il");
                return;
            }
        }

        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(hour, minute));
        LocalDateTime end = start.plusMinutes(RES_DURATION_MIN);

        if (!isAvailable(start, end)) {
            showAlert(AlertType.WARNING, "Slot Not Available",
                    "The selected slot is not available. Please choose another time.");
            return;
        }

        // Generate confirmation code
        currentConfirmationCode = generateConfirmation(start, guests);

        // Determine customer type for database
        String cType = "Casual";
        if ("Subscriber".equalsIgnoreCase(customerType)) {
            cType = "Subscriber";
            System.out.println("DEBUG onBook: Creating Subscriber reservation with ID=" + subscriberId);
        } else {
            cType = "Casual";
            System.out.println("DEBUG onBook: Creating Casual reservation");
        }
        if ("Subscriber".equalsIgnoreCase(cType)) {
            phone = "";
            email = "";
        }

        // Protocol: #CREATE_RESERVATION <numGuests> <date> <time> <code> <subscriberId> <phone> <email> <role>
        String command = "#CREATE_RESERVATION " + guests + " "
                + start.format(DATE_FMT) + " "
                + start.format(TIME_WITH_SECONDS_FMT) + " "
                + currentConfirmationCode + " " + subscriberId + " " + phone + " " + email + " " + cType;

        System.out.println("DEBUG onBook: Sending command: " + command);

        if (ClientUI.chat == null) {
            showAlert(AlertType.ERROR, "Connection Error", "Not connected to server.");
            return;
        }

        ClientUI.chat.handleMessageFromClientUI(command);

        // Show generated code immediately
        Platform.runLater(() -> confirmationField.setText(currentConfirmationCode));
    }

    /**
     * Generates a short confirmation code.
     */
    private String generateConfirmation(LocalDateTime start, int guests) {
        String randomPart = String.format("%06d", (int) (Math.random() * 1_000_000));
        return "RES" + randomPart;
    }

    /**
     * Validates email format - must end with @provider.com or @provider.co.il
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // Must contain @ and end with .com or .co.il
        return email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.(com|co\\.il)$");
    }

    /**
     * Callback when server responds to reservation creation.
     */
    public void onBookingResponse(String response) {
        System.out.println("DEBUG: onBookingResponse called with: " + response);

        if (response != null && response.startsWith("RESERVATION_CREATED")) {
            System.out.println("DEBUG: Processing RESERVATION_CREATED response");
            String[] parts = response.split("\\|");
            int resId = -1;

            if (parts.length >= 2) {
                try {
                    resId = Integer.parseInt(parts[1]);
                    currentReservationId = resId;
                    System.out.println("DEBUG: Reservation ID from server: " + resId);
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse reservation ID: " + e.getMessage());
                }
            }

            // Add booked time to local cache
            LocalDate date = datePicker.getValue();
            int hour = hourSpinner.getValue();
            int minute = minuteSpinner.getValue();
            LocalTime bookedTime = LocalTime.of(hour, minute);

            if (date != null && date.equals(datePicker.getValue())) {
                bookedTimesForDate.add(bookedTime);
                System.out.println("DEBUG: Added booked time to local cache: " + bookedTime);
            }

            if (resId > 0) {
                System.out.println("DEBUG: Setting reservation ID field to: " + resId);
                reservationIdField.setText(String.valueOf(resId));
            }
            confirmationField.setText(currentConfirmationCode);

            // Clear fields for next reservation
            phoneField.setText("");
            emailField.setText("");

            showAlert(AlertType.INFORMATION, "Reservation Confirmed",
                    "Reservation created successfully!\n\n" +
                            "Reservation ID: " + resId + "\n" +
                            "Confirmation Code: " + currentConfirmationCode + "\n" +
                            "Customer Type: " + customerTypeCombo.getValue());

        } else {
            System.out.println("ERROR: Booking failed: " + response);
            reservationIdField.setText("");
            confirmationField.setText("");
            showAlert(AlertType.ERROR, "Booking Failed", "Failed to create reservation: " + response);
        }
    }

    /**
     * Handler when server sends reservation list for a date.
     */
    public void onReservationsReceived(String response) {
        System.out.println("DEBUG: onReservationsReceived: " + response);

        if (response == null || response.equals("RESERVATIONS_FOR_DATE|EMPTY")) {
            bookedTimesForDate.clear();
            availableTables.setItems(FXCollections.observableArrayList("All tables available"));
            return;
        }

        if (response.startsWith("RESERVATIONS_FOR_DATE|")) {
            String data = response.substring("RESERVATIONS_FOR_DATE|".length());
            String[] reservations = data.split("~");

            bookedTimesForDate.clear();
            List<String> tableInfo = new ArrayList<>();

            for (String res : reservations) {
                String[] parts = res.split(",");
                if (parts.length >= 6) {
                    try {
                        LocalTime time = LocalTime.parse(parts[1]);
                        bookedTimesForDate.add(time);
                        int guests = Integer.parseInt(parts[4]);
                        String status = parts[5];
                        
                        tableInfo.add(String.format("%s - %d guests (%s)", time, guests, status));
                    } catch (Exception e) {
                        System.err.println("Error parsing reservation: " + e.getMessage());
                    }
                }
            }

            if (tableInfo.isEmpty()) {
                availableTables.setItems(FXCollections.observableArrayList("All tables available"));
            } else {
                availableTables.setItems(FXCollections.observableArrayList(tableInfo));
            }
        }
    }

    /**
     * Sets navigation parameters for returning to previous screen.
     */
    public void setReturnPath(String fxml, String title, String user, String role) {
        this.returnScreenFXML = fxml;
        this.returnTitle = title;
        this.currentUserName = user;
        this.currentUserRole = role;
        System.out.println("DEBUG: Return path set to " + fxml);
    }

    /**
     * Handler for Back button.
     */
    @FXML
    private void onBack(ActionEvent event) {
        ClientUIController.clearActiveStaffReservationController();
        navigateBack();
    }

    /**
     * Handler for Close button.
     */
    @FXML
    private void onClose(ActionEvent event) {
        ClientUIController.clearActiveStaffReservationController();
        Node node = (Node) event.getSource();
        Stage stage = (Stage) node.getScene().getWindow();
        stage.close();
    }

    /**
     * Navigates back to the previous screen.
     */
    private void navigateBack() {
        try {
            FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/ClientGUI/view/" + returnScreenFXML)
            );
            Parent root = loader.load();
            Object controller = loader.getController();

            // Restore user context based on role
            if ("Manager".equalsIgnoreCase(currentUserRole) && controller instanceof ManagerUIController) {
                ManagerUIController managerCtrl = (ManagerUIController) controller;
                managerCtrl.setManagerName(currentUserName);
            } else if ("Representative".equalsIgnoreCase(currentUserRole) && controller instanceof RepresentativeMenuUIController) {
                RepresentativeMenuUIController repCtrl = (RepresentativeMenuUIController) controller;
                repCtrl.setRepresentativeName(currentUserName);
            }

            Stage stage = (Stage) datePicker.getScene().getWindow();
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.setTitle(returnTitle);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Navigation Error", "Could not navigate back: " + e.getMessage());
        }
    }

    /**
     * Shows an alert dialog.
     */
    private void showAlert(AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Utility method to load FXML files.
     */
    private FXMLLoader loaderFor(String fxmlFileName) throws Exception {
        return new javafx.fxml.FXMLLoader(
                getClass().getResource("/ClientGUI/view/" + fxmlFileName)
        );
    }
}
