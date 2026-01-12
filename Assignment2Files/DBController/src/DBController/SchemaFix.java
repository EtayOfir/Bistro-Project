package DBController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database schema fix utility.
 * Run this once to update the CustomerType column size.
 */
public class SchemaFix {
    
    public static void main(String[] args) {
        System.out.println("Starting database schema fix...");
        
        try {
            fixCustomerTypeColumn();
            System.out.println("✓ Schema fix completed successfully!");
        } catch (Exception e) {
            System.err.println("✗ Schema fix failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Updates the CustomerType column to support longer values.
     */
    public static void fixCustomerTypeColumn() throws SQLException {
        DataSource dataSource = mysqlConnection1.getDataSource();
        
        if (dataSource == null) {
            throw new SQLException("Data source is not initialized");
        }
        
        String sql = "ALTER TABLE ActiveReservations " +
                    "MODIFY COLUMN CustomerType VARCHAR(20) NOT NULL DEFAULT 'Casual'";
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            System.out.println("Executing: " + sql);
            stmt.executeUpdate(sql);
            System.out.println("✓ CustomerType column updated to VARCHAR(20)");
            
            // Verify the change
            String verifySql = "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH " +
                             "FROM INFORMATION_SCHEMA.COLUMNS " +
                             "WHERE TABLE_NAME = 'ActiveReservations' AND COLUMN_NAME = 'CustomerType'";
            
            var rs = stmt.executeQuery(verifySql);
            if (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String dataType = rs.getString("DATA_TYPE");
                int maxLength = rs.getInt("CHARACTER_MAXIMUM_LENGTH");
                
                System.out.println("Verification:");
                System.out.println("  Column: " + columnName);
                System.out.println("  Type: " + dataType);
                System.out.println("  Max Length: " + maxLength);
                
                if (maxLength >= 20) {
                    System.out.println("✓ Column size is correct!");
                } else {
                    System.out.println("✗ Warning: Column size is still " + maxLength);
                }
            }
        }
    }
}
