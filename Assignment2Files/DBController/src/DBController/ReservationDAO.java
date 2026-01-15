package DBController;

import SQLAccess.SQLQueries;
import entities.ActiveReservation;
import entities.Reservation;
import entities.VisitHistory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Data Access Object (DAO) responsible for all database operations
 * related to reservations in the Bistro system.
 *
 * <p>
 * This class encapsulates CRUD operations on the {@code ActiveReservations}
 * table and read-only access to reservation visit history stored in
 * {@code VisitHistory}.
 * </p>
 *
 * <p>
 * <b>Design principles:</b>
 * <ul>
 *   <li>Contains only database-access logic (no business logic)</li>
 *   <li>Uses prepared statements to prevent SQL injection</li>
 *   <li>Uses try-with-resources to ensure proper resource cleanup</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Thread safety:</b><br>
 * This class is thread-safe as it does not maintain mutable shared state.
 * Each method obtains its own database connection from the pool.
 * </p>
 */
public class ReservationDAO {

    /** Data source used to obtain database connections from the pool. */
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
     * Inserts a new casual (walk-in) reservation into the database.
     *
     * <p>
     * The reservation is stored in the {@code ActiveReservations} table
     * with {@code Role = 'Casual'} and {@code Status = 'Confirmed'}.
     * </p>
     *
     * @param reservation the reservation entity containing date, time, diners and confirmation code
     * @param phone       the customer's phone number (may be empty or {@code null})
     * @param email       the customer's email address (may be empty or {@code null})
     * @return the generated {@code ReservationID} if insertion succeeded,
     *         or {@code -1} if insertion failed
     * @throws SQLException if a database access error occurs
     */
    public int insertReservation(Reservation reservation, String phone, String email) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SQLQueries.INSERT_ACTIVE_RESERVATION_CASUAL,
                     Statement.RETURN_GENERATED_KEYS)) {

//            ps.setString(1, reservation.getRole());
//            ps.setString(2, phone == null ? "" : phone);
//            ps.setString(3, email == null ? "" : email);
//            ps.setDate(4, reservation.getReservationDate());
//            ps.setTime(5, reservation.getReservationTime());
//            ps.setInt(6, reservation.getNumberOfGuests());
//            ps.setString(7, reservation.getConfirmationCode());

        	ps.setString(1, "Casual");

            if (phone == null || phone.isBlank()) ps.setNull(2, java.sql.Types.VARCHAR);
            else ps.setString(2, phone);

            if (email == null || email.isBlank()) ps.setNull(3, java.sql.Types.VARCHAR);
            else ps.setString(3, email);

            ps.setDate(4, reservation.getReservationDate());
            ps.setTime(5, reservation.getReservationTime());
            ps.setInt(6, reservation.getNumberOfGuests());
            ps.setString(7, reservation.getConfirmationCode());

            System.out.println("DEBUG inserting ActiveReservations.Role = Casual");


            if (ps.executeUpdate() != 1) {
                return -1;
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Inserts a reservation without casual contact details.
     *
     * <p>
     * This overload is provided for backward compatibility and internally
     * delegates to {@link #insertReservation(Reservation, String, String)}.
     * </p>
     *
     * @param reservation the reservation entity to insert
     * @return the generated {@code ReservationID}, or {@code -1} on failure
     * @throws SQLException if a database access error occurs
     */
    public int insertReservation(Reservation reservation) throws SQLException {
        return insertReservation(reservation, "", "");
    }

    /**
     * Inserts a new reservation into the database, handling both subscriber and casual reservations.
     *
     * <p>
     * If the subscription ID is greater than 0, it inserts as a subscriber reservation.
     * Otherwise, it inserts as a casual reservation with phone and email details.
     * </p>
     *
     * @param reservation the reservation entity containing date, time, diners and confirmation code
     * @param phone       the customer's phone number (used only for casual reservations)
     * @param email       the customer's email address (used only for casual reservations)
     * @param subscriberId the subscriber ID (if 0 or negative, treated as casual)
     * @return the generated {@code ReservationID} if insertion succeeded,
     *         or {@code -1} if insertion failed
     * @throws SQLException if a database access error occurs
     */
    public int insertReservation(Reservation reservation, String phone, String email, int subscriberId) throws SQLException {
        if (subscriberId > 0) {
            // Insert as subscriber/manager/representative reservation
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         SQLQueries.INSERT_ACTIVE_RESERVATION_SUBSCRIBER,
                         Statement.RETURN_GENERATED_KEYS)) {

            	ps.setString(1, "Subscriber"); // DB Role is reservation type only
                ps.setInt(2, subscriberId);
                ps.setDate(3, reservation.getReservationDate());
                ps.setTime(4, reservation.getReservationTime());
                ps.setInt(5, reservation.getNumberOfGuests());
                ps.setString(6, reservation.getConfirmationCode());

                if (ps.executeUpdate() != 1) {
                    return -1;
                }

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    return rs.next() ? rs.getInt(1) : -1;
                }
            }
        } else {
            // Insert as casual reservation
            return insertReservation(reservation, phone, email);
        }
    }

    /**
     * Retrieves a reservation by its unique reservation ID.
     *
     * <p>
     * Uses {@link SQLQueries#GET_RESERVATION_BY_ORDER_NUMBER} which returns aliased columns:
     * {@code order_number, number_of_guests, order_date, order_time, confirmation_code, subscriber_id}.
     * </p>
     *
     * @param reservationId the primary key of the reservation
     * @return a {@link Reservation} object if found, or {@code null} if no reservation exists
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

                Integer tableNum = rs.getObject("TableNumber", Integer.class);

                return new Reservation(
                    rs.getInt("order_number"),
                    rs.getInt("number_of_guests"),
                    rs.getDate("order_date"),
                    rs.getTime("order_time"),
                    rs.getString("confirmation_code"),
                    rs.getInt("subscriber_id"),
                    rs.getString("Status"),
                    rs.getString("Role"),
                    tableNum
                );

            }
        }
    }

    /**
     * Updates the details of an existing reservation (by ReservationID).
     *
     * @param reservationId the reservation ID to update
     * @param numGuests     the new number of diners
     * @param date          the new reservation date
     * @param time          the new reservation time
     * @return {@code true} if exactly one row was updated, {@code false} otherwise
     * @throws SQLException if a database access error occurs
     */
    public boolean updateReservation(int reservationId, int numGuests, Date date, Time time) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.UPDATE_RESERVATION_BY_ORDER_NUMBER)) {

            ps.setInt(1, numGuests);
            ps.setDate(2, date);
            ps.setTime(3, time);
            ps.setInt(4, reservationId);

            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Retrieves a reservation by its confirmation code from {@code ActiveReservations}.
     *
     * <p>
     * Uses {@link SQLQueries#GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE}.
     * Column names are the real DB column names (e.g. {@code ReservationID, NumOfDiners, ReservationDate...}).
     * </p>
     *
     * @param confirmationCode the confirmation code to search by
     * @return a {@link Reservation} if found, or {@code null} if not found
     * @throws SQLException if a database access error occurs
     */
    public Reservation getReservationByConfirmationCode(String confirmationCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE)) {

            ps.setString(1, confirmationCode);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                Integer tableNum = rs.getObject("TableNumber", Integer.class);

                return new Reservation(
                    rs.getInt("ReservationID"),
                    rs.getInt("NumOfDiners"),
                    rs.getDate("ReservationDate"),
                    rs.getTime("ReservationTime"),
                    rs.getString("ConfirmationCode"),
                    rs.getInt("SubscriberID"),
                    rs.getString("Status"),
                    rs.getString("Role"),
                    tableNum
                );
            }
        }
    }

    /**
     * Retrieves all reservations for a specific date from {@code ActiveReservations}
     * excluding canceled reservations.
     *
     * @param date the date to query
     * @return list of {@link Reservation} for that date (empty list if none)
     * @throws SQLException if a database access error occurs
     */
    public List<Reservation> getReservationsByDate(Date date) throws SQLException {
        List<Reservation> results = new ArrayList<>();

        final String sql =
        	    "SELECT ReservationID, NumOfDiners, ReservationDate, ReservationTime, " +
        	    "ConfirmationCode, SubscriberID, Status, Role, TableNumber " +
        	    "FROM ActiveReservations " +
        	    "WHERE ReservationDate = ? AND Status != 'Canceled' " +
        	    "ORDER BY ReservationTime";


        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, date);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                	Integer tableNum = rs.getObject("TableNumber", Integer.class);
                	results.add(new Reservation(
                            rs.getInt("ReservationID"),
                            rs.getInt("NumOfDiners"),
                            rs.getDate("ReservationDate"),
                            rs.getTime("ReservationTime"),
                            rs.getString("ConfirmationCode"),
                            rs.getInt("SubscriberID"),
                            rs.getString("Status"),
                            rs.getString("Role"),
                            tableNum
                        ));
                }
            }
        }

        return results;
    }

    /**
     * Cancels a reservation by setting its status to {@code 'Canceled'} using the confirmation code.
     *
     * <p>
     * Uses {@link SQLQueries#CANCEL_ACTIVE_RESERVATION}. This is a "soft cancel".
     * </p>
     *
     * @param confirmationCode the confirmation code of the reservation to cancel
     * @return {@code true} if at least one row was updated, {@code false} otherwise
     * @throws SQLException if a database access error occurs
     */
    public boolean cancelReservationByConfirmationCode(String confirmationCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.CANCEL_ACTIVE_RESERVATION)) {

            ps.setString(1, confirmationCode);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Deletes a reservation from {@code ActiveReservations} by confirmation code.
     *
     * <p><b>Note:</b> This is a "hard delete". If you prefer soft-cancel, use
     * {@link #cancelReservationByConfirmationCode(String)}.</p>
     *
     * @param confirmationCode the confirmation code of the reservation to delete
     * @return {@code true} if a row was deleted, {@code false} if nothing matched
     * @throws SQLException if a database access error occurs
     */
    public boolean deleteReservationByConfirmationCode(String confirmationCode) throws SQLException {
        final String sql = "DELETE FROM ActiveReservations WHERE ConfirmationCode = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, confirmationCode);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Marks a single specific reservation as expired.
     * Only marks the reservation if it's in 'Confirmed' status.
     * 
     * @param reservationId the ID of the reservation to mark as expired
     * @return true if the reservation was successfully marked as expired, false otherwise
     * @throws SQLException if a database access error occurs
     */
    public boolean markSingleReservationExpired(int reservationId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // First check if reservation exists and is Confirmed
            try (PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT Status, ReservationDate, ReservationTime FROM ActiveReservations WHERE ReservationID = ?")) {
                checkPs.setInt(1, reservationId);
                
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("DEBUG: Reservation ID " + reservationId + " not found");
                        return false;
                    }
                    
                    String status = rs.getString("Status");
                    System.out.println("DEBUG: Reservation ID " + reservationId + " has status: " + status);
                    
                    // Only mark if it's Confirmed
                    if (!"Confirmed".equalsIgnoreCase(status)) {
                        System.out.println("DEBUG: Reservation ID " + reservationId + " is not Confirmed, skipping mark as expired");
                        return false;
                    }
                }
            }
            
            // Update the reservation to Expired
            try (PreparedStatement updatePs = conn.prepareStatement(
                    "UPDATE ActiveReservations SET Status = 'Expired' WHERE ReservationID = ?")) {
                updatePs.setInt(1, reservationId);
                int rowsAffected = updatePs.executeUpdate();
                
                if (rowsAffected > 0) {
                    System.out.println("DEBUG: Successfully marked reservation ID " + reservationId + " as Expired");
                    return true;
                } else {
                    System.out.println("DEBUG: Failed to update reservation ID " + reservationId);
                    return false;
                }
            }
        }
    }

    /**
     * Marks expired reservations as 'Expired' instead of deleting them.
     * This preserves the reservation history while flagging them as no longer active.
     * 
     * A reservation is considered expired if its scheduled time has passed by more than 15 minutes
     * and its status is still 'Confirmed'.
     * Scans the ENTIRE database for all expired reservations.
     *
     * @return number of reservations marked as expired
     * @throws SQLException if a database access error occurs
     */
    public int deleteExpiredReservations() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            
            // First, check if Status column exists and what its type is
            boolean statusColumnExists = false;
            String currentColumnType = "";
            
            try (PreparedStatement checkColPs = conn.prepareStatement(
                    "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ActiveReservations' AND COLUMN_NAME = 'Status'")) {
                try (ResultSet rs = checkColPs.executeQuery()) {
                    if (rs.next()) {
                        statusColumnExists = true;
                        currentColumnType = rs.getString(1);
                        System.out.println("DEBUG: Status column exists with type: " + currentColumnType);
                    }
                }
            } catch (SQLException e) {
                System.out.println("DEBUG: Could not check Status column: " + e.getMessage());
            }
            
            // If Status column exists, ensure it supports 'Expired'
            if (statusColumnExists) {
                try {
                    System.out.println("DEBUG: Ensuring Status column supports 'Expired' value...");
                    String alterSQL = "ALTER TABLE ActiveReservations MODIFY COLUMN Status ENUM('Confirmed','Arrived','Late','Canceled','Completed','Paid','Expired')";
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(alterSQL);
                        System.out.println("DEBUG: Status column updated to support 'Expired' value");
                    }
                } catch (SQLException e) {
                    // Column might already have 'Expired', or alter might fail - continue anyway
                    System.out.println("DEBUG: Status column alter skipped: " + e.getMessage());
                }
            } else {
                System.out.println("WARNING: Status column does not exist in ActiveReservations table!");
                return 0;
            }
            
            java.util.List<Integer> expiredIds = new java.util.ArrayList<>();
            
            // First, check for ALL confirmed reservations that should be expired
            System.out.println("DEBUG: Scanning entire database for confirmed reservations with expired times (15+ minutes past)...");
            System.out.println("DEBUG: Current server time: " + new java.util.Date());
            
            try (PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT ReservationID, ConfirmationCode, ReservationDate, ReservationTime, Status, " +
                    "DATE_ADD(CONCAT(ReservationDate, ' ', ReservationTime), INTERVAL 15 MINUTE) as ExpiredThreshold, " +
                    "NOW() as CurrentTime " +
                    "FROM ActiveReservations " +
                    "WHERE Status = 'Confirmed' " +
                    "AND DATE_ADD(CONCAT(ReservationDate, ' ', ReservationTime), INTERVAL 15 MINUTE) < NOW() " +
                    "ORDER BY ReservationDate ASC, ReservationTime ASC")) {
                try (ResultSet rs = checkPs.executeQuery()) {
                    while (rs.next()) {
                        int resId = rs.getInt(1);
                        expiredIds.add(resId);
                        System.out.println("DEBUG: Found expired reservation - ID: " + resId + 
                                         ", Code: " + rs.getString(2) + 
                                         ", DateTime: " + rs.getDate(3) + " " + rs.getTime(4) +
                                         ", Expired Threshold: " + rs.getTimestamp(6) +
                                         ", Current Time: " + rs.getTimestamp(7) +
                                         ", Status: " + rs.getString(5));
                    }
                    System.out.println("DEBUG: Total expired Confirmed reservations found in entire database: " + expiredIds.size());
                }
            } catch (SQLException e) {
                System.err.println("ERROR scanning for expired reservations: " + e.getMessage());
                e.printStackTrace();
                return 0;
            }
            
            // Now update each reservation individually
            int updated = 0;
            if (!expiredIds.isEmpty()) {
                System.out.println("DEBUG: Executing update to mark ALL expired reservations as Expired...");
                
                try (PreparedStatement updatePs = conn.prepareStatement(
                        "UPDATE ActiveReservations SET Status = 'Expired' WHERE ReservationID = ?")) {
                    
                    for (Integer resId : expiredIds) {
                        try {
                            updatePs.setInt(1, resId);
                            int rowsAffected = updatePs.executeUpdate();
                            if (rowsAffected > 0) {
                                updated++;
                                System.out.println("DEBUG: Updated reservation ID " + resId + " to Expired");
                            }
                        } catch (SQLException e) {
                            System.err.println("ERROR updating reservation ID " + resId + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                
                System.out.println("DEBUG: *** SUCCESS *** Updated " + updated + " reservations to Expired status");
                
                // Verify by counting total Expired reservations now
                try (PreparedStatement verifyPs = conn.prepareStatement(
                        "SELECT COUNT(*) FROM ActiveReservations WHERE Status = 'Expired'")) {
                    try (ResultSet rs = verifyPs.executeQuery()) {
                        if (rs.next()) {
                            int totalExpired = rs.getInt(1);
                            System.out.println("DEBUG: Total Expired reservations in database now: " + totalExpired);
                        }
                    }
                } catch (SQLException e) {
                    System.out.println("DEBUG: Could not verify expired count: " + e.getMessage());
                }
            }
            
            return updated;
        }
    }

    /**
     * Attempts to allocate the best available table for a customer upon arrival.
     *
     * <p>Transaction steps:</p>
     * <ol>
     *   <li>Validate reservation by confirmation code (must exist, be Confirmed, and be today)</li>
     *   <li>Find best available table for number of diners</li>
     *   <li>Update table status to Taken</li>
     *   <li>Update reservation status to Arrived</li>
     * </ol>
     *
     * @param confirmationCode reservation confirmation code
     * @return allocated table number, or {@code -1} if allocation failed
     */
    public int allocateTableForCustomer(String confirmationCode) {
        int assignedTable = -1;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psRes = conn.prepareStatement(SQLQueries.GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE)) {
                psRes.setString(1, confirmationCode);

                try (ResultSet rs = psRes.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return -1;
                    }

                    int diners = rs.getInt("NumOfDiners");
                    String status = rs.getString("Status");
                    Date dbDate = rs.getDate("ReservationDate");

                    boolean isConfirmed = "Confirmed".equalsIgnoreCase(status);
                    boolean isToday = (dbDate != null && dbDate.toLocalDate().equals(LocalDate.now()));

                    if (diners <= 0 || !isConfirmed || !isToday) {
                        conn.rollback();
                        return -1;
                    }

                    // Find best table
                    try (PreparedStatement psTable = conn.prepareStatement(SQLQueries.GET_BEST_AVAILABLE_TABLE)) {
                        psTable.setInt(1, diners);

                        try (ResultSet rsTable = psTable.executeQuery()) {
                            if (!rsTable.next()) {
                                conn.rollback();
                                return -1;
                            }
                            assignedTable = rsTable.getInt("TableNumber");
                        }
                    }

                    // Update reservation: mark arrived + store assigned table
                    try (PreparedStatement psUpdateRes =
                             conn.prepareStatement(SQLQueries.SET_RESERVATION_ARRIVED_AND_TABLE)) {
                        psUpdateRes.setInt(1, assignedTable);
                        psUpdateRes.setString(2, confirmationCode);
                        psUpdateRes.executeUpdate();
                    }

                    conn.commit();
                    return assignedTable;
                }
            } catch (SQLException e) {
                conn.rollback();
                return -1;
            } finally {
                try { conn.setAutoCommit(true); } catch (Exception ignored) {}
            }

        } catch (SQLException e) {
            return -1;
        }
    }

    /**
     * Retrieves the visit history of a specific subscriber.
     *
     * <p>
     * The visit history is read from the {@code VisitHistory} table and
     * includes arrival time, departure time, billing and status information.
     * </p>
     *
     * @param subscriberId the unique ID of the subscriber
     * @return a list of {@link VisitHistory} records (empty if none exist)
     */
    public ArrayList<VisitHistory> getVisitHistory(int subscriberId) {
        ArrayList<VisitHistory> historyList = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_SUBSCRIBER_VISIT_HISTORY)) {

            ps.setInt(1, subscriberId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date sqlOriginalDate = rs.getDate("OriginalReservationDate");
                    Timestamp sqlArrival = rs.getTimestamp("ActualArrivalTime");
                    Timestamp sqlDeparture = rs.getTimestamp("ActualDepartureTime");

                    LocalDate originalDate = (sqlOriginalDate == null) ? null : sqlOriginalDate.toLocalDate();
                    LocalDateTime arrivalTime = (sqlArrival == null) ? null : sqlArrival.toLocalDateTime();
                    LocalDateTime departureTime = (sqlDeparture == null) ? null : sqlDeparture.toLocalDateTime();

                    historyList.add(new VisitHistory(
                            originalDate,
                            arrivalTime,
                            departureTime,
                            rs.getDouble("TotalBill"),
                            rs.getDouble("DiscountApplied"),
                            rs.getString("Status")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return historyList;
    }

    /**
     * Retrieves all active (current or future) reservations for a subscriber.
     *
     * <p>
     * Uses {@link SQLQueries#GET_SUBSCRIBER_ACTIVE_RESERVATIONS} and maps rows to
     * {@link ActiveReservation}.
     * </p>
     *
     * @param subscriberId the unique ID of the subscriber
     * @return a list of {@link ActiveReservation} objects (empty if none found)
     */
    public ArrayList<ActiveReservation> getActiveReservations(int subscriberId) {
        ArrayList<ActiveReservation> reservations = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_SUBSCRIBER_ACTIVE_RESERVATIONS)) {

            ps.setInt(1, subscriberId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date sqlDate = rs.getDate("ReservationDate");
                    Time sqlTime = rs.getTime("ReservationTime");

                    LocalDate date = (sqlDate == null) ? null : sqlDate.toLocalDate();
                    LocalTime time = (sqlTime == null) ? null : sqlTime.toLocalTime();

                    reservations.add(new ActiveReservation(
                            date,
                            time,
                            rs.getInt("NumOfDiners"),
                            rs.getString("ConfirmationCode"),
                            rs.getString("Status")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return reservations;
    }

    /**
     * Gets reservation statistics for a date range.
     * DEBUG: Includes detailed logging of query execution and results.
     * 
     * @param startDate the start date (SQL Date)
     * @param endDate the end date (SQL Date)
     * @return a map with keys: "total", "confirmed", "arrived", "late", "expired", "totalGuests"
     */
    public Map<String, Integer> getReservationStatsByDateRange(Date startDate, Date endDate) throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", 0);
        stats.put("confirmed", 0);
        stats.put("arrived", 0);
        stats.put("late", 0);
        stats.put("expired", 0);
        stats.put("totalGuests", 0);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_RESERVATION_STATS_BY_DATE_RANGE)) {
            ps.setDate(1, startDate);
            ps.setDate(2, endDate);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("total", rs.getInt("TotalReservations"));
                    stats.put("confirmed", rs.getInt("ConfirmedCount"));
                    stats.put("arrived", rs.getInt("ArrivedCount"));
                    stats.put("late", rs.getInt("LateCount"));
                    stats.put("expired", rs.getInt("ExpiredCount"));
                    stats.put("totalGuests", rs.getInt("TotalGuests"));
                }
            }
        }
        return stats;
    }

    /**
     * Gets detailed reservations for a date range (for Excel export).
     * @param startDate the start date (SQL Date)
     * @param endDate the end date (SQL Date)
     * @return a list of maps containing reservation details
     */
    public List<Map<String, Object>> getDetailedReservationsByDateRange(Date startDate, Date endDate) throws SQLException {
        List<Map<String, Object>> reservations = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_DETAILED_RESERVATIONS_BY_DATE_RANGE)) {
            
            ps.setDate(1, startDate);
            ps.setDate(2, endDate);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> res = new HashMap<>();
                    res.put("ReservationID", rs.getInt("ReservationID"));
                    res.put("ConfirmationCode", rs.getString("ConfirmationCode"));
                    res.put("ReservationDate", rs.getDate("ReservationDate"));
                    res.put("ReservationTime", rs.getTime("ReservationTime"));
                    res.put("NumOfDiners", rs.getInt("NumOfDiners"));
                    res.put("Status", rs.getString("Status"));
                    res.put("Role", rs.getString("Role"));
                    res.put("SubscriberID", rs.getInt("SubscriberID"));
                    res.put("CasualPhone", rs.getString("CasualPhone"));
                    res.put("CasualEmail", rs.getString("CasualEmail"));
                    reservations.add(res);
                }
            }
        }

        return reservations;
    }

    /**
     * Gets reservation time distribution for a date range.
     * @param startDate the start date (SQL Date)
     * @param endDate the end date (SQL Date)
     * @return a map with hour as key and reservation count as value
     */
    public Map<Integer, Integer> getReservationTimeDistribution(Date startDate, Date endDate) throws SQLException {
        Map<Integer, Integer> timeDistribution = new TreeMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_RESERVATION_TIME_DISTRIBUTION)) {
            
            ps.setDate(1, startDate);
            ps.setDate(2, endDate);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int hour = rs.getInt("Hour");
                    int count = rs.getInt("ReservationCount");
                    timeDistribution.put(hour, count);
                }
            }
        }

        return timeDistribution;
    }

    /**
     * Gets waiting list statistics by date for a date range.
     * @param startDate the start date (SQL Date)
     * @param endDate the end date (SQL Date)
     * @return a list of maps containing date, waiting count, and served count
     */
    public List<Map<String, Object>> getWaitingListByDate(Date startDate, Date endDate) throws SQLException {
        List<Map<String, Object>> waitingStats = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_WAITING_LIST_BY_DATE)) {
            
            ps.setDate(1, startDate);
            ps.setDate(2, endDate);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stat = new HashMap<>();
                    stat.put("EntryDate", rs.getDate("EntryDate"));
                    stat.put("WaitingCount", rs.getInt("WaitingCount"));
                    stat.put("ServedCount", rs.getInt("ServedCount"));
                    waitingStats.add(stat);
                }
            }
        }

        return waitingStats;
    }
    
    /**
     * Retrieves a comprehensive list of all active reservations from the database.
     * <p>
     * This method is designed for the <b>Representative View</b> (Manager/Staff dashboard).
     * It executes the {@code GET_ALL_ACTIVE_RESERVATIONS} query to fetch detailed
     * reservation data, including {@code Role} and {@code Status}.
     * <p>
     * The returned list allows the representative to view all orders, including those
     * that might be canceled or completed, depending on the SQL query definition.
     *
     * @return A {@code List<Reservation>} containing all found reservations.
     * Returns an empty list if no reservations are found.
     * @throws SQLException If a database access error occurs or the column labels
     * do not match the database schema.
     */
    public List<Reservation> getAllActiveReservations() throws SQLException {
        List<Reservation> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ALL_ACTIVE_RESERVATIONS);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                // Mapping the ResultSet row to a Reservation entity.
                // Note: The Reservation constructor must match this parameter order.
            	Integer tableNum = rs.getObject("TableNumber", Integer.class);
            	results.add(new Reservation(
                        rs.getInt("ReservationID"),
                        rs.getInt("NumOfDiners"),
                        rs.getDate("ReservationDate"),
                        rs.getTime("ReservationTime"),
                        rs.getString("ConfirmationCode"),
                        rs.getInt("SubscriberID"),
                        rs.getString("Status"),
                        rs.getString("Role"),
                        tableNum
                    ));
            }
        }
        return results;
    }
    
 // =================== BRANCH SETTINGS ===================

    /** Updates the single row in openinghours. */
    public boolean updateBranchHours(String openHHmm, String closeHHmm) {
        String sql = "UPDATE openinghours SET OpenTime=?, CloseTime=? WHERE SpecialDate IS NULL";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, openHHmm);
            ps.setString(2, closeHHmm);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    /**
     * Insert table if not exists; otherwise update capacity.
     * Requires TableNumber to be PRIMARY KEY or UNIQUE.
     */
    public boolean upsertRestaurantTable(int tableNum, int capacity) {
        String sql = """
            INSERT INTO restauranttables (TableNumber, Capacity)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE Capacity = VALUES(Capacity)
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, tableNum);
            ps.setInt(2, capacity);

            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Deletes a table by table number. */
    public boolean deleteRestaurantTable(int tableNum) {
        String sql = "DELETE FROM restauranttables WHERE TableNumber = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, tableNum);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Fetches opening hours for a specific date.
     * First checks if there are special opening hours for the given date.
     * If not, fetches the regular opening hours for that day of the week.
     *
     * @param date the date to check opening hours for
     * @return a map with "open" and "close" keys containing time strings (HH:mm:ss format), 
     *         or null if no opening hours found
     */
    public Map<String, String> getOpeningHoursForDate(LocalDate date) {
        try (Connection conn = dataSource.getConnection()) {
            // First check for special opening hours for this specific date
            String specialSql = "SELECT OpenTime, CloseTime FROM openinghours WHERE SpecialDate IS NOT NULL AND SpecialDate = ?";
            try (PreparedStatement ps = conn.prepareStatement(specialSql)) {
                ps.setDate(1, java.sql.Date.valueOf(date));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, String> hours = new HashMap<>();
                        hours.put("open", rs.getString("OpenTime"));
                        hours.put("close", rs.getString("CloseTime"));
                        System.out.println("DEBUG: Found special opening hours for " + date);
                        return hours;
                    }
                }
            } catch (SQLException e) {
                System.out.println("DEBUG: Special date query failed (may not have SpecialDate column): " + e.getMessage());
            }

            // If no special hours, try to fetch regular hours for the day of week
            String dayOfWeek = getDayOfWeekName(date);
            
            // Try with DayOfWeek column first
            String regularSql = "SELECT OpenTime, CloseTime FROM openinghours WHERE DayOfWeek = ? AND (SpecialDate IS NULL)";
            try (PreparedStatement ps = conn.prepareStatement(regularSql)) {
                ps.setString(1, dayOfWeek);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, String> hours = new HashMap<>();
                        hours.put("open", rs.getString("OpenTime"));
                        hours.put("close", rs.getString("CloseTime"));
                        System.out.println("DEBUG: Found regular opening hours for " + dayOfWeek + ": " + hours.get("open") + " - " + hours.get("close"));
                        return hours;
                    }
                }
            } catch (SQLException e) {
                System.out.println("DEBUG: Regular query with DayOfWeek failed: " + e.getMessage());
                
                // Fallback: Try querying just the first row (for legacy tables with single opening hours)
                String fallbackSql = "SELECT OpenTime, CloseTime FROM openinghours LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(fallbackSql)) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Map<String, String> hours = new HashMap<>();
                            hours.put("open", rs.getString("OpenTime"));
                            hours.put("close", rs.getString("CloseTime"));
                            System.out.println("DEBUG: Using fallback opening hours: " + hours.get("open") + " - " + hours.get("close"));
                            return hours;
                        }
                    }
                } catch (SQLException e2) {
                    System.out.println("DEBUG: Fallback query also failed: " + e2.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("DEBUG: Error fetching opening hours: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("DEBUG: No opening hours found for " + date);
        return null;
    }

    /**
     * Helper method to get the day of week name from a LocalDate.
     *
     * @param date the date to get day of week from
     * @return the day name (e.g., "Sunday", "Monday", etc.)
     */
    private String getDayOfWeekName(LocalDate date) {
        return date.getDayOfWeek().toString().substring(0, 1).toUpperCase() 
               + date.getDayOfWeek().toString().substring(1).toLowerCase();
    }

    
}
