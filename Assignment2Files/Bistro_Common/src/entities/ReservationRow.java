package entities;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents a specific view model for a single row in a JavaFX TableView displaying reservations.
 * <p>
 * Unlike standard entity classes (POJOs), this class utilizes JavaFX Properties 
 * ({@link StringProperty}, {@link IntegerProperty}). This allows the UI tables to 
 * automatically observe and reflect changes in the data without requiring a manual refresh.
 * </p>
 * <p>
 * It is primarily used in dashboards (e.g., Manager, Representative) to list reservations.
 * </p>
 */
public class ReservationRow {
    private final StringProperty customer = new SimpleStringProperty();
    private final IntegerProperty guests = new SimpleIntegerProperty();
    private final StringProperty time = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final StringProperty tableNumber = new SimpleStringProperty();
    private final StringProperty confirmationCode = new SimpleStringProperty();
    
    /**
     * Constructs a new ReservationRow with initial values.
     *
     * @param customer          The name of the customer.
     * @param guests            The number of guests.
     * @param time              The time string of the reservation.
     * @param status            The current status (e.g., "Active", "Pending").
     * @param tableNumber       The assigned table number (as a string).
     * @param confirmationCode  The unique confirmation code.
     */
    public ReservationRow(String customer, int guests, String time, String status, String tableNumber, String confirmationCode) {
        this.customer.set(customer);
        this.guests.set(guests);
        this.time.set(time);
        this.status.set(status);
        this.tableNumber.set(tableNumber);
        this.confirmationCode.set(confirmationCode);
    }

    /**
     * Gets the customer name property.
     * Used by TableColumn to bind data.
     * @return The StringProperty object for the customer name.
     */
    public StringProperty customerProperty() { return customer; }
    
    /**
     * Gets the guests count property.
     * Used by TableColumn to bind data.
     * @return The IntegerProperty object for the number of guests.
     */
    public IntegerProperty guestsProperty() { return guests; }
    
    /**
     * Gets the time property.
     * Used by TableColumn to bind data.
     * @return The StringProperty object for the reservation time.
     */
    public StringProperty timeProperty() { return time; }
    
    /**
     * Gets the actual confirmation code value.
     * @return The confirmation code.
     */
    public String getConfirmationCode() { return confirmationCode.get(); }
    
    /**
     * Gets the confirmation code property.
     * Used by TableColumn to bind data.
     * @return The StringProperty object for the confirmation code.
     */
    public StringProperty confirmationCodeProperty() { return confirmationCode; }
    
    /**
     * Gets the status property.
     * Used by TableColumn to bind data.
     * @return The StringProperty object for the status.
     */
    public StringProperty statusProperty() { return status; }
    
    /**
     * Gets the table number property.
     * Used by TableColumn to bind data.
     * @return The StringProperty object for the table number.
     */
    public StringProperty tableNumberProperty() { return tableNumber; }

    /**
     * Gets the actual customer name value.
     * @return The customer name.
     */
    public String getCustomer() { return customer.get(); }
    
    /**
     * Gets the actual time value.
     * @return The reservation time string.
     */
    public String getTime() { return time.get(); }

    /**
     * Gets the actual number of guests.
     * @return The number of guests.
     */
    public int getGuests() { return guests.get(); }
    
    /**
     * Gets the actual status value.
     * @return The status string.
     */
    public String getStatus() {
        return status.get();
    }
    
    /**
     * Sets the status value.
     * Automatically updates the UI if bound.
     * @param s The new status string.
     */
    public void setStatus(String s) { status.set(s); }
    
    /**
     * Gets the actual table number value.
     * @return The table number string.
     */
    public String getTableNumber() {
        return tableNumber.get();
    }
    
    /**
     * Sets the table number value.
     * Automatically updates the UI if bound.
     * @param s The new table number string.
     */
    public void setTableNumber(String s) { tableNumber.set(s); }
}