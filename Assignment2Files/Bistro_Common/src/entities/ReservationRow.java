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

    public ReservationRow(String customer, int guests, String time, String status) {
        this.customer.set(customer);
        this.guests.set(guests);
        this.time.set(time);
        this.status.set(status);
    }

    public StringProperty customerProperty() { return customer; }
    public IntegerProperty guestsProperty() { return guests; }
    public StringProperty timeProperty() { return time; }
    public StringProperty statusProperty() { return status; }

    public void setStatus(String s) { status.set(s); }
}
