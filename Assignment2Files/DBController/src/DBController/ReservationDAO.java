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
    
 // ====== Table allocation for booking (±2 hours) ======
    private static final int TABLE_RADIUS_MINUTES = 120;

    
    private int findTableForBooking(Connection conn, LocalDate date, LocalTime time, int diners) throws SQLException {

    	String sql =
    		    "SELECT rt.TableNumber " +
    		    "FROM RestaurantTables rt " +
    		    "WHERE rt.Capacity >= ? " +
    		    "  AND NOT EXISTS ( " +
    		    "      SELECT 1 FROM ActiveReservations ar " +
    		    "      WHERE ar.TableNumber = rt.TableNumber " +
    		    "        AND ar.ReservationDate = ? " +
    		    "        AND ar.Status IN ('Confirmed','Arrived','Late') " +
    		    "        AND ABS(TIMESTAMPDIFF(MINUTE, " +
    		    "            TIMESTAMP(ar.ReservationDate, ar.ReservationTime), " +
    		    "            TIMESTAMP(?, ?) " +
    		    "        )) <= ? " +
    		    "  ) " +
    		    "ORDER BY rt.Capacity ASC, RAND() " +
    		    "LIMIT 1";


        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, diners);                 // rt.Capacity >= ?
            ps.setDate(2, Date.valueOf(date));    // ar.ReservationDate = ?
            ps.setDate(3, Date.valueOf(date));    // TIMESTAMP(?, ?)   
            ps.setTime(4, Time.valueOf(time));    // TIMESTAMP(?, ?)  
            ps.setInt(5, TABLE_RADIUS_MINUTES);   // < ?

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return -1;
                return rs.getInt(1); // TableNumber
            }
        }
    }
    private int getMaxTableCapacity(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT MAX(Capacity) AS mx FROM RestaurantTables");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return 0;
            return rs.getInt("mx");
        }
    }
 // ====== Availability checks (server-side source of truth) ======

    /** Result object for an availability check. */
    public static class AvailabilityResult {
        public final boolean available;
        public final List<LocalDateTime> alternatives;
        public final String errorCode; // null if no error

        public AvailabilityResult(boolean available, List<LocalDateTime> alternatives, String errorCode) {
            this.available = available;
            this.alternatives = alternatives;
            this.errorCode = errorCode;
        }
    }

    /**
     * Checks whether the given date/time is available for the requested party size.
     *
     * Uses the same table-allocation logic as booking (including ±2 hours blocking rule),
     * but does not insert anything into the DB.
     */
    public AvailabilityResult checkAvailability(LocalDate date, LocalTime time, int diners) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            int maxCap = getMaxTableCapacity(conn);
            if (maxCap <= 0) {
                return new AvailabilityResult(false, List.of(), "NO_TABLES_DEFINED");
            }
            if (diners > maxCap) {
                return new AvailabilityResult(false, List.of(), "CAPACITY_TOO_LARGE|" + diners + "|" + maxCap);
            }

            int table = findTableForBooking(conn, date, time, diners);
            if (table != -1) {
                return new AvailabilityResult(true, List.of(), null);
            }

            List<LocalDateTime> alts = findAlternativeSlots(date, time, diners, 4);
            return new AvailabilityResult(false, alts, null);
        }
    }

    /**
     * Finds up to limit alternative slots (nearest first).
     *
     * Strategy:
     * - Same day within ±3 hours, step 15 minutes (prefer later first).
     * - If still not enough, scan next 3 days from opening time in 30-minute steps.
     */
    private List<LocalDateTime> findAlternativeSlots(LocalDate date, LocalTime requested, int diners, int limit) throws SQLException {
        List<LocalDateTime> out = new ArrayList<>();

        // Opening hours (fallback 08:00-23:00)
        LocalTime open = LocalTime.of(8, 0);
        LocalTime close = LocalTime.of(23, 0);
        try {
            Map<String, String> hours = getOpeningHoursForDate(date);
            if (hours != null) {
                if (hours.get("open") != null) open = LocalTime.parse(hours.get("open"));
                if (hours.get("close") != null) close = LocalTime.parse(hours.get("close"));
            }
        } catch (Exception ignore) {}

        int step = 15;
        int maxDist = 180;

        try (Connection conn = dataSource.getConnection()) {
            // Same day: ±3h
            for (int dist = step; dist <= maxDist && out.size() < limit; dist += step) {
                for (int sign : new int[]{+1, -1}) {
                    LocalTime cand = requested.plusMinutes(sign * dist);

                    if (cand.isBefore(open) || cand.plusMinutes(RES_DURATION_MINUTES()).isAfter(close)) {
                        continue;
                    }

                    if (findTableForBooking(conn, date, cand, diners) != -1) {
                        out.add(LocalDateTime.of(date, cand));
                        if (out.size() >= limit) break;
                    }
                }
            }

            // Next 3 days: 30-min steps from opening
            for (int day = 1; day <= 3 && out.size() < limit; day++) {
                LocalDate d = date.plusDays(day);

                LocalTime dOpen = open;
                LocalTime dClose = close;
                try {
                    Map<String, String> hours = getOpeningHoursForDate(d);
                    if (hours != null) {
                        if (hours.get("open") != null) dOpen = LocalTime.parse(hours.get("open"));
                        if (hours.get("close") != null) dClose = LocalTime.parse(hours.get("close"));
                    }
                } catch (Exception ignore) {}

                for (LocalTime t = dOpen;
                     !t.plusMinutes(RES_DURATION_MINUTES()).isAfter(dClose) && out.size() < limit;
                     t = t.plusMinutes(30)) {

                    if (findTableForBooking(conn, d, t, diners) != -1) {
                        out.add(LocalDateTime.of(d, t));
                    }
                }
            }
        }

        return out;
    }

    // Keep duration consistent (2 hours)
    private int RES_DURATION_MINUTES() {
        return 120;
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
        String insertSql =
            "INSERT INTO ActiveReservations " +
            "(Role, CasualPhone, CasualEmail, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status, TableNumber) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 'Confirmed', ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                LocalDate d = reservation.getReservationDate().toLocalDate();
                LocalTime t = reservation.getReservationTime().toLocalTime();
                int diners = reservation.getNumberOfGuests();
                int maxCap = getMaxTableCapacity(conn);
                if (diners > maxCap) {
                    conn.rollback();
                    throw new SQLException("CAPACITY_TOO_LARGE|" + diners + "|" + maxCap);
                }

                int tableNum = findTableForBooking(conn, d, t, diners);
                if (tableNum == -1) {
                    conn.rollback();
                    System.out.println("DEBUG: tableNum == -1, throwing SQLException");
                    throw new SQLException("No available table for " + diners +
                            " diners at " + d + " " + t + ". (Capacity too small or slot blocked ±2h)");
                }



                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, "Casual");

                    if (phone == null || phone.isBlank()) ps.setNull(2, java.sql.Types.VARCHAR);
                    else ps.setString(2, phone);

                    if (email == null || email.isBlank()) ps.setNull(3, java.sql.Types.VARCHAR);
                    else ps.setString(3, email);

                    ps.setDate(4, reservation.getReservationDate());
                    ps.setTime(5, reservation.getReservationTime());
                    ps.setInt(6, reservation.getNumberOfGuests());
                    ps.setString(7, reservation.getConfirmationCode());
                    ps.setInt(8, tableNum);

                    if (ps.executeUpdate() != 1) {
                        conn.rollback();
                        return -1;
                    }
                 

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        int id = (rs.next() ? rs.getInt(1) : -1);
                        if (id == -1) {
                            conn.rollback();
                            return -1;
                        }
                        conn.commit();
                        return id;
                    }

                    
                }
            } catch (SQLException ex) {
                conn.rollback();

                throw ex;
            } finally {
                conn.setAutoCommit(true);
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

            // Allocate table now (±2 hours) and insert as subscriber reservation
            String insertSql =
                    "INSERT INTO ActiveReservations " +
                    "(Role, SubscriberID, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status, TableNumber) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'Confirmed', ?)";

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    LocalDate d = reservation.getReservationDate().toLocalDate();
                    LocalTime t = reservation.getReservationTime().toLocalTime();
                    int diners = reservation.getNumberOfGuests();

                    // 0) Validate there are tables + capacity exists
                    int maxCap = getMaxTableCapacity(conn);
                    if (maxCap <= 0) {
                        conn.rollback();
                        throw new SQLException("NO_TABLES_DEFINED");
                    }
                    if (diners > maxCap) {
                        conn.rollback();
                        throw new SQLException("CAPACITY_TOO_LARGE|" + diners + "|" + maxCap);
                    }

                    // 1) Find an available table
                    int tableNum = findTableForBooking(conn, d, t, diners);
                    if (tableNum == -1) {
                        conn.rollback();
                        throw new SQLException(
                                "No available table for " + diners +
                                " diners at " + d + " " + t + ". (Capacity too small or slot blocked ±2h)"
                        );
                    }

                    // 2) Insert reservation
                    try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, "Subscriber"); // DB Role is reservation type only
                        ps.setInt(2, subscriberId);
                        ps.setDate(3, reservation.getReservationDate());
                        ps.setTime(4, reservation.getReservationTime());
                        ps.setInt(5, reservation.getNumberOfGuests());
                        ps.setString(6, reservation.getConfirmationCode());
                        ps.setInt(7, tableNum);

                        if (ps.executeUpdate() != 1) {
                            conn.rollback();
                            return -1;
                        }

                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            int id = (rs.next() ? rs.getInt(1) : -1);
                            if (id == -1) {
                                conn.rollback();
                                return -1;
                            }
                            conn.commit();
                            return id;
                        }
                    }

                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            }

        } else {
            // Insert as casual reservation (we will update this next)
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
    public boolean cancelReservationByConfirmationCode(String code) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer tableNum = null;

                // 1) get table number (lock)
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT TableNumber FROM ActiveReservations WHERE ConfirmationCode=? FOR UPDATE")) {
                    ps.setString(1, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) tableNum = rs.getObject("TableNumber", Integer.class);
                    }
                }

                // 2) cancel reservation
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE ActiveReservations " +
                        "SET Status='Canceled', TableNumber=NULL " +
                        "WHERE ConfirmationCode=? AND Status IN ('Confirmed','Late','Arrived')")) {
                    ps.setString(1, code);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                // 3) free table if we had one
                if (tableNum != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE RestaurantTables SET Status='Available' WHERE TableNumber=?")) {
                        ps.setInt(1, tableNum);
                        ps.executeUpdate();
                    }
                }

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
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

    public boolean assignSpecificTable(String code, int tableNum) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
        	conn.setAutoCommit(false);
            try {
            	System.out.println("DEBUG assignSpecificTable code=" + code + " table=" + tableNum);
            	// 1) reservation diners + status (TODAY ONLY) + lock row
                String q1 =
                    "SELECT NumOfDiners, Status, TableNumber " +
                    "FROM ActiveReservations " +
                    "WHERE ConfirmationCode=? AND ReservationDate=CURDATE() " +
                    "FOR UPDATE";

                int diners;
                String status;
                Integer existingTable;

                try (PreparedStatement ps = conn.prepareStatement(q1)) {
                    ps.setString(1, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        diners = rs.getInt("NumOfDiners");
                        status = rs.getString("Status");
                        System.out.println("DEBUG reservation status=" + status + " diners=" + diners);
                        existingTable = (Integer) rs.getObject("TableNumber");
                    }
                }

                // already assigned? don't overwrite
             // already assigned? don't overwrite, BUT keep RestaurantTables synced
                if (existingTable != null) {
                    if ("Arrived".equalsIgnoreCase(status)) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE RestaurantTables SET Status='Taken' WHERE TableNumber=?")) {
                            ps.setInt(1, existingTable);
                            ps.executeUpdate(); 
                        }
                        conn.commit();
                        return true;
                    }

                    conn.rollback();
                    return false;
                }


                // allow only Confirmed/Late
                if (!"Confirmed".equalsIgnoreCase(status) && !"Late".equalsIgnoreCase(status)) {
                    conn.rollback();
                    return false;
                }

                // 2) table capacity (lock row)
                String q2 =
                    "SELECT Capacity, Status " +
                    "FROM RestaurantTables " +
                    "WHERE TableNumber=? " +
                    "FOR UPDATE";

                int cap;
                String tableStatus;

                try (PreparedStatement ps = conn.prepareStatement(q2)) {
                    ps.setInt(1, tableNum);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        cap = rs.getInt("Capacity");
                        System.out.println("DEBUG table cap=" + cap);
                        tableStatus = rs.getString("Status");
                    }
                }

                if (diners > cap) { conn.rollback(); return false; }

                // optional: if you maintain RestaurantTables.Status
//                if ("Taken".equalsIgnoreCase(tableStatus)) { conn.rollback(); return false; }

                // 3) check if table already taken today
                String q3 =
                	    "SELECT 1 FROM ActiveReservations " +
                	    "WHERE TableNumber=? AND ReservationDate=CURDATE() " +
                	    "AND Status IN ('Arrived') " +
                	    "LIMIT 1";
                boolean existingTable1 = false;

                try (PreparedStatement ps = conn.prepareStatement(q3)) {
                    ps.setInt(1, tableNum);
                    try (ResultSet rs = ps.executeQuery()) {
                        existingTable1 = rs.next();
                    }
                }

                System.out.println("DEBUG existingTable=" + existingTable1);

                if (existingTable1) { conn.rollback(); return false; }
//                try (PreparedStatement ps = conn.prepareStatement(q3)) {
//                    ps.setInt(1, tableNum);
//                    try (ResultSet rs = ps.executeQuery()) {
//                        if (rs.next()) { conn.rollback(); return false; }
//                    }
//                }

                // 4) update reservation (TODAY ONLY, no overwrite)
                String upd =
                    "UPDATE ActiveReservations " +
                    "SET Status='Arrived', TableNumber=? " +
                    "WHERE ConfirmationCode=? " +
                    "  AND ReservationDate=CURDATE() " +
                    "  AND Status IN ('Confirmed','Late') " +
                    "  AND TableNumber IS NULL";

                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    ps.setInt(1, tableNum);
                    ps.setString(2, code);
                    boolean ok = ps.executeUpdate() > 0;
                    if (!ok) { conn.rollback(); return false; }
                }

             // keep RestaurantTables.Status in sync
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE RestaurantTables SET Status='Taken' WHERE TableNumber=?")) {
                    ps.setInt(1, tableNum);
                    if (ps.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }


                conn.commit();
                return true;

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
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

            try {
                // 1) Load reservation by confirmation code (and lock it)
                try (PreparedStatement psRes =
                             conn.prepareStatement(SQLQueries.GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE)) {
                    psRes.setString(1, confirmationCode);

                    try (ResultSet rs = psRes.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return -1;
                        }

                        int diners = rs.getInt("NumOfDiners");
                        String status = rs.getString("Status");
                        Date dbDate = rs.getDate("ReservationDate");
                        Integer existingTable = rs.getObject("TableNumber", Integer.class);

                        boolean isConfirmed = "Confirmed".equalsIgnoreCase(status);
                        boolean isToday = (dbDate != null && dbDate.toLocalDate().equals(LocalDate.now()));

                        if (diners <= 0 || !isConfirmed || !isToday) {
                            conn.rollback();
                            return -1;
                        }

                        // 2) Table must already be assigned at BOOK time
                        if (existingTable == null) {
                            conn.rollback();
                            return -1;
                        }

                        assignedTable = existingTable;
                    }
                }

                // 3) Update reservation: mark arrived (do NOT change table number, just confirm it)
                try (PreparedStatement psUpdateRes =
                             conn.prepareStatement(SQLQueries.SET_RESERVATION_ARRIVED_AND_TABLE)) {
                    psUpdateRes.setInt(1, assignedTable);
                    psUpdateRes.setString(2, confirmationCode);

                    if (psUpdateRes.executeUpdate() == 0) {
                        conn.rollback();
                        return -1;
                    }
                }

                // 4) Update RestaurantTables status to Taken
                try (PreparedStatement psUpdTable =
                             conn.prepareStatement("UPDATE RestaurantTables SET Status='Taken' WHERE TableNumber=?")) {
                    psUpdTable.setInt(1, assignedTable);

                    if (psUpdTable.executeUpdate() == 0) {
                        conn.rollback();
                        return -1;
                    }
                }

                conn.commit();
                return assignedTable;

            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    e.addSuppressed(ex); // Suppress rollback exception to show original error
                }
                e.printStackTrace(); // Log the exception
                return -1;
            }
    
        } catch (SQLException e) {
            e.printStackTrace(); // Log connection acquisition exception
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
     * Uses {@link SQLQueriesGET_SUBSCRIBER_ACTIVE_RESERVATIONS} and maps rows to
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
    /** Deletes a table by table number.
     *  IMPORTANT: If there are active reservations assigned to this table,
     *  we cancel them before deleting the table (otherwise FK ON DELETE SET NULL will only null TableNumber).
     */
    public boolean deleteRestaurantTable(int tableNum) {
        String cancelSql =
            "UPDATE ActiveReservations " +
            "SET Status='Canceled', TableNumber=NULL " +
            "WHERE TableNumber = ? " +
            "  AND Status IN ('Confirmed','Late','Arrived','CheckedIn')";

        String deleteSql = "DELETE FROM restauranttables WHERE TableNumber = ?";

        try (Connection conn = dataSource.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // 1) cancel all active reservations that currently use this table
                try (PreparedStatement psCancel = conn.prepareStatement(cancelSql)) {
                    psCancel.setInt(1, tableNum);
                    psCancel.executeUpdate();
                }

                // 2) delete the table itself
                int deletedRows;
                try (PreparedStatement psDelete = conn.prepareStatement(deleteSql)) {
                    psDelete.setInt(1, tableNum);
                    deletedRows = psDelete.executeUpdate();
                }

                conn.commit();
                return deletedRows > 0;

            } catch (Exception e) {
                try { conn.rollback(); } catch (Exception ignored) {}
                e.printStackTrace();
                return false;
            } finally {
                try { conn.setAutoCommit(oldAutoCommit); } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Retrieves the restaurant's regular weekly opening hours from the database.
     * <p>
     * Queries the {@code openinghours} table for rows that represent regular weekly hours
     * (i.e., {@code SpecialDate IS NULL} and {@code DayOfWeek IS NOT NULL}) and orders the
     * results from Sunday to Saturday.
     * <p>
     * The returned string is formatted as:
     * <pre>
     * DayOfWeek,OpenTime,CloseTime~DayOfWeek,OpenTime,CloseTime~...
     * </pre>
     * where times are returned in {@code HH:mm:ss} format. If no rows are found, returns {@code "EMPTY"}.
     * If an exception occurs, returns {@code "ERROR"}.
     *
     * @return a formatted string containing weekly opening hours, {@code "EMPTY"} if none exist,
     *         or {@code "ERROR"} on failure
     */
    public String getOpeningHoursWeekly() {
        StringBuilder sb = new StringBuilder();
        String query = "SELECT DayOfWeek, OpenTime, CloseTime " +
                       "FROM openinghours " +
                       "WHERE SpecialDate IS NULL AND DayOfWeek IS NOT NULL " +
                       "ORDER BY FIELD(DayOfWeek,'Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday')";

        try (Connection con = mysqlConnection1.getDataSource().getConnection();
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            boolean first = true;
            while (rs.next()) {
                String day = rs.getString("DayOfWeek");
                String open = rs.getTime("OpenTime").toString();   // HH:mm:ss
                String close = rs.getTime("CloseTime").toString(); // HH:mm:ss

                if (!first) sb.append("~");
                first = false;
                sb.append(day).append(",").append(open).append(",").append(close);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }

        return sb.length() == 0 ? "EMPTY" : sb.toString();
    }

    /**
     * Updates the regular (weekly) opening hours for a specific day of the week.
     * <p>
     * This method updates rows in the {@code openinghours} table where {@code SpecialDate IS NULL}
     * (i.e., regular weekly hours) and {@code DayOfWeek} matches the provided value.
     * <p>
     * The {@code open} and {@code close} parameters are expected in {@code HH:mm} format and are
     * converted to {@code HH:mm:ss} by appending {@code ":00"} before being written to the database.
     * <p>
     * Return values:
     * <ul>
     *   <li>{@code "OK"} - at least one row was updated</li>
     *   <li>{@code "NOT_FOUND"} - no matching row was found to update</li>
     *   <li>{@code "ERROR"} - an exception occurred during the update</li>
     * </ul>
     *
     * @param dayOfWeek the day of the week to update (e.g., "Sunday", "Monday")
     * @param open      the new opening time in {@code HH:mm} format
     * @param close     the new closing time in {@code HH:mm} format
     * @return {@code "OK"} if updated, {@code "NOT_FOUND"} if no row matched, or {@code "ERROR"} on failure
     */
    public String updateBranchHoursByDay(String dayOfWeek, String open, String close) {
        String query = "UPDATE openinghours SET OpenTime = ?, CloseTime = ? " +
                       "WHERE SpecialDate IS NULL AND DayOfWeek = ?";

        try (Connection con = mysqlConnection1.getDataSource().getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, open + ":00");   // HH:mm -> HH:mm:ss
            ps.setString(2, close + ":00");
            ps.setString(3, dayOfWeek);

            int updated = ps.executeUpdate();
            return updated > 0 ? "OK" : "NOT_FOUND";

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
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
    public boolean isSlotAvailable(LocalDate date, LocalTime time, int diners) throws SQLException {
        return countAvailableTables(date, time, diners) > 0;
    }

    public int countAvailableTables(LocalDate date, LocalTime time, int diners) throws SQLException {
        String sql =
            "SELECT COUNT(*) AS cnt " +
            "FROM RestaurantTables rt " +
            "WHERE rt.Capacity >= ? " +
            "  AND NOT EXISTS ( " +
            "      SELECT 1 FROM ActiveReservations ar " +
            "      WHERE ar.TableNumber = rt.TableNumber " +
            "        AND ar.ReservationDate = ? " +
            "        AND ar.Status IN ('Confirmed','Arrived','Late') " +
            "        AND ABS(TIMESTAMPDIFF(MINUTE, " +
            "            TIMESTAMP(ar.ReservationDate, ar.ReservationTime), " +
            "            TIMESTAMP(?, ?) " +
            "        )) <= ? " +
            "  )";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

        	ps.setInt(1, diners);                 // rt.Capacity >= ?
        	ps.setDate(2, Date.valueOf(date));    // ar.ReservationDate = ?
        	ps.setDate(3, Date.valueOf(date));    // TIMESTAMP(?, ?) date
        	ps.setTime(4, Time.valueOf(time));    // TIMESTAMP(?, ?) time
        	ps.setInt(5, TABLE_RADIUS_MINUTES);   // <= ?


            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("cnt");
            }
        }
    }

   

    /**
     * Retrieves the number of arrivals per hour for a given month and year.
     * <p>
     * The returned map contains keys from {@code 0} to {@code 23} (hours of day). All hours are
     * initialized to {@code 0} before applying the results from the database query, so missing
     * hours will still appear in the map with value {@code 0}.
     *
     * @param month the month to query (typically 1-12)
     * @param year  the year to query (e.g., 2026)
     * @return a map where the key is the hour (0-23) and the value is the number of arrivals
     */
    public Map<Integer, Integer> getHourlyArrivals(int month, int year) {
        Map<Integer, Integer> resultMap = new HashMap<>();
        
        for (int h = 0; h < 24; h++) {
            resultMap.put(h, 0);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_HOURLY_ARRIVALS)) {
            
            ps.setInt(1, month);
            ps.setInt(2, year);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int hour = rs.getInt("Hour");
                    int count = rs.getInt("Count");
                    resultMap.put(hour, count);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resultMap;
    }
    
    /**
     * Retrieves daily statistics (counts per day of month) for a given month and year.
     * <p>
     * The SQL query is selected based on {@code queryType}:
     * <ul>
     *   <li>If {@code queryType.equals("SUBSCRIBER")}, uses {@code SQLQueries.GET_SUBSCRIBER_DAILY_STATS}.</li>
     *   <li>Otherwise, uses {@code SQLQueries.GET_WAITING_DAILY_STATS_SUBSCRIBERS_ONLY}.</li>
     * </ul>
     * Results are returned in a {@link TreeMap} to keep the days ordered (1, 2, 3, ...).
     *
     * @param month     the month to query (typically 1-12)
     * @param year      the year to query (e.g., 2026)
     * @param queryType determines which SQL query to run (e.g., "SUBSCRIBER")
     * @return a map where the key is the day of month (1-31) and the value is the count for that day
     */
    public Map<Integer, Integer> getDailyStats(int month, int year, String queryType) {
        Map<Integer, Integer> stats = new TreeMap<>(); 
        
        String sql = queryType.equals("SUBSCRIBER") ? 
                     SQLQueries.GET_SUBSCRIBER_DAILY_STATS : 
                     SQLQueries.GET_WAITING_DAILY_STATS_SUBSCRIBERS_ONLY;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, month);
            ps.setInt(2, year);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getInt("Day"), rs.getInt("Count"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }
    
    /**
     * Retrieves the number of departures per hour for a given month and year.
     * <p>
     * The returned map contains keys from {@code 0} to {@code 23} (hours of day). All hours are
     * initialized to {@code 0} before applying the results from the database query, so missing
     * hours will still appear in the map with value {@code 0}.
     *
     * @param month the month to query (typically 1-12)
     * @param year  the year to query (e.g., 2026)
     * @return a map where the key is the hour (0-23) and the value is the number of departures
     */
    public Map<Integer, Integer> getHourlyDepartures(int month, int year) {
        Map<Integer, Integer> resultMap = new HashMap<>();
        
        for (int h = 0; h < 24; h++) resultMap.put(h, 0);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_HOURLY_DEPARTURES)) {
            
            ps.setInt(1, month);
            ps.setInt(2, year);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultMap.put(rs.getInt("Hour"), rs.getInt("Count"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resultMap;
    }
}
