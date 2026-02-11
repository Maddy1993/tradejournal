# OCI Terraform Authentication Setup

## Overview

Terraform uses **API Key Authentication** (not API tokens) to access OCI. This requires:
1. API key pair (private + public)
2. OCI CLI configuration file
3. Terraform variables

---

## Authentication Methods

### Method 1: OCI CLI Configuration (Recommended)

Terraform reads from `~/.oci/config` automatically.

**Pros:**
- ✅ Same config for OCI CLI and Terraform
- ✅ No need to duplicate credentials
- ✅ Easy to manage

**Cons:**
- ❌ Requires local OCI CLI setup

### Method 2: Environment Variables

Export credentials as environment variables.

**Pros:**
- ✅ Good for CI/CD
- ✅ No config file needed

**Cons:**
- ❌ Must set variables every session

### Method 3: Terraform Provider Block

Hardcode credentials in `providers.tf`.

**Pros:**
- ✅ Self-contained

**Cons:**
- ❌ **NOT RECOMMENDED** - credentials in source code
- ❌ Security risk

---

## Step-by-Step Setup (Method 1 - Recommended)

### Step 1: Generate API Key Pair

**If you haven't already:**
```bash
# Create .oci directory
mkdir -p ~/.oci

# Generate private key (2048-bit RSA)
openssl genrsa -out ~/.oci/oci_api_key.pem 2048

# Generate public key from private key
openssl rsa -pubout -in ~/.oci/oci_api_key.pem -out ~/.oci/oci_api_key_public.pem

# Set proper permissions
chmod 600 ~/.oci/oci_api_key.pem
chmod 644 ~/.oci/oci_api_key_public.pem

# Display public key (you'll upload this to OCI)
cat ~/.oci/oci_api_key_public.pem
```

**Output will look like:**
```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
-----END PUBLIC KEY-----
```

---

### Step 2: Upload Public Key to OCI Console

1. **Login to OCI Console:**
   - Go to: https://cloud.oracle.com/
   - Login with your credentials

2. **Navigate to API Keys:**
   - Click your **profile icon** (top right)
   - Click **User Settings**
   - Under **Resources** (left sidebar), click **API Keys**

3. **Add API Key:**
   - Click **Add API Key**
   - Select **Paste Public Key**
   - Copy/paste the content of `~/.oci/oci_api_key_public.pem`
   - Click **Add**

4. **Save Configuration Preview:**
   - OCI will show a configuration file preview
   - **Copy the fingerprint** (looks like: `xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx`)
   - You'll need this for the next step

**Example preview:**
```
[DEFAULT]
user=ocid1.user.oc1..aaaaaaaaXXXXX
fingerprint=12:34:56:78:90:ab:cd:ef:12:34:56:78:90:ab:cd:ef
tenancy=ocid1.tenancy.oc1..aaaaaaaaXXXXX
region=us-sanjose-1
key_file=<path to your private keyfile>
```

---

### Step 3: Get Your User OCID

**Option A: From API Keys page**
- The configuration preview from Step 2 shows your user OCID

**Option B: From Console**
1. Click your profile icon → **User Settings**
2. Copy the **OCID** shown under user information

**Option C: Using OCI CLI (if already configured)**
```bash
oci iam user list --all | jq -r '.data[] | select(.name == "your-username") | .id'
```

---

### Step 4: Create OCI CLI Configuration File

**Create/Edit `~/.oci/config`:**
```bash
cat > ~/.oci/config << 'EOF'
[DEFAULT]
user=ocid1.user.oc1..aaaaaaaaXXXXXXXXXXXX
fingerprint=xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx
tenancy=ocid1.tenancy.oc1..aaaaaaaaoe2titp5eb46nexp2rozoath6jd3w6cbwt4rxkymggjc5tuf5kaq
region=us-sanjose-1
key_file=~/.oci/oci_api_key.pem
EOF

# Set proper permissions
chmod 600 ~/.oci/config
```

**Replace:**
- `user` - Your user OCID (get from Step 3)
- `fingerprint` - The fingerprint from Step 2
- `tenancy` - Your tenancy OCID (already filled with your value)
- `region` - Your region (already filled: us-sanjose-1)
- `key_file` - Path to private key (already filled)

---

### Step 5: Test OCI CLI Configuration

```bash
# Test authentication
oci iam region list --output table

# If successful, you'll see a list of regions
```

**Expected output:**
```
+----------------+
| name           |
+----------------+
| us-ashburn-1   |
| us-phoenix-1   |
| us-sanjose-1   |
| ...            |
+----------------+
```

**If you get an error:**
```bash
# Verify config file
cat ~/.oci/config

# Check private key exists
ls -l ~/.oci/oci_api_key.pem

# Verify fingerprint matches in OCI Console
# Go to: Profile → User Settings → API Keys
```

---

### Step 6: Configure Terraform Variables

**Create `.env.infrastructure`:**
```bash
cat > infrastructure/.env.infrastructure << 'EOF'
# OCI Authentication (Terraform will read from ~/.oci/config)
export TF_VAR_tenancy_ocid="ocid1.tenancy.oc1..aaaaaaaaoe2titp5eb46nexp2rozoath6jd3w6cbwt4rxkymggjc5tuf5kaq"
export TF_VAR_user_ocid="ocid1.user.oc1..aaaaaaaaXXXXXXXXXXXX"  # Replace with your user OCID
export TF_VAR_fingerprint="xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx"  # Replace with your fingerprint
export TF_VAR_private_key_path="~/.oci/oci_api_key.pem"
export TF_VAR_region="us-sanjose-1"
export TF_VAR_compartment_id="ocid1.compartment.oc1..aaaaaaaameyhu7sgwehn47flwdws5ayf4exg34uxut4ajthf6haqwogstzwa"

# Database Configuration (set strong passwords)
export TF_VAR_db_admin_password="YourSecurePassword123!"
export TF_VAR_db_wallet_password="YourWalletPassword123!"

# Cloudflare Configuration (add when ready)
export TF_VAR_cloudflare_api_token="your-cloudflare-token-here"
export TF_VAR_cloudflare_zone_id="your-zone-id-here"
export TF_VAR_domain_name="yourdomain.com"

# Application Configuration
export TF_VAR_app_name="trade-journal"
export TF_VAR_environment="production"
EOF

# Make sure to add to .gitignore
echo "infrastructure/.env.infrastructure" >> .gitignore
```

**⚠️ IMPORTANT:** Replace these values:
- `TF_VAR_user_ocid` - Your user OCID from Step 3
- `TF_VAR_fingerprint` - Your fingerprint from Step 2
- `TF_VAR_db_admin_password` - Choose a strong password (12+ chars, mix of upper/lower/numbers/symbols)
- `TF_VAR_db_wallet_password` - Choose another strong password

---

### Step 7: Test Terraform Authentication

```bash
# Source environment variables
source infrastructure/.env.infrastructure

# Navigate to Terraform directory
cd infrastructure/terraform/oci

# Initialize Terraform
terraform init

# Validate configuration
terraform validate

# Test by running a plan (won't create anything)
terraform plan

# If successful, you'll see:
# Plan: X to add, 0 to change, 0 to destroy.
```

---

## Alternative: Environment Variables Method (CI/CD)

For GitHub Actions or CI/CD pipelines:

```yaml
# .github/workflows/deploy.yml
env:
  TF_VAR_tenancy_ocid: ${{ secrets.OCI_TENANCY_OCID }}
  TF_VAR_user_ocid: ${{ secrets.OCI_USER_OCID }}
  TF_VAR_fingerprint: ${{ secrets.OCI_FINGERPRINT }}
  TF_VAR_region: "us-sanjose-1"
  TF_VAR_compartment_id: ${{ secrets.OCI_COMPARTMENT_ID }}
  # Private key as secret (base64 encoded)
```

**Store private key in GitHub Secrets:**
```bash
# Encode private key
cat ~/.oci/oci_api_key.pem | base64 | pbcopy

# Add as GitHub Secret: OCI_PRIVATE_KEY_BASE64

# In workflow, decode:
- name: Setup OCI Key
  run: |
    mkdir -p ~/.oci
    echo "${{ secrets.OCI_PRIVATE_KEY_BASE64 }}" | base64 -d > ~/.oci/oci_api_key.pem
    chmod 600 ~/.oci/oci_api_key.pem
```

---

## How Terraform Authenticates

### Authentication Flow

```
1. Terraform reads provider configuration (providers.tf)
   ↓
2. Looks for credentials in this order:
   a. Provider block (NOT RECOMMENDED)
   b. Environment variables (TF_VAR_*)
   c. ~/.oci/config file
   ↓
3. Uses private key + fingerprint to sign API requests
   ↓
4. OCI verifies signature using stored public key
   ↓
5. API request authorized
```

### What Terraform Needs

| Item | Purpose | Where It Comes From |
|------|---------|---------------------|
| **Tenancy OCID** | Identifies your OCI account | You provided: `ocid1.tenancy.oc1..aaaaaaaaoe2titp5eb46nexp2rozoath6jd3w6cbwt4rxkymggjc5tuf5kaq` |
| **User OCID** | Identifies you as a user | Get from: Profile → User Settings |
| **Fingerprint** | Identifies which API key to use | Get from: API Keys page after upload |
| **Private Key** | Signs API requests | Generated: `~/.oci/oci_api_key.pem` |
| **Region** | Where to create resources | You provided: `us-sanjose-1` |
| **Compartment OCID** | Where to organize resources | You provided: `ocid1.compartment.oc1..aaaaaaaameyhu7sgwehn47flwdws5ayf4exg34uxut4ajthf6haqwogstzwa` |

---

## Security Best Practices

✅ **Never commit private keys** - Add `*.pem` to `.gitignore`
✅ **Use strong permissions** - `chmod 600` on private keys
✅ **Rotate keys regularly** - Recommended every 90 days
✅ **Use separate keys for CI/CD** - Don't share personal keys
✅ **Enable MFA** - Add extra security to OCI console
✅ **Review API key usage** - Check audit logs regularly

---

## Troubleshooting

### Error: "Service error: NotAuthenticated"

**Cause:** Terraform can't authenticate with OCI

**Solutions:**
```bash
# 1. Verify config file exists
cat ~/.oci/config

# 2. Check private key permissions
ls -l ~/.oci/oci_api_key.pem  # Should be -rw------- (600)

# 3. Verify fingerprint matches
# Go to OCI Console → Profile → User Settings → API Keys
# Compare with fingerprint in config

# 4. Test with OCI CLI
oci iam region list
```

### Error: "Invalid private key format"

**Cause:** Private key is corrupted or wrong format

**Solution:**
```bash
# Regenerate key pair
openssl genrsa -out ~/.oci/oci_api_key.pem 2048
openssl rsa -pubout -in ~/.oci/oci_api_key.pem -out ~/.oci/oci_api_key_public.pem

# Re-upload public key to OCI Console
```

### Error: "Compartment not found"

**Cause:** Compartment OCID is incorrect

**Solution:**
```bash
# List compartments
oci iam compartment list --all | jq -r '.data[] | {name: .name, id: .id}'

# Verify your compartment OCID
```

### Error: "Region not found"

**Cause:** Region name is incorrect

**Solution:**
```bash
# List available regions
oci iam region list --output table

# Use the exact name (e.g., us-sanjose-1, not San Jose)
```

---

## Quick Start Checklist

Before running Terraform:

- [ ] API key pair generated (`~/.oci/oci_api_key.pem` and `~/.oci/oci_api_key_public.pem`)
- [ ] Public key uploaded to OCI Console
- [ ] User OCID obtained
- [ ] Fingerprint obtained
- [ ] `~/.oci/config` file created with correct values
- [ ] OCI CLI test successful (`oci iam region list`)
- [ ] `.env.infrastructure` file created with all variables
- [ ] Environment variables sourced (`source infrastructure/.env.infrastructure`)
- [ ] Terraform initialized (`terraform init`)
- [ ] Terraform validation passed (`terraform validate`)

---

## Next Steps

Once authentication is working:

1. **Run Terraform plan:**
   ```bash
   cd infrastructure/terraform/oci
   terraform plan -out=tfplan
   ```

2. **Review the plan** - Verify it will create the expected resources

3. **Apply infrastructure:**
   ```bash
   terraform apply tfplan
   ```

4. **Save outputs:**
   ```bash
   terraform output -json > ../configs/oci-outputs.json
   ```

---

## Your OCI Configuration Summary

Based on your details:

```
Tenancy OCID: ocid1.tenancy.oc1..aaaaaaaaoe2titp5eb46nexp2rozoath6jd3w6cbwt4rxkymggjc5tuf5kaq
Region: us-sanjose-1
Compartment: ocid1.compartment.oc1..aaaaaaaameyhu7sgwehn47flwdws5ayf4exg34uxut4ajthf6haqwogstzwa

Status: ✅ Tenancy, Region, Compartment configured
Needed: User OCID, Fingerprint (get from OCI Console)
```

---

## Additional Resources

- [OCI Authentication Guide](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm)
- [Terraform OCI Provider](https://registry.terraform.io/providers/oracle/oci/latest/docs)
- [OCI CLI Configuration](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/cliinstall.htm)

---

**Last Updated:** 2026-02-10
**Status:** Ready for Setup
