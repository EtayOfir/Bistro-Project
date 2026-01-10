package entities;

import java.sql.Timestamp;

public class WaitingEntry {
    private final int waitingId;
    private final String contactInfo;
    private final Integer subscriberId; // nullable - extracted from contactInfo when available
    private final int numOfDiners;
    private final String confirmationCode;
    private final String status;
    private final Timestamp entryTime;
    
    public WaitingEntry(int waitingId, String contactInfo, int numOfDiners,
                        String confirmationCode, String status, Timestamp entryTime) {
        this(waitingId, contactInfo, null, numOfDiners, confirmationCode, status, entryTime);
    }

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
