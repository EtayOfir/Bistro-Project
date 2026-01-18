package entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents a scheduled operating window for the restaurant.
 * <p>
 * This entity handles two types of schedules:
 * <ul>
 * <li><b>Regular Weekly Hours:</b> Defined by a specific {@code dayOfWeek} (e.g., "Monday") with {@code specialDate} set to null.</li>
 * <li><b>Special Event/Holiday Hours:</b> Defined by a specific {@code specialDate}, which overrides the regular weekly schedule for that specific calendar date.</li>
 * </ul>
 * </p>
 * Implements {@link Serializable} for network transmission between Client and Server.
 */
public class OpeningHours implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the schedule record in the database.
     */
    private Integer id;
    /**
     * The day of the week this schedule applies to (e.g., "Sunday", "Monday").
     * If this is a special date record, this field may be set to "Special".
     */
    private String dayOfWeek; // e.g., "Sunday", "Monday", "Special"
    /**
     * The time the restaurant opens.
     */
    private LocalTime openTime;
    /**
     * The time the restaurant closes.
     */
    private LocalTime closeTime;
    /**
     * Specific calendar date for special opening hours (e.g., Holidays).
     * <p>
     * If this field is {@code null}, the record represents a recurring regular weekly schedule.
     * If this field is set, it represents a one-time schedule for that specific date.
     * </p>
     */
    private LocalDate specialDate; // null for regular hours, specific date for special hours

    /**
     * Default constructor required for serialization and frameworks.
     */
    public OpeningHours() {
    }

    /**
     * Constructs a new OpeningHours record.
     *
     * @param id          The unique ID of the record.
     * @param dayOfWeek   The day name (e.g., "Sunday") or "Special".
     * @param openTime    Opening time.
     * @param closeTime   Closing time.
     * @param specialDate The specific date for special hours, or {@code null} for regular weekly hours.
     */
    public OpeningHours(Integer id, String dayOfWeek, LocalTime openTime, LocalTime closeTime, LocalDate specialDate) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.specialDate = specialDate;
    }

    // Getters and Setters
    /**
     * Gets the unique ID of the schedule record.
     * @return The ID.
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the unique ID of the schedule record.
     * @param id The ID to set.
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Gets the day of the week.
     * @return The day name.
     */
    public String getDayOfWeek() {
        return dayOfWeek;
    }

    /**
     * Sets the day of the week.
     * @param dayOfWeek The day name to set.
     */
    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    /**
     * Gets the opening time.
     * @return The opening time.
     */
    public LocalTime getOpenTime() {
        return openTime;
    }

    /**
     * Sets the opening time.
     * @param openTime The opening time to set.
     */
    public void setOpenTime(LocalTime openTime) {
        this.openTime = openTime;
    }

    /**
     * Gets the closing time.
     * @return The closing time.
     */
    public LocalTime getCloseTime() {
        return closeTime;
    }

    /**
     * Sets the closing time.
     * @param closeTime The closing time to set.
     */
    public void setCloseTime(LocalTime closeTime) {
        this.closeTime = closeTime;
    }

    /**
     * Gets the specific date for special hours.
     * @return The date, or {@code null} if this is a regular weekly schedule.
     */
    public LocalDate getSpecialDate() {
        return specialDate;
    }

    /**
     * Sets the specific date for special hours.
     * @param specialDate The date to set (or null for regular schedule).
     */
    public void setSpecialDate(LocalDate specialDate) {
        this.specialDate = specialDate;
    }

    /**
     * Returns a string representation of the OpeningHours object.
     * @return A string containing the schedule details.
     */
    @Override
    public String toString() {
        return "OpeningHours{" +
                "id=" + id +
                ", dayOfWeek='" + dayOfWeek + '\'' +
                ", openTime=" + openTime +
                ", closeTime=" + closeTime +
                ", specialDate=" + specialDate +
                '}';
    }
}
