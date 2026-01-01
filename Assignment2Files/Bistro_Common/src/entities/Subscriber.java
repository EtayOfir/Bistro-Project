package entities;

import java.util.List;

public class Subscriber {
	private int subscriberId;
	private String fullName;
	private String userName;
	private String phoneNumber;
	private String email;
	private List<ActiveReservation> activeReservations;
	private List<VisitHistory> visitHistory;

	public int getSubscriberId() {
		return subscriberId;
	}

	public void setSubscriberId(int subscriberId) {
		this.subscriberId = subscriberId;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
	
	public List<ActiveReservation> getActiveReservations() {
	    return activeReservations;
	}

	public void setActiveReservations(List<ActiveReservation> activeReservations) {
	    this.activeReservations = activeReservations;
	}
	
	public List<VisitHistory> getVisitHistory() {
        return visitHistory;
    }

    public void setVisitHistory(List<VisitHistory> visitHistory) {
        this.visitHistory = visitHistory;
    }
}
