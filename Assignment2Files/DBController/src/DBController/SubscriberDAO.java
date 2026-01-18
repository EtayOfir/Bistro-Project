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

/**
 * Data Access Object (DAO) for managing Subscriber entities in the database.
 * <p>
 * This class handles all CRUD operations related to subscribers, including:
 * <ul>
 * <li>Authentication and retrieval by username or ID.</li>
 * <li>Registration of new subscribers with role-based permission checks.</li>
 * <li>Updating contact information.</li>
 * <li>Retrieving associated data like visit history and active reservations.</li>
 * </ul>
 * </p>
 */
public class SubscriberDAO {
    private final DataSource dataSource;

    /**
     * Constructs a new SubscriberDAO.
     *
     * @param dataSource The {@link DataSource} used for database connections.
     */
    public SubscriberDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Retrieves a list of all registered subscribers.
     *
     * @return A list of {@link Subscriber} objects.
     * @throws SQLException If a database access error occurs.
     */
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

    /**
     * Inserts a new subscriber directly into the database (low-level insert).
     *
     * @param fullName The full name.
     * @param phone    The phone number.
     * @param email    The email address.
     * @param userName The username.
     * @param qrCode   The QR code string.
     * @param role     The role (Subscriber/Representative/Manager).
     * @return The generated SubscriberID, or -1 if insertion failed.
     * @throws SQLException If a database error occurs.
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
            try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getInt(1) : -1; }
        }
    }

    /**
     * Retrieves a subscriber by their username.
     * Used primarily during the login process to fetch password and role.
     *
     * @param username The username to search for.
     * @return A populated {@link Subscriber} object, or {@code null} if not found.
     * @throws SQLException If a database error occurs.
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
                s.setPassword(rs.getString("Password")); // Critical for Login
                s.setRole(rs.getString("Role"));         // Critical for Login
                return s;
            }
        }
    }
    
    /**
     * Registers a new subscriber with business logic validation.
     * 

[Image of User Registration Flowchart]

     * <p>
     * This method enforces role-based permissions:
     * <ul>
     * <li><b>Representatives</b> can only create regular Subscribers.</li>
     * <li><b>Managers</b> can create Managers, Representatives, or Subscribers.</li>
     * </ul>
     * It also automatically generates a QR code string for the new user.
     * </p>
     *
     * @param newSub      The subscriber object containing the new user's details.
     * @param creatorRole The role of the user performing the registration.
     * @return The registered {@link Subscriber} object with its new ID, or {@code null} if permission denied or failure.
     */
    public Subscriber register(Subscriber newSub, String creatorRole) {
	    // Representative can only create Subscribers
	    if ("Representative".equalsIgnoreCase(creatorRole)) {
	        newSub.setRole("Subscriber");
	    }

	    // Manager can create Manager / Representative / Subscriber
	    if ("Manager".equalsIgnoreCase(creatorRole)) {
	        String r = newSub.getRole();
	        boolean ok = "Manager".equalsIgnoreCase(r)
	                  || "Representative".equalsIgnoreCase(r)
	                  || "Subscriber".equalsIgnoreCase(r);
	        if (!ok) return null;
	    }

	    // If someone else tries (optional)
	    if (!"Manager".equalsIgnoreCase(creatorRole) && !"Representative".equalsIgnoreCase(creatorRole)) {
	        return null;
	    }

	    String qr = "qr_" + newSub.getFullName().trim().replaceAll("\\s+", "_");

	   
	    try (Connection conn = dataSource.getConnection();
	         PreparedStatement ps = conn.prepareStatement(SQLQueries.REGISTER_SUBSCRIBER, Statement.RETURN_GENERATED_KEYS)) {

	        ps.setString(1, newSub.getFullName());
	        ps.setString(2, newSub.getPhoneNumber());
	        ps.setString(3, newSub.getEmail());
	        ps.setString(4, newSub.getUserName());
	        ps.setString(5, newSub.getPassword());
	        ps.setString(6, qr);                    // qr_<name>
	        ps.setString(7, newSub.getRole());      // Manager / Representative / Subscriber

	        int rows = ps.executeUpdate();
	        if (rows == 0) return null;

	        try (ResultSet keys = ps.getGeneratedKeys()) {
	            if (keys.next()) newSub.setSubscriberId(keys.getInt(1));
	        }

	        // (Only if you add these fields to Subscriber class)
	        // newSub.setQRCode(qr);

	        return newSub;

	    } catch (SQLException e) {
	        e.printStackTrace();
	        return null;
	    }
	}
    
    /**
     * Updates the contact information for a specific subscriber.
     *
     * @param id    The ID of the subscriber.
     * @param phone The new phone number.
     * @param email The new email address.
     * @return {@code true} if the update was successful.
     * @throws SQLException If a database error occurs.
     */
    public boolean updateContactInfo(int id, String phone, String email) throws SQLException {
        // SQLQueries.UPDATE_SUBSCRIBER_CONTACT_INFO = "UPDATE Subscribers SET PhoneNumber = ?, Email = ? WHERE SubscriberID = ?"
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.UPDATE_SUBSCRIBER_CONTACT_INFO)) {
            ps.setString(1, phone);
            ps.setString(2, email);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Retrieves the visit history for a specific subscriber.
     *
     * @param subscriberId The ID of the subscriber.
     * @return A list of {@link entities.VisitHistory} objects.
     * @throws SQLException If a database error occurs.
     */
    public List<entities.VisitHistory> getSubscriberVisitHistory(int subscriberId) throws SQLException {
        List<entities.VisitHistory> history = new ArrayList<>();
        // SQLQueries.GET_SUBSCRIBER_VISIT_HISTORY
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_SUBSCRIBER_VISIT_HISTORY)) {
            ps.setInt(1, subscriberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entities.VisitHistory vh = new entities.VisitHistory(
                        rs.getDate("OriginalReservationDate").toLocalDate(),
                        rs.getTimestamp("ActualArrivalTime") != null ? rs.getTimestamp("ActualArrivalTime").toLocalDateTime() : null,
                        rs.getTimestamp("ActualDepartureTime") != null ? rs.getTimestamp("ActualDepartureTime").toLocalDateTime() : null,
                        rs.getDouble("TotalBill"),
                        rs.getDouble("DiscountApplied"),
                        rs.getString("Status")
                    );
                    history.add(vh);
                }
            }
        }
        return history;
    }

    /**
     * Retrieves the active (future) reservations for a specific subscriber.
     *
     * @param subscriberId The ID of the subscriber.
     * @return A list of {@link entities.ActiveReservation} objects.
     * @throws SQLException If a database error occurs.
     */
    public List<entities.ActiveReservation> getSubscriberActiveReservations(int subscriberId) throws SQLException {
        List<entities.ActiveReservation> list = new ArrayList<>();
        // SQLQueries.GET_SUBSCRIBER_ACTIVE_RESERVATIONS
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_SUBSCRIBER_ACTIVE_RESERVATIONS)) {
            ps.setInt(1, subscriberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entities.ActiveReservation ar = new entities.ActiveReservation(
                        rs.getDate("ReservationDate").toLocalDate(),
                        rs.getTime("ReservationTime").toLocalTime(),
                        rs.getInt("NumOfDiners"),
                        rs.getString("ConfirmationCode"),
                        rs.getString("Status")
                    );
                    list.add(ar);
                }
            }
        }
        return list;
    }
    
    /**
     * Retrieves a subscriber's basic details by their unique ID.
     *
     * @param id The subscriber ID.
     * @return A {@link Subscriber} object with basic details (ID, Phone, Email), or {@code null} if not found.
     * @throws SQLException If a database error occurs.
     */
    public Subscriber getSubscriberById(int id) throws SQLException {
        try (Connection conn = dataSource.getConnection();
        		PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_SUBSCRIBER_BY_ID)) {            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Subscriber s = new Subscriber();
                    s.setSubscriberId(rs.getInt("SubscriberID"));
                    s.setPhoneNumber(rs.getString("PhoneNumber"));
                    s.setEmail(rs.getString("Email"));
                    // אפשר להוסיף עוד שדות אם צריך
                    return s;
                }
            }
        }
        return null;
    }
    
}