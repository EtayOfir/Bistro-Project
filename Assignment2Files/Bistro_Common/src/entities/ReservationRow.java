package entities;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ReservationRow {
    private final StringProperty customer = new SimpleStringProperty();
    private final IntegerProperty guests = new SimpleIntegerProperty();
    private final StringProperty time = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final StringProperty tableNumber = new SimpleStringProperty();
    private final StringProperty confirmationCode = new SimpleStringProperty();
    
    
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