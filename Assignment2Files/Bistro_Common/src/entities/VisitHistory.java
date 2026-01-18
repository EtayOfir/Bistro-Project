package entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a historical record of a subscriber's visit/reservation.
 * <p>
 * Stores the originally reserved date, the actual arrival and departure timestamps (if available),
 * the final bill amount, any discount applied, and the final status of the visit.
 * This class is commonly used as a model for displaying visit history in UI tables.
 */
public class VisitHistory {
	private LocalDate originalReservationDate;
	private LocalDateTime actualArrivalTime;
	private LocalDateTime actualDepartureTime;
	private double totalBill;
	private double discountApplied;
	private String status;

	//getters&setters
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

	 /**
     * Constructs a {@link VisitHistory} record with the provided details.
     *
     * @param originalReservationDate the original reservation date
     * @param actualArrivalTime       the actual arrival date/time (may be {@code null})
     * @param actualDepartureTime     the actual departure date/time (may be {@code null})
     * @param totalBill               the total bill amount
     * @param discountApplied         the discount applied
     * @param status                  the final status of the visit/reservation
     */
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
