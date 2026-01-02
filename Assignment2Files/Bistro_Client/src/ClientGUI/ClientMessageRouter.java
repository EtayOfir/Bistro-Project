package ClientGUI;

import common.ChatIF;
import javafx.application.Platform;

/**
 * Central router for all server messages.
 *
 * <p>This class contains no FXML fields and never touches UI nodes directly.
 * It dispatches messages to active controllers when available.</p>
 */
public class ClientMessageRouter implements ChatIF {

    @Override
    public void display(String message) {
        System.out.println("ROUTER display(): " + message);
        Platform.runLater(() -> route(message));
    }

    private void route(String message) {
        if (message == null) return;

        // 1) Route to active reservation window (if exists)
        ReservationUIController r = ClientUIController.getActiveReservationController();
        if (r != null) {
            if (message.startsWith("RESERVATIONS_FOR_DATE|")) { r.onReservationsReceived(message); return; }
            if (message.startsWith("RESERVATION_CREATED")) { r.onBookingResponse(message); return; }
            if (message.startsWith("RESERVATION_CANCELED")
                    || message.startsWith("ERROR|RESERVATION_NOT_FOUND")
                    || message.startsWith("ERROR|CANCEL")) { r.onCancelResponse(message); return; }
        }

        // 2) Route to active receive-table window (if exists)
        ReceiveTableUIController t = ClientUIController.getActiveReceiveTableController();
        if (t != null) {
            if (message.startsWith("TABLE_ASSIGNED|")
                    || message.equals("NO_TABLE_AVAILABLE")
                    || message.equals("INVALID_CONFIRMATION_CODE")) {
                t.onReceiveTableResponse(message);
                return;
            }
        }

        // 3) Route to main UI controller if loaded
        ClientUIController main = ClientUIController.getMainController();
        if (main != null) {
            main.handleMainScreenServerMessageFromRouter(message);
            return;
        }

        // 4) Fallback: no UI loaded yet
        System.out.println("SERVER (no UI yet): " + message);
    }
}
