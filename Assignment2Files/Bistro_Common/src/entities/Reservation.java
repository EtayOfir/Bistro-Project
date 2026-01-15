package entities;

import java.sql.Date;
import java.sql.Time;

/**
 * Represents a reservation entity in the Bistro system.
 * <p>
 * This class corresponds to the 'ActiveReservations' table in the database.
 * It acts as a data transfer object (DTO) that encapsulates all relevant
 * information regarding a scheduled reservation.
 * <p>
 * Instances of this class are immutable (read-only) once created, ensuring
 * data consistency when passed between the DAO and the Controller layers.
 */
public class Reservation {

    /** Unique identifier (Primary Key) of the reservation in the database */
    private final int reservationId;

    /** Number of guests (diners) included in the reservation */
    private final int numberOfGuests;

    /** The specific date of the reservation */
    private final Date reservationDate;

    /** The specific time of the reservation */
    private final Time reservationTime;

    /** Unique confirmation code provided to the client for identification */
    private final String confirmationCode;

    /** Identifier of the subscriber who placed the reservation (0 or -1 if casual) */
    private final Integer subscriberId;

    /** Assigned table number when seated (null if not seated). */
    private final Integer tableNumber;

    /** * The current status of the reservation (e.g., "Active", "Canceled", "Arrived", "Finished").
     * Maps to the 'ReservationStatus' column.
     */
    private String status;
    
    /** * The type of the customer making the reservation.
     * Can be 'Subscriber' or 'Casual'.
     * Maps to the 'Role' column in the database.
     */
    private String Role;
    
    /**
     * Constructs a new {@code Reservation} object with all relevant reservation data.
     * <p>
     * This constructor is typically used by the DAO layer when mapping a
     * ResultSet row to a Java object.
     *
     * @param reservationId    the unique database ID of the reservation
     * @param numberOfGuests   number of diners
     * @param reservationDate  the date of the visit (java.sql.Date)
     * @param reservationTime  the time of the visit (java.sql.Time)
     * @param confirmationCode the unique string code for the user
     * @param subscriberId     the ID of the subscriber (if applicable)
     * @param status           the current status of the reservation
     * @param Role     the type of customer ('Subscriber' or 'Casual')
     */
    public Reservation(int reservationId, int numberOfGuests, Date reservationDate,
                       Time reservationTime, String confirmationCode, Integer subscriberId, 
                       String status, String Role, Integer tableNumber) {
        this.reservationId = reservationId;
        this.numberOfGuests = numberOfGuests;
        this.reservationDate = reservationDate;
        this.reservationTime = reservationTime;
        this.confirmationCode = confirmationCode;
        this.subscriberId = subscriberId;
        this.status = status;
        this.Role = Role;
        this.tableNumber = tableNumber;
    }

    /**
     * Returns the unique identifier of the reservation.
     * Maps to the 'ReservationID' column.
     *
     * @return the reservation ID
     */
    public int getReservationId() {
        return reservationId;
    }

    /**
     * Returns the number of guests included in the reservation.
     * Maps to the 'NumOfDiners' column.
     *
     * @return number of guests
     */
    public int getNumberOfGuests() {
        return numberOfGuests;
    }

    /**
     * Returns the scheduled date of the reservation.
     * Maps to the 'ReservationDate' column.
     *
     * @return the reservation date
     */
    public Date getReservationDate() {
        return reservationDate;
    }

    /**
     * Returns the scheduled time of the reservation.
     * Maps to the 'ReservationTime' column.
     *
     * @return the reservation time
     */
    public Time getReservationTime() {
        return reservationTime;
    }

    /**
     * Returns the confirmation code assigned to the reservation.
     * Maps to the 'ConfirmationCode' column.
     *
     * @return unique confirmation string
     */
    public String getConfirmationCode() {
        return confirmationCode;
    }

    /**
     * Returns the subscriber identifier associated with the reservation.
     * Maps to the 'SubscriberID' column.
     *
     * @return subscriber ID
     */
    public Integer getSubscriberId() {
        return subscriberId;
    }
    
    /**
	 * Returns the current status of the reservation.
	 * Maps to the 'ReservationStatus' column.
	 *
	 * @return reservation status
	 */
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    
    /**
     * Returns the type of customer for this reservation.
     * Maps to the 'Role' column.
     * * @return 'Subscriber' or 'Casual'
     */
    public String getRole() { return Role; }
    public void setRole(String Role) { this.Role = Role; }
    
    /** 
	 * Returns the assigned table number for this reservation.
	 * Maps to the 'TableNumber' column.
	 * @return table number or null if not assigned
	 */
    public Integer getTableNumber() { return tableNumber; }
}