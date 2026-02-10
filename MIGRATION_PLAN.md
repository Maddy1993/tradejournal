# Multi-Cloud Hybrid Migration Plan

**Trade Journal Application - Migration to Oracle Cloud + Vercel + Cloudflare**

---

## Overview

Migration of Vert.x 4.4.6 trade journal application to a multi-cloud hybrid architecture leveraging free tiers across Oracle Cloud Infrastructure, Vercel, and Cloudflare.

**Current Stack:**
- Backend: Vert.x 4.4.6 (Java 11, reactive framework)
- Database: PostgreSQL (vertx-pg-client)
- Cache: Redis (vertx-redis-client)
- Communication: gRPC (vertx-grpc-server/client)
- Build: Maven

**Target Architecture:**
```
Cloudflare (CDN/Security) → Vercel (Frontend) → OCI Functions (Backend) → Autonomous DB + Object Storage
```

**Timeline:** 13-15 weeks

**Cost:** $0/month (all free tiers)

---

## ✅ Phase 1: Infrastructure (Completed)

**Status:** Deployed via Terraform & GitHub Actions.

- **Networking:** VCN, Subnets, Internet Gateway ([x] `network.tf`)
- **Database:** Autonomous Database (Free Tier) ([x] `database.tf`)
- **Storage:** Object Storage Buckets ([x] `storage.tf`)
- **API Gateway:** Gateway & Deployment ([x] `api-gateway.tf`)
- **CI/CD:** GitHub Actions Workflow ([x] `terraform-deploy.yml`)

---

## 🔄 Phase 2: Application Migration (In Progress)

### 2.1 Backend Architecture (Grouped Functions)

| Group | Function | Type | Status | Route | Description |
|-------|----------|------|--------|-------|-------------|
| **Interactive** | `trades-api` | Sync | **Implemented** (Pending Deploy) | `GET/POST /trades` | Handles UI requests, stats, and **Job Polling**. |
| **Background** | `sync-processor` | Async | **Implemented** (Pending Deploy) | `POST /sync` | Fetches trades from SnapTrade. Updates Job Status. |
| **Background** | `reports-processor` | Async | Pending | `POST /reports` | Generates PDFs. Updates Job Status. |

### 2.2 Async Polling Architecture

To handle long-running processes (Sync, Reports) without timeouts:

1.  **Initiation:**
    -   Client calls `POST /api/sync`.
    -   API Gateway triggers `sync-processor`.
    -   Function creates a `Job` record in DB with `status=QUEUED`.
    -   Function returns `202 Accepted` with `{"jobId": "uuid"}`.
2.  **Processing:**
    -   `sync-processor` continues in background.
    -   Updates DB record to `PROCESSING` -> `COMPLETED` / `FAILED`.
3.  **Polling:**
    -   Client polls `GET /api/jobs/{jobId}` (handled by `trades-api`).
    -   Returns current status and result summary.

**Infrastructure Helper:**
- **Job Status Table:** A `job_status` table in Autonomous DB to track state.

### 2.3 Shared Library (`functions-shared`) - ✅ Completed

- **Models:** `Trade`, `BrokerAccount`, `User`, `JobStatus` (POJOs, Lombok removed)
- **Utilities:** `DatabaseClient` (Switched to **Oracle JDBC** `ojdbc11` + `vertx-jdbc-client`)
- **Architecture:** Shared code packaged as a Maven dependency.
- **Build Status:** Successfully built with `local-settings.xml` to bypass internal Artifactory.

### 2.4 Validation & Testing

- **Testing Guide:** Detailed testing steps for `health-check`, `trades-api`, and `sync-processor` are documented in [TESTING.md](./TESTING.md).
- **Validation Status:**
    - [x] Health Check (Remote OCI)
    - [ ] Trades API (Pending Deploy)
    - [ ] Sync Processor (Pending Deploy)


---

## ⏳ Phase 3: Frontend & Cloudflare (Pending)

### 3.1 Next.js Frontend
-   **Vercel Deployment:** Connect to GitHub.
-   **API Integration:** Point to Cloudflare/API Gateway.
-   **Polling Hook:** Implement `useJobPoll(jobId)` for UX.

### 3.2 Cloudflare
-   **DNS:** Proxy traffic to Vercel (Frontend) and OCI API Gateway (Backend).
-   **Caching:** Cache static assets.

### 3.3 Sample Function Implementation

**CreateTradeFunction.java:**
```java
package com.zenith.trade;

import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

public class CreateTradeFunction {
    private PgPool pgPool;
    private Vertx vertx;

    @FnConfiguration
    public void config(RuntimeContext ctx) {
        vertx = Vertx.vertx();

        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(System.getenv("DB_HOST"))
            .setPort(Integer.parseInt(System.getenv("DB_PORT")))
            .setDatabase(System.getenv("DB_NAME"))
            .setUser(System.getenv("DB_USER"))
            .setPassword(System.getenv("DB_PASSWORD"))
            .setSsl(true);

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        pgPool = PgPool.pool(vertx, connectOptions, poolOptions);
    }

    public String handleRequest(HTTPGatewayContext hctx, JsonObject input) {
        JsonObject response = new JsonObject();

        try {
            String tradeId = insertTrade(input).toCompletionStage().toCompletableFuture().get();
            response.put("success", true).put("tradeId", tradeId);

            hctx.setResponseHeader("Content-Type", "application/json");
            return response.encode();
        } catch (Exception e) {
            response.put("success", false).put("error", e.getMessage());
            hctx.setStatusCode(500);
            return response.encode();
        }
    }

    private Future<String> insertTrade(JsonObject trade) {
        Promise<String> promise = Promise.promise();

        pgPool.preparedQuery(
            "INSERT INTO trades (symbol, quantity, price, trade_date) VALUES ($1, $2, $3, $4) RETURNING id"
        ).execute(Tuple.of(
            trade.getString("symbol"),
            trade.getInteger("quantity"),
            trade.getDouble("price"),
            LocalDateTime.now()
        ), ar -> {
            if (ar.succeeded()) {
                RowSet<Row> rows = ar.result();
                promise.complete(rows.iterator().next().getString("id"));
            } else {
                promise.fail(ar.cause());
            }
        });

        return promise.future();
    }
}
```

**func.yaml:**
```yaml
schema_version: 20180708
name: create-trade
version: 0.0.1
runtime: java
build_image: fnproject/fn-java-fdk-build:jdk11-1.0.157
run_image: fnproject/fn-java-fdk:jre11-1.0.157
cmd: com.zenith.trade.CreateTradeFunction::handleRequest
memory: 256
timeout: 30
```

### 3.4 Long-Running Operations

**GenerateReportFunction.java:**
```java
public class GenerateReportFunction {

    public String handleRequest(HTTPGatewayContext hctx, JsonObject input) {
        String reportType = input.getString("type");
        String userId = input.getString("userId");
        String jobId = UUID.randomUUID().toString();

        // Start async processing (can run up to 1 hour)
        CompletableFuture.runAsync(() -> {
            try {
                generateReport(reportType, userId, jobId);
            } catch (Exception e) {
                updateJobStatus(jobId, "failed", e.getMessage());
            }
        });

        // Return job ID immediately
        JsonObject response = new JsonObject()
            .put("jobId", jobId)
            .put("status", "processing")
            .put("statusUrl", "/api/reports/status/" + jobId);

        hctx.setResponseHeader("Content-Type", "application/json");
        return response.encode();
    }

    private void generateReport(String type, String userId, String jobId) {
        updateJobStatus(jobId, "processing", null);

        // Generate report (long operation, up to 1 hour)
        byte[] reportData = generatePdfReport(type, userId);

        // Upload to Object Storage
        String objectName = String.format("reports/%s/%s.pdf", userId, jobId);
        uploadToObjectStorage(objectName, reportData);

        updateJobStatus(jobId, "completed", objectName);
    }
}
```

### 3.5 Deployment

**deploy-functions.sh:**
```bash
#!/bin/bash
set -e

APP_NAME="trade-journal"
FUNCTIONS=(
  "trades/create-trade"
  "trades/get-trades"
  "trades/update-trade"
  "trades/delete-trade"
  "reports/generate-report"
  "reports/get-report-status"
  "reconciliation/reconcile-trades"
)

fn use context oracle-cloud
fn update context oracle.compartment-id <compartment-ocid>

for func in "${FUNCTIONS[@]}"; do
  echo "Deploying $func..."
  cd $func
  fn -v deploy --app $APP_NAME
  cd -
done

echo "All functions deployed successfully!"
```

---

## Phase 4: Frontend Development (Next.js on Vercel) (Week 7-9)

### 4.1 Create Next.js Application

```bash
npx create-next-app@latest trade-journal-frontend \
  --typescript \
  --tailwind \
  --app \
  --src-dir \
  --import-alias "@/*"

cd trade-journal-frontend
```

### 4.2 Project Structure

```
/trade-journal-frontend
  /src
    /app
      /api          # Server-side API routes
      /trades       # Trade management pages
      /reports      # Reports pages
      /dashboard    # Main dashboard
      layout.tsx
      page.tsx
    /components
      /ui           # Reusable UI components
      /forms        # Form components
      /tables       # Data tables
    /lib
      /api          # API client
      /hooks        # Custom React hooks
      /utils        # Utilities
    /types          # TypeScript types
  next.config.js
  vercel.json
```

### 4.3 API Client

**/src/lib/api/client.ts:**
```typescript
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'https://api.yourdomain.com';

export class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string = API_BASE_URL) {
    this.baseUrl = baseUrl;
  }

  private async request<T>(endpoint: string, options?: RequestInit): Promise<T> {
    const url = `${this.baseUrl}${endpoint}`;
    const response = await fetch(url, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
    });

    if (!response.ok) {
      throw new Error(`API error: ${response.statusText}`);
    }

    return response.json();
  }

  async createTrade(trade: CreateTradeDto) {
    return this.request<TradeResponse>('/api/trades', {
      method: 'POST',
      body: JSON.stringify(trade),
    });
  }

  async getTrades(filters?: TradeFilters) {
    const params = new URLSearchParams(filters as any);
    return this.request<Trade[]>(`/api/trades?${params}`);
  }

  async generateReport(type: string, params: any) {
    return this.request<ReportJobResponse>('/api/reports/generate', {
      method: 'POST',
      body: JSON.stringify({ type, ...params }),
    });
  }

  async getReportStatus(jobId: string) {
    return this.request<ReportStatusResponse>(`/api/reports/status/${jobId}`);
  }
}

export const apiClient = new ApiClient();
```

### 4.4 Long-Running Operations Hook

**/src/lib/hooks/usePolling.ts:**
```typescript
import { useEffect, useState, useCallback } from 'react';

export function usePolling<T>(
  pollFn: () => Promise<T>,
  interval: number = 2000,
  shouldPoll: (data: T | null) => boolean
) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [isPolling, setIsPolling] = useState(false);

  const poll = useCallback(async () => {
    try {
      const result = await pollFn();
      setData(result);
      setError(null);

      if (!shouldPoll(result)) {
        setIsPolling(false);
      }
    } catch (err) {
      setError(err as Error);
      setIsPolling(false);
    }
  }, [pollFn, shouldPoll]);

  useEffect(() => {
    if (!isPolling) return;
    const intervalId = setInterval(poll, interval);
    return () => clearInterval(intervalId);
  }, [isPolling, interval, poll]);

  const startPolling = useCallback(() => {
    setIsPolling(true);
    poll();
  }, [poll]);

  return { data, error, isPolling, startPolling };
}

export function useReportGeneration(jobId: string | null) {
  return usePolling(
    () => apiClient.getReportStatus(jobId!),
    2000,
    (status) => status?.status === 'processing'
  );
}
```

### 4.5 Vercel Configuration

**vercel.json:**
```json
{
  "buildCommand": "npm run build",
  "framework": "nextjs",
  "env": {
    "NEXT_PUBLIC_API_URL": "https://api.yourdomain.com"
  },
  "headers": [
    {
      "source": "/(.*)",
      "headers": [
        { "key": "X-Content-Type-Options", "value": "nosniff" },
        { "key": "X-Frame-Options", "value": "DENY" },
        { "key": "X-XSS-Protection", "value": "1; mode=block" }
      ]
    }
  ]
}
```

**Deploy to Vercel:**
```bash
cd trade-journal-frontend
vercel login
vercel --prod
```

---

## Phase 5: Database Migration (Week 9-10)

### 5.1 Database Schema

**schema.sql:**
```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trades (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    trade_type VARCHAR(10) NOT NULL CHECK (trade_type IN ('BUY', 'SELL')),
    trade_date TIMESTAMP NOT NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_trades ON trades(user_id, trade_date DESC);
CREATE INDEX idx_symbol ON trades(symbol);

CREATE TABLE report_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    report_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('pending', 'processing', 'completed', 'failed')),
    result_url TEXT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_jobs ON report_jobs(user_id, created_at DESC);
CREATE INDEX idx_status ON report_jobs(status);
```

### 5.2 Migration Process

**Step 1: Download Wallet**
```bash
oci db autonomous-database generate-wallet \
  --autonomous-database-id <adb-ocid> \
  --file wallet.zip \
  --password <wallet-password>

unzip wallet.zip -d wallet/
```

**Step 2: Export Local Data**
```bash
pg_dump -h localhost -U postgres -d tradejournal \
  --data-only \
  --inserts \
  --column-inserts \
  > data_export.sql
```

**Step 3: Import to Autonomous DB**
```bash
psql "host=adb.<region>.oraclecloud.com port=1522 dbname=<service_name> user=ADMIN sslmode=require" \
  -f schema.sql

psql "host=adb.<region>.oraclecloud.com port=1522 dbname=<service_name> user=ADMIN sslmode=require" \
  -f data_export.sql
```

---

## Phase 6: CI/CD Pipeline (Week 11)

### 6.1 GitHub Actions Workflow

**.github/workflows/deploy.yml:**
```yaml
name: Deploy Multi-Cloud Application

on:
  push:
    branches: [main, staging]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Run backend tests
        run: |
          cd backend
          mvn clean test

  deploy-functions:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v3

      - name: Setup OCI CLI
        uses: oracle-actions/configure-oci-cli@v1
        with:
          user: ${{ secrets.OCI_CLI_USER }}
          tenancy: ${{ secrets.OCI_CLI_TENANCY }}
          region: us-ashburn-1
          fingerprint: ${{ secrets.OCI_CLI_FINGERPRINT }}
          private_key: ${{ secrets.OCI_CLI_KEY_CONTENT }}

      - name: Deploy functions
        run: |
          cd backend/functions
          ./deploy-all.sh

  deploy-frontend:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Deploy to Vercel
        uses: amondnet/vercel-action@v20
        with:
          vercel-token: ${{ secrets.VERCEL_TOKEN }}
          vercel-org-id: ${{ secrets.VERCEL_ORG_ID }}
          vercel-project-id: ${{ secrets.VERCEL_PROJECT_ID }}
          vercel-args: '--prod'
          working-directory: ./frontend
```

---

## Phase 7: Zero-Downtime Migration (Week 12-13)

### 7.1 Migration Phases

**Week 12:**
- Deploy new infrastructure alongside existing
- Route 10% traffic to new stack (Cloudflare Workers)
- Monitor metrics

**Week 13:**
- Increase to 25%, 50%, 75%, 100%
- Run daily reconciliation
- Full cutover

### 7.2 Traffic Routing (Cloudflare Worker)

```javascript
addEventListener('fetch', event => {
  event.respondWith(handleRequest(event.request))
})

async function handleRequest(request) {
  const url = new URL(request.url)
  const userId = request.headers.get('X-User-ID')

  const migrationPercent = 50 // Gradually increase
  const useNewStack = shouldRouteToNewStack(userId, migrationPercent)

  const backend = useNewStack
    ? 'https://api.yourdomain.com'  // New OCI Functions
    : 'https://old-api.yourdomain.com'  // Old Vert.x

  const backendUrl = new URL(url.pathname + url.search, backend)
  return fetch(backendUrl.toString(), request)
}

function shouldRouteToNewStack(userId, percentage) {
  const hash = simpleHash(userId || 'anonymous')
  return (hash % 100) < percentage
}
```

### 7.3 Rollback Plan

```bash
#!/bin/bash
# rollback.sh

echo "Initiating rollback..."

# Route 100% to old stack
curl -X PUT "https://api.cloudflare.com/client/v4/accounts/$ACCOUNT_ID/workers/scripts/router" \
  -H "Authorization: Bearer $CF_API_TOKEN" \
  --data-binary "@rollback-worker.js"

# Notify team
curl -X POST $SLACK_WEBHOOK \
  -d '{"text": "🔴 ROLLBACK - Traffic routed to old stack"}'
```

---

## Phase 8: Monitoring & Observability (Week 14)

### 8.1 OCI Monitoring

```bash
# Enable function logging
fn config app trade-journal SYSLOG_URL "tcp://logs.papertrailapp.com:12345"

# Create alarms
oci monitoring alarm create \
  --display-name "Function Errors" \
  --namespace "oci_faas" \
  --query "Errors[1m].sum() > 10" \
  --severity "CRITICAL"
```

### 8.2 Vercel Analytics

```typescript
import { Analytics } from '@vercel/analytics/react';
import { SpeedInsights } from '@vercel/speed-insights/next';

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        {children}
        <Analytics />
        <SpeedInsights />
      </body>
    </html>
  );
}
```

### 8.3 Health Checks

```java
public class HealthCheckFunction {
    public String handleRequest() {
        JsonObject health = new JsonObject();
        health.put("database", checkDatabase() ? "healthy" : "unhealthy");
        health.put("redis", checkRedis() ? "healthy" : "unhealthy");
        health.put("storage", checkObjectStorage() ? "healthy" : "unhealthy");
        health.put("timestamp", Instant.now().toString());
        return health.encode();
    }
}
```

---

## Phase 9: Cost Optimization (Week 15)

### 9.1 Free Tier Limits

| Service | Limit | Monitoring |
|---------|-------|------------|
| OCI Functions | 2M calls/month | OCI Console |
| OCI Autonomous DB | 2×20GB, 1 OCPU each | Always free |
| OCI Object Storage | 10GB | Alert at 8GB |
| Vercel Bandwidth | 100GB/month | Dashboard |
| Vercel Build Minutes | 6,000/month | Dashboard |
| Cloudflare | Unlimited | N/A |

### 9.2 Optimization Strategies

**1. Aggressive Caching:**
```typescript
// Next.js ISR (Incremental Static Regeneration)
export const revalidate = 3600; // 1 hour

export async function generateStaticParams() {
  return [
    { slug: 'dashboard' },
    { slug: 'trades' },
  ];
}
```

**2. Function Result Caching:**
```java
public String handleRequest(String input) {
    String cacheKey = "result:" + hashInput(input);

    String cached = redisClient.get(cacheKey);
    if (cached != null) return cached;

    String result = expensiveOperation(input);
    redisClient.setex(cacheKey, 3600, result);

    return result;
}
```

**3. Object Storage Lifecycle:**
```bash
# Delete old reports after 90 days
oci os object-lifecycle-policy put \
  --bucket-name trade-journal-files \
  --items '[{
    "action": "DELETE",
    "is-enabled": true,
    "name": "delete-old-reports",
    "object-name-filter": {
      "inclusion-prefixes": ["reports/"]
    },
    "time-amount": 90,
    "time-unit": "DAYS"
  }]'
```

---

## Summary

### Timeline Overview

| Week | Phase | Activities |
|------|-------|------------|
| 1-2 | Prerequisites | Account setup, tool installation |
| 3-4 | Infrastructure | Terraform provisioning (OCI, Cloudflare) |
| 5-8 | Backend | Convert Vert.x to OCI Functions |
| 7-9 | Frontend | Build Next.js app on Vercel |
| 9-10 | Database | Migrate PostgreSQL to Autonomous DB |
| 11 | CI/CD | GitHub Actions pipelines |
| 12-13 | Migration | Gradual traffic shifting |
| 14 | Monitoring | Setup observability |
| 15 | Optimization | Cost optimization, cleanup |

### Key Benefits

✅ **$0/month cost** (all free tiers)
✅ **Global CDN** via Cloudflare (195+ locations)
✅ **Auto-scaling** with serverless functions
✅ **Long-running support** (1 hour function timeout)
✅ **Modern developer experience** (Git-based deployments)
✅ **Zero vendor lock-in** (multi-cloud architecture)
✅ **Enterprise-grade security** (DDoS, WAF, SSL)

### Critical Files

**Current codebase:**
- `/pom.xml` - Dependencies to migrate
- `/src/main/java/com/zenith/trade/journal/MainVerticle.java` - Main entry point to decompose

**To create:**
- `/infrastructure/terraform/oci/main.tf` - OCI infrastructure
- `/backend/functions/shared/DatabaseClient.java` - Reusable DB client
- `/frontend/src/lib/api/client.ts` - API client
- `/.github/workflows/deploy.yml` - CI/CD pipeline

### Next Steps

1. **Start with Phase 1**: Create OCI, Vercel, Cloudflare accounts
2. **Setup infrastructure**: Run Terraform for OCI resources
3. **Convert first function**: Migrate MainVerticle to CreateTradeFunction
4. **Build frontend**: Create Next.js app
5. **Iterate**: Add more functions, test, deploy

---

**Document Version:** 1.0
**Last Updated:** 2026-02-10
**Migration Status:** Planning Phase
