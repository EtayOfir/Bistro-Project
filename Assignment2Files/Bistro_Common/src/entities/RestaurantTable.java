package entities;

/**
 * Represents a physical dining table within the restaurant.
 * <p>
 * This entity is used to track the layout and availability of the restaurant.
 * It contains details about the table's unique identifier, its seating capacity,
 * and its current operational status (e.g., "Available", "Occupied", "Reserved").
 * </p>
 */
public class RestaurantTable {

	/**
     * The unique numeric identifier of the table.
     */
    private int tableNumber;
    
    /**
     * The maximum number of diners this table can accommodate.
     */
    private int capacity;
    
    /**
     * The current state of the table.
     * Common values might include "Available", "Occupied", "Reserved", or "Dirty".
     */
    private String status; 

    /**
     * Gets the table's unique number.
     * @return The table number.
     */
    public int getTableNumber() {
        return tableNumber;
    }

    /**
     * Sets the table's unique number.
     * @param tableNumber The table number to set.
     */
    public void setTableNumber(int tableNumber) {
        this.tableNumber = tableNumber;
    }

    /**
     * Gets the seating capacity of the table.
     * @return The maximum number of diners.
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Sets the seating capacity of the table.
     * @param capacity The maximum number of diners.
     */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Gets the current status of the table.
     * @return The status string (e.g., "Available", "Occupied").
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the current status of the table.
     * @param status The new status string.
     */
    public void setStatus(String status) {
        this.status = status;
    }
}
