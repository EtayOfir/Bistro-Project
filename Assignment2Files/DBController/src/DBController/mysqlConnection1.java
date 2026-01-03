package DBController;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides and manages a shared MySQL database connection pool for the Bistro system
 * using the HikariCP connection pooling library.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Loading database configuration from an external {@code db.properties} file.</li>
 *   <li>Initializing a single {@link HikariDataSource} instance at server startup.</li>
 *   <li>Providing access to the shared {@link DataSource} for all DAO classes.</li>
 *   <li>Ensuring proper shutdown of the connection pool when the server stops.</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * The database credentials and connection URL are <b>not hard-coded</b>.
 * They are loaded at runtime from:
 * <pre>
 * Assignment2Files/DBController/config/db.properties
 * </pre>
 *
 * A template file {@code db.properties.example} is provided in the repository.
 * Each developer must create a local {@code db.properties} file based on that template.
 *
 * <h2>Error Handling</h2>
 * If the configuration file cannot be found or loaded, the application will fail fast
 * with a clear runtime exception, preventing the server from starting in an invalid state.
 *
 * <h2>Design Notes</h2>
 * <ul>
 *   <li>This class follows the <b>utility class</b> pattern.</li>
 *   <li>It cannot be instantiated.</li>
 *   <li>The connection pool is initialized once using a static initialization block.</li>
 * </ul>
 *
 * <p>
 * <b>Thread safety:</b> HikariCP manages thread safety internally.
 * The returned {@link DataSource} is safe to use concurrently across multiple threads.
 *
 * @author Bistro Team
 */
public final class mysqlConnection1 {

    /**
     * The shared HikariCP {@link DataSource} instance used throughout the application.
     * <p>
     * This data source represents a connection pool and should never be recreated
     * after initialization.
     */
    private static final HikariDataSource dataSource;

    /*
     * Static initialization block that loads database configuration
     * and initializes the HikariCP connection pool.
     */
    static {
        Properties props = new Properties();

        // Load database configuration from external properties file
        try (InputStream in = mysqlConnection1.class
                .getClassLoader()
                .getResourceAsStream("config/db.properties")) {

            if (in == null) {
                throw new RuntimeException(
                    "Missing config/db.properties in classpath. " +
                    "Make sure it exists under DBController/config"
                );
            }

            props.load(in);

        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to load database configuration from classpath",e);
        }

        HikariConfig config = new HikariConfig();

        // JDBC connection configuration
        config.setJdbcUrl(props.getProperty("db.url"));
        config.setUsername(props.getProperty("db.user"));
        config.setPassword(props.getProperty("db.password"));

        // Required for MySQL authentication with caching_sha2_password
        config.addDataSourceProperty("allowPublicKeyRetrieval", "true");

        // Connection pool tuning
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000); // 10 seconds
        config.setIdleTimeout(300_000);      // 5 minutes
        config.setMaxLifetime(1_800_000);    // 30 minutes

        dataSource = new HikariDataSource(config);
        System.out.println("HikariCP pool initialized ✓");
    }

    /**
     * Private constructor to prevent instantiation.
     * <p>
     * This class is intended to be used in a static context only.
     */
    private mysqlConnection1() {
        // Prevent instantiation
    }

    /**
     * Returns the shared {@link DataSource} instance backed by the HikariCP connection pool.
     * <p>
     * DAO classes should call this method to obtain database connections.
     * Connections must be closed after use so they can be returned to the pool.
     *
     * @return the configured {@link DataSource} instance
     */
    public static DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Shuts down the HikariCP connection pool and releases all database resources.
     * <p>
     * This method should be invoked when the server is stopping to ensure
     * a clean and graceful shutdown.
     */
    public static void shutdownPool() {
        if (dataSource != null) {
            dataSource.close();
            System.out.println("HikariCP pool shut down ✓");
        }
    }
}
