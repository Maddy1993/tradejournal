# OCI Resource Principal Authentication Guide

**Using Resource Principal Authentication in OCI Functions**

---

## Overview

Resource Principal authentication allows OCI Functions to securely access other OCI services (Autonomous Database, Object Storage, Secrets, etc.) without storing credentials in code or configuration.

### Benefits

✅ **No credential management** - No need to store API keys or passwords in functions
✅ **Automatic rotation** - Credentials are managed by OCI
✅ **Fine-grained access** - IAM policies control exactly what each function can access
✅ **Audit trail** - All access is logged in OCI audit logs
✅ **Best practice** - Recommended by Oracle for production workloads

---

## How It Works

1. **Dynamic Group** - Created in IAM containing all functions in your compartment
2. **IAM Policies** - Grant the dynamic group permissions to access resources
3. **Resource Principal Provider** - SDK automatically uses function's identity to authenticate
4. **Runtime** - Functions make API calls without explicit credentials

**Flow:**
```
Function invoked → OCI injects resource principal token →
SDK uses token → Accesses OCI service → Audit logged
```

---

## Infrastructure Setup (Already Configured)

The Terraform configuration has created:

### 1. Dynamic Group
Located in: `infrastructure/terraform/oci/iam-policies.tf`

```hcl
resource "oci_identity_dynamic_group" "functions_dynamic_group" {
  name = "trade-journal-functions-dg"
  matching_rule = "ALL {resource.type = 'fnfunc', resource.compartment.id = '${var.compartment_id}'}"
}
```

### 2. IAM Policies

**Database Access:**
```
Allow dynamic-group trade-journal-functions-dg to read autonomous-databases in compartment
Allow dynamic-group trade-journal-functions-dg to use autonomous-databases in compartment
```

**Object Storage Access:**
```
Allow dynamic-group trade-journal-functions-dg to read buckets in compartment
Allow dynamic-group trade-journal-functions-dg to manage objects in compartment
```

**Secrets Access:**
```
Allow dynamic-group trade-journal-functions-dg to read secret-bundles in compartment
```

**Logging:**
```
Allow dynamic-group trade-journal-functions-dg to use log-content in compartment
```

### 3. Secrets in OCI Vault

Sensitive data stored securely:
- `trade-journal-db-admin-password` - Database admin password
- `trade-journal-app-secret` - Application JWT secret

---

## Using Resource Principal in Java Functions

### Step 1: Add OCI SDK Dependencies

**pom.xml:**
```xml
<dependencies>
    <!-- OCI Java SDK BOM -->
    <dependency>
        <groupId>com.oracle.oci.sdk</groupId>
        <artifactId>oci-java-sdk-bom</artifactId>
        <version>3.33.0</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>

    <!-- Secrets Management -->
    <dependency>
        <groupId>com.oracle.oci.sdk</groupId>
        <artifactId>oci-java-sdk-secrets</artifactId>
    </dependency>

    <!-- Object Storage -->
    <dependency>
        <groupId>com.oracle.oci.sdk</groupId>
        <artifactId>oci-java-sdk-objectstorage</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>com.oracle.oci.sdk</groupId>
        <artifactId>oci-java-sdk-database</artifactId>
    </dependency>

    <!-- Common -->
    <dependency>
        <groupId>com.oracle.oci.sdk</groupId>
        <artifactId>oci-java-sdk-common</artifactId>
    </dependency>
</dependencies>
```

---

### Step 2: Create Authentication Provider Utility

**`backend/functions/shared/src/main/java/com/zenith/trade/shared/OCIAuthProvider.java`:**

```java
package com.zenith.trade.shared;

import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides OCI authentication using Resource Principal
 */
public class OCIAuthProvider {
    private static final ResourcePrincipalAuthenticationDetailsProvider provider;
    private static final Map<String, String> secretsCache = new HashMap<>();

    static {
        try {
            // Initialize Resource Principal provider
            provider = ResourcePrincipalAuthenticationDetailsProvider.builder().build();
            System.out.println("✅ Resource Principal authentication initialized");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Resource Principal authentication", e);
        }
    }

    /**
     * Get the Resource Principal authentication provider
     */
    public static ResourcePrincipalAuthenticationDetailsProvider getProvider() {
        return provider;
    }

    /**
     * Fetch secret from OCI Vault using Resource Principal
     *
     * @param secretOcid The OCID of the secret
     * @return The secret value as a string
     */
    public static String getSecret(String secretOcid) {
        // Check cache first
        if (secretsCache.containsKey(secretOcid)) {
            return secretsCache.get(secretOcid);
        }

        try {
            SecretsClient secretsClient = SecretsClient.builder()
                .build(provider);

            GetSecretBundleRequest request = GetSecretBundleRequest.builder()
                .secretId(secretOcid)
                .build();

            GetSecretBundleResponse response = secretsClient.getSecretBundle(request);

            // Decode the secret content
            Base64SecretBundleContentDetails content =
                (Base64SecretBundleContentDetails) response.getSecretBundle().getSecretBundleContent();

            byte[] decodedContent = Base64.getDecoder().decode(content.getContent());
            String secretValue = new String(decodedContent, StandardCharsets.UTF_8);

            // Cache the secret
            secretsCache.put(secretOcid, secretValue);

            secretsClient.close();
            return secretValue;

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch secret: " + secretOcid, e);
        }
    }

    /**
     * Get environment variable or throw exception
     */
    public static String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new RuntimeException("Required environment variable not set: " + name);
        }
        return value;
    }
}
```

---

### Step 3: Create Database Client with Resource Principal

**`backend/functions/shared/src/main/java/com/zenith/trade/shared/DatabaseClient.java`:**

```java
package com.zenith.trade.shared;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

/**
 * PostgreSQL database client using credentials from OCI Vault
 */
public class DatabaseClient {
    private static PgPool pool;
    private static Vertx vertx;

    /**
     * Initialize database connection pool using Resource Principal
     */
    public static synchronized PgPool getPool() {
        if (pool != null) {
            return pool;
        }

        try {
            vertx = Vertx.vertx();

            // Get configuration from environment (set by Functions Application)
            String dbConnectionString = OCIAuthProvider.getRequiredEnv("DB_CONNECTION_STRING");
            String dbName = OCIAuthProvider.getRequiredEnv("DB_NAME");
            String dbUser = OCIAuthProvider.getRequiredEnv("DB_USER");

            // Get password from OCI Vault using Resource Principal
            String dbPasswordSecretOcid = OCIAuthProvider.getRequiredEnv("DB_PASSWORD_SECRET_OCID");
            String dbPassword = OCIAuthProvider.getSecret(dbPasswordSecretOcid);

            // Parse connection string to get host and port
            // Format: (description=(address=(host=xxx)(port=1522))(connect_data=...))
            String host = extractHost(dbConnectionString);
            int port = extractPort(dbConnectionString);

            PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(host)
                .setPort(port)
                .setDatabase(dbName)
                .setUser(dbUser)
                .setPassword(dbPassword)
                .setSsl(true)
                .setTrustAll(true); // For Autonomous DB

            PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(5)
                .setIdleTimeout(300)
                .setConnectionTimeout(10);

            pool = PgPool.pool(vertx, connectOptions, poolOptions);

            System.out.println("✅ Database connection pool initialized");
            return pool;

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database connection", e);
        }
    }

    private static String extractHost(String connectionString) {
        // Simple extraction - implement proper TNS parsing for production
        int hostIndex = connectionString.indexOf("host=");
        if (hostIndex == -1) return "localhost";

        int start = hostIndex + 5;
        int end = connectionString.indexOf(")", start);
        return connectionString.substring(start, end);
    }

    private static int extractPort(String connectionString) {
        int portIndex = connectionString.indexOf("port=");
        if (portIndex == -1) return 1522;

        int start = portIndex + 5;
        int end = connectionString.indexOf(")", start);
        return Integer.parseInt(connectionString.substring(start, end));
    }

    public static void close() {
        if (pool != null) {
            pool.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }
}
```

---

### Step 4: Create Object Storage Client

**`backend/functions/shared/src/main/java/com/zenith/trade/shared/ObjectStorageClient.java`:**

```java
package com.zenith.trade.shared;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Object Storage client using Resource Principal authentication
 */
public class ObjectStorageClient {
    private static ObjectStorage client;
    private static String namespace;
    private static String bucketName;
    private static String reportsBucket;

    static {
        try {
            // Initialize client with Resource Principal
            client = ObjectStorageClient.builder()
                .build(OCIAuthProvider.getProvider());

            // Get configuration from environment
            namespace = OCIAuthProvider.getRequiredEnv("OBJECT_STORAGE_NAMESPACE");
            bucketName = OCIAuthProvider.getRequiredEnv("OBJECT_STORAGE_BUCKET");
            reportsBucket = OCIAuthProvider.getRequiredEnv("REPORTS_BUCKET");

            System.out.println("✅ Object Storage client initialized");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Object Storage client", e);
        }
    }

    /**
     * Upload file to Object Storage
     *
     * @param objectName The name of the object (path in bucket)
     * @param content The file content
     * @param contentType The content type (e.g., "application/pdf")
     * @param useReportsBucket Whether to use reports bucket (default: false)
     */
    public static void uploadObject(String objectName, byte[] content,
                                     String contentType, boolean useReportsBucket) {
        try {
            String bucket = useReportsBucket ? reportsBucket : bucketName;

            PutObjectRequest request = PutObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucket)
                .objectName(objectName)
                .contentType(contentType)
                .putObjectBody(new ByteArrayInputStream(content))
                .contentLength((long) content.length)
                .build();

            PutObjectResponse response = client.putObject(request);
            System.out.println("✅ Uploaded object: " + objectName + " to bucket: " + bucket);

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload object: " + objectName, e);
        }
    }

    /**
     * Download file from Object Storage
     */
    public static byte[] downloadObject(String objectName, boolean useReportsBucket) {
        try {
            String bucket = useReportsBucket ? reportsBucket : bucketName;

            GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucket)
                .objectName(objectName)
                .build();

            GetObjectResponse response = client.getObject(request);

            try (InputStream inputStream = response.getInputStream()) {
                return inputStream.readAllBytes();
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to download object: " + objectName, e);
        }
    }

    /**
     * Generate pre-authenticated request (PAR) URL for direct download
     */
    public static String generateDownloadUrl(String objectName, boolean useReportsBucket) {
        // Implementation for generating PAR URLs
        // This allows clients to download files directly without going through functions
        String bucket = useReportsBucket ? reportsBucket : bucketName;
        return String.format("https://objectstorage.%s.oraclecloud.com/n/%s/b/%s/o/%s",
            OCIAuthProvider.getRequiredEnv("REGION"),
            namespace,
            bucket,
            objectName
        );
    }

    public static void close() {
        if (client != null) {
            client.close();
        }
    }
}
```

---

### Step 5: Use in Function Implementation

**Example: `backend/functions/trades/CreateTradeFunction.java`:**

```java
package com.zenith.trade;

import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;
import com.zenith.trade.shared.DatabaseClient;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;

import java.time.LocalDateTime;

public class CreateTradeFunction {
    private PgPool pgPool;

    @FnConfiguration
    public void config(RuntimeContext ctx) {
        // Get database pool - uses Resource Principal internally
        pgPool = DatabaseClient.getPool();
        System.out.println("✅ CreateTradeFunction initialized with Resource Principal auth");
    }

    public String handleRequest(HTTPGatewayContext hctx, JsonObject input) {
        JsonObject response = new JsonObject();

        try {
            // Database operations use Resource Principal automatically
            String tradeId = insertTrade(input)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

            response.put("success", true)
                    .put("tradeId", tradeId);

            hctx.setResponseHeader("Content-Type", "application/json");
            return response.encode();

        } catch (Exception e) {
            response.put("success", false)
                    .put("error", e.getMessage());
            hctx.setStatusCode(500);
            return response.encode();
        }
    }

    private Future<String> insertTrade(JsonObject trade) {
        Promise<String> promise = Promise.promise();

        pgPool.preparedQuery(
            "INSERT INTO trades (symbol, quantity, price, trade_date) " +
            "VALUES ($1, $2, $3, $4) RETURNING id"
        ).execute(Tuple.of(
            trade.getString("symbol"),
            trade.getInteger("quantity"),
            trade.getDouble("price"),
            LocalDateTime.now()
        ), ar -> {
            if (ar.succeeded()) {
                promise.complete(ar.result().iterator().next().getString("id"));
            } else {
                promise.fail(ar.cause());
            }
        });

        return promise.future();
    }
}
```

---

## Environment Variables Set by Functions Application

The following environment variables are automatically available in functions:

```bash
# Resource Principal
OCI_RESOURCE_PRINCIPAL_VERSION=2.2

# Database
DB_OCID=ocid1.autonomousdatabase.oc1...
DB_CONNECTION_STRING=(description=...)
DB_NAME=tradejournaldb
DB_USER=ADMIN
DB_PASSWORD_SECRET_OCID=ocid1.vaultsecret.oc1...

# Secrets
APP_SECRET_OCID=ocid1.vaultsecret.oc1...

# Object Storage
OBJECT_STORAGE_NAMESPACE=your-namespace
OBJECT_STORAGE_BUCKET=trade-journal-files
REPORTS_BUCKET=trade-journal-reports

# Application
APP_NAME=trade-journal
ENVIRONMENT=production
REGION=us-ashburn-1
```

---

## Testing Resource Principal Locally

**Note:** Resource Principal authentication only works inside OCI Functions runtime. For local development:

### Option 1: Use API Key Authentication (Development Only)

```java
public static void initLocalDevelopment() {
    if (System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION") == null) {
        // Local development - use API key
        ConfigFileAuthenticationDetailsProvider provider =
            new ConfigFileAuthenticationDetailsProvider("~/.oci/config", "DEFAULT");
        return provider;
    } else {
        // Production - use Resource Principal
        return ResourcePrincipalAuthenticationDetailsProvider.builder().build();
    }
}
```

### Option 2: Use Local Environment Variables

Create `.env.local` for local testing:
```bash
DB_CONNECTION_STRING=localhost:5432
DB_NAME=tradejournal_dev
DB_USER=postgres
DB_PASSWORD=local_password  # Direct password for local dev
OBJECT_STORAGE_NAMESPACE=local
# ... other vars
```

---

## Troubleshooting

### Error: "Failed to initialize Resource Principal authentication"

**Cause:** Function not running in OCI Functions runtime

**Solution:**
- Ensure function is deployed to OCI Functions
- Check that `OCI_RESOURCE_PRINCIPAL_VERSION` environment variable is set
- Verify function is in the correct compartment

### Error: "Access denied to secret"

**Cause:** IAM policy not configured correctly

**Solution:**
```bash
# Verify dynamic group exists
oci iam dynamic-group list --compartment-id <tenancy-ocid> | grep trade-journal

# Verify policies
oci iam policy list --compartment-id <compartment-ocid> | grep functions

# Check function is in dynamic group
oci fn function list --application-id <app-ocid>
```

### Error: "Failed to fetch secret"

**Cause:** Secret OCID incorrect or secret doesn't exist

**Solution:**
```bash
# List secrets
oci secrets secret list --compartment-id <compartment-ocid>

# Verify secret OCID
echo $DB_PASSWORD_SECRET_OCID

# Test secret access
oci secrets secret-bundle get --secret-id <secret-ocid>
```

---

## Best Practices

1. **Never log secrets** - Even when debugging, never log secret values
2. **Cache secrets** - Fetch secrets once and cache for the function lifetime
3. **Use least privilege** - Grant only the minimum required permissions
4. **Monitor access** - Enable audit logging for all resource access
5. **Rotate secrets** - Regularly rotate secrets in OCI Vault
6. **Handle errors gracefully** - Implement proper error handling for auth failures

---

## Security Considerations

✅ **Secrets never in code** - All sensitive data in OCI Vault
✅ **No credential files** - No wallet files or config files in function images
✅ **Automatic rotation** - OCI manages credential lifecycle
✅ **Audit trail** - All access logged in OCI Audit
✅ **Network isolation** - Functions in private subnet
✅ **Encryption at rest** - Secrets encrypted with KMS key

---

## Migration from Credentials to Resource Principal

If you have existing functions using credentials, migrate:

**Before (insecure):**
```java
String password = System.getenv("DB_PASSWORD"); // ❌ Password in plain text
```

**After (secure):**
```java
String secretOcid = System.getenv("DB_PASSWORD_SECRET_OCID");
String password = OCIAuthProvider.getSecret(secretOcid); // ✅ Fetched securely
```

---

## Additional Resources

- [OCI Resource Principal Docs](https://docs.oracle.com/en-us/iaas/Content/Functions/Tasks/functionsaccessingociresources.htm)
- [OCI Java SDK Resource Principal](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdk_authentication_methods.htm#sdk_authentication_methods_resource_principal)
- [OCI Vault Documentation](https://docs.oracle.com/en-us/iaas/Content/KeyManagement/Concepts/keyoverview.htm)

---

**Document Version:** 1.0
**Last Updated:** 2026-02-10
**Authentication Method:** Resource Principal (Recommended for Production)
