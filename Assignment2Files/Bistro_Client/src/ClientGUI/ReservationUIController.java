package ClientGUI;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import client.ChatClient;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller class for the Reservation UI.
 * Handles user interactions for checking availability and booking reservations.
 */
public class ReservationUIController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int RES_DURATION_MIN = 120;
    private static final int OPEN_HOUR = 9;
    private static final int CLOSE_HOUR = 23;
    private static final int MIN_NOTICE_MIN = 60;
    private static final int MAX_DAYS_AHEAD = 30;

    @FXML
    private Spinner<Integer> hourSpinner;
    @FXML
    private Spinner<Integer> minuteSpinner;
    @FXML
    private Spinner<Integer> guestSpinner;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField reservationIdField;
    @FXML
    private TextField confirmationField;
    @FXML
    private ListView<String> alternativesList;
    @FXML
    private javafx.scene.control.DatePicker datePicker;

    private Set<LocalTime> bookedTimesForDate = new HashSet<>();
    private Map<LocalDate, Set<LocalTime>> bookedTimesCache = new java.util.HashMap<>();
    private ChatClient chatClient;
    private String currentConfirmationCode = null;
    private int currentReservationId = -1;
    
    // Pending availability check (deferred until data arrives)
    private LocalDateTime pendingCheckDateTime = null;
    private boolean pendingCheckRequested = false;

    @FXML
    private void initialize() {
        hourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(9, 22, 12));
        minuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 45, 0, 15));
        guestSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 2));
        datePicker.setValue(LocalDate.now());
        
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
        
        // Fetch on startup
        fetchExistingReservations(LocalDate.now());
    }
    
    /**
     * Parse a suggestion and update date/time fields accordingly.
     * Format can be: "HH:mm (same day)" or "yyyy-MM-dd HH:mm"
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
                // Update date picker
                datePicker.setValue(newDate);
                
                // Update time spinners
                hourSpinner.getValueFactory().setValue(newTime.getHour());
                minuteSpinner.getValueFactory().setValue(newTime.getMinute());
                
                System.out.println("DEBUG: Applied alternative - Date: " + newDate + ", Time: " + newTime);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse alternative: " + suggestion + " - " + e.getMessage());
        }
    }


    public void setChatClient(ChatClient client) {
        this.chatClient = client;
    }

    /**
     * Fetch existing reservations for a given date from the server.
     */
    private void fetchExistingReservations(LocalDate date) {
        if (chatClient == null) {
            System.out.println("ERROR: Not connected to server.");
            return;
        }

        String command = "#GET_RESERVATIONS_BY_DATE " + date.format(DATE_FMT);
        try {
            chatClient.handleMessageFromClientUI(command);
            System.out.println("DEBUG: Fetching reservations for " + date);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR: Error fetching reservations: " + e.getMessage());
        }
    }

    /**
     * Called by ChatClient to receive the booked times for the selected date.
     * Format: RESERVATIONS_FOR_DATE|2025-01-15|09:00|10:30|14:00|...
     */
    public void onReservationsReceived(String message) {
        if (message == null || !message.startsWith("RESERVATIONS_FOR_DATE|")) {
            System.out.println("DEBUG: Invalid message or not RESERVATIONS_FOR_DATE: " + message);
            return;
        }

        String[] parts = message.split("\\|");
        if (parts.length < 2) return;
        
        LocalDate dateReceived = null;
        try {
            dateReceived = LocalDate.parse(parts[1], DATE_FMT);
        } catch (Exception e) {
            System.err.println("Failed to parse date from message: " + parts[1]);
            return;
        }
        
        final LocalDate finalDateReceived = dateReceived;  // Make final for lambda
        
        Set<LocalTime> bookedTimes = new HashSet<>();
        System.out.println("DEBUG: onReservationsReceived() - Received " + (parts.length - 2) + " booked times for " + dateReceived);
        
        if (parts.length > 2) {
            for (int i = 2; i < parts.length; i++) {
                try {
                    // Parse time - handle both HH:mm and HH:mm:ss formats
                    String timeStr = parts[i].trim();
                    LocalTime bookedTime;
                    if (timeStr.contains(":") && timeStr.split(":").length == 3) {
                        // HH:mm:ss format
                        bookedTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss"));
                    } else {
                        // HH:mm format
                        bookedTime = LocalTime.parse(timeStr, TIME_FMT);
                    }
                    bookedTimes.add(bookedTime);
                    System.out.println("DEBUG: Added booked time: " + bookedTime);
                } catch (Exception e) {
                    System.err.println("Failed to parse booked time '" + parts[i] + "': " + e.getMessage());
                }
            }
        }

        // Cache the booked times for this date
        bookedTimesCache.put(dateReceived, bookedTimes);
        System.out.println("DEBUG: Total unique booked times for " + dateReceived + ": " + bookedTimes.size());
        for (LocalTime t : bookedTimes) {
            System.out.println("  - " + t);
        }
        
        // If this is the currently selected date, update the active set
        LocalDate selectedDate = datePicker.getValue();
        if (selectedDate != null && selectedDate.equals(dateReceived)) {
            bookedTimesForDate = bookedTimes;
            System.out.println("DEBUG: Updated bookedTimesForDate for currently selected date");
        }

        Platform.runLater(() -> {
            updateAvailableHours();
            System.out.println("DEBUG: Reservations loaded silently");
            
            // If there's a pending availability check for this date, process it now
            if (pendingCheckRequested && pendingCheckDateTime != null && 
                pendingCheckDateTime.toLocalDate().equals(finalDateReceived)) {
                System.out.println("DEBUG: Processing pending availability check");
                pendingCheckRequested = false;
                processPendingAvailabilityCheck();
            }
        });
    }

    private void updateAvailableHours() {
        LocalDate date = datePicker.getValue();
        if (date == null) return;

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        int selectedHour = hourSpinner.getValue();

        // Check if selected hour is still valid
        if (date.equals(today) && selectedHour < now.getHour() + 1) {
            hourSpinner.getValueFactory().setValue(Math.max(OPEN_HOUR, now.getHour() + 1));
        }

        // Ensure valid range
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            System.out.println("ERROR: Cannot book beyond " + MAX_DAYS_AHEAD + " days.");
            hourSpinner.setDisable(true);
        } else {
            hourSpinner.setDisable(false);
        }
    }

    @FXML
    private void onCheckAvailability(ActionEvent event) {
        LocalDate date = datePicker.getValue();
        int hour = hourSpinner.getValue();
        int minute = minuteSpinner.getValue();
        Integer guests = guestSpinner.getValue();

        System.out.println("DEBUG: onCheckAvailability() called");
        System.out.println("  Date: " + date);
        System.out.println("  Time: " + hour + ":" + String.format("%02d", minute));
        System.out.println("  Booked times BEFORE check: " + bookedTimesForDate);

        if (date == null) {
            System.out.println("ERROR: No date selected");
            showAlert(AlertType.ERROR, "Invalid Input", "Please select a date.");
            return;
        }

        // Store the check for potential deferral
        LocalDateTime checkDateTime = LocalDateTime.of(date, LocalTime.of(hour, minute));

        // If booked times for this date aren't cached yet, fetch and defer the check
        if (!bookedTimesCache.containsKey(date)) {
            System.out.println("DEBUG: Booked times not cached for " + date + ". Fetching and deferring check...");
            pendingCheckDateTime = checkDateTime;
            pendingCheckRequested = true;
            fetchExistingReservations(date);
            return;  // Wait for data to arrive
        }

        // Data is already cached, process immediately
        processAvailabilityCheck(checkDateTime);
    }

    /**
     * Process the actual availability check (called either immediately or deferred).
     */
    private void processAvailabilityCheck(LocalDateTime checkDateTime) {
        LocalDateTime start = checkDateTime;
        LocalDateTime end = start.plusMinutes(RES_DURATION_MIN);

        if (!validateWindow(start)) return;

        boolean available = isAvailable(start, end);
        System.out.println("DEBUG: Availability result: " + available);
        
        if (available) {
            // Show success dialog
            showAlert(AlertType.INFORMATION, "Slot Available", 
                "✓ The slot on " + start.toLocalDate() + " at " + String.format("%02d:%02d", start.getHour(), start.getMinute()) + 
                " is available!\n\nYou can now book this reservation.");
            alternativesList.getItems().clear();
        } else {
            // Generate suggestions in background thread to avoid blocking UI thread
            new Thread(() -> {
                suggestAlternatives(start);
                
                // Show dialog on UI thread
                Platform.runLater(() -> {
                    StringBuilder message = new StringBuilder();
                    message.append("✗ The slot on ").append(start.toLocalDate()).append(" at ")
                           .append(String.format("%02d:%02d", start.getHour(), start.getMinute()))
                           .append(" is not available.\n\n")
                           .append("Suggested alternative times:\n\n");
                    
                    if (alternativesList.getItems().isEmpty() || alternativesList.getItems().get(0).equals("No alternatives found")) {
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
     * Process the pending availability check (called when deferred data arrives).
     */
    private void processPendingAvailabilityCheck() {
        if (pendingCheckDateTime != null) {
            System.out.println("DEBUG: Processing deferred availability check for " + pendingCheckDateTime);
            processAvailabilityCheck(pendingCheckDateTime);
            pendingCheckDateTime = null;
        }
    }
    
    /**
     * Show an alert dialog with custom title and message.
     */
    private void showAlert(AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

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
     * Check if a 2-hour slot starting at 'start' is available (doesn't overlap with any booked time).
     * A booked reservation runs for 2 hours. We need to check if our slot overlaps.
     */
    private boolean isAvailable(LocalDateTime start, LocalDateTime end) {
        for (LocalTime booked : bookedTimesForDate) {
            LocalDateTime bookedStart = LocalDateTime.of(start.toLocalDate(), booked);
            LocalDateTime bookedEnd = bookedStart.plusMinutes(RES_DURATION_MIN);
            
            // Check if there's any overlap
            if (start.isBefore(bookedEnd) && end.isAfter(bookedStart)) {
                System.out.println("DEBUG: Overlap detected - Requested: " + start + " to " + end + ", Booked: " + bookedStart + " to " + bookedEnd);
                return false;
            }
        }
        System.out.println("DEBUG: No conflicts for slot " + start + " to " + end);
        return true;
    }

    private void suggestAlternatives(LocalDateTime desired) {
        Platform.runLater(() -> alternativesList.getItems().clear());
        LocalDate desiredDate = desired.toLocalDate();
        int suggestions = 0;
        
        System.out.println("DEBUG: Looking for alternatives starting from " + desired);
        System.out.println("DEBUG: Current booked times for " + desiredDate + ": " + bookedTimesForDate);
        
        // Use the booked times we already have from the last fetch (no waiting)
        Set<LocalTime> sameDayBookedTimes = bookedTimesForDate;
        
        // First, try same day alternatives (hour by hour, forward from desired time + 1 hour)
        for (int hour = desired.getHour() + 1; hour <= CLOSE_HOUR && suggestions < 4; hour++) {
            LocalDateTime alt = LocalDateTime.of(desiredDate, LocalTime.of(hour, 0));
            LocalDateTime altEnd = alt.plusMinutes(RES_DURATION_MIN);
            
            // Check if end time fits before closing
            if (altEnd.getHour() > CLOSE_HOUR || (altEnd.getHour() == CLOSE_HOUR && altEnd.getMinute() > 0)) {
                break;
            }
            
            // Check if this time is available (no overlap with booked times)
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
        
        // Then try next few days - fetch data if needed with a brief wait
        if (suggestions < 4) {
            for (int day = 1; day <= 3 && suggestions < 4; day++) {
                LocalDate nextDate = desiredDate.plusDays(day);
                
                // Check if data is cached
                Set<LocalTime> nextDayBookedTimes = bookedTimesCache.get(nextDate);
                
                if (nextDayBookedTimes == null) {
                    // Data not cached, send request and wait briefly
                    System.out.println("DEBUG: Booked times not cached for " + nextDate + ". Sending request...");
                    String command = "#GET_RESERVATIONS_BY_DATE " + nextDate.format(DATE_FMT);
                    try {
                        chatClient.handleMessageFromClientUI(command);
                    } catch (Exception e) {
                        System.out.println("DEBUG: Could not request " + nextDate);
                    }
                    
                    // Wait for data to arrive (up to 3 seconds, checking every 100ms)
                    long startTime = System.currentTimeMillis();
                    while (System.currentTimeMillis() - startTime < 3000) {
                        nextDayBookedTimes = bookedTimesCache.get(nextDate);
                        if (nextDayBookedTimes != null) {
                            System.out.println("DEBUG: ✓ Got data for " + nextDate + " after " + 
                                (System.currentTimeMillis() - startTime) + "ms");
                            break;
                        }
                        try {
                            Thread.sleep(100);  // Check every 100ms
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    
                    // If still no data after wait, skip this date
                    if (nextDayBookedTimes == null) {
                        System.out.println("DEBUG: Timeout waiting for " + nextDate + ", skipping");
                        continue;
                    }
                }
                
                System.out.println("DEBUG: Checking " + nextDate + " with booked times: " + nextDayBookedTimes);
                
                // Now suggest available times for this day
                for (int hour = OPEN_HOUR; hour <= CLOSE_HOUR && suggestions < 4; hour++) {
                    LocalDateTime alt = LocalDateTime.of(nextDate, LocalTime.of(hour, 0));
                    LocalDateTime altEnd = alt.plusMinutes(RES_DURATION_MIN);
                    
                    // Check if end time fits
                    if (altEnd.getHour() > CLOSE_HOUR || (altEnd.getHour() == CLOSE_HOUR && altEnd.getMinute() > 0)) {
                        continue;
                    }
                    
                    // Check if time is available
                    boolean available = true;
                    for (LocalTime booked : nextDayBookedTimes) {
                        LocalDateTime bookedStart = LocalDateTime.of(nextDate, booked);
                        LocalDateTime bookedEnd = bookedStart.plusMinutes(RES_DURATION_MIN);
                        if (alt.isBefore(bookedEnd) && altEnd.isAfter(bookedStart)) {
                            available = false;
                            System.out.println("DEBUG: " + hour + ":00 NOT available - overlaps with booked");
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

    @FXML
    private void onBook(ActionEvent event) {
        LocalDate date = datePicker.getValue();
        int hour = hourSpinner.getValue();
        int minute = minuteSpinner.getValue();
        Integer guests = guestSpinner.getValue();
        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();

        System.out.println("DEBUG: onBook() called");
        System.out.println("  Date: " + date);
        System.out.println("  Time: " + hour + ":" + String.format("%02d", minute));
        System.out.println("  Guests: " + guests);
        System.out.println("  Phone: " + phone);
        System.out.println("  Email: " + email);
        System.out.println("  Booked times for date: " + bookedTimesForDate);

        if (date == null) {
            System.out.println("ERROR: No date selected");
            showAlert(AlertType.ERROR, "Invalid Input", "Please select a date.");
            return;
        }
        if (phone.isEmpty()) {
            System.out.println("ERROR: Phone is required");
            showAlert(AlertType.ERROR, "Missing Phone", "Phone number is required.");
            return;
        }
        if (email.isEmpty()) {
            System.out.println("ERROR: Email is required");
            showAlert(AlertType.ERROR, "Missing Email", "Email address is required.");
            return;
        }
        
        // Validate email format (basic check)
        if (!email.contains("@")) {
            System.out.println("ERROR: Invalid email format");
            showAlert(AlertType.ERROR, "Invalid Email", "Please enter a valid email address.");
            return;
        }

        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(hour, minute));
        LocalDateTime end = start.plusMinutes(RES_DURATION_MIN);

        System.out.println("DEBUG: Checking window validity for " + start);
        if (!validateWindow(start)) {
            System.out.println("DEBUG: Window validation failed");
            return;
        }

        System.out.println("DEBUG: Checking availability for " + start + " to " + end);
        boolean available = isAvailable(start, end);
        System.out.println("DEBUG: Availability check result: " + available);

        if (!available) {
            System.out.println("DEBUG: Slot not available, showing alternatives");
            suggestAlternatives(start);
            showAlert(AlertType.WARNING, "Slot Not Available",
                "The selected slot is not available. Please choose from the suggested alternatives.");
            return;
        }

        // Generate SHORT confirmation code (max 10 chars)
        currentConfirmationCode = generateConfirmation(start, guests);
        System.out.println("DEBUG: Generated confirmation code: " + currentConfirmationCode);

        // Send CREATE_RESERVATION to server with phone and email
        // Format: #CREATE_RESERVATION <numGuests> <yyyy-MM-dd> <HH:mm:ss> <confirmationCode> <subscriberId> <phone> <email>
        String command = "#CREATE_RESERVATION " + guests + " " + 
                         start.format(DATE_FMT) + " " + 
                         start.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + 
                         currentConfirmationCode + " 0 " + phone + " " + email;

        if (chatClient == null) {
            System.out.println("ERROR: Not connected to server");
            showAlert(AlertType.ERROR, "Connection Error", "Not connected to server.");
            return;
        }

        try {
            System.out.println("DEBUG: Sending command: " + command);
            chatClient.handleMessageFromClientUI(command);
            Platform.runLater(() -> {
                confirmationField.setText(currentConfirmationCode);
            });
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR: " + e.getMessage());
            showAlert(AlertType.ERROR, "Booking Error", "Failed to send booking request: " + e.getMessage());
        }
    }

    /**
     * Called when server responds to CREATE_RESERVATION.
     */
    public void onBookingResponse(String response) {
        Platform.runLater(() -> {
            if (response != null && response.startsWith("RESERVATION_CREATED")) {
                // Parse: RESERVATION_CREATED|<reservationId>
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
                
                // Add the newly booked time to our local cache immediately
                LocalDate date = datePicker.getValue();
                int hour = hourSpinner.getValue();
                int minute = minuteSpinner.getValue();
                LocalTime bookedTime = LocalTime.of(hour, minute);
                
                if (date != null && date.equals(datePicker.getValue())) {
                    bookedTimesForDate.add(bookedTime);
                    System.out.println("DEBUG: Added booked time to local cache: " + bookedTime);
                }
                
                // Display both reservation ID and confirmation code
                if (resId > 0) {
                    reservationIdField.setText(String.valueOf(resId));
                }
                confirmationField.setText(currentConfirmationCode);
                
                System.out.println("DEBUG: Reservation confirmed! ID: " + resId + ", Code: " + currentConfirmationCode);
                
                // Show success alert
                showAlert(AlertType.INFORMATION, "Reservation Confirmed",
                    "Your reservation has been successfully created!\n\n" +
                    "Reservation ID: " + resId + "\n" +
                    "Confirmation Code: " + currentConfirmationCode);
                
                // Clear input fields for next booking (keep ID and code visible)
                phoneField.setText("");
                emailField.setText("");
                
                // Refresh from server to ensure sync
                fetchExistingReservations(datePicker.getValue());
            } else {
                System.out.println("ERROR: Booking failed: " + response);
                reservationIdField.setText("");
                confirmationField.setText("");
                showAlert(AlertType.ERROR, "Booking Failed", "Failed to create reservation: " + response);
            }
        });
    }

    private String generateConfirmation(LocalDateTime start, int guests) {
        // Generate a VERY short unique confirmation code (max 10 chars)
        // Format: RES + 6 digit random
        String randompart = String.format("%06d", (int)(Math.random() * 1000000));
        return "RES" + randompart;
    }

    /**
     * Called when server responds to CANCEL_RESERVATION (if called from New Reservation window).
     */
    public void onCancelResponse(String response) {
        Platform.runLater(() -> {
            if (response != null && response.startsWith("RESERVATION_CANCELED")) {
                System.out.println("DEBUG: Reservation canceled successfully");
                confirmationField.setText("");
                reservationIdField.setText("");
                phoneField.setText("");
                emailField.setText("");
                
                showAlert(AlertType.INFORMATION, "Reservation Canceled",
                    "Your reservation has been successfully canceled and deleted from the system!");
                
                // Refresh the reservations list
                LocalDate date = datePicker.getValue();
                if (date != null) {
                    bookedTimesCache.remove(date);  // Clear cache so it gets refreshed
                    fetchExistingReservations(date);
                }
            } else if (response != null && response.startsWith("ERROR|RESERVATION_NOT_FOUND")) {
                System.out.println("ERROR: Reservation not found with given code");
                showAlert(AlertType.ERROR, "Not Found", "No reservation found with this confirmation code.");
            } else {
                System.out.println("ERROR: Cancel failed: " + response);
                showAlert(AlertType.ERROR, "Cancel Failed", "Failed to cancel reservation: " + response);
            }
        });
    }

    @FXML
    private void onClose(ActionEvent event) {
        ClientUIController.clearActiveReservationController();
        Node node = (Node) event.getSource();
        Stage stage = (Stage) node.getScene().getWindow();
        stage.close();
    }
}
