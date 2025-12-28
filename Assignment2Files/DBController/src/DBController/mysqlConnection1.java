package DBController;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Manages the database connection pool for the Bistro system using HikariCP.
 * <p>
 * This class is responsible for initializing and maintaining a single
 * {@link HikariDataSource} instance that serves as a connection pool.
 * The pool is created once when the server starts and is shared across
 * all DAO classes.
 * <p>
 * Database connections are obtained on demand from the pool and are
 * automatically returned to the pool when closed, ensuring efficient,
 * safe, and scalable database access without opening a new connection
 * for each request.
 * <p>
 * This class follows the utility-class design pattern and cannot be instantiated.
 */
public final class mysqlConnection1 {

    /** The shared HikariCP data source (connection pool) */
    private static final HikariDataSource dataSource;

    /*
     * Static initialization block that configures and initializes
     * the HikariCP connection pool.
     */
    static {
        HikariConfig config = new HikariConfig();

        // JDBC connection configuration
        config.setJdbcUrl(
                "jdbc:mysql://localhost:3306/bistro" +
                "?allowLoadLocalInfile=true&serverTimezone=Asia/Jerusalem&useSSL=false"
        );
        config.setUsername("root");
        config.setPassword("3tango");
        //config.setPassword("Rootroot");

        // Explicitly enable public key retrieval for MySQL's caching_sha2_password
        config.addDataSourceProperty("allowPublicKeyRetrieval", "true");

        // Pool tuning parameters
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000); // 10 seconds
        config.setIdleTimeout(300_000);      // 5 minutes
        config.setMaxLifetime(1_800_000);    // 30 minutes

        dataSource = new HikariDataSource(config);
        System.out.println("HikariCP pool initialized ✓");
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private mysqlConnection1() {}

    /**
     * Returns the shared {@link DataSource} used to obtain database connections.
     * <p>
     * DAO classes should call this method to acquire connections from the pool.
     *
     * @return the configured {@link DataSource} instance
     */
    public static DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Shuts down the connection pool and releases all database resources.
     * <p>
     * This method should be called when the server is stopping to ensure
     * a clean shutdown of database connections.
     */
    public static void shutdownPool() {
        if (dataSource != null) {
            dataSource.close();
            System.out.println("HikariCP pool shut down ✓");
        }
    }
}