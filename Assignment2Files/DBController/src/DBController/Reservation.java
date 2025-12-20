package DBController;

/**
 * Represents a reservation entity in the Bistro system.
 * <p>
 * This class is a plain data holder that encapsulates all relevant
 * information related to a reservation as stored in the database.
 * <p>
 * Instances of this class are created by the DAO layer and transferred
 * to other layers of the system without containing any business logic.
 */
public class Reservation {
	/** Unique order number identifying the reservation */
    private final String orderNumber;

    /** Number of guests included in the reservation */
    private final int numberOfGuests;

    /** Reservation date in the format yyyy-MM-dd */
    private final String orderDate;

    /** Confirmation code provided to the client */
    private final String confirmationCode;

    /** Identifier of the subscriber who placed the reservation (may be null) */
    private final String subscriberId;

    /** Date on which the reservation was placed */
    private final String placingDate;
    
    /**
     * Constructs a new {@code Reservation} object with all relevant reservation data.
     *
     * @param orderNumber      unique order number of the reservation
     * @param numberOfGuests  number of guests for the reservation
     * @param orderDate       date of the reservation (yyyy-MM-dd)
     * @param confirmationCode confirmation code assigned to the reservation
     * @param subscriberId    identifier of the subscriber who placed the reservation
     * @param placingDate     date on which the reservation was created
     */
    public Reservation(String orderNumber, int numberOfGuests, String orderDate,
                       String confirmationCode, String subscriberId, String placingDate) {
        this.orderNumber = orderNumber;
        this.numberOfGuests = numberOfGuests;
        this.orderDate = orderDate;
        this.confirmationCode = confirmationCode;
        this.subscriberId = subscriberId;
        this.placingDate = placingDate;
    }

    /**
     * Returns the unique order number of the reservation.
     *
     * @return the reservation order number
     */
    public String getOrderNumber() {
        return orderNumber;
    }

    /**
     * Returns the number of guests included in the reservation.
     *
     * @return number of guests
     */
    public int getNumberOfGuests() {
        return numberOfGuests;
    }

    /**
     * Returns the date of the reservation.
     *
     * @return reservation date in the format yyyy-MM-dd
     */
    public String getOrderDate() {
        return orderDate;
    }

    /**
     * Returns the confirmation code assigned to the reservation.
     *
     * @return confirmation code
     */
    public String getConfirmationCode() {
        return confirmationCode;
    }

    /**
     * Returns the subscriber identifier associated with the reservation.
     *
     * @return subscriber identifier, or {@code null} if the reservation was made by a guest
     */
    public String getSubscriberId() {
        return subscriberId;
    }

    /**
     * Returns the date on which the reservation was placed.
     *
     * @return placing date
     */
    public String getPlacingDate() {
        return placingDate;
    }
}
