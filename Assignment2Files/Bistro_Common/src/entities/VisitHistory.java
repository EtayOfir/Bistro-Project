package entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a historical record of a completed or past reservation visit.
 * <p>
 * This entity is used primarily for reporting and displaying the subscriber's history.
 * It tracks the difference between the planned reservation and the actual event,
 * including arrival/departure times and financial details (bill and discount).
 * </p>
 */
public class VisitHistory {
	private LocalDate originalReservationDate;
	private LocalDateTime actualArrivalTime;
	private LocalDateTime actualDepartureTime;
	private double totalBill;
	private double discountApplied;
	private String status;

	/**
	 * Gets the date for which the reservation was originally scheduled.
	 * @return The planned reservation date.
	 */
	public LocalDate getOriginalReservationDate() {
		return originalReservationDate;
	}

	/**
	 * Sets the original reservation date.
	 * @param originalReservationDate The scheduled date.
	 */
	public void setOriginalReservationDate(LocalDate originalReservationDate) {
		this.originalReservationDate = originalReservationDate;
	}

	/**
	 * Gets the actual timestamp when the customer arrived at the restaurant.
	 * @return The arrival timestamp, or null if they did not arrive.
	 */
	public LocalDateTime getActualArrivalTime() {
		return actualArrivalTime;
	}

	/**
	 * Sets the actual arrival timestamp.
	 * @param actualArrivalTime The arrival time.
	 */
	public void setActualArrivalTime(LocalDateTime actualArrivalTime) {
		this.actualArrivalTime = actualArrivalTime;
	}

	
	/**
	 * Gets the actual timestamp when the customer left the restaurant.
	 * @return The departure timestamp.
	 */
	public LocalDateTime getActualDepartureTime() {
		return actualDepartureTime;
	}

	/**
	 * Sets the actual departure timestamp.
	 * @param actualDepartureTime The departure time.
	 */
	public void setActualDepartureTime(LocalDateTime actualDepartureTime) {
		this.actualDepartureTime = actualDepartureTime;
	}

	/**
	 * Gets the final bill amount charged for the visit.
	 * @return The total bill amount.
	 */
	public double getTotalBill() {
		return totalBill;
	}

	/**
	 * Sets the total bill amount.
	 * @param totalBill The bill amount.
	 */
	public void setTotalBill(double totalBill) {
		this.totalBill = totalBill;
	}

	/**
	 * Gets the amount of discount applied to the bill.
	 * @return The discount amount.
	 */
	public double getDiscountApplied() {
		return discountApplied;
	}

	/**
	 * Sets the discount amount applied.
	 * @param discountApplied The discount amount.
	 */
	public void setDiscountApplied(double discountApplied) {
		this.discountApplied = discountApplied;
	}

	/**
	 * Gets the final status of the visit (e.g., "Finished", "No Show", "Cancelled").
	 * @return The status string.
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * Sets the final status of the visit.
	 * @param status The status string.
	 */
	public void setStatus(String status) {
		this.status = status;
	}

	/**
	 * Constructs a new VisitHistory record.
	 *
	 * @param originalReservationDate The date the reservation was booked for.
	 * @param actualArrivalTime       The actual time the customer arrived.
	 * @param actualDepartureTime     The actual time the customer left.
	 * @param totalBill               The final amount paid.
	 * @param discountApplied         The total discount given.
	 * @param status                  The outcome of the visit (e.g., "Finished").
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
