package entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents the opening hours for a restaurant, either regular or for a special date.
 */
public class OpeningHours implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String dayOfWeek; // e.g., "Sunday", "Monday", "Special"
    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalDate specialDate; // null for regular hours, specific date for special hours

    public OpeningHours() {
    }

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
