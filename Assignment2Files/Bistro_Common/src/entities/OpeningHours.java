package entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents restaurant opening hours for either a regular day of the week or a specific special date.
 * <p>
 * Regular opening hours are identified by {@link #dayOfWeek} (e.g., "Sunday", "Monday"), where
 * {@link #specialDate} is typically {@code null}. Special opening hours may use a {@link #specialDate}
 * to indicate an override for a specific calendar date.
 * <p>
 * This class is {@link Serializable} for transport between client/server or persistence if needed.
 */
public class OpeningHours implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String dayOfWeek; // e.g., "Sunday", "Monday", "Special"
    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalDate specialDate; // null for regular hours, specific date for special hours

    /**
     * Default constructor (required for frameworks/serialization).
     */
    public OpeningHours() {
    }

    /**
     * Constructs an {@link OpeningHours} record with the given parameters.
     *
     * @param id          the record ID
     * @param dayOfWeek   the day-of-week label (e.g., "Sunday") or a special marker
     * @param openTime    the opening time
     * @param closeTime   the closing time
     * @param specialDate the specific date for special hours; {@code null} for regular weekly hours
     */
    public OpeningHours(Integer id, String dayOfWeek, LocalTime openTime, LocalTime closeTime, LocalDate specialDate) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.specialDate = specialDate;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public void setOpenTime(LocalTime openTime) {
        this.openTime = openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(LocalTime closeTime) {
        this.closeTime = closeTime;
    }

    public LocalDate getSpecialDate() {
        return specialDate;
    }

    public void setSpecialDate(LocalDate specialDate) {
        this.specialDate = specialDate;
    }

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
