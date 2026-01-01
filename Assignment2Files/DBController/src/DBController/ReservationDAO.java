package DBController;

import SQLAccess.SQLQueries;
import entities.ActiveReservation;
import entities.VisitHistory;
import entities.Reservation;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;

/**
 * Data Access Object (DAO) responsible for all database operations
 * related to reservations in the Bistro system.
 *
 * <p>
 * This class encapsulates CRUD operations on the {@code ActiveReservations}
 * table and read-only access to reservation visit history stored in
 * {@code VisitHistory}.
 * </p>
 *
 * <p>
 * <b>Design principles:</b>
 * <ul>
 *   <li>Contains only database-access logic (no business logic)</li>
 *   <li>Uses prepared statements to prevent SQL injection</li>
 *   <li>Uses try-with-resources to ensure proper resource cleanup</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Thread safety:</b><br>
 * This class is thread-safe as it does not maintain mutable shared state.
 * Each method obtains its own database connection from the pool.
 * </p>
 */
public class ReservationDAO {

    /** Data source used to obtain database connections from the pool. */
    private final DataSource dataSource;

    /**
     * Constructs a new {@code ReservationDAO} using the given data source.
     *
     * @param dataSource the {@link DataSource} providing pooled database connections
     */
    public ReservationDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Inserts a new casual (walk-in) reservation into the database.
     *
     * <p>
     * The reservation is stored in the {@code ActiveReservations} table
     * with {@code CustomerType = 'Casual'} and {@code Status = 'Confirmed'}.
     * </p>
     *
     * @param reservation the reservation entity containing date, time, diners and confirmation code
     * @param phone       the customer's phone number (may be empty or {@code null})
     * @param email       the customer's email address (may be empty or {@code null})
     * @return the generated {@code ReservationID} if insertion succeeded,
     *         or {@code -1} if insertion failed
     * @throws SQLException if a database access error occurs
     */
    public int insertReservation(Reservation reservation, String phone, String email) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SQLQueries.INSERT_ACTIVE_RESERVATION_CASUAL,
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, phone == null ? "" : phone);
            ps.setString(2, email == null ? "" : email);
            ps.setDate(3, reservation.getReservationDate());
            ps.setTime(4, reservation.getReservationTime());
            ps.setInt(5, reservation.getNumberOfGuests());
            ps.setString(6, reservation.getConfirmationCode());

            if (ps.executeUpdate() != 1) {
                return -1;
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Inserts a reservation without casual contact details.
     *
     * <p>
     * This overload is provided for backward compatibility and internally
     * delegates to {@link #insertReservation(Reservation, String, String)}.
     * </p>
     *
     * @param reservation the reservation entity to insert
     * @return the generated {@code ReservationID}, or {@code -1} on failure
     * @throws SQLException if a database access error occurs
     */
    public int insertReservation(Reservation reservation) throws SQLException {
        return insertReservation(reservation, "", "");
    }

    /**
     * Retrieves a reservation by its unique reservation ID.
     *
     * @param reservationId the primary key of the reservation
     * @return a {@link Reservation} object if found, or {@code null} if no reservation exists
     * @throws SQLException if a database access error occurs
     */
    public Reservation getReservationById(int reservationId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SQLQueries.GET_RESERVATION_BY_ORDER_NUMBER)) {

            ps.setInt(1, reservationId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new Reservation(
                        rs.getInt("order_number"),
                        rs.getInt("number_of_guests"),
                        rs.getDate("order_date"),
                        rs.getTime("order_time"),
                        rs.getString("confirmation_code"),
                        rs.getInt("subscriber_id")
                );
            }
        }
    }

    /**
     * Updates the details of an existing reservation.
     *
     * @param reservationId the reservation ID to update
     * @param numGuests     the new number of diners
     * @param date          the new reservation date
     * @param time          the new reservation time
     * @return {@code true} if exactly one row was updated, {@code false} otherwise
     * @throws SQLException if a database access error occurs
     */
    public boolean updateReservation(int reservationId, int numGuests, Date date, Time time)
            throws SQLException {

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SQLQueries.UPDATE_RESERVATION_BY_ORDER_NUMBER)) {

            ps.setInt(1, numGuests);
            ps.setDate(2, date);
            ps.setTime(3, time);
            ps.setInt(4, reservationId);

            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Retrieves the visit history of a specific subscriber.
     *
     * <p>
     * The visit history is read from the {@code VisitHistory} table and
     * includes arrival time, departure time, billing and status information.
     * </p>
     *
     * @param subscriberId the unique ID of the subscriber
     * @return a list of {@link VisitHistory} records (empty if none exist)
     */
    public ArrayList<VisitHistory> getVisitHistory(int subscriberId) {
        ArrayList<VisitHistory> historyList = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SQLQueries.GET_SUBSCRIBER_VISIT_HISTORY)) {

            ps.setInt(1, subscriberId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate originalDate = rs.getDate("OriginalReservationDate").toLocalDate();
                    LocalDateTime arrivalTime = rs.getTimestamp("ActualArrivalTime").toLocalDateTime();
                    LocalDateTime departureTime = rs.getTimestamp("ActualDepartureTime").toLocalDateTime();

                    historyList.add(new VisitHistory(
                            originalDate,
                            arrivalTime,
                            departureTime,
                            rs.getDouble("TotalBill"),
                            rs.getDouble("DiscountApplied"),
                            rs.getString("Status")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return historyList;
    }

    /**
     * Retrieves all active (current or future) reservations for a subscriber.
     *
     * @param subscriberId the unique ID of the subscriber
     * @return a list of {@link ActiveReservation} objects (empty if none found)
     */
    public ArrayList<ActiveReservation> getActiveReservations(int subscriberId) {
        ArrayList<ActiveReservation> reservations = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SQLQueries.GET_SUBSCRIBER_ACTIVE_RESERVATIONS)) {

            ps.setInt(1, subscriberId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate("ReservationDate").toLocalDate();
                    LocalTime time = rs.getTime("ReservationTime").toLocalTime();

                    reservations.add(new ActiveReservation(
                            date,
                            time,
                            rs.getInt("NumOfDiners"),
                            rs.getString("ConfirmationCode"),
                            rs.getString("Status")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return reservations;
    }
}
