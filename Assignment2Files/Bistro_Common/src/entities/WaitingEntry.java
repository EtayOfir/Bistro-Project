package entities;

import java.sql.Timestamp;

/**
 * Represents a single entry in the restaurant waiting list.
 * <p>
 * A waiting entry stores identifying information (waiting ID, contact details, optional subscriber ID),
 * party size, confirmation code, current status, and the timestamp when the party was added to the list.
 * <p>
 * The {@link #subscriberId} field may be {@code null} when the waiting entry is not associated with a
 * registered subscriber (e.g., walk-in or contact info without a recognized subscriber ID).
 */
public class WaitingEntry {
    private final int waitingId;
    private final String contactInfo;
    private final Integer subscriberId; // nullable - extracted from contactInfo when available
    private final int numOfDiners;
    private final String confirmationCode;
    private final String status;
    private final Timestamp entryTime;
    
    /**
     * Constructs a {@link WaitingEntry} without an explicit subscriber ID.
     * <p>
     * This constructor assumes the entry is not linked to a subscriber (subscriberId will be {@code null}).
     *
     * @param waitingId         the waiting list entry ID
     * @param contactInfo       contact information for the party
     * @param numOfDiners       number of diners in the party
     * @param confirmationCode  confirmation code for the entry
     * @param status            current status of the entry
     * @param entryTime         timestamp when the entry was created
     */
    public WaitingEntry(int waitingId, String contactInfo, int numOfDiners,
                        String confirmationCode, String status, Timestamp entryTime) {
        this(waitingId, contactInfo, null, numOfDiners, confirmationCode, status, entryTime);
    }

    /**
     * Constructs a {@link WaitingEntry} with an optional subscriber ID.
     *
     * @param waitingId         the waiting list entry ID
     * @param contactInfo       contact information for the party
     * @param subscriberId      the associated subscriber ID (may be {@code null})
     * @param numOfDiners       number of diners in the party
     * @param confirmationCode  confirmation code for the entry
     * @param status            current status of the entry
     * @param entryTime         timestamp when the entry was created
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

    public int getWaitingId() { return waitingId; }
    public String getContactInfo() { return contactInfo; }
    public Integer getSubscriberId() { return subscriberId; }
    public int getNumOfDiners() { return numOfDiners; }
    public String getConfirmationCode() { return confirmationCode; }
    public String getStatus() { return status; }
    public Timestamp getEntryTime() { return entryTime; }
}
