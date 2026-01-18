package ClientGUI.controller;

import common.ChatIF;
import javafx.application.Platform;


/**
 * Central router for all server messages.
 *
 * <p>This class contains no FXML fields and never touches UI nodes directly.
 * It dispatches messages to active controllers when available.</p>
 */
public class ClientMessageRouter implements ChatIF {

	/**
     * Receives a raw message from the server and schedules routing on the JavaFX Application Thread.
     *
     * <p>This method prints the received message for debugging and then delegates actual dispatching
     * logic to {@link #route(String)} using {@link Platform#runLater(Runnable)}.</p>
     *
     * @param message the server message (may be {@code null})
     */
    @Override
    public void display(String message) {
        System.out.println("ROUTER display(): " + message);
        Platform.runLater(() -> route(message));
    }

    
    /**
     * Routes a server message to the currently active controller (if one exists) based on message prefix/content.
     *
     * <p>Routing priority (top-to-bottom):
     * <ol>
     *   <li>{@link StaffReservationUIController} (staff reservation screen)</li>
     *   <li>{@link ReservationUIController} (regular reservation screen)</li>
     *   <li>{@link ReceiveTableUIController} (receive/assign table screen)</li>
     *   <li>{@link RepresentativeViewDetailsUIController} (representative details screen)</li>
     *   <li>{@link ClientUIController} main screen controller</li>
     *   <li>Reports screen controller (if active)</li>
     *   <li>Fallback to console log if no UI is ready</li>
     * </ol>
     *
     * <p><b>Important:</b> This method assumes it is running on the JavaFX Application Thread
     * (because {@link #display(String)} schedules it via {@link Platform#runLater(Runnable)}).</p>
     *
     * @param message the server message to route (may be {@code null})
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
            if (message.startsWith("ERROR|DB_ERROR")) { sr.onBookingResponse(message); return; }

            if (message.startsWith("RESERVATION_CANCELED")
                    || message.startsWith("ERROR|RESERVATION_NOT_FOUND")
                    || message.startsWith("ERROR|CANCEL")) { return; }
        }
       
        // 2) Route to active reservation window (if exists)
        ReservationUIController r = ClientUIController.getActiveReservationController();
        
        if (r != null) {
        	System.out.println("DEBUG ROUTER: sr=" + (sr!=null) + ", r=" + (r!=null) + ", msg=" + message);

            if (message.startsWith("RESERVATIONS_FOR_DATE|")) { r.onReservationsReceived(message); return; }
            if (message.startsWith("OPENING_HOURS|")) { r.onOpeningHoursReceived(message); return; }
            if (message.startsWith("AVAILABILITY|")) { r.onAvailabilityResponse(message); return; }
            if (message.startsWith("AVAILABILITY|")) { 
                r.onAvailabilityResponse(message); 
                return; 
            }

            if (message.startsWith("RESERVATION_CREATED")) { r.onBookingResponse(message); return; }
            if (message.startsWith("ERROR|DB_ERROR")) { r.onBookingResponse(message); return; }

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
