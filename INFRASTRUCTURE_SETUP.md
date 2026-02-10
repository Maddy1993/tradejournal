# Infrastructure Setup Guide

**Trade Journal Application - Multi-Cloud Infrastructure Provisioning**

---

## Prerequisites Checklist

Before starting, ensure you have:

- ✅ Oracle Cloud Infrastructure (OCI) account created
- ✅ Vercel account created
- ✅ Cloudflare account created
- ✅ GitHub repository created
- ✅ Domain name (or use provided subdomain)

---

## Phase 1: Local Environment Setup

### Step 1.1: Install Required Tools

**Install OCI CLI:**
```bash
# Install OCI CLI
bash -c "$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)"

# Verify installation
oci --version
# Expected: oci-cli 3.x.x or higher
```

**Install Terraform:**
```bash
# macOS
brew install terraform

# Linux
wget https://releases.hashicorp.com/terraform/1.7.0/terraform_1.7.0_linux_amd64.zip
unzip terraform_1.7.0_linux_amd64.zip
sudo mv terraform /usr/local/bin/

# Verify
terraform --version
# Expected: Terraform v1.7.0 or higher
```

**Install Fn Project CLI (for OCI Functions):**
```bash
curl -LSs https://raw.githubusercontent.com/fnproject/cli/master/install | sh

# Verify
fn version
# Expected: fn version 0.6.x or higher
```

**Install Vercel CLI:**
```bash
npm install -g vercel

# Login
vercel login

# Verify
vercel --version
```

**Install Cloudflare Terraform Provider Dependencies:**
```bash
# Install jq for JSON processing
# macOS
brew install jq

# Linux
sudo apt-get install jq
```

---

### Step 1.2: Configure OCI CLI

**Generate API Key Pair:**
```bash
# Create .oci directory
mkdir -p ~/.oci

# Generate API keys
openssl genrsa -out ~/.oci/oci_api_key.pem 2048
openssl rsa -pubout -in ~/.oci/oci_api_key.pem -out ~/.oci/oci_api_key_public.pem

# Set proper permissions
chmod 600 ~/.oci/oci_api_key.pem
chmod 644 ~/.oci/oci_api_key_public.pem

# Print public key (you'll upload this to OCI)
cat ~/.oci/oci_api_key_public.pem
```

**Upload Public Key to OCI:**
1. Login to OCI Console: https://cloud.oracle.com/
2. Click your profile icon → **User Settings**
3. Under **Resources**, click **API Keys**
4. Click **Add API Key**
5. Select **Paste Public Key**
6. Paste the content from `oci_api_key_public.pem`
7. Click **Add**
8. **IMPORTANT:** Copy the configuration file preview shown - you'll need these values

**Configure OCI CLI:**
```bash
# Run configuration wizard
oci setup config

# When prompted, enter:
# - Location for config: ~/.oci/config (press Enter for default)
# - User OCID: (from configuration preview)
# - Tenancy OCID: (from configuration preview)
# - Region: (e.g., us-ashburn-1)
# - Private key path: ~/.oci/oci_api_key.pem

# Test configuration
oci iam region list --output table
```

**Get Critical OCI Values:**
```bash
# Get your tenancy OCID (save this)
oci iam tenancy get --tenancy-id <your-tenancy-ocid> | jq -r '.data.id'

# Get your compartment OCID (root compartment)
oci iam compartment list --all | jq -r '.data[] | select(.name == "root") | .id'

# Get your user OCID
oci iam user list | jq -r '.data[0].id'

# Get available regions
oci iam region-subscription list | jq -r '.data[].["region-name"]'

# Save these values - you'll need them for Terraform
```

---

### Step 1.3: Configure Cloudflare API

**Get Cloudflare API Token:**
1. Login to Cloudflare Dashboard: https://dash.cloudflare.com/
2. Go to **My Profile** → **API Tokens**
3. Click **Create Token**
4. Use **Edit zone DNS** template
5. Configure:
   - Permissions: `Zone.Zone Settings`, `Zone.DNS` (Edit)
   - Zone Resources: Include → Specific zone → Select your domain
6. Click **Continue to summary**
7. Click **Create Token**
8. **IMPORTANT:** Copy the token immediately (shown only once)

**Get Cloudflare Account & Zone IDs:**
```bash
# Set your API token as environment variable
export CLOUDFLARE_API_TOKEN="your-token-here"

# Get Account ID
curl -X GET "https://api.cloudflare.com/client/v4/accounts" \
  -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
  -H "Content-Type: application/json" | jq -r '.result[0].id'

# Get Zone ID (for your domain)
curl -X GET "https://api.cloudflare.com/client/v4/zones?name=yourdomain.com" \
  -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
  -H "Content-Type: application/json" | jq -r '.result[0].id'

# Save these values
```

---

### Step 1.4: Configure Vercel

**Get Vercel Tokens:**
```bash
# Login
vercel login

# Create project (we'll do this later, but get org ID now)
# Go to https://vercel.com/account/tokens
# Create a new token with full access
# Save the token

# Get your Vercel Org ID
vercel teams ls
# Copy the ID (starts with "team_")
```

---

## Phase 2: Project Structure Setup

### Step 2.1: Create Directory Structure

```bash
# From your project root
cd /home/user/tradejournal

# Create infrastructure directories
mkdir -p infrastructure/terraform/oci
mkdir -p infrastructure/terraform/cloudflare
mkdir -p infrastructure/scripts
mkdir -p infrastructure/configs

# Create backend directories (for later)
mkdir -p backend/functions/trades
mkdir -p backend/functions/reports
mkdir -p backend/functions/reconciliation
mkdir -p backend/functions/shared

# Create frontend directory (for later)
mkdir -p frontend

# Verify structure
tree -L 3 infrastructure/
```

Expected structure:
```
infrastructure/
├── terraform/
│   ├── oci/
│   └── cloudflare/
├── scripts/
└── configs/
```

---

### Step 2.2: Create Environment Configuration

**Create `.env.infrastructure`:**
```bash
cat > infrastructure/.env.infrastructure << 'EOF'
# OCI Configuration
export TF_VAR_tenancy_ocid="ocid1.tenancy.oc1..aaaaaaaaXXXXXXXX"
export TF_VAR_user_ocid="ocid1.user.oc1..aaaaaaaaXXXXXXXX"
export TF_VAR_fingerprint="xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx:xx"
export TF_VAR_private_key_path="~/.oci/oci_api_key.pem"
export TF_VAR_region="us-ashburn-1"
export TF_VAR_compartment_id="ocid1.compartment.oc1..aaaaaaaaXXXXXXXX"

# Database Configuration
export TF_VAR_db_admin_password="SecurePassword123!"
export TF_VAR_db_wallet_password="WalletPassword123!"

# Cloudflare Configuration
export TF_VAR_cloudflare_api_token="your-cloudflare-token"
export TF_VAR_cloudflare_zone_id="your-zone-id"
export TF_VAR_domain_name="yourdomain.com"

# Vercel Configuration
export TF_VAR_vercel_token="your-vercel-token"
export TF_VAR_vercel_org_id="team_XXXXX"

# Application Configuration
export TF_VAR_app_name="trade-journal"
export TF_VAR_environment="production"
EOF

# Source the environment
source infrastructure/.env.infrastructure

# Add to .gitignore
echo "infrastructure/.env.infrastructure" >> .gitignore
```

**⚠️ IMPORTANT: Replace placeholder values with your actual credentials**

---

## Phase 3: Oracle Cloud Infrastructure (OCI) Terraform

### Step 3.1: Create Terraform Provider Configuration

**`infrastructure/terraform/oci/providers.tf`:**
```bash
cat > infrastructure/terraform/oci/providers.tf << 'EOF'
terraform {
  required_version = ">= 1.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 5.0"
    }
  }
}

provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}
EOF
```

---

### Step 3.2: Create Variables File

**`infrastructure/terraform/oci/variables.tf`:**
```bash
cat > infrastructure/terraform/oci/variables.tf << 'EOF'
# OCI Authentication
variable "tenancy_ocid" {
  description = "OCI Tenancy OCID"
  type        = string
}

variable "user_ocid" {
  description = "OCI User OCID"
  type        = string
}

variable "fingerprint" {
  description = "OCI API Key Fingerprint"
  type        = string
}

variable "private_key_path" {
  description = "Path to OCI private key"
  type        = string
}

variable "region" {
  description = "OCI Region"
  type        = string
  default     = "us-ashburn-1"
}

variable "compartment_id" {
  description = "OCI Compartment OCID"
  type        = string
}

# Application Configuration
variable "app_name" {
  description = "Application name"
  type        = string
  default     = "trade-journal"
}

variable "environment" {
  description = "Environment (dev, staging, production)"
  type        = string
  default     = "production"
}

# Database Configuration
variable "db_admin_password" {
  description = "Admin password for Autonomous Database"
  type        = string
  sensitive   = true
}

variable "db_wallet_password" {
  description = "Password for database wallet"
  type        = string
  sensitive   = true
}

# Network Configuration
variable "vcn_cidr_block" {
  description = "CIDR block for VCN"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR block for public subnet"
  type        = string
  default     = "10.0.1.0/24"
}

variable "private_subnet_cidr" {
  description = "CIDR block for private subnet"
  type        = string
  default     = "10.0.2.0/24"
}
EOF
```

---

### Step 3.3: Create VCN (Virtual Cloud Network)

**`infrastructure/terraform/oci/vcn.tf`:**
```bash
cat > infrastructure/terraform/oci/vcn.tf << 'EOF'
# Get availability domains
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

# Create VCN
resource "oci_core_vcn" "trade_journal_vcn" {
  compartment_id = var.compartment_id
  display_name   = "${var.app_name}-vcn"
  cidr_blocks    = [var.vcn_cidr_block]
  dns_label      = replace(var.app_name, "-", "")
}

# Create Internet Gateway
resource "oci_core_internet_gateway" "trade_journal_igw" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-igw"
  enabled        = true
}

# Create NAT Gateway (for private subnet)
resource "oci_core_nat_gateway" "trade_journal_nat" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-nat"
}

# Create Service Gateway (for OCI services)
data "oci_core_services" "all_services" {
  filter {
    name   = "name"
    values = ["All .* Services In Oracle Services Network"]
    regex  = true
  }
}

resource "oci_core_service_gateway" "trade_journal_sgw" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-sgw"

  services {
    service_id = data.oci_core_services.all_services.services[0].id
  }
}

# Route Table for Public Subnet
resource "oci_core_route_table" "public_route_table" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-public-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_internet_gateway.trade_journal_igw.id
  }
}

# Route Table for Private Subnet (Functions)
resource "oci_core_route_table" "private_route_table" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-private-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_nat_gateway.trade_journal_nat.id
  }

  route_rules {
    destination       = data.oci_core_services.all_services.services[0].cidr_block
    network_entity_id = oci_core_service_gateway.trade_journal_sgw.id
  }
}

# Security List for Public Subnet
resource "oci_core_security_list" "public_security_list" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-public-sl"

  # Allow inbound HTTPS
  ingress_security_rules {
    protocol    = "6" # TCP
    source      = "0.0.0.0/0"
    description = "Allow HTTPS"

    tcp_options {
      min = 443
      max = 443
    }
  }

  # Allow inbound HTTP
  ingress_security_rules {
    protocol    = "6" # TCP
    source      = "0.0.0.0/0"
    description = "Allow HTTP"

    tcp_options {
      min = 80
      max = 80
    }
  }

  # Allow all outbound
  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
    description = "Allow all outbound"
  }
}

# Security List for Private Subnet (Functions)
resource "oci_core_security_list" "private_security_list" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-private-sl"

  # Allow inbound from VCN
  ingress_security_rules {
    protocol    = "all"
    source      = var.vcn_cidr_block
    description = "Allow traffic from VCN"
  }

  # Allow all outbound
  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
    description = "Allow all outbound"
  }

  # Allow outbound to Oracle Services
  egress_security_rules {
    protocol    = "6" # TCP
    destination = data.oci_core_services.all_services.services[0].cidr_block
    description = "Allow access to Oracle Services"

    tcp_options {
      min = 443
      max = 443
    }
  }
}

# Public Subnet (for API Gateway)
resource "oci_core_subnet" "public_subnet" {
  compartment_id    = var.compartment_id
  vcn_id            = oci_core_vcn.trade_journal_vcn.id
  cidr_block        = var.public_subnet_cidr
  display_name      = "${var.app_name}-public-subnet"
  dns_label         = "public"
  route_table_id    = oci_core_route_table.public_route_table.id
  security_list_ids = [oci_core_security_list.public_security_list.id]

  prohibit_public_ip_on_vnic = false
}

# Private Subnet (for Functions)
resource "oci_core_subnet" "functions_subnet" {
  compartment_id    = var.compartment_id
  vcn_id            = oci_core_vcn.trade_journal_vcn.id
  cidr_block        = var.private_subnet_cidr
  display_name      = "${var.app_name}-functions-subnet"
  dns_label         = "functions"
  route_table_id    = oci_core_route_table.private_route_table.id
  security_list_ids = [oci_core_security_list.private_security_list.id]

  prohibit_public_ip_on_vnic = true
}
EOF
```

---

### Step 3.4: Create Autonomous Database

**`infrastructure/terraform/oci/autonomous-db.tf`:**
```bash
cat > infrastructure/terraform/oci/autonomous-db.tf << 'EOF'
# Autonomous Database
resource "oci_database_autonomous_database" "trade_journal_db" {
  compartment_id           = var.compartment_id
  db_name                  = "${replace(var.app_name, "-", "")}db"
  display_name            = "${var.app_name}-database"
  admin_password          = var.db_admin_password

  # Free tier configuration
  cpu_core_count          = 1
  data_storage_size_in_tbs = 1
  db_version              = "19c"
  is_free_tier            = true
  license_model           = "LICENSE_INCLUDED"
  is_auto_scaling_enabled = false

  # Database workload
  db_workload = "OLTP"

  # Network access
  whitelisted_ips = ["0.0.0.0/0"] # Restrict this in production

  # Auto-renewable
  is_mtls_connection_required = false
}

# Generate wallet
resource "oci_database_autonomous_database_wallet" "trade_journal_wallet" {
  autonomous_database_id = oci_database_autonomous_database.trade_journal_db.id
  password              = var.db_wallet_password
  base64_encode_content = true

  generate_type = "SINGLE"
}

# Save wallet to file
resource "local_file" "wallet_file" {
  content_base64 = oci_database_autonomous_database_wallet.trade_journal_wallet.content
  filename       = "${path.module}/../configs/wallet.zip"
  file_permission = "0600"
}
EOF
```

---

### Step 3.5: Create Object Storage

**`infrastructure/terraform/oci/object-storage.tf`:**
```bash
cat > infrastructure/terraform/oci/object-storage.tf << 'EOF'
# Get Object Storage namespace
data "oci_objectstorage_namespace" "ns" {
  compartment_id = var.compartment_id
}

# Create bucket for trade journal files
resource "oci_objectstorage_bucket" "trade_journal_storage" {
  compartment_id = var.compartment_id
  namespace      = data.oci_objectstorage_namespace.ns.namespace
  name           = "${var.app_name}-files"
  access_type    = "NoPublicAccess"

  versioning     = "Enabled"

  storage_tier   = "Standard"
}

# Create bucket for reports
resource "oci_objectstorage_bucket" "reports_storage" {
  compartment_id = var.compartment_id
  namespace      = data.oci_objectstorage_namespace.ns.namespace
  name           = "${var.app_name}-reports"
  access_type    = "NoPublicAccess"

  versioning     = "Enabled"

  storage_tier   = "Standard"
}

# Lifecycle policy for old reports (delete after 90 days)
resource "oci_objectstorage_object_lifecycle_policy" "reports_lifecycle" {
  namespace = data.oci_objectstorage_namespace.ns.namespace
  bucket    = oci_objectstorage_bucket.reports_storage.name

  rules {
    action      = "DELETE"
    is_enabled  = true
    name        = "delete-old-reports"

    object_name_filter {
      inclusion_prefixes = ["reports/"]
    }

    time_amount = 90
    time_unit   = "DAYS"
  }
}
EOF
```

---

### Step 3.6: Create Functions Application

**`infrastructure/terraform/oci/functions.tf`:**
```bash
cat > infrastructure/terraform/oci/functions.tf << 'EOF'
# Functions Application
resource "oci_functions_application" "trade_journal_app" {
  compartment_id = var.compartment_id
  display_name   = var.app_name
  subnet_ids     = [oci_core_subnet.functions_subnet.id]

  config = {
    # Database connection
    DB_CONNECTION_STRING     = oci_database_autonomous_database.trade_journal_db.connection_urls[0].apex_url
    DB_NAME                  = oci_database_autonomous_database.trade_journal_db.db_name
    DB_USER                  = "ADMIN"

    # Object Storage
    OBJECT_STORAGE_NAMESPACE = data.oci_objectstorage_namespace.ns.namespace
    OBJECT_STORAGE_BUCKET    = oci_objectstorage_bucket.trade_journal_storage.name
    REPORTS_BUCKET           = oci_objectstorage_bucket.reports_storage.name

    # Application settings
    APP_NAME                 = var.app_name
    ENVIRONMENT              = var.environment
  }

  # Network configuration
  network_security_group_ids = []

  # Logging
  syslog_url = ""

  # Tracing
  trace_config {
    is_enabled = true
  }
}
EOF
```

---

### Step 3.7: Create API Gateway

**`infrastructure/terraform/oci/api-gateway.tf`:**
```bash
cat > infrastructure/terraform/oci/api-gateway.tf << 'EOF'
# API Gateway
resource "oci_apigateway_gateway" "trade_journal_gateway" {
  compartment_id = var.compartment_id
  endpoint_type  = "PUBLIC"
  subnet_id      = oci_core_subnet.public_subnet.id

  display_name = "${var.app_name}-gateway"
}

# API Deployment (we'll configure routes after deploying functions)
resource "oci_apigateway_deployment" "trade_journal_deployment" {
  compartment_id = var.compartment_id
  gateway_id     = oci_apigateway_gateway.trade_journal_gateway.id
  path_prefix    = "/api/v1"

  display_name = "${var.app_name}-deployment"

  specification {
    request_policies {
      cors {
        allowed_origins = ["*"] # Restrict in production
        allowed_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
        allowed_headers = ["*"]
        exposed_headers = ["*"]
        is_allow_credentials_enabled = false
        max_age_in_seconds = 3600
      }

      rate_limiting {
        rate_in_requests_per_second = 10
        rate_key                    = "CLIENT_IP"
      }
    }

    # We'll add routes dynamically after deploying functions
    routes {
      path    = "/health"
      methods = ["GET"]

      backend {
        type = "HTTP_BACKEND"
        url  = "https://httpbin.org/status/200"
      }
    }
  }
}
EOF
```

---

### Step 3.8: Create Outputs

**`infrastructure/terraform/oci/outputs.tf`:**
```bash
cat > infrastructure/terraform/oci/outputs.tf << 'EOF'
# VCN Outputs
output "vcn_id" {
  description = "VCN OCID"
  value       = oci_core_vcn.trade_journal_vcn.id
}

output "public_subnet_id" {
  description = "Public Subnet OCID"
  value       = oci_core_subnet.public_subnet.id
}

output "functions_subnet_id" {
  description = "Functions Subnet OCID"
  value       = oci_core_subnet.functions_subnet.id
}

# Database Outputs
output "autonomous_database_id" {
  description = "Autonomous Database OCID"
  value       = oci_database_autonomous_database.trade_journal_db.id
}

output "database_connection_string" {
  description = "Database connection string"
  value       = oci_database_autonomous_database.trade_journal_db.connection_strings[0].profiles[0].value
  sensitive   = true
}

output "database_name" {
  description = "Database name"
  value       = oci_database_autonomous_database.trade_journal_db.db_name
}

# Object Storage Outputs
output "object_storage_namespace" {
  description = "Object Storage namespace"
  value       = data.oci_objectstorage_namespace.ns.namespace
}

output "storage_bucket_name" {
  description = "Main storage bucket name"
  value       = oci_objectstorage_bucket.trade_journal_storage.name
}

output "reports_bucket_name" {
  description = "Reports bucket name"
  value       = oci_objectstorage_bucket.reports_storage.name
}

# Functions Outputs
output "functions_application_id" {
  description = "Functions Application OCID"
  value       = oci_functions_application.trade_journal_app.id
}

output "functions_application_name" {
  description = "Functions Application name"
  value       = oci_functions_application.trade_journal_app.display_name
}

# API Gateway Outputs
output "api_gateway_id" {
  description = "API Gateway OCID"
  value       = oci_apigateway_gateway.trade_journal_gateway.id
}

output "api_gateway_hostname" {
  description = "API Gateway hostname"
  value       = oci_apigateway_gateway.trade_journal_gateway.hostname
}

output "api_gateway_endpoint" {
  description = "API Gateway endpoint URL"
  value       = "https://${oci_apigateway_gateway.trade_journal_gateway.hostname}"
}

output "api_deployment_endpoint" {
  description = "Full API deployment endpoint"
  value       = "https://${oci_apigateway_gateway.trade_journal_gateway.hostname}${oci_apigateway_deployment.trade_journal_deployment.path_prefix}"
}

# Wallet Output
output "wallet_file_path" {
  description = "Path to database wallet file"
  value       = local_file.wallet_file.filename
}
EOF
```

---

### Step 3.9: Create Main Terraform File

**`infrastructure/terraform/oci/main.tf`:**
```bash
cat > infrastructure/terraform/oci/main.tf << 'EOF'
# Main Terraform configuration for OCI infrastructure

terraform {
  backend "local" {
    path = "terraform.tfstate"
  }
}

# Tags for all resources
locals {
  common_tags = {
    Application = var.app_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    CreatedDate = timestamp()
  }
}
EOF
```

---

## Phase 4: Cloudflare Terraform Configuration

### Step 4.1: Create Cloudflare Provider

**`infrastructure/terraform/cloudflare/providers.tf`:**
```bash
cat > infrastructure/terraform/cloudflare/providers.tf << 'EOF'
terraform {
  required_version = ">= 1.0"

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4.0"
    }
  }
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}
EOF
```

---

### Step 4.2: Create Cloudflare Variables

**`infrastructure/terraform/cloudflare/variables.tf`:**
```bash
cat > infrastructure/terraform/cloudflare/variables.tf << 'EOF'
variable "cloudflare_api_token" {
  description = "Cloudflare API Token"
  type        = string
  sensitive   = true
}

variable "cloudflare_zone_id" {
  description = "Cloudflare Zone ID"
  type        = string
}

variable "domain_name" {
  description = "Domain name"
  type        = string
}

variable "oci_api_gateway_hostname" {
  description = "OCI API Gateway hostname (from OCI Terraform output)"
  type        = string
}

variable "vercel_deployment_url" {
  description = "Vercel deployment URL (e.g., your-app.vercel.app)"
  type        = string
  default     = "your-app.vercel.app"
}
EOF
```

---

### Step 4.3: Create DNS Records

**`infrastructure/terraform/cloudflare/dns.tf`:**
```bash
cat > infrastructure/terraform/cloudflare/dns.tf << 'EOF'
# DNS Record for Frontend (Vercel)
resource "cloudflare_record" "frontend" {
  zone_id = var.cloudflare_zone_id
  name    = "@" # Root domain
  value   = "cname.vercel-dns.com"
  type    = "CNAME"
  proxied = true
  ttl     = 1 # Auto (proxied)

  comment = "Frontend hosted on Vercel"
}

# DNS Record for API (OCI API Gateway)
resource "cloudflare_record" "api" {
  zone_id = var.cloudflare_zone_id
  name    = "api"
  value   = var.oci_api_gateway_hostname
  type    = "CNAME"
  proxied = true
  ttl     = 1 # Auto (proxied)

  comment = "Backend API on OCI Functions via API Gateway"
}

# DNS Record for www subdomain
resource "cloudflare_record" "www" {
  zone_id = var.cloudflare_zone_id
  name    = "www"
  value   = var.domain_name
  type    = "CNAME"
  proxied = true
  ttl     = 1

  comment = "WWW redirect to root"
}
EOF
```

---

### Step 4.4: Create Page Rules

**`infrastructure/terraform/cloudflare/page-rules.tf`:**
```bash
cat > infrastructure/terraform/cloudflare/page-rules.tf << 'EOF'
# Cache API responses for static content
resource "cloudflare_page_rule" "api_cache_static" {
  zone_id = var.cloudflare_zone_id
  target  = "api.${var.domain_name}/static/*"

  priority = 1

  actions {
    cache_level = "cache_everything"
    edge_cache_ttl = 7200 # 2 hours
  }
}

# Cache API responses for reports (long-running operations)
resource "cloudflare_page_rule" "api_cache_reports" {
  zone_id = var.cloudflare_zone_id
  target  = "api.${var.domain_name}/api/v1/reports/download/*"

  priority = 2

  actions {
    cache_level = "cache_everything"
    edge_cache_ttl = 86400 # 24 hours
  }
}

# Always use HTTPS
resource "cloudflare_page_rule" "force_https" {
  zone_id = var.cloudflare_zone_id
  target  = "*${var.domain_name}/*"

  priority = 3

  actions {
    always_use_https = true
  }
}

# WWW redirect
resource "cloudflare_page_rule" "www_redirect" {
  zone_id = var.cloudflare_zone_id
  target  = "www.${var.domain_name}/*"

  priority = 4

  actions {
    forwarding_url {
      url         = "https://${var.domain_name}/$1"
      status_code = 301
    }
  }
}
EOF
```

---

### Step 4.5: Create Firewall Rules

**`infrastructure/terraform/cloudflare/firewall.tf`:**
```bash
cat > infrastructure/terraform/cloudflare/firewall.tf << 'EOF'
# Rate limiting for API
resource "cloudflare_rate_limit" "api_rate_limit" {
  zone_id = var.cloudflare_zone_id

  threshold  = 100
  period     = 60

  match {
    request {
      url_pattern = "api.${var.domain_name}/*"
    }
  }

  action {
    mode    = "challenge"
    timeout = 60
  }

  description = "Rate limit API requests to 100 per minute"
}

# Block common threats
resource "cloudflare_filter" "block_threats" {
  zone_id     = var.cloudflare_zone_id
  description = "Block known threats"
  expression  = "(cf.threat_score gt 14)"
}

resource "cloudflare_firewall_rule" "block_threats_rule" {
  zone_id     = var.cloudflare_zone_id
  description = "Block requests with high threat scores"
  filter_id   = cloudflare_filter.block_threats.id
  action      = "block"
  priority    = 1
}
EOF
```

---

### Step 4.6: Create Cloudflare Outputs

**`infrastructure/terraform/cloudflare/outputs.tf`:**
```bash
cat > infrastructure/terraform/cloudflare/outputs.tf << 'EOF'
output "frontend_url" {
  description = "Frontend URL"
  value       = "https://${var.domain_name}"
}

output "api_url" {
  description = "API URL"
  value       = "https://api.${var.domain_name}"
}

output "nameservers" {
  description = "Cloudflare nameservers (update at your domain registrar)"
  value       = "Check Cloudflare dashboard for nameservers"
}
EOF
```

---

## Phase 5: Deployment

### Step 5.1: Initialize and Deploy OCI Infrastructure

```bash
# Source environment variables
source infrastructure/.env.infrastructure

# Navigate to OCI Terraform directory
cd infrastructure/terraform/oci

# Initialize Terraform
terraform init

# Validate configuration
terraform validate

# Format Terraform files
terraform fmt

# Plan deployment (review carefully)
terraform plan -out=tfplan

# Apply infrastructure (this takes 10-15 minutes)
terraform apply tfplan

# Save outputs to file
terraform output -json > ../configs/oci-outputs.json
terraform output api_gateway_hostname > ../configs/api-gateway-hostname.txt

# View important outputs
echo "=== OCI Infrastructure Deployed ==="
echo "API Gateway Endpoint: $(terraform output -raw api_deployment_endpoint)"
echo "Database Name: $(terraform output -raw database_name)"
echo "Functions App: $(terraform output -raw functions_application_name)"
echo "Object Storage Namespace: $(terraform output -raw object_storage_namespace)"
```

**Expected output:**
```
Apply complete! Resources: 25 added, 0 changed, 0 destroyed.

Outputs:

api_deployment_endpoint = "https://xxxxxx.apigateway.us-ashburn-1.oci.customer-oci.com/api/v1"
api_gateway_hostname = "xxxxxx.apigateway.us-ashburn-1.oci.customer-oci.com"
...
```

---

### Step 5.2: Deploy Cloudflare Configuration

```bash
# Navigate to Cloudflare Terraform directory
cd ../cloudflare

# Get OCI API Gateway hostname
export TF_VAR_oci_api_gateway_hostname=$(cat ../configs/api-gateway-hostname.txt)

# Initialize Terraform
terraform init

# Plan deployment
terraform plan -out=tfplan

# Apply Cloudflare configuration
terraform apply tfplan

# Save outputs
terraform output -json > ../configs/cloudflare-outputs.json

echo "=== Cloudflare Configuration Deployed ==="
echo "Frontend URL: $(terraform output -raw frontend_url)"
echo "API URL: $(terraform output -raw api_url)"
```

---

### Step 5.3: Verify Infrastructure

**Create verification script:**
```bash
cat > infrastructure/scripts/verify-infrastructure.sh << 'EOF'
#!/bin/bash
set -e

echo "=== Infrastructure Verification ==="
echo ""

# OCI Verification
echo "1. Verifying OCI Resources..."
cd infrastructure/terraform/oci

# Check VCN
vcn_id=$(terraform output -raw vcn_id)
oci network vcn get --vcn-id "$vcn_id" > /dev/null && echo "✅ VCN exists"

# Check Database
db_id=$(terraform output -raw autonomous_database_id)
oci db autonomous-database get --autonomous-database-id "$db_id" | jq -r '.data."lifecycle-state"'
echo "✅ Database state verified"

# Check Functions App
app_id=$(terraform output -raw functions_application_id)
oci fn application get --application-id "$app_id" > /dev/null && echo "✅ Functions app exists"

# Check API Gateway
gw_id=$(terraform output -raw api_gateway_id)
oci api-gateway gateway get --gateway-id "$gw_id" | jq -r '.data."lifecycle-state"'
echo "✅ API Gateway state verified"

# Test API Gateway endpoint
api_endpoint=$(terraform output -raw api_deployment_endpoint)
echo "Testing API Gateway health check..."
curl -s "$api_endpoint/health" && echo "✅ API Gateway responding"

echo ""
echo "2. Verifying Cloudflare Configuration..."
cd ../cloudflare

# Verify DNS records
echo "✅ Cloudflare DNS configured (verify manually in dashboard)"

echo ""
echo "=== Verification Complete ==="
echo ""
echo "Next steps:"
echo "1. Update nameservers at your domain registrar to Cloudflare nameservers"
echo "2. Deploy backend functions (see FUNCTIONS_DEPLOYMENT.md)"
echo "3. Deploy frontend to Vercel (see FRONTEND_DEPLOYMENT.md)"
EOF

chmod +x infrastructure/scripts/verify-infrastructure.sh
./infrastructure/scripts/verify-infrastructure.sh
```

---

### Step 5.4: Extract Database Wallet

```bash
# Extract wallet for local development
cd infrastructure/terraform/oci
unzip -o ../configs/wallet.zip -d ../configs/wallet/

echo "✅ Database wallet extracted to infrastructure/configs/wallet/"
```

---

## Phase 6: Post-Deployment Configuration

### Step 6.1: Configure Fn CLI for OCI Functions

```bash
# Create Fn context for OCI
fn create context oracle-cloud --provider oracle

# Configure context
fn use context oracle-cloud

# Get values from Terraform outputs
cd infrastructure/terraform/oci

# Set context values
fn update context oracle.compartment-id "$(terraform output -raw functions_application_id | cut -d'.' -f1-5)"
fn update context api-url "https://functions.us-ashburn-1.oraclecloud.com"
fn update context registry "iad.ocir.io/<namespace>/<repo-prefix>"

echo "✅ Fn CLI configured for OCI"
```

---

### Step 6.2: Create Environment Files for Functions

```bash
cat > backend/.env.functions << EOF
# Database Configuration
DB_HOST=$(cd infrastructure/terraform/oci && terraform output -raw database_connection_string | cut -d'@' -f2 | cut -d':' -f1)
DB_PORT=1522
DB_NAME=$(cd infrastructure/terraform/oci && terraform output -raw database_name)
DB_USER=ADMIN
DB_PASSWORD=${TF_VAR_db_admin_password}

# Object Storage
OBJECT_STORAGE_NAMESPACE=$(cd infrastructure/terraform/oci && terraform output -raw object_storage_namespace)
OBJECT_STORAGE_BUCKET=$(cd infrastructure/terraform/oci && terraform output -raw storage_bucket_name)
REPORTS_BUCKET=$(cd infrastructure/terraform/oci && terraform output -raw reports_bucket_name)

# Application
APP_NAME=${TF_VAR_app_name}
ENVIRONMENT=${TF_VAR_environment}
EOF

echo "✅ Environment file created for functions"
echo "⚠️  Add backend/.env.functions to .gitignore"
```

---

## Phase 7: Verification Checklist

### Infrastructure Verification

```bash
# Run this checklist
echo "=== Infrastructure Setup Checklist ==="
echo ""

# OCI Resources
echo "OCI Resources:"
echo "  [ ] VCN created with public and private subnets"
echo "  [ ] Internet Gateway, NAT Gateway, Service Gateway configured"
echo "  [ ] Security lists and route tables configured"
echo "  [ ] Autonomous Database created (Free Tier)"
echo "  [ ] Database wallet downloaded and extracted"
echo "  [ ] Object Storage buckets created (files + reports)"
echo "  [ ] Functions application created"
echo "  [ ] API Gateway deployed with health check"
echo ""

# Cloudflare
echo "Cloudflare Configuration:"
echo "  [ ] DNS records created (@ for frontend, api for backend)"
echo "  [ ] Page rules configured (caching, HTTPS redirect)"
echo "  [ ] Rate limiting enabled"
echo "  [ ] Firewall rules configured"
echo ""

# Configuration Files
echo "Configuration Files:"
echo "  [ ] Database wallet: $(ls infrastructure/configs/wallet/*.zip 2>/dev/null && echo '✅' || echo '❌')"
echo "  [ ] OCI outputs: $(ls infrastructure/configs/oci-outputs.json 2>/dev/null && echo '✅' || echo '❌')"
echo "  [ ] Functions env: $(ls backend/.env.functions 2>/dev/null && echo '✅' || echo '❌')"
echo ""

# Test Endpoints
echo "Endpoint Tests:"
API_ENDPOINT=$(cd infrastructure/terraform/oci && terraform output -raw api_deployment_endpoint 2>/dev/null)
if [ -n "$API_ENDPOINT" ]; then
  echo "  API Gateway: $API_ENDPOINT"
  curl -s -o /dev/null -w "  Health check: HTTP %{http_code}\n" "$API_ENDPOINT/health"
else
  echo "  ❌ API endpoint not found"
fi
echo ""

echo "=== Setup Complete ==="
```

---

## Troubleshooting

### Issue: Terraform fails with "Service error"

**Solution:**
```bash
# Verify OCI CLI configuration
oci iam region list --output table

# Check permissions
oci iam policy list --compartment-id "$TF_VAR_compartment_id"

# Ensure you have permissions for:
# - VCN management
# - Compute management
# - Database management
# - Functions management
# - API Gateway management
```

### Issue: "Autonomous Database creation failed"

**Solution:**
```bash
# Check if you've already used your free tier database
oci db autonomous-database list --compartment-id "$TF_VAR_compartment_id" | jq '.data[] | {name: .["display-name"], state: .["lifecycle-state"], isFree: .["is-free-tier"]}'

# Free tier limit is 2 databases per tenancy
# If you've hit the limit, delete an unused database:
oci db autonomous-database delete --autonomous-database-id <db-ocid>
```

### Issue: "Subnet CIDR conflict"

**Solution:**
```bash
# Check existing subnets
oci network subnet list --compartment-id "$TF_VAR_compartment_id" --vcn-id "$vcn_id"

# Change CIDR blocks in variables.tf to avoid conflicts
```

### Issue: "API Gateway health check failing"

**Solution:**
```bash
# Check API Gateway logs
oci logging-search search-logs \
  --search-query "search \"<compartment-ocid>\" | source='<gateway-ocid>'"

# Verify security lists allow traffic
oci network security-list get --security-list-id <sl-ocid>
```

---

## Cost Monitoring

### Setup Cost Alerts

```bash
# Create budget alert (optional, for monitoring)
oci budgets budget create \
  --compartment-id "$TF_VAR_compartment_id" \
  --amount 10 \
  --reset-period "MONTHLY" \
  --target-type "COMPARTMENT" \
  --targets "[\"$TF_VAR_compartment_id\"]" \
  --display-name "trade-journal-budget" \
  --description "Alert when spending exceeds $10/month"

# All resources should be free tier, so any cost indicates an issue
```

---

## Next Steps

After infrastructure setup is complete:

1. **Deploy Backend Functions**
   - See: `docs/FUNCTIONS_DEPLOYMENT.md` (to be created)
   - Convert Vert.x verticles to OCI Functions
   - Deploy functions to OCI

2. **Build and Deploy Frontend**
   - See: `docs/FRONTEND_DEPLOYMENT.md` (to be created)
   - Create Next.js application
   - Deploy to Vercel
   - Configure environment variables

3. **Migrate Database**
   - See: `docs/DATABASE_MIGRATION.md` (to be created)
   - Export data from local PostgreSQL
   - Import to Autonomous Database
   - Test connectivity

4. **Setup CI/CD**
   - Configure GitHub Actions
   - Setup automated deployments
   - Configure secrets

---

## Infrastructure Cleanup (if needed)

```bash
# DANGER: This will destroy all infrastructure
# Only run if you want to start over or teardown

cd infrastructure/terraform/cloudflare
terraform destroy

cd ../oci
terraform destroy

# Remove local files
rm -rf ../configs/*
```

---

**Document Version:** 1.0
**Last Updated:** 2026-02-10
**Status:** Ready for execution
