package entities;

import java.util.List;

/**
 * Represents a restaurant subscriber/user in the system.
 * <p>
 * A subscriber contains personal identification details (ID, name, username), contact information
 * (phone and email), authentication/authorization fields (password and role), and optional data
 * related to reservations and visit history.
 */
public class Subscriber {
	private int subscriberId;
	private String fullName;
	private String userName;
	private String phoneNumber;
	private String email;
	private String role;
	private String password;
	private String qr_code;


	private List<ActiveReservation> activeReservations;
	private List<VisitHistory> visitHistory;

	//getters&setters
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

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
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

	public String getQr_code() {
		return qr_code;
	}

	public void setQr_code(String qr_code) {
		this.qr_code = qr_code;
	}
}
