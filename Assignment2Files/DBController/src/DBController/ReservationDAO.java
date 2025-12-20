package DBController;

import SQLAccess.SQLQueries;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object (DAO) responsible for handling database operations
 * related to {@link Reservation} entities.
 * <p>
 * This class encapsulates all SQL interactions required to retrieve
 * and update reservation data. It uses a {@link DataSource} provided
 * by the database connection pool to obtain connections on demand.
 * <p>
 * Each database operation acquires a connection from the pool and
 * releases it automatically using the try-with-resources mechanism,
 * ensuring safe and efficient resource management.
 */
public class ReservationDAO {

    /** Data source used to obtain database connections from the pool */
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
     * Retrieves a reservation from the database using its unique order number.
     * <p>
     * If no reservation with the given order number exists, this method
     * returns {@code null}.
     *
     * @param orderNum the unique order number of the reservation
     * @return a {@link Reservation} object if found, or {@code null} otherwise
     * @throws SQLException if a database access error occurs
     */
    public Reservation getReservationByOrderNumber(String orderNum) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(SQLQueries.GET_RESERVATION_BY_ORDER_NUMBER)) {

            ps.setString(1, orderNum);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new Reservation(
                        rs.getString("order_number"),
                        rs.getInt("number_of_guests"),
                        rs.getString("order_date"),
                        rs.getString("confirmation_code"),
                        rs.getString("subscriber_id"),
                        rs.getString("date_of_placing_order")
                );
            }
        }
        // Closing the connection returns it to the connection pool
    }

    /**
     * Updates the number of guests and reservation date of an existing reservation.
     * <p>
     * The reservation is identified by its unique order number.
     * The method returns {@code true} if the reservation was successfully updated,
     * or {@code false} if no matching reservation was found.
     *
     * @param orderNum   the unique order number of the reservation
     * @param numGuests the new number of guests
     * @param orderDate the new reservation date (yyyy-MM-dd)
     * @return {@code true} if the update succeeded, {@code false} otherwise
     * @throws SQLException if a database access error occurs
     */
    public boolean updateReservation(String orderNum,
                                     int numGuests,
                                     String orderDate) throws SQLException {

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(SQLQueries.UPDATE_RESERVATION_BY_ORDER_NUMBER)) {

            ps.setInt(1, numGuests);
            ps.setString(2, orderDate);
            ps.setString(3, orderNum);

            return ps.executeUpdate() == 1;
        }
    }
}
