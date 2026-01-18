package DBController;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import SQLAccess.SQLQueries;
import entities.Subscriber;

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
    
    /**
     * Authenticates a user against the database using the provided credentials.
     * <p>
     * This method executes a secure prepared statement ({@code SQLQueries.LOGIN_SUB}) to verify
     * the username and password. If a matching record is found, it maps the database result
     * set to a {@link Subscriber} entity object.
     * </p>
     * * @param username The username submitted by the client.
     * @param password The password submitted by the client.
     * @return A populated {@link Subscriber} object if authentication is successful; 
     * {@code null} if the credentials are invalid or if a database error occurs.
     */
    public Subscriber doLogin(String username, String password) {
        Subscriber sub = null;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.LOGIN_SUB)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    sub = new Subscriber();

                    // Use your actual column names from the Subscribers table
                    sub.setSubscriberId(rs.getInt("SubscriberID"));
                    sub.setFullName(rs.getString("FullName"));
                    sub.setPhoneNumber(rs.getString("PhoneNumber"));
                    sub.setEmail(rs.getString("Email"));
                    sub.setUserName(rs.getString("UserName"));
                    sub.setRole(rs.getString("Role"));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return sub; // returns null if login failed
    }

}