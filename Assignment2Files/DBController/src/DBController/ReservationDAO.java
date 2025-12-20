package DBController;

import SQLAccess.SQLQueries;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ReservationDAO {

    private final DataSource dataSource;

    public ReservationDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Reservation getReservationByOrderNumber(String orderNum) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_RESERVATION_BY_ORDER_NUMBER)) {

            ps.setString(1, orderNum);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

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
        // conn.close() מחזיר ל־pool
    }

    public boolean updateReservation(String orderNum, int numGuests, String orderDate) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.UPDATE_RESERVATION_BY_ORDER_NUMBER)) {

            ps.setInt(1, numGuests);
            ps.setString(2, orderDate);
            ps.setString(3, orderNum);
            return ps.executeUpdate() == 1;
        }
    }
}
