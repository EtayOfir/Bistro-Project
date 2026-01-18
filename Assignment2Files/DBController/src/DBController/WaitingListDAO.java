package DBController;

import SQLAccess.SQLQueries;
import entities.WaitingEntry;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for managing the restaurant's Waiting List.
 * <p>
 * This class handles all database operations related to customers waiting for a table, including:
 * <ul>
 * <li>Adding new customers to the queue.</li>
 * <li>Retrieving waiting entries by various criteria (ID, Code, Status).</li>
 * <li>Updating status (e.g., notifying a customer that a table is ready).</li>
 * <li>Generating statistical reports for wait times.</li>
 * </ul>
 * </p>
 */
public class WaitingListDAO {

    private final DataSource dataSource;

    /**
     * Constructs a new WaitingListDAO.
     *
     * @param dataSource The {@link DataSource} used for database connections.
     */
    public WaitingListDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // -------------------------
    // CREATE
    // -------------------------
    /**
     * Inserts a new entry into the waiting list.
     *
     * @param contactInfo      The customer's contact details (phone/email).
     * @param numOfDiners      The number of guests.
     * @param confirmationCode A unique code generated for this request.
     * @param status           The initial status (usually "Waiting").
     * @return {@code true} if the insertion was successful, {@code false} otherwise.
     * @throws SQLException If a database access error occurs.
     */
    public boolean insert(String contactInfo,int numOfDiners, String confirmationCode, String status) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.INSERT_WAITING)) {

            ps.setString(1, contactInfo);
            ps.setInt(2, numOfDiners);
            ps.setString(3, confirmationCode);
            ps.setString(4, status);

               // ps.setNull(2, Types.INTEGER);
            //} else {
             //   ps.setInt(2, subscriberId);
           // }
            //ps.setInt(3, numOfDiners);
            //ps.setString(4, confirmationCode);
            //ps.setString(5, status);

            return ps.executeUpdate() == 1;
        }
        
    }

    // -------------------------
    // READ (single)
    // -------------------------
    /**
     * Retrieves a waiting entry by its unique database ID.
     *
     * @param waitingId The primary key of the waiting entry.
     * @return A {@link WaitingEntry} object, or {@code null} if not found.
     * @throws SQLException If a database error occurs.
     */
    public WaitingEntry getById(int waitingId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_WAITING_BY_ID)) {

            ps.setInt(1, waitingId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /**
     * Retrieves a waiting entry by its confirmation code.
     *
     * @param confirmationCode The unique code assigned to the customer.
     * @return A {@link WaitingEntry} object, or {@code null} if not found.
     * @throws SQLException If a database error occurs.
     */
    public WaitingEntry getByConfirmationCode(String confirmationCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_WAITING_BY_CODE)) {

            ps.setString(1, confirmationCode);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    // -------------------------
    // READ (list)  "SHOW"
    // -------------------------
    /**
     * Retrieves all entries in the waiting list history.
     *
     * @return A list of all {@link WaitingEntry} records.
     * @throws SQLException If a database error occurs.
     */
    public List<WaitingEntry> getAll() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ALL_WAITING);
             ResultSet rs = ps.executeQuery()) {

            List<WaitingEntry> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }
    }

    /**
     * Retrieves only the currently active waiting entries (e.g., status is "Waiting").
     *
     * @return A list of active {@link WaitingEntry} records.
     * @throws SQLException If a database error occurs.
     */
    public List<WaitingEntry> getActiveWaiting() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ACTIVE_WAITING);
             ResultSet rs = ps.executeQuery()) {

            List<WaitingEntry> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }
    }

    // -------------------------
    // UPDATE
    // -------------------------
    /**
     * Updates the details of a specific waiting entry.
     *
     * @param waitingId   The ID of the entry to update.
     * @param contactInfo The new contact info.
     * @param numOfDiners The new number of diners.
     * @param status      The new status.
     * @return {@code true} if the update was successful.
     * @throws SQLException If a database error occurs.
     */
    public boolean updateById(int waitingId, String contactInfo, int numOfDiners, String status) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.UPDATE_WAITING_BY_ID)) {

            ps.setString(1, contactInfo);
            ps.setInt(2, numOfDiners);
            ps.setString(3, status);
            ps.setInt(4, waitingId);

            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Updates the status of an entry using its confirmation code.
     *
     * @param confirmationCode The code identifying the entry.
     * @param status           The new status to set.
     * @return {@code true} if the update was successful.
     * @throws SQLException If a database error occurs.
     */
    public boolean updateStatusByCode(String confirmationCode, String status) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.UPDATE_WAITING_STATUS_BY_CODE)) {

            ps.setString(1, status);
            ps.setString(2, confirmationCode);

            return ps.executeUpdate() == 1;
        }
    }

    // -------------------------
    // DELETE (hard delete)
    // -------------------------
    /**
     * Permanently deletes a waiting entry by ID.
     *
     * @param waitingId The ID of the entry to delete.
     * @return {@code true} if the deletion was successful.
     * @throws SQLException If a database error occurs.
     */
    public boolean deleteById(int waitingId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.DELETE_WAITING_BY_ID)) {

            ps.setInt(1, waitingId);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Permanently deletes a waiting entry by confirmation code.
     *
     * @param confirmationCode The code of the entry to delete.
     * @return {@code true} if the deletion was successful.
     * @throws SQLException If a database error occurs.
     */
    public boolean deleteByConfirmationCode(String confirmationCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.DELETE_WAITING_BY_CODE)) {

            ps.setString(1, confirmationCode);
            return ps.executeUpdate() == 1;
        }
    }

    // -------------------------
    // Helper: map ResultSet row -> WaitingEntry
    // -------------------------
    /**
     * Maps a single row from a ResultSet to a WaitingEntry domain object.
     *
     * @param rs The ResultSet positioned at the current row.
     * @return A populated WaitingEntry object.
     * @throws SQLException If a column mapping error occurs.
     */
    private WaitingEntry mapRow(ResultSet rs) throws SQLException {
        Integer subscriberId = null;
        try {
            Object o = rs.getObject("SubscriberID");
            if (o != null) subscriberId = ((Number) o).intValue();
        } catch (SQLException ignored) {
            // Column may not exist in older schema; leave null
        }

        return new WaitingEntry(
            rs.getInt("WaitingID"),
            rs.getString("ContactInfo"),
            subscriberId,
            rs.getInt("NumOfDiners"),
            rs.getString("ConfirmationCode"),
            rs.getString("Status"),
            rs.getTimestamp("EntryTime")
        );
    }

    // -------------------------
    // Reports: Get waiting list statistics by date
    // -------------------------
    /**
     * Generates a statistical report of the waiting list grouped by date.
     *
     * @param startDate The beginning of the reporting period.
     * @param endDate   The end of the reporting period.
     * @return A list of maps, where each map contains: "EntryDate", "WaitingCount", and "ServedCount".
     * @throws SQLException If a database error occurs.
     */
    public List<java.util.Map<String, Object>> getWaitingListByDateRange(Date startDate, Date endDate) throws SQLException {
        List<java.util.Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_WAITING_LIST_BY_DATE)) {
            
            // Set date parameters (SQL will handle the time portion)
            ps.setDate(1, startDate);
            ps.setDate(2, endDate);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    Date entryDate = rs.getDate("EntryDate");
                    row.put("EntryDate", entryDate != null ? entryDate.toString() : "");
                    row.put("WaitingCount", rs.getInt("WaitingCount"));
                    row.put("ServedCount", rs.getInt("ServedCount"));
                    result.add(row);
                }
            }
        }

        return result;
    }

    /**
     * Atomically finds the earliest waiting customer who fits the available seats and marks them as notified.
     * 
     * <p>
     * This method utilizes a <b>Pessimistic Locking</b> strategy ({@code SELECT ... FOR UPDATE}) within a transaction.
     * This ensures that if multiple tables become available simultaneously, two threads cannot grab the same 
     * waiting customer at the same time.
     * </p>
     * * @param availableSeats The capacity of the table that just became free.
     * @return The {@link WaitingEntry} of the customer who was selected and updated, or {@code null} if no match found.
     * @throws SQLException If the transaction fails.
     */
    public WaitingEntry findAndNotifyEligibleWaitingEntry(int availableSeats) throws SQLException {
        String findQuery = "SELECT * FROM WaitingList WHERE Status = 'Waiting' AND NumOfDiners <= ? ORDER BY EntryTime ASC LIMIT 1 FOR UPDATE";
        String updateQuery = "UPDATE WaitingList SET Status = 'TableFound' WHERE WaitingID = ?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // Start transaction

            try {
                WaitingEntry entry = null;
                try (PreparedStatement findPs = conn.prepareStatement(findQuery)) {
                    findPs.setInt(1, availableSeats);

                    try (ResultSet rs = findPs.executeQuery()) {
                        if (rs.next()) {
                            entry = mapRow(rs);
                        }
                    }
                }

                if (entry != null) {
                    try (PreparedStatement updatePs = conn.prepareStatement(updateQuery)) {
                        updatePs.setInt(1, entry.getWaitingId());
                        int updatedRows = updatePs.executeUpdate();
                        if (updatedRows > 0) {
                            conn.commit();
                            return entry;
                        } else {
                            conn.rollback();
                            return null;
                        }
                    }
                } else {
                    conn.rollback(); // No entry found
                    return null;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    e.printStackTrace(); // Log error on resetting auto-commit
                }
            }
        }
    }
}
