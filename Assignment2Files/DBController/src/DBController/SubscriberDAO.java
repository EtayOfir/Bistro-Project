package DBController;

import SQLAccess.SQLQueries;
import entities.Subscriber;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DAO for Subscriber-related DB operations.
 */
public class SubscriberDAO {

    private final DataSource dataSource;

    public SubscriberDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Inserts a new subscriber and returns generated SubscriberID, or -1 on failure.
     */
    public int insert(String fullName, String phone, String email, String userName, String qrCode, String role) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.INSERT_SUBSCRIBER, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, fullName == null ? "" : fullName);
            ps.setString(2, phone == null ? "" : phone);
            ps.setString(3, email == null ? "" : email);
            ps.setString(4, userName == null ? "" : userName);
            ps.setString(5, qrCode == null ? "" : qrCode);
            ps.setString(6, role == null ? "" : role);

            int updated = ps.executeUpdate();
            if (updated != 1) return -1;

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Retrieves a subscriber by username, or null if not found.
     */
    public Subscriber getByUsername(String username) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_SUBSCRIBER_BY_USERNAME)) {

            ps.setString(1, username == null ? "" : username);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                Subscriber s = new Subscriber();
                s.setSubscriberId(rs.getInt("SubscriberID"));
                s.setFullName(rs.getString("FullName"));
                s.setUserName(rs.getString("UserName"));
                s.setPhoneNumber(rs.getString("PhoneNumber"));
                s.setEmail(rs.getString("Email"));
                // Role is available in the result set but the entity currently doesn't have a field for it.
                return s;
            }
        }
    }
}
