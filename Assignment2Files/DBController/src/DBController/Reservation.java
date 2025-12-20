package DBController;

public class Reservation {
    private final String orderNumber;
    private final int numberOfGuests;
    private final String orderDate;          // yyyy-MM-dd (or DB string)
    private final String confirmationCode;
    private final String subscriberId;
    private final String placingDate;

    public Reservation(String orderNumber, int numberOfGuests, String orderDate,
                       String confirmationCode, String subscriberId, String placingDate) {
        this.orderNumber = orderNumber;
        this.numberOfGuests = numberOfGuests;
        this.orderDate = orderDate;
        this.confirmationCode = confirmationCode;
        this.subscriberId = subscriberId;
        this.placingDate = placingDate;
    }

    public String getOrderNumber() { return orderNumber; }
    public int getNumberOfGuests() { return numberOfGuests; }
    public String getOrderDate() { return orderDate; }
    public String getConfirmationCode() { return confirmationCode; }
    public String getSubscriberId() { return subscriberId; }
    public String getPlacingDate() { return placingDate; }
}
