package entities;

import java.util.List;

/**
 * Represents a registered user (subscriber) in the restaurant management system.
 * <p>
 * This entity serves as the core user profile, containing:
 * <ul>
 * <li><b>Personal Details:</b> ID, Name, Phone, Email.</li>
 * <li><b>Authentication Data:</b> Username, Password, Role.</li>
 * <li><b>Identification:</b> QR Code string/path.</li>
 * <li><b>Transactional Data:</b> Lists of active upcoming reservations and past visit history.</li>
 * </ul>
 * </p>
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

	/**
	 * Gets the unique identifier of the subscriber.
	 * @return The subscriber ID.
	 */
	public int getSubscriberId() {
		return subscriberId;
	}

	/**
	 * Sets the unique identifier of the subscriber.
	 * @param subscriberId The new subscriber ID.
	 */
	public void setSubscriberId(int subscriberId) {
		this.subscriberId = subscriberId;
	}

	/**
	 * Gets the full name of the subscriber.
	 * @return The full name (First + Last).
	 */
	public String getFullName() {
		return fullName;
	}

	/**
	 * Sets the full name of the subscriber.
	 * @param fullName The new full name.
	 */
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	/**
	 * Gets the password used for authentication.
	 * @return The password string.
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * Sets the password for authentication.
	 * @param password The new password.
	 */
	public void setPassword(String password) {
		this.password = password;
	}
	
	/**
	 * Gets the username used for login.
	 * @return The username.
	 */
	public String getUserName() {
		return userName;
	}

	/**
	 * Sets the username used for login.
	 * @param userName The new username.
	 */
	public void setUserName(String userName) {
		this.userName = userName;
	}

	/**
	 * Gets the role of the user (e.g., "Subscriber", "Manager").
	 * @return The role string.
	 */
	public String getRole() {
		return role;
	}

	/**
	 * Sets the role of the user.
	 * @param role The new role string.
	 */
	public void setRole(String role) {
		this.role = role;
	}

	/**
	 * Gets the contact phone number.
	 * @return The phone number.
	 */
	public String getPhoneNumber() {
		return phoneNumber;
	}

	/**
	 * Sets the contact phone number.
	 * @param phoneNumber The new phone number.
	 */
	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	/**
	 * Gets the contact email address.
	 * @return The email address.
	 */
	public String getEmail() {
		return email;
	}

	/**
	 * Sets the contact email address.
	 * @param email The new email address.
	 */
	public void setEmail(String email) {
		this.email = email;
	}

	/**
	 * Gets the list of currently active (future) reservations for this subscriber.
	 * @return A list of {@link ActiveReservation} objects.
	 */
	public List<ActiveReservation> getActiveReservations() {
		return activeReservations;
	}

	/**
	 * Sets the list of active reservations.
	 * @param activeReservations The list of active reservations.
	 */
	public void setActiveReservations(List<ActiveReservation> activeReservations) {
		this.activeReservations = activeReservations;
	}

	/**
	 * Gets the history of past visits for this subscriber.
	 * @return A list of {@link VisitHistory} objects.
	 */
	public List<VisitHistory> getVisitHistory() {
		return visitHistory;
	}

	/**
	 * Sets the history of past visits.
	 * @param visitHistory The list of visit history records.
	 */
	public void setVisitHistory(List<VisitHistory> visitHistory) {
		this.visitHistory = visitHistory;
	}

	/**
	 * Gets the QR code string representation or file path associated with the subscriber.
	 * Used for identification at the restaurant terminal.
	 * @return The QR code string.
	 */
	public String getQr_code() {
		return qr_code;
	}

	/**
	 * Sets the QR code string representation.
	 * @param qr_code The new QR code string.
	 */
	public void setQr_code(String qr_code) {
		this.qr_code = qr_code;
	}
}
