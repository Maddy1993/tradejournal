# Database Migration Function

This OCI Function automatically applies database schema migrations to the Autonomous Database.

## Features

- **Automatic Migration**: Applies pending SQL migrations in version order
- **Migration Tracking**: Records applied migrations in `schema_migrations` table
- **Idempotent**: Safe to run multiple times - only applies new migrations
- **Checksum Verification**: Calculates SHA-256 checksum for each migration
- **Execution Time Tracking**: Records how long each migration took

## How It Works

1. **Migration Files**: SQL files in `migrations/` directory with naming pattern `V###__description.sql`
   - `V000__migrations_table.sql` - Creates the tracking table
   - `V001__initial_schema.sql` - Initial database schema

2. **Execution Order**: Migrations are applied in numerical order (V000, V001, V002, etc.)

3. **Tracking**: Each applied migration is recorded in the `schema_migrations` table with:
   - Version number
   - Description
   - Timestamp
   - Checksum (SHA-256)
   - Execution time in milliseconds

## Usage

### Deploy the Function

The function is automatically deployed via GitHub Actions when changes are detected in:
- `backend/functions/db-migrate/`
- `backend/functions/shared/` (triggers rebuild of all functions)

Or manually trigger deployment:
```bash
gh workflow run "Deploy OCI Functions" -f functions=db-migrate
```

### Run Migrations

Invoke the function to apply pending migrations:

```bash
# Using OCI CLI
oci fn function invoke --profile personal \
  --function-id <db-migrate-function-ocid> \
  --file response.json \
  --body '{}'

# View response
cat response.json
```

**Response Format:**
```json
{
  "status": "success",
  "appliedCount": 2,
  "totalMigrations": 2,
  "appliedMigrations": [
    {
      "version": "V000",
      "description": "migrations table",
      "executionTime": "245ms"
    },
    {
      "version": "V001",
      "description": "initial schema",
      "executionTime": "1523ms"
    }
  ]
}
```

### Adding New Migrations

1. Create a new SQL file in `migrations/` directory:
   ```
   V002__add_user_preferences.sql
   ```

2. Follow the naming pattern:
   - Start with `V` followed by version number (3 digits)
   - Double underscore `__`
   - Description with underscores instead of spaces
   - `.sql` extension

3. Write your SQL statements:
   ```sql
   -- V002: Add user preferences table

   CREATE TABLE user_preferences (
       id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
       user_id RAW(16) NOT NULL,
       preference_key VARCHAR2(100) NOT NULL,
       preference_value VARCHAR2(1000),
       created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
       CONSTRAINT fk_user_prefs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
   );

   CREATE UNIQUE INDEX idx_user_prefs_key ON user_preferences(user_id, preference_key);
   ```

4. Commit and push - GitHub Actions will automatically deploy the updated function

5. Invoke the function - it will apply only the new migration

## Database Connection

The function uses:
- **Database Client**: `DatabaseClient.getPool(vertx)` from shared library
- **Connection String**: From `DB_CONNECTION_STRING` environment variable
- **Credentials**: Fetched from OCI Vault using Resource Principal
- **Authentication**: Resource Principal (no API keys needed)

## Function Configuration

- **Memory**: 512 MB
- **Timeout**: 300 seconds (5 minutes)
- **Runtime**: Java 11
- **Trigger**: Manual invocation (can be automated with OCI Events)

## Checking Migration Status

Query the `schema_migrations` table to see applied migrations:

```sql
SELECT version, description, applied_at, execution_time_ms
FROM schema_migrations
ORDER BY applied_at DESC;
```

## Best Practices

1. **Never Modify Applied Migrations**: Once a migration is applied, don't change it
2. **Always Add New Migrations**: Create new V### files for schema changes
3. **Test Migrations**: Test SQL statements before committing
4. **Use Transactions**: Migrations should be atomic (single transaction per file)
5. **Backup Before Major Changes**: Take database backup before large migrations

## Troubleshooting

### Function Fails on Invoke

Check logs:
```bash
oci logging-search search-logs \
  --search-query 'search "<compartment-ocid>" | where data.functionName="db-migrate"' \
  --time-start "2024-01-01T00:00:00Z" \
  --time-end "2024-12-31T23:59:59Z"
```

### Migration Already Applied

The function is idempotent - it will skip already applied migrations:
```json
{
  "status": "success",
  "appliedCount": 0,
  "totalMigrations": 2
}
```

### SQL Syntax Error

Check the SQL file for Oracle-specific syntax. Common issues:
- Use `RAW(16)` for UUID columns
- Use `TIMESTAMP WITH TIME ZONE` for timestamps
- Use `NUMBER` for numeric types
- Foreign keys require `ON DELETE CASCADE` or other action

## Architecture

```
┌─────────────────┐
│  db-migrate     │
│  Function       │
└────────┬────────┘
         │
         ├─ Read migrations/*.sql
         ├─ Check schema_migrations table
         ├─ Apply pending migrations
         └─ Record applied migrations
                  │
                  ▼
         ┌────────────────┐
         │  Autonomous    │
         │  Database      │
         └────────────────┘
```

## Related Files

- `migrations/V000__migrations_table.sql` - Migration tracking table
- `migrations/V001__initial_schema.sql` - Initial schema
- `src/main/java/com/zenith/trade/DbMigrateFunction.java` - Migration function code
- `../../shared/src/main/java/com/zenith/trade/util/DatabaseClient.java` - Database connection
- `../../shared/src/main/java/com/zenith/trade/util/VaultClient.java` - Secret retrieval
