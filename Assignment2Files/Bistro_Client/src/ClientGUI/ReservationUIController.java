package ClientGUI;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import entities.Subscriber;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller for the Reservation UI (JavaFX).
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Collect user input (date/time/guests/contact details).</li>
 *   <li>Send protocol messages to the server using the shared {@link ClientUI#chat} connection.</li>
 *   <li>Maintain a local cache of booked times per date to perform fast availability checks.</li>
 *   <li>React to asynchronous server responses routed by {@link ClientUIController}.</li>
 * </ul>
 *
 * <p><b>Asynchronous design note:</b>
 * OCSF responses arrive asynchronously. Therefore this controller does not "wait" for server replies.
 * Instead, {@link ClientUIController} routes incoming messages to these callbacks:
 * {@link #onReservationsReceived(String)}, {@link #onBookingResponse(String)},
 * {@link #onCancelResponse(String)}.</p>
 *
 * <p><b>Server protocol messages used here:</b>
 * <ul>
 *   <li>{@code #GET_RESERVATIONS_BY_DATE yyyy-MM-dd}</li>
 *   <li>{@code #CREATE_RESERVATION <numGuests> <yyyy-MM-dd> <HH:mm:ss> <confirmationCode> <subscriberId> <phone> <email>}</li>
 * </ul>
 *
 * <p><b>Expected server responses handled here:</b>
 * <ul>
 *   <li>{@code RESERVATIONS_FOR_DATE|yyyy-MM-dd|HH:mm|HH:mm|...}</li>
 *   <li>{@code RESERVATION_CREATED|<reservationId>}</li>
 *   <li>{@code RESERVATION_CANCELED}</li>
 *   <li>{@code ERROR|RESERVATION_NOT_FOUND}</li>
 * </ul>
 *
 * @author Bistro Team
 */
public class ReservationUIController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_WITH_SECONDS_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int RES_DURATION_MIN = 120;
    private static final int OPEN_HOUR = 9;
    private static final int CLOSE_HOUR = 23;
    private static final int MIN_NOTICE_MIN = 60;
    private static final int MAX_DAYS_AHEAD = 30;

    // -------------------- FXML fields --------------------

    @FXML private Spinner<Integer> hourSpinner;
    @FXML private Spinner<Integer> minuteSpinner;
    @FXML private Spinner<Integer> guestSpinner;

    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField reservationIdField;
    @FXML private TextField confirmationField;

    @FXML private ListView<String> alternativesList;
    @FXML private javafx.scene.control.DatePicker datePicker;

    // State & caching 

    /** Booked times for the currently selected date (used by availability checks). */
    private Set<LocalTime> bookedTimesForDate = new HashSet<>();

    /** Cache of booked times per date to avoid redundant server requests. */
    private final Map<LocalDate, Set<LocalTime>> bookedTimesCache = new java.util.HashMap<>();

    /** Last generated confirmation code for the current booking flow. */
    private String currentConfirmationCode = null;

    /** Last reservation id received from the server for the current booking flow. */
    private int currentReservationId = -1;

    /** Deferred availability check requested while waiting for booked-times data. */
    private LocalDateTime pendingCheckDateTime = null;

    /** Indicates that an availability check is waiting for data for its date. */
    private boolean pendingCheckRequested = false;

    /** Currently logged-in subscriber (null for guests). */
    private Subscriber currentSubscriber = null;

    // JavaFX lifecycle

    /**
     * JavaFX controller initialization.
     *
     * <p>Registers this controller as active so {@link ClientUIController} can route
     * server messages to it.</p>
     *
     * <p>Initializes spinners, sets default date, attaches UI listeners, and
     * fetches booked reservations for today's date.</p>
     */
    @FXML
    private void initialize() {
        // Register as active controller for routing server messages to callbacks
        ClientUIController.setActiveReservationController(this);

        hourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(9, 22, 12));
        minuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 45, 0, 15));
        guestSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 2));
        datePicker.setValue(LocalDate.now());

        // When the date changes, fetch booked times for the new date
        datePicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                fetchExistingReservations(newV);
            }
        });

        // Make alternatives list clickable
        alternativesList.setOnMouseClicked(event -> {
            String selected = alternativesList.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.equals("No alternatives found")) {
                applyAlternativeSelection(selected);
            }
        });

        // Fetch data for today at startup
        fetchExistingReservations(LocalDate.now());
    }

    // UI helpers

    /**
     * Parses a suggested alternative time and updates the date/time inputs accordingly.
     *
     * <p>Supported formats:
     * <ul>
     *   <li>{@code "HH:mm (same day)"}</li>
     *   <li>{@code "yyyy-MM-dd HH:mm"}</li>
     * </ul>
     *
     * @param suggestion suggestion string from the alternatives list
     */
    private void applyAlternativeSelection(String suggestion) {
        try {
            LocalDate newDate = datePicker.getValue();
            LocalTime newTime = null;

            if (suggestion.contains("(same day)")) {
                // Format: "HH:mm (same day)"
                String timeStr = suggestion.substring(0, suggestion.indexOf(" (")).trim();
                newTime = LocalTime.parse(timeStr, TIME_FMT);
            } else if (suggestion.contains(" ")) {
                // Format: "yyyy-MM-dd HH:mm"
                String[] parts = suggestion.split(" ");
                newDate = LocalDate.parse(parts[0], DATE_FMT);
                newTime = LocalTime.parse(parts[1], TIME_FMT);
            }

            if (newDate != null && newTime != null) {
                datePicker.setValue(newDate);
                hourSpinner.getValueFactory().setValue(newTime.getHour());
                minuteSpinner.getValueFactory().setValue(newTime.getMinute());

                System.out.println("DEBUG: Applied alternative - Date: " + newDate + ", Time: " + newTime);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse alternative: " + suggestion + " - " + e.getMessage());
        }
    }

    /**
     * Shows a simple pop-up alert.
     *
     * @param type alert type
     * @param title window title
     * @param message message body
     */
    private void showAlert(AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Server requests

    /**
     * Requests all existing reservations for a given date from the server.
     *
     * <p>Protocol: {@code #GET_RESERVATIONS_BY_DATE yyyy-MM-dd}</p>
     *
     * @param date the date to query
     */
    private void fetchExistingReservations(LocalDate date) {
        if (ClientUI.chat == null) {
            System.out.println("ERROR: Not connected to server.");
            return;
        }

        String command = "#GET_RESERVATIONS_BY_DATE " + date.format(DATE_FMT);
        ClientUI.chat.handleMessageFromClientUI(command);
        System.out.println("DEBUG: Fetching reservations for " + date);
    }

    // Server response callbacks (routed)

    /**
     * Callback invoked by {@link ClientUIController} when the server responds with booked
     * reservation times for a specific date.
     *
     * <p>Expected format:
     * {@code RESERVATIONS_FOR_DATE|yyyy-MM-dd|HH:mm|HH:mm|...}</p>
     *
     * @param message the raw server message
     */
    public void onReservationsReceived(String message) {
        if (message == null || !message.startsWith("RESERVATIONS_FOR_DATE|")) {
            System.out.println("DEBUG: Invalid message or not RESERVATIONS_FOR_DATE: " + message);
            return;
        }

        String[] parts = message.split("\\|");
        if (parts.length < 2) return;

        LocalDate dateReceived;
        try {
            dateReceived = LocalDate.parse(parts[1], DATE_FMT);
        } catch (Exception e) {
            System.err.println("Failed to parse date from message: " + parts[1]);
            return;
        }

        final LocalDate finalDateReceived = dateReceived; // for lambda usage

        Set<LocalTime> bookedTimes = new HashSet<>();
        System.out.println("DEBUG: onReservationsReceived() - Received " + (parts.length - 2)
                + " booked times for " + dateReceived);

        if (parts.length > 2) {
            for (int i = 2; i < parts.length; i++) {
                try {
                    String timeStr = parts[i].trim();

                    // Handle HH:mm:ss or HH:mm
                    LocalTime bookedTime;
                    if (timeStr.split(":").length == 3) {
                        bookedTime = LocalTime.parse(timeStr, TIME_WITH_SECONDS_FMT);
                    } else {
                        bookedTime = LocalTime.parse(timeStr, TIME_FMT);
                    }

                    bookedTimes.add(bookedTime);
                    System.out.println("DEBUG: Added booked time: " + bookedTime);

                } catch (Exception e) {
                    System.err.println("Failed to parse booked time '" + parts[i] + "': " + e.getMessage());
                }
            }
        }

        // Cache booked times for this date
        bookedTimesCache.put(dateReceived, bookedTimes);

        // If this is the currently selected date, update active set
        LocalDate selectedDate = datePicker.getValue();
        if (selectedDate != null && selectedDate.equals(dateReceived)) {
            bookedTimesForDate = bookedTimes;
            System.out.println("DEBUG: Updated bookedTimesForDate for currently selected date");
        }

        Platform.runLater(() -> {
            updateAvailableHours();
            System.out.println("DEBUG: Reservations loaded silently");

            // If there is a pending availability check for this date, process it now
            if (pendingCheckRequested && pendingCheckDateTime != null
                    && pendingCheckDateTime.toLocalDate().equals(finalDateReceived)) {
                System.out.println("DEBUG: Processing pending availability check");
                pendingCheckRequested = false;
                processPendingAvailabilityCheck();
            }
        });
    }

    /**
     * Callback invoked by {@link ClientUIController} when the server responds to a
     * CREATE_RESERVATION request.
     *
     * <p>Expected formats:
     * <ul>
     *   <li>{@code RESERVATION_CREATED|<reservationId>}</li>
     *   <li>Any other string is treated as an error message</li>
     * </ul>
     *
     * @param response raw server response
     */
    public void onBookingResponse(String response) {
        System.out.println("DEBUG: onBookingResponse called with: " + response);
        // Note: No Platform.runLater here - caller (ClientMessageRouter.display) already ensures JavaFX thread
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

            // Add newly booked time to local set immediately
            LocalDate date = datePicker.getValue();
            int hour = hourSpinner.getValue();
            int minute = minuteSpinner.getValue();
            LocalTime bookedTime = LocalTime.of(hour, minute);

            if (date != null && date.equals(datePicker.getValue())) {
                bookedTimesForDate.add(bookedTime);
                System.out.println("DEBUG: Added booked time to local cache: " + bookedTime);
            }

            if (resId > 0) {
                reservationIdField.setText(String.valueOf(resId));
            }
            confirmationField.setText(currentConfirmationCode);

            phoneField.setText("");
            emailField.setText("");

            // Show confirmation after clearing fields
            showAlert(AlertType.INFORMATION, "Reservation Confirmed",
                    "Your reservation has been successfully created!\n\n" +
                            "Reservation ID: " + resId + "\n" +
                            "Confirmation Code: " + currentConfirmationCode);

            // Note: fetchExistingReservations is NOT called here to avoid duplicate requests.
            // The time slot is already added to bookedTimesForDate above, and UI will update automatically.

        } else {
            System.out.println("ERROR: Booking failed: " + response);
            reservationIdField.setText("");
            confirmationField.setText("");
            showAlert(AlertType.ERROR, "Booking Failed", "Failed to create reservation: " + response);
        }
    }

    /**
     * Callback invoked by {@link ClientUIController} when the server responds to a
     * CANCEL_RESERVATION request.
     *
     * @param response raw server response
     */
    public void onCancelResponse(String response) {
        // Note: No Platform.runLater here - caller (ClientUIController.display) already ensures JavaFX thread
        if (response != null && response.startsWith("RESERVATION_CANCELED")) {
            System.out.println("DEBUG: Reservation canceled successfully");

            confirmationField.setText("");
            reservationIdField.setText("");
            phoneField.setText("");
            emailField.setText("");

            showAlert(AlertType.INFORMATION, "Reservation Canceled",
                    "Your reservation has been successfully canceled and deleted from the system!");

            // Refresh reservations list for current date
            LocalDate date = datePicker.getValue();
            if (date != null) {
                bookedTimesCache.remove(date);
                fetchExistingReservations(date);
            }

        } else if (response != null && response.startsWith("ERROR|RESERVATION_NOT_FOUND")) {
            System.out.println("ERROR: Reservation not found with given code");
            showAlert(AlertType.ERROR, "Not Found", "No reservation found with this confirmation code.");
        } else {
            System.out.println("ERROR: Cancel failed: " + response);
            showAlert(AlertType.ERROR, "Cancel Failed", "Failed to cancel reservation: " + response);
        }
    }

    // Availability logic

    /**
     * Updates time input controls to stay within business constraints (opening hours,
     * max days ahead, and minimum notice).
     */
    private void updateAvailableHours() {
        LocalDate date = datePicker.getValue();
        if (date == null) return;

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        int selectedHour = hourSpinner.getValue();

        // If today, enforce minimum notice by shifting earliest hour
        if (date.equals(today) && selectedHour < now.getHour() + 1) {
            hourSpinner.getValueFactory().setValue(Math.max(OPEN_HOUR, now.getHour() + 1));
        }

        // Disable if beyond max days ahead
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            System.out.println("ERROR: Cannot book beyond " + MAX_DAYS_AHEAD + " days.");
            hourSpinner.setDisable(true);
        } else {
            hourSpinner.setDisable(false);
        }
    }

    /**
     * UI handler for availability check button.
     *
     * <p>If booked times for the selected date are not cached yet, this method requests
     * them from the server and defers the availability check until the response arrives.</p>
     *
     * @param event action event
     */
    @FXML
    private void onCheckAvailability(ActionEvent event) {
        LocalDate date = datePicker.getValue();
        int hour = hourSpinner.getValue();
        int minute = minuteSpinner.getValue();

        System.out.println("DEBUG: onCheckAvailability() called");
        System.out.println("  Date: " + date);
        System.out.println("  Time: " + hour + ":" + String.format("%02d", minute));
        System.out.println("  Booked times BEFORE check: " + bookedTimesForDate);

        if (date == null) {
            System.out.println("ERROR: No date selected");
            showAlert(AlertType.ERROR, "Invalid Input", "Please select a date.");
            return;
        }

        LocalDateTime checkDateTime = LocalDateTime.of(date, LocalTime.of(hour, minute));

        // If no cache, fetch and defer
        if (!bookedTimesCache.containsKey(date)) {
            System.out.println("DEBUG: Booked times not cached for " + date + ". Fetching and deferring check...");
            pendingCheckDateTime = checkDateTime;
            pendingCheckRequested = true;
            fetchExistingReservations(date);
            return;
        }

        // Cached, process now
        processAvailabilityCheck(checkDateTime);
    }

    /**
     * Performs the availability check (immediate or deferred).
     *
     * @param checkDateTime requested start date/time
     */
    private void processAvailabilityCheck(LocalDateTime checkDateTime) {
        LocalDateTime start = checkDateTime;
        LocalDateTime end = start.plusMinutes(RES_DURATION_MIN);

        if (!validateWindow(start)) return;

        boolean available = isAvailable(start, end);
        System.out.println("DEBUG: Availability result: " + available);

        if (available) {
            showAlert(AlertType.INFORMATION, "Slot Available",
                    "✓ The slot on " + start.toLocalDate() + " at " +
                            String.format("%02d:%02d", start.getHour(), start.getMinute()) +
                            " is available!\n\nYou can now book this reservation.");
            alternativesList.getItems().clear();
        } else {
            new Thread(() -> {
                suggestAlternatives(start);

                Platform.runLater(() -> {
                    StringBuilder message = new StringBuilder();
                    message.append("✗ The slot on ").append(start.toLocalDate()).append(" at ")
                            .append(String.format("%02d:%02d", start.getHour(), start.getMinute()))
                            .append(" is not available.\n\n")
                            .append("Suggested alternative times:\n\n");

                    if (alternativesList.getItems().isEmpty()
                            || alternativesList.getItems().get(0).equals("No alternatives found")) {
                        message.append("No alternatives available for your requested timeframe.");
                    } else {
                        for (String alt : alternativesList.getItems()) {
                            message.append("• ").append(alt).append("\n");
                        }
                        message.append("\nClick on any suggestion to auto-fill the form.");
                    }

                    showAlert(AlertType.WARNING, "Slot Not Available", message.toString());
                });
            }).start();
        }
    }

    /**
     * Processes a deferred availability check after the requested date's booked times arrive.
     */
    private void processPendingAvailabilityCheck() {
        if (pendingCheckDateTime != null) {
            System.out.println("DEBUG: Processing deferred availability check for " + pendingCheckDateTime);
            processAvailabilityCheck(pendingCheckDateTime);
            pendingCheckDateTime = null;
        }
    }

    /**
     * Validates that the requested booking time is within allowed window constraints.
     *
     * @param start requested start time
     * @return true if valid; false otherwise (and shows an alert)
     */
    private boolean validateWindow(LocalDateTime start) {
        LocalDateTime now = LocalDateTime.now();

        if (start.isBefore(now.plusMinutes(MIN_NOTICE_MIN))) {
            System.out.println("ERROR: Must book at least " + MIN_NOTICE_MIN + " minutes ahead.");
            showAlert(AlertType.ERROR, "Invalid Booking Time",
                    "You must book at least " + MIN_NOTICE_MIN + " minutes in advance.");
            return false;
        }

        if (start.isAfter(now.plusDays(MAX_DAYS_AHEAD))) {
            System.out.println("ERROR: Can book up to " + MAX_DAYS_AHEAD + " days ahead.");
            showAlert(AlertType.ERROR, "Date Too Far",
                    "You can only book up to " + MAX_DAYS_AHEAD + " days in advance.");
            return false;
        }

        return true;
    }

    /**
     * Checks whether a reservation slot is available by ensuring it does not overlap
     * with any booked reservation slot (each assumed to last {@link #RES_DURATION_MIN} minutes).
     *
     * @param start requested slot start
     * @param end requested slot end
     * @return true if available; false if overlaps with an existing reservation
     */
    private boolean isAvailable(LocalDateTime start, LocalDateTime end) {
        for (LocalTime booked : bookedTimesForDate) {
            LocalDateTime bookedStart = LocalDateTime.of(start.toLocalDate(), booked);
            LocalDateTime bookedEnd = bookedStart.plusMinutes(RES_DURATION_MIN);

            if (start.isBefore(bookedEnd) && end.isAfter(bookedStart)) {
                System.out.println("DEBUG: Overlap detected - Requested: " + start + " to " + end
                        + ", Booked: " + bookedStart + " to " + bookedEnd);
                return false;
            }
        }
        System.out.println("DEBUG: No conflicts for slot " + start + " to " + end);
        return true;
    }

    /**
     * Suggests alternative reservation times to the user, attempting same-day first and then
     * the next few days using cached data only.
     *
     * <p><b>Important:</b> This method does not block waiting for server data. If data for a day
     * is not cached, it simply skips that day. (This keeps the UI responsive and avoids polling.)</p>
     *
     * @param desired desired reservation start date/time
     */
    private void suggestAlternatives(LocalDateTime desired) {
        Platform.runLater(() -> alternativesList.getItems().clear());

        LocalDate desiredDate = desired.toLocalDate();
        int suggestions = 0;

        System.out.println("DEBUG: Looking for alternatives starting from " + desired);
        System.out.println("DEBUG: Current booked times for " + desiredDate + ": " + bookedTimesForDate);

        // Same-day alternatives: from desired hour+1 onward
        Set<LocalTime> sameDayBookedTimes = bookedTimesForDate;

        for (int hour = desired.getHour() + 1; hour <= CLOSE_HOUR && suggestions < 4; hour++) {
            LocalDateTime alt = LocalDateTime.of(desiredDate, LocalTime.of(hour, 0));
            LocalDateTime altEnd = alt.plusMinutes(RES_DURATION_MIN);

            if (altEnd.getHour() > CLOSE_HOUR || (altEnd.getHour() == CLOSE_HOUR && altEnd.getMinute() > 0)) {
                break;
            }

            boolean available = true;
            for (LocalTime booked : sameDayBookedTimes) {
                LocalDateTime bookedStart = LocalDateTime.of(desiredDate, booked);
                LocalDateTime bookedEnd = bookedStart.plusMinutes(RES_DURATION_MIN);
                if (alt.isBefore(bookedEnd) && altEnd.isAfter(bookedStart)) {
                    available = false;
                    break;
                }
            }

            if (available) {
                String suggestion = String.format("%02d:%02d (same day)", hour, 0);
                final String finalSuggestion = suggestion;
                Platform.runLater(() -> alternativesList.getItems().add(finalSuggestion));
                suggestions++;
                System.out.println("DEBUG: ✓ Found same-day alternative: " + suggestion);
            }
        }

        // Next days: use cache only (no waiting)
        if (suggestions < 4) {
            for (int day = 1; day <= 3 && suggestions < 4; day++) {
                LocalDate nextDate = desiredDate.plusDays(day);
                Set<LocalTime> nextDayBookedTimes = bookedTimesCache.get(nextDate);

                if (nextDayBookedTimes == null) {
                    System.out.println("DEBUG: No cached booked times for " + nextDate + ", skipping suggestions for that day.");
                    continue;
                }

                for (int hour = OPEN_HOUR; hour <= CLOSE_HOUR && suggestions < 4; hour++) {
                    LocalDateTime alt = LocalDateTime.of(nextDate, LocalTime.of(hour, 0));
                    LocalDateTime altEnd = alt.plusMinutes(RES_DURATION_MIN);

                    if (altEnd.getHour() > CLOSE_HOUR || (altEnd.getHour() == CLOSE_HOUR && altEnd.getMinute() > 0)) {
                        continue;
                    }

                    boolean available = true;
                    for (LocalTime booked : nextDayBookedTimes) {
                        LocalDateTime bookedStart = LocalDateTime.of(nextDate, booked);
                        LocalDateTime bookedEnd = bookedStart.plusMinutes(RES_DURATION_MIN);
                        if (alt.isBefore(bookedEnd) && altEnd.isAfter(bookedStart)) {
                            available = false;
                            break;
                        }
                    }

                    if (available) {
                        String suggestion = alt.toLocalDate() + " " + String.format("%02d:%02d", hour, 0);
                        final String finalSuggestion = suggestion;
                        Platform.runLater(() -> alternativesList.getItems().add(finalSuggestion));
                        suggestions++;
                        System.out.println("DEBUG: ✓ Found next-day alternative: " + suggestion);
                    }
                }
            }
        }

        if (suggestions == 0) {
            Platform.runLater(() -> alternativesList.getItems().add("No alternatives found"));
        }
    }

    // Booking logic

    /**
     * UI handler for booking a reservation.
     *
     * <p>Validates input, checks availability locally, generates a confirmation code,
     * and sends {@code #CREATE_RESERVATION} to the server.</p>
     *
     * @param event action event
     */
    @FXML
    private void onBook(ActionEvent event) {
        LocalDate date = datePicker.getValue();
        int hour = hourSpinner.getValue();
        int minute = minuteSpinner.getValue();
        Integer guests = guestSpinner.getValue();

        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();

        if (date == null) {
            showAlert(AlertType.ERROR, "Invalid Input", "Please select a date.");
            return;
        }
        if (phone.isEmpty()) {
            showAlert(AlertType.ERROR, "Missing Phone", "Phone number is required.");
            return;
        }
        if (email.isEmpty()) {
            showAlert(AlertType.ERROR, "Missing Email", "Email address is required.");
            return;
        }
        if (!email.contains("@")) {
            showAlert(AlertType.ERROR, "Invalid Email", "Please enter a valid email address.");
            return;
        }

        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(hour, minute));
        LocalDateTime end = start.plusMinutes(RES_DURATION_MIN);

        if (!validateWindow(start)) return;

        boolean available = isAvailable(start, end);
        if (!available) {
            suggestAlternatives(start);
            showAlert(AlertType.WARNING, "Slot Not Available",
                    "The selected slot is not available. Please choose from the suggested alternatives.");

            // Open waiting-list UI and pass attempted reservation context (include subscriber if present)
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                        getClass().getResource("ClientWaitingList.fxml"));
                javafx.scene.Parent root = loader.load();
                ClientGUI.ClientWaitingListController ctrl = loader.getController();

                String timeStr = String.format("%02d:%02d", hour, minute);
                // area is not selected in this UI; pass null. The controller should handle nulls.
                ctrl.setReservationContext(datePicker.getValue(), timeStr, null, guestSpinner.getValue(), currentSubscriber);

                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(new javafx.scene.Scene(root));
                stage.setTitle("Waiting List - Alternatives");
                stage.show();
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }

            return;
        }

        // Generate SHORT confirmation code (max 10 chars)
        currentConfirmationCode = generateConfirmation(start, guests);

        // Protocol:
        // #CREATE_RESERVATION <numGuests> <yyyy-MM-dd> <HH:mm:ss> <confirmationCode> <subscriberId> <phone> <email>
        String command = "#CREATE_RESERVATION " + guests + " "
                + start.format(DATE_FMT) + " "
                + start.format(TIME_WITH_SECONDS_FMT) + " "
                + currentConfirmationCode + " 0 " + phone + " " + email;

        if (ClientUI.chat == null) {
            showAlert(AlertType.ERROR, "Connection Error", "Not connected to server.");
            return;
        }

        ClientUI.chat.handleMessageFromClientUI(command);

        // Show generated code immediately (server will confirm with reservation id)
        Platform.runLater(() -> confirmationField.setText(currentConfirmationCode));
    }

    /**
     * Generates a short confirmation code.
     *
     * <p>Format: {@code RES + 6 random digits}</p>
     *
     * @param start reservation start date/time (currently not used but kept for future improvements)
     * @param guests number of guests (currently not used but kept for future improvements)
     * @return confirmation code string (max ~9 characters)
     */
    private String generateConfirmation(LocalDateTime start, int guests) {
        String randomPart = String.format("%06d", (int) (Math.random() * 1_000_000));
        return "RES" + randomPart;
    }

    // Window close

    /**
     * Closes the reservation window and unregisters this controller from routing.
     *
     * @param event action event
     */
    @FXML
    private void onClose(ActionEvent event) {
        ClientUIController.clearActiveReservationController();

        Node node = (Node) event.getSource();
        Stage stage = (Stage) node.getScene().getWindow();
        stage.close();
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
     * Sets the logged-in subscriber object for this controller.
     * If a subscriber is provided, the controller will treat the session as a subscriber.
     * @param subscriber subscriber object or null for guests
     */
    public void setSubscriber(Subscriber subscriber) {
        this.currentSubscriber = subscriber;
        if (subscriber != null) {
            this.currentUserName = subscriber.getUserName();
            this.currentUserRole = "Subscriber";
        }
    }

    /**
     * Generic setter for user context (username + role) for non-subscriber callers.
     */
    public void setUserContext(String username, String role) {
        this.currentUserName = username;
        this.currentUserRole = role;
    }

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

                // 2. Representative Dashboard
                case RepresentativeMenuUIController c -> 
                    c.setRepresentativeName(currentUserName);

                // 3. Subscriber Area
                case LoginSubscriberUIController c -> 
                    c.setSubscriberName(currentUserName);

                // 4. Guest Menu
                case LoginGuestUIController c -> {
                    // Usually no state to restore for guest, just navigation
                }

                default -> {
                    // Handle unknown controller types
                    System.out.println("Returning to generic screen: " + controller.getClass().getSimpleName());
                }
            }

            // Unregister this controller from the ClientUI routing map
            ClientUIController.clearActiveReservationController();

            javafx.stage.Stage stage = (javafx.stage.Stage)((javafx.scene.Node)event.getSource()).getScene().getWindow();
            stage.setTitle(returnTitle);
            stage.setScene(new javafx.scene.Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
