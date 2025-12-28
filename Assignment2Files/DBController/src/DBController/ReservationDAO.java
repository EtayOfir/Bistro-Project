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
    private BillPaymentDAO billPaymentDAO;

    /**
     * Constructs a new {@code ReservationDAO} using the given data source.
     *
     * @param dataSource the {@link DataSource} providing pooled database connections
     */
    public ReservationDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Inserts a new reservation into the database.
     * <p>
     * This method maps the fields of the {@link Reservation} object to the
     * SQL parameters required by {@code SQLQueries.INSERT_RESERVATION}.
     * <p>
     * <b>Note:</b> The {@code ReservationID} is not included in the insert
     * statement as it is an {@code AUTO_INCREMENT} field in the database.
     *
     * @param reservation the reservation entity containing data to be inserted
     * @return {@code true} if the reservation was successfully inserted (1 row affected),
     * {@code false} otherwise
     * @throws SQLException if a database access error occurs or the connection is closed
     */
    public boolean insertReservation(Reservation reservation) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.INSERT_RESERVATION)) {

            // 1. NumOfDiners
            ps.setInt(1, reservation.getNumberOfGuests());
            
            // 2. ReservationDate (Expects java.sql.Date)
            ps.setDate(2, reservation.getReservationDate());
            
            // 3. ConfirmationCode
            ps.setString(3, reservation.getConfirmationCode());
            
            // 4. SubscriberID (int)
            ps.setInt(4, reservation.getSubscriberId());
            
            // 5. ReservationTime (Expects java.sql.Time)
            ps.setTime(5, reservation.getReservationTime());

            return ps.executeUpdate() == 1;
        }
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
}