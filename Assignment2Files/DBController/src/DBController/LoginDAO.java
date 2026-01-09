package DBController;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import SQLAccess.SQLQueries;

/**
 * DAO specifically for handling User Login and Role detection.
 */
public class LoginDAO {

    private final DataSource dataSource;

    public LoginDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Checks the database to determine the role of the user.
     * Now validates PASSWORD for Subscribers as well.
     */
    public String identifyUserRole(String username, String password) {
        // 1. Check if user is a defined Manager (Hardcoded for Phase 1)
        if ("manager".equalsIgnoreCase(username) && "manager".equals(password)) {
            return "Manager";
        }
        if ("rep".equalsIgnoreCase(username) && "rep".equals(password)) {
            return "Representative";
        }

        // 2. Check if user is a Subscriber in the DB (Username AND Password)
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.LOGIN_SUBSCRIBER)) {
            
            ps.setString(1, username);
            ps.setString(2, password); // <--- Now checking password
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // User found with matching username AND password
                    return "Subscriber";
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 3. If not found (or wrong password), return null
        return null;
    }
}