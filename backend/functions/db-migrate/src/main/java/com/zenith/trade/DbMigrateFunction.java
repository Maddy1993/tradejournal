package com.zenith.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.RuntimeContext;
import com.zenith.trade.util.DatabaseClient;
import com.zenith.trade.util.ErrorLogUploader;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Database Migration Function
 * Applies SQL migrations in version order and tracks them in schema_migrations table.
 */
public class DbMigrateFunction {

    private static final Logger logger = Logger.getLogger(DbMigrateFunction.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern MIGRATION_PATTERN = Pattern.compile("V(\\d+)__(.+)\\.sql");

    private Vertx vertx;
    private JDBCPool dbClient;

    @FnConfiguration
    public void config(RuntimeContext ctx) {
        this.vertx = Vertx.vertx();
        this.dbClient = DatabaseClient.getPool(vertx);
    }

    public String handleRequest(String input) {
        logger.info("Starting database migration process");

        try {
            // ... (existing try block content)
            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> appliedMigrations = new ArrayList<>();

            // Ensure schema_migrations table exists
            ensureMigrationsTable().toCompletionStage().toCompletableFuture().get();

            // Get list of applied migrations
            Set<String> applied = getAppliedMigrations().toCompletionStage().toCompletableFuture().get();
            logger.info("Already applied migrations: " + applied);

            // Get all migration files from resources
            List<Migration> migrations = getMigrationFiles();
            logger.info("Found " + migrations.size() + " migration files");

            // Sort migrations by version
            migrations.sort(Comparator.comparing(Migration::getVersion));

            // Apply pending migrations
            int appliedCount = 0;
            for (Migration migration : migrations) {
                if (!applied.contains(migration.getVersion())) {
                    logger.info("Applying migration: " + migration.getFileName());
                    long startTime = System.currentTimeMillis();

                    applyMigration(migration).toCompletionStage().toCompletableFuture().get();

                    long executionTime = System.currentTimeMillis() - startTime;
                    recordMigration(migration, executionTime).toCompletionStage().toCompletableFuture().get();

                    appliedCount++;
                    appliedMigrations.add(Map.of(
                        "version", migration.getVersion(),
                        "description", migration.getDescription(),
                        "executionTime", executionTime + "ms"
                    ));

                    logger.info("Successfully applied migration: " + migration.getFileName() + " in " + executionTime + "ms");
                } else {
                    logger.info("Skipping already applied migration: " + migration.getFileName());
                }
            }

            result.put("status", "success");
            result.put("appliedCount", appliedCount);
            result.put("totalMigrations", migrations.size());
            result.put("appliedMigrations", appliedMigrations);

            logger.info("Migration process completed. Applied " + appliedCount + " migrations.");
            return mapper.writeValueAsString(result);

        } catch (Exception e) {
            logger.severe("Migration failed: " + e.getMessage());
            e.printStackTrace();

            // Upload detailed error log to Object Storage
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                String stackTrace = sw.toString();
                
                String errorLog = "Migration Failed:\n" +
                                  "Message: " + e.getMessage() + "\n" +
                                  "Input: " + input + "\n" + 
                                  "Stack Trace:\n" + stackTrace;
                                  
                ErrorLogUploader.uploadErrorLog("db-migrate", errorLog);
            } catch (Exception uploadEx) {
                 logger.warning("Failed to upload error log: " + uploadEx.getMessage());
            }

            Map<String, Object> error = Map.of(
                "status", "error",
                "message", e.getMessage()
            );
            try {
                return mapper.writeValueAsString(error);
            } catch (Exception ex) {
                return "{\"status\":\"error\",\"message\":\"" + e.getMessage().replace("\"", "'") + "\"}";
            }
        }
    }

    private Future<Void> ensureMigrationsTable() {
        String sql = "SELECT COUNT(*) as cnt FROM user_tables WHERE table_name = 'SCHEMA_MIGRATIONS'";

        return dbClient.preparedQuery(sql)
            .execute()
            .compose(rows -> {
                Row row = rows.iterator().next();
                int count = row.getInteger("CNT");

                if (count == 0) {
                    logger.info("schema_migrations table doesn't exist. Creating it...");
                    // Read and apply V000__migrations_table.sql
                    String createTableSql = readMigrationFile("V000__migrations_table.sql");
                    return executeSqlStatements(createTableSql);
                } else {
                    logger.info("schema_migrations table already exists");
                    return Future.succeededFuture();
                }
            });
    }

    private Future<Set<String>> getAppliedMigrations() {
        return dbClient.preparedQuery("SELECT version FROM schema_migrations")
            .execute()
            .map(rows -> {
                Set<String> applied = new HashSet<>();
                for (Row row : rows) {
                    applied.add(row.getString("VERSION"));
                }
                return applied;
            });
    }

    private List<Migration> getMigrationFiles() {
        List<Migration> migrations = new ArrayList<>();

        try {
            // List all resources in migrations directory
            ClassLoader classLoader = getClass().getClassLoader();
            InputStream is = classLoader.getResourceAsStream("migrations/");

            if (is == null) {
                logger.warning("migrations directory not found in resources");
                return migrations;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = MIGRATION_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String version = "V" + matcher.group(1);
                    String description = matcher.group(2).replace("_", " ");
                    String fileName = line;
                    String content = readMigrationFile(fileName);

                    migrations.add(new Migration(version, description, fileName, content));
                }
            }
        } catch (Exception e) {
            logger.warning("Error listing migration files: " + e.getMessage());
        }

        // Fallback: try to load known migrations directly
        if (migrations.isEmpty()) {
            String[] knownMigrations = {
                "V001__initial_schema.sql"
            };

            for (String fileName : knownMigrations) {
                try {
                    String content = readMigrationFile(fileName);
                    if (content != null && !content.isEmpty()) {
                        Matcher matcher = MIGRATION_PATTERN.matcher(fileName);
                        if (matcher.matches()) {
                            String version = "V" + matcher.group(1);
                            String description = matcher.group(2).replace("_", " ");
                            migrations.add(new Migration(version, description, fileName, content));
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Could not load migration: " + fileName);
                }
            }
        }

        return migrations;
    }

    private String readMigrationFile(String fileName) {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("migrations/" + fileName);
            if (is == null) {
                logger.warning("Migration file not found: " + fileName);
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            logger.severe("Error reading migration file " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    private Future<Void> applyMigration(Migration migration) {
        return executeSqlStatements(migration.getContent());
    }

    private Future<Void> executeSqlStatements(String sql) {
        // Remove comment lines and split by semicolon
        String cleanedSql = Arrays.stream(sql.split("\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("--"))
            .collect(Collectors.joining("\n"));

        String[] statements = cleanedSql.split(";");

        Future<Void> chain = Future.succeededFuture();
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                chain = chain.compose(v ->
                    dbClient.query(trimmed).execute().mapEmpty()
                );
            }
        }

        return chain;
    }

    private Future<Void> recordMigration(Migration migration, long executionTime) {
        String checksum = calculateChecksum(migration.getContent());

        return dbClient.preparedQuery(
            "INSERT INTO schema_migrations (version, description, checksum, execution_time_ms) VALUES (?, ?, ?, ?)"
        ).execute(io.vertx.sqlclient.Tuple.of(
            migration.getVersion(),
            migration.getDescription(),
            checksum,
            executionTime
        )).mapEmpty();
    }

    private String calculateChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static class Migration {
        private final String version;
        private final String description;
        private final String fileName;
        private final String content;

        public Migration(String version, String description, String fileName, String content) {
            this.version = version;
            this.description = description;
            this.fileName = fileName;
            this.content = content;
        }

        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public String getFileName() { return fileName; }
        public String getContent() { return content; }
    }
}
