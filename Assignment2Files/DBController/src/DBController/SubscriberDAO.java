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
 * Data Access Object (DAO) for performing CRUD and related operations on {@link Subscriber} entities.
 * <p>
 * Provides methods to:
 * <ul>
 *   <li>Fetch subscribers (all, by username, by ID)</li>
 *   <li>Insert/register new subscribers (with role-based rules)</li>
 *   <li>Update subscriber contact info</li>
 *   <li>Fetch a subscriber's active reservations and visit history</li>
 * </ul>
 */
public class SubscriberDAO {
    private final DataSource dataSource;

    /**
     * Constructs a {@link SubscriberDAO} with the provided {@link DataSource}.
     *
     * @param dataSource the data source to use for database connections
     */
    public SubscriberDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Retrieves all subscribers from the database.
     * <p>
     * Uses {@code SQLQueries.GET_ALL_SUBSCRIBERS} and maps each row to a {@link Subscriber} object.
     *
     * @return a list of all subscribers (may be empty if none exist)
     * @throws SQLException if a database error occurs while querying
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
     * Inserts a new subscriber record into the database and returns the generated subscriber ID.
     * <p>
     * Uses the 6-parameter insert query (including role): {@code SQLQueries.INSERT_SUBSCRIBER}.
     * Any {@code null} input fields are converted to empty strings before insertion.
     *
     * @param fullName the subscriber full name
     * @param phone    the subscriber phone number
     * @param email    the subscriber email address
     * @param userName the subscriber username
     * @param qrCode   the subscriber QR code string
     * @param role     the role to assign
     * @return the generated subscriber ID if inserted successfully; {@code -1} otherwise
     * @throws SQLException if a database error occurs during insert
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
     * Retrieves a subscriber by username.
     * <p>
     * Uses {@code SQLQueries.GET_SUBSCRIBER_BY_USERNAME}. If found, returns a populated {@link Subscriber}
     * including password and role (marked as critical for login).
     *
     * @param username the username to search for
     * @return the matching subscriber, or {@code null} if not found
     * @throws SQLException if a database error occurs while querying
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
     * Registers a new subscriber (or staff user) with role-based authorization rules.
     * <p>
     * Rules enforced:
     * <ul>
     *   <li>If creator role is {@code Representative}: can only create {@code Subscriber} users.</li>
     *   <li>If creator role is {@code Manager}: can create {@code Manager}, {@code Representative}, or {@code Subscriber}.</li>
     *   <li>Any other creator role is rejected (returns {@code null}).</li>
     * </ul>
     * Also generates a QR code string in the form {@code qr_<FullName>} (spaces replaced with underscores).
     * <p>
     * Uses {@code SQLQueries.REGISTER_SUBSCRIBER} and sets the generated subscriber ID back into {@code newSub}.
     *
     * @param newSub       the subscriber object to register (will be updated with generated ID on success)
     * @param creatorRole  the role of the user performing the creation (authorization control)
     * @return the registered subscriber with generated ID on success; {@code null} if not authorized or on failure
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
     * Updates a subscriber's contact information (phone number and email).
     * <p>
     * Uses {@code SQLQueries.UPDATE_SUBSCRIBER_CONTACT_INFO}.
     *
     * @param id    the subscriber ID
     * @param phone the new phone number
     * @param email the new email address
     * @return {@code true} if at least one row was updated; {@code false} otherwise
     * @throws SQLException if a database error occurs during update
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
     * Retrieves the visit history for a given subscriber.
     * <p>
     * Uses {@code SQLQueries.GET_SUBSCRIBER_VISIT_HISTORY} and maps each row into a
     * {@link entities.VisitHistory} object. Timestamp fields may be {@code null} and are handled accordingly.
     *
     * @param subscriberId the subscriber ID
     * @return a list of visit history records (may be empty)
     * @throws SQLException if a database error occurs while querying
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
     * Retrieves the active reservations for a given subscriber.
     * <p>
     * Uses {@code SQLQueries.GET_SUBSCRIBER_ACTIVE_RESERVATIONS} and maps each row into an
     * {@link entities.ActiveReservation} object.
     *
     * @param subscriberId the subscriber ID
     * @return a list of active reservations (may be empty)
     * @throws SQLException if a database error occurs while querying
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
     * Retrieves a subscriber by ID with minimal contact details.
     * <p>
     * Uses {@code SQLQueries.GET_SUBSCRIBER_BY_ID} and returns a {@link Subscriber} populated with
     * subscriber ID, phone number, and email. Additional fields can be added if needed.
     *
     * @param id the subscriber ID
     * @return the subscriber object if found; {@code null} otherwise
     * @throws SQLException if a database error occurs while querying
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
                    return s;
                }
            }
        }
        return null;
    }
    
}