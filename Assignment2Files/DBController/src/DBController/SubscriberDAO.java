package DBController;

import SQLAccess.SQLQueries;
import entities.Subscriber;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public class SubscriberDAO {
    private final DataSource dataSource;

    public SubscriberDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * מחזיר את כל המנויים הרשומים במערכת.
     */
    public List<Subscriber> getAllSubscribers() throws SQLException {
        List<Subscriber> subscribers = new ArrayList<>();
        
        // משתמש בשאילתה שהגדרת ב-SQLQueries
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ALL_SUBSCRIBERS);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Subscriber sub = new Subscriber();
                // ודאי שהשמות כאן תואמים למה שיש לך ב-Entity של Subscriber
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
}