package ClientGUI.controller;

import common.ChatIF;
import javafx.application.Platform;


/**
 * A centralized message dispatcher responsible for routing server responses 
 * to the currently active user interface controller.
 * <p>
 * This class implements the {@link ChatIF} interface to intercept all incoming 
 * network messages. It decouples the networking layer from the UI logic by:
 * <ol>
 * <li>Ensuring all message processing occurs on the JavaFX Application Thread 
 * via {@link Platform#runLater(Runnable)}.</li>
 * <li>Identifying the active screen/controller based on the application state.</li>
 * <li>Analyzing the message protocol (prefix) and delegating it to the specific handler method.</li>
 * </ol>
 */
public class ClientMessageRouter implements ChatIF {

	/**
     * Entry point for incoming messages from the network client.
     * <p>
     * Logs the message for debugging purposes and schedules the {@link #route(String)} 
     * method to run on the JavaFX Application Thread. This ensures that any UI 
     * updates resulting from this message will be thread-safe.
     *
     * @param message The raw string message received from the server.
     */
    @Override
    public void display(String message) {
        System.out.println("ROUTER display(): " + message);
        Platform.runLater(() -> route(message));
    }

    
    /**
     * Analyzes the message content and dispatches it to the appropriate active controller.
     * <p>
     * The routing logic follows a hierarchy of checks:
     * <ol>
     * <li><b>Staff Reservation:</b> Checks if a staff member is currently making a reservation.</li>
     * <li><b>Customer Reservation:</b> Checks if a customer is currently making a reservation.</li>
     * <li><b>Table Receipt:</b> Checks if the table allocation screen is active.</li>
     * <li><b>Representative View:</b> Routes administrative data (waiting lists, reports).</li>
     * <li><b>Main Controller:</b> Fallback for general login/navigation messages.</li>
     * </ol>
     * If no suitable controller is found, the message is logged to the console.
     *
     * @param message The message to parse and route.
     */
    private void route(String message) {
        if (message == null) return;

        // Debug log for opening hours messages
        if (message.startsWith("OPENING_HOURS|")) {
            System.out.println("CLIENT ROUTER: Received OPENING_HOURS message: " + message);
        }

        // 1) Route to active staff reservation window (for creating customer reservations)
        StaffReservationUIController sr = ClientUIController.getActiveStaffReservationController();
        if (sr != null) {
            if (message.startsWith("RESERVATIONS_FOR_DATE|")) { sr.onReservationsReceived(message); return; }
            if (message.startsWith("OPENING_HOURS|")) { 
                System.out.println("CLIENT ROUTER: Routing OPENING_HOURS to StaffReservationUIController");
                sr.onOpeningHoursReceived(message); 
                return; 
                
            }
            if (message.startsWith("RESERVATION_CREATED")) { sr.onBookingResponse(message); return; }
            if (message.startsWith("RESERVATION_FAILED|")) { sr.onBookingResponse(message); return; }

            if (message.startsWith("RESERVATION_CANCELED")
                    || message.startsWith("ERROR|RESERVATION_NOT_FOUND")
                    || message.startsWith("ERROR|CANCEL")) { return; }
        }
       
        // 2) Route to active reservation window (if exists)
        ReservationUIController r = ClientUIController.getActiveReservationController();
        
        if (r != null) {
            if (message.startsWith("RESERVATIONS_FOR_DATE|")) { r.onReservationsReceived(message); return; }
            if (message.startsWith("OPENING_HOURS|")) { r.onOpeningHoursReceived(message); return; }
            if (message.startsWith("AVAILABILITY|")) { r.onAvailabilityResponse(message); return; }

            if (message.startsWith("AVAILABILITY|")) { 
                r.onAvailabilityResponse(message); 
                return; 
            }

            if (message.startsWith("RESERVATION_CREATED")) { r.onBookingResponse(message); return; }
            if (message.startsWith("RESERVATION_FAILED|")) { r.onBookingResponse(message); return; }

            if (message.startsWith("RESERVATION_CANCELED")
                    || message.startsWith("ERROR|RESERVATION_NOT_FOUND")
                    || message.startsWith("ERROR|CANCEL")) { r.onCancelResponse(message); return; }
        }

        // 3) Route to active receive-table window (if exists)
        ReceiveTableUIController t = ClientUIController.getActiveReceiveTableController();
        if (t != null) {

            if (message.startsWith("SUBSCRIBER_DATA_RESPONSE|")) {
                t.onSubscriberDataReceived(message);
                return;
            }            if (message.startsWith("TABLE_ASSIGNED|")
                    || message.equals("NO_TABLE_AVAILABLE")
                    || message.equals("INVALID_CONFIRMATION_CODE") 
            	|| message.equals("RESERVATION_ALREADY_USED") 
            	|| message.equals("RESERVATION_ALREADY_USED")
                || message.equals("RESERVATION_NOT_FOR_TODAY")) { 
                t.onReceiveTableResponse(message);
                return;
            }
        }
        //Route to Representative View Details
        ClientGUI.controller.RepresentativeViewDetailsUIController rep =
                ClientGUI.controller.RepresentativeViewDetailsUIController.instance;
        if (rep != null) {
            if (message.startsWith("WAITING_LIST|")) {
                rep.updateWaitingListFromMessage(message);
                return;
            }
            if (message.startsWith("SUBSCRIBERS_LIST|")) {
                rep.updateSubscribersFromMessage(message);
                return;
            }
            if (message.startsWith("ACTIVE_RESERVATIONS|")) {
                rep.updateReservationsFromMessage(message);
                return;
            }
        }

        // 4) Route to main UI controller if loaded
        ClientUIController main = ClientUIController.getMainController();
        if (main != null) {
            main.handleMainScreenServerMessageFromRouter(message);
            return;
        }

     // 4) Route to Reports Screen
        if (message.startsWith("REPORTS_DATA|") || message.startsWith("ERROR|REPORTS")) {
            if (ClientUIController.activeReportsController != null) {
                ClientUIController.activeReportsController.updateReportsData(message);
            }
            return;
        }
        
        // 5) Fallback: no UI loaded yet
        System.out.println("SERVER (no UI yet): " + message);
    }
}
