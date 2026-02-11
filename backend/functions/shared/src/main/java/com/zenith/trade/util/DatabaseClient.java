package com.zenith.trade.util;

import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;

import java.util.logging.Logger;

public class DatabaseClient {

    private static final Logger logger = Logger.getLogger(DatabaseClient.class.getName());
    private static volatile JDBCPool pool;

    public static synchronized JDBCPool getPool(Vertx vertx) {
        if (pool == null) {
            try {
                // Get database connection details from environment
                String dbConnectionString = System.getenv("DB_CONNECTION_STRING");
                String dbUser = System.getenv("DB_USER");
                String dbPasswordSecretOcid = System.getenv("DB_PASSWORD_SECRET_OCID");

                if (dbConnectionString == null || dbConnectionString.isEmpty()) {
                    throw new IllegalStateException("DB_CONNECTION_STRING environment variable is not set");
                }
                if (dbUser == null || dbUser.isEmpty()) {
                    throw new IllegalStateException("DB_USER environment variable is not set");
                }
                if (dbPasswordSecretOcid == null || dbPasswordSecretOcid.isEmpty()) {
                    throw new IllegalStateException("DB_PASSWORD_SECRET_OCID environment variable is not set");
                }

                logger.info("Initializing database connection pool");
                logger.info("DB Connection String: " + dbConnectionString);
                logger.info("DB User: " + dbUser);

                // Fetch password from OCI Vault using Resource Principal
                logger.info("Fetching database password from OCI Vault");
                String dbPassword = VaultClient.getSecret(dbPasswordSecretOcid);

                // Build JDBC URL with proper prefix
                String jdbcUrl = buildJdbcUrl(dbConnectionString);
                logger.info("JDBC URL: " + jdbcUrl);

                JDBCConnectOptions connectOptions = new JDBCConnectOptions()
                    .setJdbcUrl(jdbcUrl)
                    .setUser(dbUser)
                    .setPassword(dbPassword);

                int poolSize = Integer.parseInt(System.getenv().getOrDefault("DB_POOL_SIZE", "5"));
                logger.info("Database pool size: " + poolSize);

                PoolOptions poolOptions = new PoolOptions()
                    .setMaxSize(poolSize);

                pool = JDBCPool.pool(vertx, connectOptions, poolOptions);
                logger.info("Database connection pool initialized successfully");
            } catch (Exception e) {
                logger.severe("Failed to initialize database connection pool: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Failed to initialize database connection", e);
            }
        }
        return pool;
    }

    /**
     * Build proper JDBC URL from connection string.
     * Adds jdbc:oracle:thin:@ prefix if not present.
     */
    /**
     * Build proper JDBC URL from connection string.
     * Ensure SSL properties are set for ADB connection.
     */
    private static String buildJdbcUrl(String connectionString) {
        String url;
        if (connectionString.startsWith("jdbc:")) {
            url = connectionString;
        } else if (connectionString.contains("1522") && !connectionString.toLowerCase().contains("tcps")) {
            // ADB uses port 1522 and requires TCPS. Force usage of tcps:// prefix for EZ Connect Plus
            url = "jdbc:oracle:thin:@tcps://" + connectionString;
        } else {
            url = "jdbc:oracle:thin:@tcps://" + connectionString;
        }
        
        // For ADB 1-way TLS, ensure we use the system truststore if no wallet is provided via TNS_ADMIN
        if (!url.contains("TNS_ADMIN") && System.getenv("TNS_ADMIN") == null) {
             // Append properties for 1-way TLS using system Default TrustStore (SSLET)
             // and ensure DN match is enabled for security.
             // We assume the connection string is in EZ Connect format or similar that accepts properties.
             // If it already has properties (?), append with &. Otherwise ?.
             if (url.contains("?")) {
                 url += "&javax.net.ssl.trustStoreType=SSLET&oracle.net.ssl_server_dn_match=true";
             } else {
                 url += "?javax.net.ssl.trustStoreType=SSLET&oracle.net.ssl_server_dn_match=true";
             }
        }
        return url;
    }
}
