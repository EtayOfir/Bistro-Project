package DBController;

import SQLAccess.SQLQueries;
import entities.WaitingEntry;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for managing waiting list entries.
 * <p>
 * Provides CRUD operations for {@link WaitingEntry} records, plus helper mapping logic and
 * additional report/statistics queries. Also includes a transactional method to find an eligible
 * waiting entry for available seats and mark it accordingly.
 */
public class WaitingListDAO {

    private final DataSource dataSource;

    /**
     * Constructs a {@link WaitingListDAO} using the given {@link DataSource}.
     *
     * @param dataSource the data source to use for database connections
     */
    public WaitingListDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Constructs a {@link WaitingListDAO} using the given {@link DataSource}.
     *
     * @param dataSource the data source to use for database connections
     */
    public boolean insert(String contactInfo,int numOfDiners, String confirmationCode, String status) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.INSERT_WAITING)) {

            ps.setString(1, contactInfo);
            ps.setInt(2, numOfDiners);
            ps.setString(3, confirmationCode);
            ps.setString(4, status);
            return ps.executeUpdate() == 1;
        }
        
    }


    /**
     * Retrieves a waiting list entry by its waiting ID.
     * <p>
     * Uses {@code SQLQueries.GET_WAITING_BY_ID}. If no record is found, returns {@code null}.
     *
     * @param waitingId the waiting entry ID
     * @return the matching {@link WaitingEntry}, or {@code null} if not found
     * @throws SQLException if a database error occurs while querying
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
     * Retrieves a waiting list entry by its confirmation code.
     * <p>
     * Uses {@code SQLQueries.GET_WAITING_BY_CODE}. If no record is found, returns {@code null}.
     *
     * @param confirmationCode the confirmation code to search by
     * @return the matching {@link WaitingEntry}, or {@code null} if not found
     * @throws SQLException if a database error occurs while querying
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

    /**
     * Retrieves all waiting list entries.
     * <p>
     * Uses {@code SQLQueries.GET_ALL_WAITING} and maps each row into {@link WaitingEntry}.
     *
     * @return a list of all waiting entries (may be empty)
     * @throws SQLException if a database error occurs while querying
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
     * Retrieves all active waiting list entries.
     * <p>
     * Uses {@code SQLQueries.GET_ACTIVE_WAITING}. "Active" is determined by the SQL query
     * (typically statuses like "Waiting", excluding "Paid"/"Cancelled"/etc.).
     *
     * @return a list of active waiting entries (may be empty)
     * @throws SQLException if a database error occurs while querying
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

    /**
     * Updates a waiting entry by its waiting ID.
     * <p>
     * Uses {@code SQLQueries.UPDATE_WAITING_BY_ID} to update contact info, number of diners, and status.
     *
     * @param waitingId   the waiting entry ID to update
     * @param contactInfo the new contact info value
     * @param numOfDiners the new number of diners
     * @param status      the new status value
     * @return {@code true} if exactly one row was updated; {@code false} otherwise
     * @throws SQLException if a database error occurs during update
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
     * Updates the status of a waiting entry by its confirmation code.
     * <p>
     * Uses {@code SQLQueries.UPDATE_WAITING_STATUS_BY_CODE}.
     *
     * @param confirmationCode the confirmation code identifying the waiting entry
     * @param status           the new status value
     * @return {@code true} if exactly one row was updated; {@code false} otherwise
     * @throws SQLException if a database error occurs during update
     */
    public boolean updateStatusByCode(String confirmationCode, String status) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.UPDATE_WAITING_STATUS_BY_CODE)) {

            ps.setString(1, status);
            ps.setString(2, confirmationCode);

            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Deletes a waiting entry by its waiting ID (hard delete).
     * <p>
     * Uses {@code SQLQueries.DELETE_WAITING_BY_ID}.
     *
     * @param waitingId the waiting entry ID to delete
     * @return {@code true} if exactly one row was deleted; {@code false} otherwise
     * @throws SQLException if a database error occurs during delete
     */
    public boolean deleteById(int waitingId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.DELETE_WAITING_BY_ID)) {

            ps.setInt(1, waitingId);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Deletes a waiting entry by its confirmation code (hard delete).
     * <p>
     * Uses {@code SQLQueries.DELETE_WAITING_BY_CODE}.
     *
     * @param confirmationCode the confirmation code identifying the waiting entry
     * @return {@code true} if exactly one row was deleted; {@code false} otherwise
     * @throws SQLException if a database error occurs during delete
     */
    public boolean deleteByConfirmationCode(String confirmationCode) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.DELETE_WAITING_BY_CODE)) {

            ps.setString(1, confirmationCode);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Maps a single {@link ResultSet} row into a {@link WaitingEntry} object.
     * <p>
     * Attempts to read {@code SubscriberID} if present in the schema; if the column does not exist
     * (older schema) or the value is {@code null}, the subscriber ID is left {@code null}.
     *
     * @param rs the result set positioned on a valid row
     * @return a mapped {@link WaitingEntry} instance
     * @throws SQLException if a database access error occurs while reading row fields
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
     * Gets waiting list statistics grouped by date for reports.
     * @param startDate the start date (SQL Date)
     * @param endDate the end date (SQL Date)
     * @return a list of maps containing date, waiting count, and served count
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
     * Finds the earliest eligible waiting entry that can fit into the given number of available seats,
     * updates its status to {@code "TableFound"}, and returns it.
     * <p>
     * This method runs inside a transaction and uses row-level locking:
     * <ul>
     *   <li>Selects the first entry with {@code Status='Waiting'} and {@code NumOfDiners <= availableSeats}</li>
     *   <li>Orders by {@code EntryTime ASC} (FIFO behavior)</li>
     *   <li>Locks the selected row using {@code FOR UPDATE}</li>
     *   <li>Updates that entry's status to {@code "TableFound"}</li>
     * </ul>
     * If no eligible entry is found, the method returns {@code null}.
     * If the update fails, the transaction is rolled back and {@code null} is returned.
     *
     * @param availableSeats the number of seats currently available
     * @return the eligible {@link WaitingEntry} whose status was updated, or {@code null} if none found/updated
     * @throws SQLException if a database error occurs during the transaction
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
