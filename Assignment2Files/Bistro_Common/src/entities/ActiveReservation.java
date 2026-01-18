package entities;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents a simplified view of a reservation that is currently active or upcoming.
 * <p>
 * This entity is primarily used for data transfer and display purposes within the 
 * client-side UI (specifically the Subscriber Dashboard tables). It aggregates 
 * the essential details required for a user to track their future bookings.
 * </p>
 */
public class ActiveReservation {
	private LocalDate reservationDate;
	private LocalTime reservationTime;
	private int numOfDiners;
	private String confirmationCode;
	private String status;

	/**
	 * Gets the date of the reservation.
	 * @return The reservation date.
	 */
	public LocalDate getReservationDate() {
		return reservationDate;
	}

	/**
	 * Sets the date of the reservation.
	 * @param reservationDate The new reservation date.
	 */
	public void setReservationDate(LocalDate reservationDate) {
		this.reservationDate = reservationDate;
	}

	/**
	 * Gets the time of the reservation.
	 * @return The reservation time.
	 */
	public LocalTime getReservationTime() {
		return reservationTime;
	}

	/**
	 * Sets the time of the reservation.
	 * @param reservationTime The new reservation time.
	 */
	public void setReservationTime(LocalTime reservationTime) {
		this.reservationTime = reservationTime;
	}

	/**
	 * Gets the number of diners for this reservation.
	 * @return The number of diners.
	 */
	public int getNumOfDiners() {
		return numOfDiners;
	}

	/**
	 * Sets the number of diners.
	 * @param numOfDiners The new number of diners.
	 */
	public void setNumOfDiners(int numOfDiners) {
		this.numOfDiners = numOfDiners;
	}

	/**
	 * Gets the unique confirmation code of the reservation.
	 * @return The confirmation code string.
	 */
	public String getConfirmationCode() {
		return confirmationCode;
	}

	/**
	 * Sets the confirmation code.
	 * @param confirmationCode The new confirmation code.
	 */
	public void setConfirmationCode(String confirmationCode) {
		this.confirmationCode = confirmationCode;
	}

	/**
	 * Gets the current status of the reservation (e.g., "Approved", "Pending").
	 * @return The status string.
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * Sets the status of the reservation.
	 * @param status The new status string.
	 */
	public void setStatus(String status) {
		this.status = status;
	}
	
	/**
	 * Constructs a new ActiveReservation with the specified details.
	 *
	 * @param reservationDate   The date of the reservation.
	 * @param reservationTime   The time of the reservation.
	 * @param numOfDiners       The number of guests expected.
	 * @param confirmationCode  The unique code associated with the reservation.
	 * @param status            The current status of the reservation.
	 */
	public ActiveReservation(LocalDate reservationDate, LocalTime reservationTime, int numOfDiners, String confirmationCode, String status) {
	    this.reservationDate = reservationDate;
	    this.reservationTime = reservationTime;
	    this.numOfDiners = numOfDiners;
	    this.confirmationCode = confirmationCode;
	    this.status = status;
	}
}
