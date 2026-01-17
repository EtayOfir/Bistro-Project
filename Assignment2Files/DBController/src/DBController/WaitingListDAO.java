package DBController;

import SQLAccess.SQLQueries;
import entities.WaitingEntry;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WaitingListDAO {

    private final DataSource dataSource;

    public WaitingListDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // -------------------------
    // CREATE
    // -------------------------
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
    public List<WaitingEntry> getAll() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ALL_WAITING);
             ResultSet rs = ps.executeQuery()) {

            List<WaitingEntry> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }
    }

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
    public boolean deleteById(int waitingId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQLQueries.DELETE_WAITING_BY_ID)) {

            ps.setInt(1, waitingId);
            return ps.executeUpdate() == 1;
        }
    }

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
    
    public WaitingEntry pickNextForTable(int tableCapacity) throws SQLException {
        String sql =
            "SELECT WaitingID, ContactInfo, NumOfDiners, ConfirmationCode, Status, EntryTime " +
            "FROM WaitingList " +
            "WHERE Status = 'Waiting' AND NumOfDiners <= ? " +
            "ORDER BY EntryTime ASC " +
            "LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, tableCapacity);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                WaitingEntry e = new WaitingEntry();
                e.setWaitingId(rs.getInt("WaitingID"));
                e.setContactInfo(rs.getString("ContactInfo"));
                e.setNumOfDiners(rs.getInt("NumOfDiners"));
                e.setConfirmationCode(rs.getString("ConfirmationCode"));
                e.setStatus(rs.getString("Status"));
                e.setEntryTime(rs.getTimestamp("EntryTime"));
                return e;
            }}
        }
        public void notifyNextWaitingCustomer(int tableNumber, WaitingListDAO waitingListDAO) throws SQLException {
            int capacity = 0;

            try (Connection conn = dataSource.getConnection()) {
                // 1) capacity for this table
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT Capacity FROM restauranttables WHERE TableNumber = ?")) {
                    ps.setInt(1, tableNumber);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) capacity = rs.getInt("Capacity");
                    }
                }

                if (capacity <= 0) return;

                // 2) pick next waiting entry that fits
                WaitingEntry next = waitingListDAO.pickNextForTable(capacity);
                if (next == null) return;

                // 3) mark as notified (or seated)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE WaitingList SET Status='Notified' WHERE WaitingID=?")) {
                    ps.setInt(1, next.getWaitingId());
                    ps.executeUpdate();
                }

                // 4) send mock SMS
                StringBuilder sb = new StringBuilder();
                sb.append("Table is now available!\n");
                sb.append("Table Number: ").append(tableNumber).append("\n");
                sb.append("For ").append(next.getNumOfDiners()).append(" diners\n");
                sb.append("Your waiting confirmation code: ").append(next.getConfirmationCode()).append("\n");
                sb.append("Please come to the host now.\n");

                methods.CommonMethods.sendSMSMock(sb);
            }
        }
    
    

    
    public Integer findAvailableTableNumber(Connection conn, int diners) throws SQLException {
        String sql =
            "SELECT TableNumber " +
            "FROM restauranttables " +
            "WHERE Status='Available' AND Capacity >= ? " +
            "ORDER BY Capacity ASC, TableNumber ASC " +
            "LIMIT 1 FOR UPDATE";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, diners);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("TableNumber") : null;
            }
        }
    }

    public void setTableTaken(Connection conn, int tableNum) throws SQLException {
        String sql = "UPDATE restauranttables SET Status='Taken' WHERE TableNumber=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableNum);
            ps.executeUpdate();
        }
    }

    

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
