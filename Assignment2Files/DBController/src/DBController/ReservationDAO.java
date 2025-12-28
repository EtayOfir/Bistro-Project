package DBController;

import SQLAccess.SQLQueries;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;

/**
 * Data Access Object (DAO) responsible for handling database operations
 * related to {@link Reservation} entities.
 * <p>
 * This class encapsulates all SQL interactions required to create, retrieve,
 * and update reservation data in the 'ActiveReservations' table.
 * It uses a {@link DataSource} provided by the database connection pool to
 * obtain connections on demand.
 * <p>
 * <b>Thread Safety:</b> Each database operation acquires a unique connection
 * from the pool and releases it automatically using the try-with-resources
 * mechanism, ensuring safe execution in a multi-threaded environment.
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
     * Inserts a new casual (walk-in) reservation into the database with phone and email.
     *
     * @param reservation the reservation entity containing data to be inserted
     * @param phone the customer's phone number
     * @param email the customer's email address
     * @return the generated ReservationID if successful, or -1 if insertion failed
     * @throws SQLException if a database access error occurs or the connection is closed
     */
    public int insertReservation(Reservation reservation, String phone, String email) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.INSERT_ACTIVE_RESERVATION_CASUAL, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            // Format: INSERT INTO ActiveReservations (CustomerType, CasualPhone, CasualEmail, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status)
            // VALUES ('Casual', ?, ?, ?, ?, ?, ?, 'Confirmed')
            
            // 1. CasualPhone
            ps.setString(1, phone == null ? "" : phone); 
            
            // 2. CasualEmail
            ps.setString(2, email == null ? "" : email);
            
            // 3. ReservationDate (Expects java.sql.Date)
            ps.setDate(3, reservation.getReservationDate());
            
            // 4. ReservationTime (Expects java.sql.Time)
            ps.setTime(4, reservation.getReservationTime());
            
            // 5. NumOfDiners
            ps.setInt(5, reservation.getNumberOfGuests());
            
            // 6. ConfirmationCode (max 10 chars - database column is small)
            String code = reservation.getConfirmationCode();
            if (code.length() > 10) {
                code = code.substring(0, 10);
            }
            ps.setString(6, code);

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return -1;
        }
    }

    /**
     * Inserts a new casual (walk-in) reservation into the database.
     * (Overloaded version for backward compatibility)
     */
    public int insertReservation(Reservation reservation) throws SQLException {
        return insertReservation(reservation, "", "");
    }
    
    /**
     * Retrieves a reservation from the database using its unique identifier.
     * <p>
     * This method executes {@code SQLQueries.GET_RESERVATION_BY_ORDER_NUMBER}
     * and maps the result set aliases (e.g., 'order_number', 'order_date')
     * back to a Java {@link Reservation} object.
     *
     * @param reservationId the unique primary key (ReservationID) of the reservation
     * @return a {@link Reservation} object if found, or {@code null} if no match exists
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

                // Constructing the object based on the aliases defined in SQLQueries
                return new Reservation(
                        rs.getInt("order_number"),       // Mapped from ReservationID
                        rs.getInt("number_of_guests"),   // Mapped from NumOfDiners
                        rs.getDate("order_date"),        // Mapped from ReservationDate
                        rs.getTime("order_time"),        // Mapped from ReservationTime
                        rs.getString("confirmation_code"),
                        rs.getInt("subscriber_id")
                );
            }
        }
    }

    /**
     * Updates the details of an existing reservation.
     * <p>
     * This method updates the number of guests, the date, and the time for a
     * reservation identified by the given {@code reservationId}.
     *
     * @param reservationId the unique ID of the reservation to update
     * @param numGuests     the new number of guests
     * @param newDate       the new date of the reservation (java.sql.Date)
     * @param newTime       the new time of the reservation (java.sql.Time)
     * @return {@code true} if the update succeeded (row was found and modified),
     * {@code false} otherwise
     * @throws SQLException if a database access error occurs
     */
    public boolean updateReservation(int reservationId, int numGuests, Date newDate, Time newTime) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.UPDATE_RESERVATION_BY_ORDER_NUMBER)) {

            // Query params: 1:NumOfDiners, 2:ReservationDate, 3:ReservationTime, 4:ReservationID
            ps.setInt(1, numGuests);
            ps.setDate(2, newDate);
            ps.setTime(3, newTime);
            ps.setInt(4, reservationId);

            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Retrieves all reservations for a specific date from ActiveReservations table.
     * Only returns non-canceled reservations (Status != 'Canceled')
     *
     * @param date the date to query (java.sql.Date)
     * @return a list of Reservation objects for that date, or empty list if none found
     * @throws SQLException if a database access error occurs
     */
    public java.util.List<Reservation> getReservationsByDate(Date date) throws SQLException {
        java.util.List<Reservation> results = new java.util.ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT ReservationID, NumOfDiners, ReservationDate, ReservationTime, ConfirmationCode, SubscriberID " +
                "FROM ActiveReservations WHERE ReservationDate = ? AND Status != 'Canceled' ORDER BY ReservationTime")) {

            ps.setDate(1, date);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation res = new Reservation(
                            rs.getInt("ReservationID"),
                            rs.getInt("NumOfDiners"),
                            rs.getDate("ReservationDate"),
                            rs.getTime("ReservationTime"),
                            rs.getString("ConfirmationCode"),
                            rs.getInt("SubscriberID")
                    );
                    results.add(res);
                }
            }
        }
        return results;
    }

    /**
     * Cancels a reservation by setting its status to 'Canceled' using the confirmation code.
     *
     * @param confirmationCode the confirmation code of the reservation to cancel
     * @return {@code true} if the cancellation succeeded, {@code false} if reservation not found
     * @throws SQLException if a database access error occurs
     */
    public boolean cancelReservationByConfirmationCode(String confirmationCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.CANCEL_ACTIVE_RESERVATION)) {

            ps.setString(1, confirmationCode);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        }
    }

    /**
     * Deletes a reservation from the database by confirmation code.
     *
     * @param confirmationCode the confirmation code of the reservation to delete
     * @return {@code true} if the deletion succeeded, {@code false} if reservation not found
     * @throws SQLException if a database access error occurs
     */
    public boolean deleteReservationByConfirmationCode(String confirmationCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM ActiveReservations WHERE ConfirmationCode = ?")) {

            ps.setString(1, confirmationCode);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        }
    }

    /**
     * Retrieves a reservation by its confirmation code.
     *
     * @param confirmationCode the confirmation code to search for
     * @return a Reservation object if found, or {@code null} if not found
     * @throws SQLException if a database access error occurs
     */
    public Reservation getReservationByConfirmationCode(String confirmationCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE)) {

            ps.setString(1, confirmationCode);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

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
}