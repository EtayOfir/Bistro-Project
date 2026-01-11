package DBController;

import SQLAccess.SQLQueries;
import entities.Subscriber;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SubscriberDAO {
    private final DataSource dataSource;

    public SubscriberDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Subscriber> getAllSubscribers() throws SQLException {
        List<Subscriber> subscribers = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ALL_SUBSCRIBERS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Subscriber sub = new Subscriber();
                sub.setSubscriberId(rs.getInt("SubscriberID"));
                sub.setFullName(rs.getString("FullName"));
                sub.setPhoneNumber(rs.getString("PhoneNumber"));
                sub.setEmail(rs.getString("Email"));
                sub.setUserName(rs.getString("UserName"));
                subscribers.add(sub);
            }
        }
        return subscribers;
    }

    // Uses the 6-parameter query (includes Role)
    public int insert(String fullName, String phone, String email, String userName, String qrCode, String role) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.INSERT_SUBSCRIBER, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName == null ? "" : fullName);
            ps.setString(2, phone == null ? "" : phone);
            ps.setString(3, email == null ? "" : email);
            ps.setString(4, userName == null ? "" : userName);
            ps.setString(5, qrCode == null ? "" : qrCode);
            ps.setString(6, role == null ? "Subscriber" : role);

            int updated = ps.executeUpdate();
            if (updated != 1) return -1;
            try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getInt(1) : -1; }
        }
    }

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
                s.setPassword(rs.getString("Password")); // Critical for Login
                s.setRole(rs.getString("Role"));         // Critical for Login
                return s;
            }
        }
    }
}