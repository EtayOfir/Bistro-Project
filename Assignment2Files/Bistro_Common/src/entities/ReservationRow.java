package entities;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents a single row in a reservations table (JavaFX UI model).
 * <p>
 * All fields are stored as JavaFX properties so they can be bound to UI controls (e.g., {@code TableView})
 * and automatically update when changed.
 */
public class ReservationRow {
    private final StringProperty customer = new SimpleStringProperty();
    private final IntegerProperty guests = new SimpleIntegerProperty();
    private final StringProperty time = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final StringProperty tableNumber = new SimpleStringProperty();
    private final StringProperty confirmationCode = new SimpleStringProperty();
    
    /**
     * Constructs a {@code ReservationRow} with the provided reservation details.
     * <p>
     * Initializes all observable properties (customer name, number of guests, time, status,
     * table number, and confirmation code) with the given values.
     *
     * @param customer          the customer name (or identifier) to display
     * @param guests            the number of guests for the reservation
     * @param time              the reservation time (formatted as a string for display)
     * @param status            the current reservation status (e.g., Approved, Pending, Seated)
     * @param tableNumber       the assigned table number (or placeholder if not assigned)
     * @param confirmationCode  the reservation confirmation code
     */
    public ReservationRow(String customer, int guests, String time, String status, String tableNumber, String confirmationCode) {
        this.customer.set(customer);
        this.guests.set(guests);
        this.time.set(time);
        this.status.set(status);
        this.tableNumber.set(tableNumber);
        this.confirmationCode.set(confirmationCode);
    }

    public StringProperty customerProperty() { return customer; }
    public IntegerProperty guestsProperty() { return guests; }
    public StringProperty timeProperty() { return time; }
    
    //getters$setters
    public String getConfirmationCode() { return confirmationCode.get(); }
    public StringProperty confirmationCodeProperty() { return confirmationCode; }
    public StringProperty statusProperty() { return status; }
    public StringProperty tableNumberProperty() { return tableNumber; }

    public String getCustomer() { return customer.get(); }
    public String getTime() { return time.get(); }

    public int getGuests() { return guests.get(); }
    public String getStatus() {
        return status.get();
    }
    public void setStatus(String s) { status.set(s); }
    public String getTableNumber() {
        return tableNumber.get();
    }
    public void setTableNumber(String s) { tableNumber.set(s); }
}