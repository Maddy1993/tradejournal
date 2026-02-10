# OCI Functions Testing Guide

This document outlines the steps to validate the deployed OCI functions for the Trade Journal application.

## 📋 Prerequisites

- **OCI CLI** installed and configured (`oci setup config`).
- **Fn CLI** installed (`curl -LSs https://raw.githubusercontent.com/fnproject/cli/master/install | sh`).
- **Docker** installed and running.
- **JQ** (optional) for pretty-printing JSON output.

## ⚙️ Environment Setup

Ensure your Fn CLI is configured for the correct context:

```bash
# List contexts
fn list contexts

# Use the Oracle Cloud context
fn use context oracle-cloud

# Verify registry configuration
fn inspect context oracle-cloud
```

## 🚀 Validating Functions

### 1. Health Check
**Function:** `health-check`
**Purpose:** Simple connectivity test.

```bash
# Invoke via Fn CLI
fn invoke trade-journal health-check

# Expected Output:
# {"status":"OK"}
```

### 2. Trades API
**Function:** `trades-api`
**Purpose:** Manage trades (CRUD).

#### A. Get Trades (List)
```bash
# Invoke with GET method (simulated via payload or HTTP trigger)
# Note: Fn invoke sends a direct request. For HTTP handling, we simulate the context or use the endpoint URL.

# Get Endpoint URL
export TRADES_ENDPOINT=$(fn inspect function trade-journal trades-api | jq -r .annotations."fnproject.io/fn/invokeEndpoint")
echo "Endpoint: $TRADES_ENDPOINT"

# Invoke via OCI CLI (Signed Request) or curl if public
# For simplicity with 'fn invoke', we pass input that the function expects if it parses body.
# However, our function checks HTTP method from context. 
# Best way to test HTTP functions is via their Trigger URL.

# 1. Get Trigger URL
export TRADES_TRIGGER=$(oci fn trigger list --function-id <function-ocid> --query "data[0].\"endpoint\"" --raw)

# 2. Curl (if public/auth handled)
curl -X GET $TRADES_TRIGGER/trades \
  -H "X-User-Email: test@zenith.com"
```

**Alternative: Direct Fn Invoke (Debugging)**
If the function implementation allows payload-based method overrides (useful for testing):
```bash
echo '{"method": "GET"}' | fn invoke trade-journal trades-api
```
*(Note: Current implementation relies on HTTPGatewayContext, so `fn invoke` might default to POST or require specific handling)*.

#### B. Create Trade (POST)
```bash
# Payload
cat <<EOF > trade.json
{
  "symbol": "AAPL",
  "action": "BUY",
  "quantity": 10,
  "price": 150.00,
  "tradeDate": "2023-10-27",
  "accountId": "uuid-of-account"
}
EOF

# Invoke
curl -X POST $TRADES_TRIGGER/trades \
  -H "Content-Type: application/json" \
  -H "X-User-Email: test@zenith.com" \
  -d @trade.json
```

### 3. Sync Processor
**Function:** `sync-processor`
**Purpose:** Trigger background sync.

```bash
# Invoke
echo '{"action": "sync"}' | fn invoke trade-journal sync-processor

# Expected Output:
# {"status": "Sync initiated", "user": "test@zenith.com"}
```

## 🔍 Troubleshooting

**View Logs:**
```bash
# Get function ID
fn inspect function trade-journal trades-api

# Query OCI Logs (if configured)
oci logging search search-logs \
  --search-query 'search "<compartment_id>/<log_group_id>/<log_id>" | source = "<function_id>"' \
  --time-start 2023-10-27T00:00:00Z
```

**Common Errors:**
- `502 Bad Gateway`: Function crashed or timed out. Check Docker logs if running locally, or OCI logs.
- `401 Unauthorized`: Missing `X-User-Email` header (mock auth).
