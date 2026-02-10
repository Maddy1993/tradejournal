#!/bin/bash
set -e

# Get script directory for reliable path resolution
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$SCRIPT_DIR/../terraform/oci"

echo "========================================="
echo "Remote Backend Setup for Terraform"
echo "========================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if OCI CLI is configured
if ! command -v oci &> /dev/null; then
    echo -e "${RED}❌ OCI CLI not found${NC}"
    echo "Install: bash -c \"\$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)\""
    exit 1
fi

echo -e "${GREEN}✅ OCI CLI found${NC}"
echo ""

# Prompt for backend choice
echo "Choose state backend:"
echo "1) Terraform Cloud (Recommended - includes state locking)"
echo "2) OCI Object Storage (S3-compatible)"
echo "3) Skip backend setup (use local state)"
echo ""
read -p "Enter choice [1-3]: " choice

case $choice in
    1)
        echo ""
        echo "=== Terraform Cloud Setup ==="
        echo ""
        echo "Steps:"
        echo "1. Create account: https://app.terraform.io/signup"
        echo "2. Create organization (e.g., 'your-name-org')"
        echo "3. Create workspace: 'trade-journal-oci'"
        echo "4. Generate API token: User Settings → Tokens"
        echo ""
        read -p "Enter your Terraform Cloud organization name: " tf_org
        read -p "Enter workspace name [trade-journal-oci]: " tf_workspace
        tf_workspace=${tf_workspace:-trade-journal-oci}

        # Update providers.tf
        cd "$TERRAFORM_DIR"

        # Backup providers.tf
        cp providers.tf providers.tf.backup

        # Update with Terraform Cloud config
        sed -i.bak '/# OPTION 1: Terraform Cloud/,/# }/{
            s|# cloud {|cloud {|
            s|#   organization|  organization|
            s|#   workspaces|  workspaces|
            s|#     name|    name|
            s|#   }|  }|
            s|# }|}|
            s|your-org-name|'"$tf_org"'|
            s|trade-journal-oci|'"$tf_workspace"'|
        }' providers.tf

        echo ""
        echo -e "${GREEN}✅ Updated providers.tf with Terraform Cloud config${NC}"
        echo ""
        echo "Next steps:"
        echo "1. Run: terraform login"
        echo "2. Enter your Terraform Cloud API token"
        echo "3. Run: terraform init"
        echo "4. Answer 'yes' to migrate existing state (if any)"
        ;;

    2)
        echo ""
        echo "=== OCI Object Storage Setup ==="
        echo ""

        # Get namespace
        echo "Getting Object Storage namespace..."
        NAMESPACE=$(oci os ns get --query data --raw-output)
        echo -e "${GREEN}✅ Namespace: $NAMESPACE${NC}"
        echo ""

        # Get compartment ID
        read -p "Enter compartment OCID (or press Enter to use from config): " compartment_id
        if [ -z "$compartment_id" ]; then
            compartment_id=$(grep -A5 "\[DEFAULT\]" ~/.oci/config | grep compartment | cut -d'=' -f2 | tr -d ' ')
        fi

        if [ -z "$compartment_id" ]; then
            echo -e "${RED}❌ Compartment OCID not found${NC}"
            echo "Please provide compartment OCID"
            exit 1
        fi

        echo "Using compartment: $compartment_id"
        echo ""

        # Create bucket
        bucket_name="terraform-state-trade-journal"
        echo "Creating bucket: $bucket_name..."

        if oci os bucket get --bucket-name "$bucket_name" --namespace "$NAMESPACE" &> /dev/null; then
            echo -e "${YELLOW}⚠️  Bucket already exists${NC}"
        else
            oci os bucket create \
                --compartment-id "$compartment_id" \
                --name "$bucket_name" \
                --namespace "$NAMESPACE" \
                --public-access-type NoPublicAccess \
                --versioning Enabled

            echo -e "${GREEN}✅ Bucket created${NC}"
        fi
        echo ""

        # Create backend config file
        cd "$TERRAFORM_DIR"

        cat > backend-oci.hcl <<EOF
# Backend configuration for OCI Object Storage
bucket                      = "$bucket_name"
key                         = "oci/terraform.tfstate"
region                      = "us-sanjose-1"
endpoint                    = "https://${NAMESPACE}.compat.objectstorage.us-sanjose-1.oraclecloud.com"
skip_region_validation      = true
skip_credentials_validation = true
skip_metadata_api_check     = true
force_path_style            = true
EOF

        echo -e "${GREEN}✅ Created backend-oci.hcl${NC}"
        echo ""
        echo "Next steps:"
        echo "1. Run: terraform init -backend-config=backend-oci.hcl"
        echo "2. Answer 'yes' to migrate existing state (if any)"
        echo ""
        echo "⚠️  Note: OCI Object Storage backend doesn't support state locking"
        echo "For state locking, consider using Terraform Cloud"
        ;;

    3)
        echo ""
        echo "Skipping backend setup - using local state"
        echo ""
        echo "⚠️  Warning: Local state is not suitable for team collaboration"
        echo "Consider setting up remote backend for production use"
        ;;

    *)
        echo -e "${RED}Invalid choice${NC}"
        exit 1
        ;;
esac

echo ""
echo "========================================="
echo "Setup Complete!"
echo "========================================="
echo ""
echo "Next: Configure GitHub Secrets for CI/CD"
echo "See: docs/REMOTE_FIRST_DEPLOYMENT.md"
