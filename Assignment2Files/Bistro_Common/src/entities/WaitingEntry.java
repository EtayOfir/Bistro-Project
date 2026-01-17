package entities;

import java.sql.Timestamp;

public class WaitingEntry {
	private int waitingId;
    private String contactInfo;
    private Integer subscriberId; // nullable - extracted from contactInfo when available
    private int numOfDiners;
    private String confirmationCode;
    private String status;
    private Timestamp entryTime;
    public void setWaitingId(int waitingId) {
		this.waitingId = waitingId;
	}

	public void setContactInfo(String contactInfo) {
		this.contactInfo = contactInfo;
	}

	public void setSubscriberId(Integer subscriberId) {
		this.subscriberId = subscriberId;
	}

	public void setNumOfDiners(int numOfDiners) {
		this.numOfDiners = numOfDiners;
	}

	public void setConfirmationCode(String confirmationCode) {
		this.confirmationCode = confirmationCode;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void setEntryTime(Timestamp entryTime) {
		this.entryTime = entryTime;
	}

    
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

    public WaitingEntry() {
		this.waitingId = 0;
		this.contactInfo = "";
		// TODO Auto-generated constructor stub
		this.subscriberId = null;
		this.numOfDiners = 0;
		this.confirmationCode = "";
		this.status = "";
		this.entryTime = null;
	}

	public int getWaitingId() { return waitingId; }
    public String getContactInfo() { return contactInfo; }
    public Integer getSubscriberId() { return subscriberId; }
    public int getNumOfDiners() { return numOfDiners; }
    public String getConfirmationCode() { return confirmationCode; }
    public String getStatus() { return status; }
    public Timestamp getEntryTime() { return entryTime; }
}
