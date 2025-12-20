package DBController;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class mysqlConnection1 {

    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(
                "jdbc:mysql://localhost:3306/bistro" +
                "?allowLoadLocalInfile=true&serverTimezone=Asia/Jerusalem&useSSL=false"
        );
        config.setUsername("root");
        // config.setPassword("Dy1908");
        config.setPassword("Rootroot");
        // Pool tuning (safe defaults for a course project)
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000); // 10s wait for a connection
        config.setIdleTimeout(300_000);      // 5min
        config.setMaxLifetime(1_800_000);    // 30min

        dataSource = new HikariDataSource(config);
        System.out.println("HikariCP pool initialized ✓");
    }

    private mysqlConnection1() {}

    public static DataSource getDataSource() {
        return dataSource;
    }

    public static void shutdownPool() {
        if (dataSource != null) {
            dataSource.close();
            System.out.println("HikariCP pool shut down ✓");
        }
    }
}
