package entities;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents an active (upcoming or currently valid) reservation for a subscriber.
 * <p>
 * Contains basic reservation details such as date, time, number of diners, confirmation code,
 * and current status. This class is commonly used as a model for UI table views.
 */
public class ActiveReservation {
	private LocalDate reservationDate;
	private LocalTime reservationTime;
	private int numOfDiners;
	private String confirmationCode;
	private String status;

	/**
     * Returns the reservation time.
     *
     * @return the reservation time
     */
	public LocalDate getReservationDate() {
		return reservationDate;
	}

	/**
     * Sets the reservation time.
     *
     * @param reservationTime the reservation time to set
     */
	public void setReservationDate(LocalDate reservationDate) {
		this.reservationDate = reservationDate;
	}

	/**
     * Returns the reservation time.
     *
     * @return the reservation time
     */
	public LocalTime getReservationTime() {
		return reservationTime;
	}


    /**
     * Sets the reservation time.
     *
     * @param reservationTime the reservation time to set
     */
	public void setReservationTime(LocalTime reservationTime) {
		this.reservationTime = reservationTime;
	}

	 /**
     * Returns the number of diners.
     *
     * @return the number of diners
     */
	public int getNumOfDiners() {
		return numOfDiners;
	}

	 /**
     * Sets the number of diners.
     *
     * @param numOfDiners the number of diners to set
     */
	public void setNumOfDiners(int numOfDiners) {
		this.numOfDiners = numOfDiners;
	}

	/**
     * Returns the reservation confirmation code.
     *
     * @return the confirmation code
     */
	public String getConfirmationCode() {
		return confirmationCode;
	}

	/**
     * Sets the reservation confirmation code.
     *
     * @param confirmationCode the confirmation code to set
     */
	public void setConfirmationCode(String confirmationCode) {
		this.confirmationCode = confirmationCode;
	}

	 /**
     * Returns the current reservation status.
     *
     * @return the reservation status
     */
	public String getStatus() {
		return status;
	}

	 /**
     * Sets the current reservation status.
     *
     * @param status the reservation status to set
     */
	public void setStatus(String status) {
		this.status = status;
	}
	
	/**
     * Constructs a new {@link ActiveReservation} with the provided details.
     *
     * @param reservationDate   the reservation date
     * @param reservationTime   the reservation time
     * @param numOfDiners       the number of diners
     * @param confirmationCode  the reservation confirmation code
     * @param status            the current reservation status
     */
	public ActiveReservation(LocalDate reservationDate, LocalTime reservationTime, int numOfDiners, String confirmationCode, String status) {
	    this.reservationDate = reservationDate;
	    this.reservationTime = reservationTime;
	    this.numOfDiners = numOfDiners;
	    this.confirmationCode = confirmationCode;
	    this.status = status;
	}
}
