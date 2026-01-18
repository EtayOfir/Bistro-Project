package entities;

import java.sql.Timestamp;

/**
 * Represents a specific entry in the restaurant's waiting list.
 * <p>
 * This entity is created when a customer requests a table but none are available.
 * It stores the details of the request, including the contact information,
 * party size, and the exact time the request was made (to prioritize the queue).
 * </p>
 * <p>
 * This class is <b>immutable</b>; all fields are {@code final} and set upon construction.
 * </p>
 */
public class WaitingEntry {
    private final int waitingId;
    private final String contactInfo;
    /**
     * The ID of the subscriber, if the customer is registered. 
     * Can be {@code null} for casual customers (walk-ins/guests).
     */
    private final Integer subscriberId; // nullable - extracted from contactInfo when available
    private final int numOfDiners;
    private final String confirmationCode;
    private final String status;
    private final Timestamp entryTime;
    
    /**
     * Constructs a new WaitingEntry for a customer without a known subscriber ID (or guest).
     * Delegates to the main constructor with {@code subscriberId} set to {@code null}.
     *
     * @param waitingId        The unique ID of the waiting record.
     * @param contactInfo      The contact details (phone/email/name).
     * @param numOfDiners      The size of the party.
     * @param confirmationCode The unique code for this request.
     * @param status           The current status (e.g., "Waiting").
     * @param entryTime        The timestamp when the customer joined the list.
     */
    public WaitingEntry(int waitingId, String contactInfo, int numOfDiners,
                        String confirmationCode, String status, Timestamp entryTime) {
        this(waitingId, contactInfo, null, numOfDiners, confirmationCode, status, entryTime);
    }

    /**
     * Constructs a new WaitingEntry with all details, including subscriber ID.
     *
     * @param waitingId        The unique ID of the waiting record.
     * @param contactInfo      The contact details.
     * @param subscriberId     The registered subscriber ID (nullable).
     * @param numOfDiners      The size of the party.
     * @param confirmationCode The unique code for this request.
     * @param status           The current status.
     * @param entryTime        The timestamp when the customer joined the list.
     */
    public WaitingEntry(int waitingId, String contactInfo, Integer subscriberId, int numOfDiners,
                        String confirmationCode, String status, Timestamp entryTime) {
        this.waitingId = waitingId;
        this.contactInfo = contactInfo;
        this.subscriberId = subscriberId;
        this.numOfDiners = numOfDiners;
        this.confirmationCode = confirmationCode;
        this.status = status;
        this.entryTime = entryTime;
    }

    /**
     * Gets the unique identifier of this waiting list entry.
     * @return The waiting ID.
     */
    public int getWaitingId() { return waitingId; }
    
    /**
     * Gets the contact information provided by the customer.
     * @return A string containing phone, email, or name.
     */
    public String getContactInfo() { return contactInfo; }
    
    /**
     * Gets the subscriber ID associated with this entry, if applicable.
     * @return The subscriber ID, or {@code null} if the customer is a guest.
     */
    public Integer getSubscriberId() { return subscriberId; }
    
    /**
     * Gets the number of diners (party size) waiting for a table.
     * @return The number of diners.
     */
    public int getNumOfDiners() { return numOfDiners; }
    
    /**
     * Gets the unique confirmation code generated for this waiting request.
     * @return The confirmation code.
     */
    public String getConfirmationCode() { return confirmationCode; }
    
    /**
     * Gets the current status of the request (e.g., "Waiting", "Notified", "Expired").
     * @return The status string.
     */
    public String getStatus() { return status; }
    
    /**
     * Gets the exact time the customer was added to the waiting list.
     * Used for sorting the queue (FIFO logic).
     * @return The entry timestamp.
     */
    public Timestamp getEntryTime() { return entryTime; }
}
