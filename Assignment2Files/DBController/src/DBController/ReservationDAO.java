package DBController;

import SQLAccess.SQLQueries;
import entities.ActiveReservation;
import entities.Reservation;
import entities.VisitHistory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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
     * <p>
     * Uses {@link SQLQueries#GET_RESERVATION_BY_ORDER_NUMBER} which returns aliased columns:
     * {@code order_number, number_of_guests, order_date, order_time, confirmation_code, subscriber_id}.
     * </p>
     *
     * @param reservationId the primary key of the reservation
     * @return a {@link Reservation} object if found, or {@code null} if no reservation exists
     * @throws SQLException if a database access error occurs
     */
    public Reservation getReservationById(int reservationId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_RESERVATION_BY_ORDER_NUMBER)) {

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
     * Updates the details of an existing reservation (by ReservationID).
     *
     * @param reservationId the reservation ID to update
     * @param numGuests     the new number of diners
     * @param date          the new reservation date
     * @param time          the new reservation time
     * @return {@code true} if exactly one row was updated, {@code false} otherwise
     * @throws SQLException if a database access error occurs
     */
    public boolean updateReservation(int reservationId, int numGuests, Date date, Time time) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.UPDATE_RESERVATION_BY_ORDER_NUMBER)) {

            ps.setInt(1, numGuests);
            ps.setDate(2, date);
            ps.setTime(3, time);
            ps.setInt(4, reservationId);

            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Retrieves a reservation by its confirmation code from {@code ActiveReservations}.
     *
     * <p>
     * Uses {@link SQLQueries#GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE}.
     * Column names are the real DB column names (e.g. {@code ReservationID, NumOfDiners, ReservationDate...}).
     * </p>
     *
     * @param confirmationCode the confirmation code to search by
     * @return a {@link Reservation} if found, or {@code null} if not found
     * @throws SQLException if a database access error occurs
     */
    public Reservation getReservationByConfirmationCode(String confirmationCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE)) {

            ps.setString(1, confirmationCode);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                return new Reservation(
                        rs.getInt("ReservationID"),
                        rs.getInt("NumOfDiners"),
                        rs.getDate("ReservationDate"),
                        rs.getTime("ReservationTime"),
                        rs.getString("ConfirmationCode"),
                        rs.getInt("SubscriberID")
                );
            }
        }
    }

    /**
     * Retrieves all reservations for a specific date from {@code ActiveReservations}
     * excluding canceled reservations.
     *
     * @param date the date to query
     * @return list of {@link Reservation} for that date (empty list if none)
     * @throws SQLException if a database access error occurs
     */
    public List<Reservation> getReservationsByDate(Date date) throws SQLException {
        List<Reservation> results = new ArrayList<>();

        final String sql =
                "SELECT ReservationID, NumOfDiners, ReservationDate, ReservationTime, ConfirmationCode, SubscriberID " +
                "FROM ActiveReservations " +
                "WHERE ReservationDate = ? AND Status != 'Canceled' " +
                "ORDER BY ReservationTime";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, date);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new Reservation(
                            rs.getInt("ReservationID"),
                            rs.getInt("NumOfDiners"),
                            rs.getDate("ReservationDate"),
                            rs.getTime("ReservationTime"),
                            rs.getString("ConfirmationCode"),
                            rs.getInt("SubscriberID")
                    ));
                }
            }
        }

        return results;
    }

    /**
     * Cancels a reservation by setting its status to {@code 'Canceled'} using the confirmation code.
     *
     * <p>
     * Uses {@link SQLQueries#CANCEL_ACTIVE_RESERVATION}. This is a "soft cancel".
     * </p>
     *
     * @param confirmationCode the confirmation code of the reservation to cancel
     * @return {@code true} if at least one row was updated, {@code false} otherwise
     * @throws SQLException if a database access error occurs
     */
    public boolean cancelReservationByConfirmationCode(String confirmationCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.CANCEL_ACTIVE_RESERVATION)) {

            ps.setString(1, confirmationCode);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Deletes a reservation from {@code ActiveReservations} by confirmation code.
     *
     * <p><b>Note:</b> This is a "hard delete". If you prefer soft-cancel, use
     * {@link #cancelReservationByConfirmationCode(String)}.</p>
     *
     * @param confirmationCode the confirmation code of the reservation to delete
     * @return {@code true} if a row was deleted, {@code false} if nothing matched
     * @throws SQLException if a database access error occurs
     */
    public boolean deleteReservationByConfirmationCode(String confirmationCode) throws SQLException {
        final String sql = "DELETE FROM ActiveReservations WHERE ConfirmationCode = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, confirmationCode);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Deletes all reservations whose combined {@code ReservationDate + ReservationTime}
     * have passed by at least 15 minutes (excluding canceled).
     *
     * @return number of rows deleted
     * @throws SQLException if a database access error occurs
     */
    public int deleteExpiredReservations() throws SQLException {
        final String sql =
                "DELETE FROM ActiveReservations " +
                "WHERE Status != 'Canceled' " +
                "AND TIMESTAMP(ReservationDate, ReservationTime) < (NOW() - INTERVAL 15 MINUTE)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    /**
     * Attempts to allocate the best available table for a customer upon arrival.
     *
     * <p>Transaction steps:</p>
     * <ol>
     *   <li>Validate reservation by confirmation code (must exist, be Confirmed, and be today)</li>
     *   <li>Find best available table for number of diners</li>
     *   <li>Update table status to Taken</li>
     *   <li>Update reservation status to Arrived</li>
     * </ol>
     *
     * @param confirmationCode reservation confirmation code
     * @return allocated table number, or {@code -1} if allocation failed
     */
    public int allocateTableForCustomer(String confirmationCode) {
        int assignedTable = -1;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psRes = conn.prepareStatement(SQLQueries.GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE)) {
                psRes.setString(1, confirmationCode);

                try (ResultSet rs = psRes.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return -1;
                    }

                    int diners = rs.getInt("NumOfDiners");
                    String status = rs.getString("Status");
                    Date dbDate = rs.getDate("ReservationDate");

                    boolean isConfirmed = "Confirmed".equalsIgnoreCase(status);
                    boolean isToday = (dbDate != null && dbDate.toLocalDate().equals(LocalDate.now()));

                    if (diners <= 0 || !isConfirmed || !isToday) {
                        conn.rollback();
                        return -1;
                    }

                    // Find best table
                    try (PreparedStatement psTable = conn.prepareStatement(SQLQueries.GET_BEST_AVAILABLE_TABLE)) {
                        psTable.setInt(1, diners);

                        try (ResultSet rsTable = psTable.executeQuery()) {
                            if (!rsTable.next()) {
                                conn.rollback();
                                return -1;
                            }
                            assignedTable = rsTable.getInt("TableNumber");
                        }
                    }

                    // Update table status
                    try (PreparedStatement psUpdateTable = conn.prepareStatement(SQLQueries.UPDATE_TABLE_STATUS)) {
                        psUpdateTable.setString(1, "Taken");
                        psUpdateTable.setInt(2, assignedTable);
                        psUpdateTable.executeUpdate();
                    }

                    // Update reservation status
                    try (PreparedStatement psUpdateRes = conn.prepareStatement(SQLQueries.SET_RESERVATION_STATUS_ARRIVED)) {
                        psUpdateRes.setString(1, confirmationCode);
                        psUpdateRes.executeUpdate();
                    }

                    conn.commit();
                    return assignedTable;
                }
            } catch (SQLException e) {
                conn.rollback();
                return -1;
            } finally {
                try { conn.setAutoCommit(true); } catch (Exception ignored) {}
            }

        } catch (SQLException e) {
            return -1;
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
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_SUBSCRIBER_VISIT_HISTORY)) {

            ps.setInt(1, subscriberId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date sqlOriginalDate = rs.getDate("OriginalReservationDate");
                    Timestamp sqlArrival = rs.getTimestamp("ActualArrivalTime");
                    Timestamp sqlDeparture = rs.getTimestamp("ActualDepartureTime");

                    LocalDate originalDate = (sqlOriginalDate == null) ? null : sqlOriginalDate.toLocalDate();
                    LocalDateTime arrivalTime = (sqlArrival == null) ? null : sqlArrival.toLocalDateTime();
                    LocalDateTime departureTime = (sqlDeparture == null) ? null : sqlDeparture.toLocalDateTime();

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
     * <p>
     * Uses {@link SQLQueries#GET_SUBSCRIBER_ACTIVE_RESERVATIONS} and maps rows to
     * {@link ActiveReservation}.
     * </p>
     *
     * @param subscriberId the unique ID of the subscriber
     * @return a list of {@link ActiveReservation} objects (empty if none found)
     */
    public ArrayList<ActiveReservation> getActiveReservations(int subscriberId) {
        ArrayList<ActiveReservation> reservations = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_SUBSCRIBER_ACTIVE_RESERVATIONS)) {

            ps.setInt(1, subscriberId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date sqlDate = rs.getDate("ReservationDate");
                    Time sqlTime = rs.getTime("ReservationTime");

                    LocalDate date = (sqlDate == null) ? null : sqlDate.toLocalDate();
                    LocalTime time = (sqlTime == null) ? null : sqlTime.toLocalTime();

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
