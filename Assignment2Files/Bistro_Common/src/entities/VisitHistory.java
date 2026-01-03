package entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class VisitHistory {
	private LocalDate originalReservationDate;
	private LocalDateTime actualArrivalTime;
	private LocalDateTime actualDepartureTime;
	private double totalBill;
	private double discountApplied;
	private String status;

	public LocalDate getOriginalReservationDate() {
		return originalReservationDate;
	}

	public void setOriginalReservationDate(LocalDate originalReservationDate) {
		this.originalReservationDate = originalReservationDate;
	}

	public LocalDateTime getActualArrivalTime() {
		return actualArrivalTime;
	}

	public void setActualArrivalTime(LocalDateTime actualArrivalTime) {
		this.actualArrivalTime = actualArrivalTime;
	}

	public LocalDateTime getActualDepartureTime() {
		return actualDepartureTime;
	}

	public void setActualDepartureTime(LocalDateTime actualDepartureTime) {
		this.actualDepartureTime = actualDepartureTime;
	}

	public double getTotalBill() {
		return totalBill;
	}

	public void setTotalBill(double totalBill) {
		this.totalBill = totalBill;
	}

	public double getDiscountApplied() {
		return discountApplied;
	}

	public void setDiscountApplied(double discountApplied) {
		this.discountApplied = discountApplied;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public VisitHistory(LocalDate originalReservationDate, LocalDateTime actualArrivalTime,
			LocalDateTime actualDepartureTime, double totalBill, double discountApplied, String status) {
		this.originalReservationDate = originalReservationDate;
		this.actualArrivalTime = actualArrivalTime;
		this.actualDepartureTime = actualDepartureTime;
		this.totalBill = totalBill;
		this.discountApplied = discountApplied;
		this.status = status;
	}
}
