package entities;

import java.time.LocalDate;
import java.time.LocalTime;

public class ActiveReservation {
	private LocalDate reservationDate;
	private LocalTime reservationTime;
	private int numOfDiners;
	private String confirmationCode;
	private String status;

	public LocalDate getReservationDate() {
		return reservationDate;
	}

	public void setReservationDate(LocalDate reservationDate) {
		this.reservationDate = reservationDate;
	}

	public LocalTime getReservationTime() {
		return reservationTime;
	}

	public void setReservationTime(LocalTime reservationTime) {
		this.reservationTime = reservationTime;
	}

	public int getNumOfDiners() {
		return numOfDiners;
	}

	public void setNumOfDiners(int numOfDiners) {
		this.numOfDiners = numOfDiners;
	}

	public String getConfirmationCode() {
		return confirmationCode;
	}

	public void setConfirmationCode(String confirmationCode) {
		this.confirmationCode = confirmationCode;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	public ActiveReservation(LocalDate reservationDate, LocalTime reservationTime, int numOfDiners, String confirmationCode, String status) {
	    this.reservationDate = reservationDate;
	    this.reservationTime = reservationTime;
	    this.numOfDiners = numOfDiners;
	    this.confirmationCode = confirmationCode;
	    this.status = status;
	}
}
