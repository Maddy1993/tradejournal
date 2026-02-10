# Remote-First Terraform Deployment Guide

**Automated infrastructure deployment using GitHub Actions**

---

## Overview

This setup enables:
- ✅ **Automated deployments** on push to main
- ✅ **Remote state storage** (no local .tfstate files)
- ✅ **PR previews** (terraform plan comments on PRs)
- ✅ **Team collaboration** (shared state)
- ✅ **CI/CD integration** (deploy on merge)
- ✅ **State locking** (prevent conflicts)

**Cost: $0** - GitHub Actions free tier includes 2,000 minutes/month

---

## Architecture

```
GitHub Push → GitHub Actions → Terraform → OCI Infrastructure
                    ↓
              Remote State Storage
              (Terraform Cloud or OCI Object Storage)
```

---

## Step 1: Choose State Backend

You have 3 options:

### Option A: Terraform Cloud (Recommended) ⭐

**Pros:**
- ✅ Free for up to 5 users
- ✅ Built-in state locking
- ✅ State versioning & rollback
- ✅ Web UI to view state
- ✅ No additional OCI setup needed

**Setup:**
1. Create account: https://app.terraform.io/signup
2. Create organization: `your-name-org`
3. Create workspace: `trade-journal-oci`
4. Generate API token: User Settings → Tokens → Create API token

**Configure in `providers.tf`:**
```hcl
terraform {
  cloud {
    organization = "your-name-org"
    workspaces {
      name = "trade-journal-oci"
    }
  }
}
```

### Option B: OCI Object Storage

**Pros:**
- ✅ Keep everything in OCI
- ✅ S3-compatible
- ✅ No external dependencies

**Cons:**
- ❌ No built-in state locking
- ❌ Requires bucket setup

**Setup:**
```bash
# Get namespace
NAMESPACE=$(oci os ns get --query data --raw-output)

# Create bucket for state
oci os bucket create \
  --compartment-id $TF_VAR_compartment_id \
  --name terraform-state-trade-journal \
  --namespace $NAMESPACE

# Copy example config
cp infrastructure/terraform/oci/backend-oci.hcl.example \
   infrastructure/terraform/oci/backend-oci.hcl

# Edit with your namespace
sed -i "s/YOUR_NAMESPACE/$NAMESPACE/g" infrastructure/terraform/oci/backend-oci.hcl
```

**Add to `.gitignore`:**
```bash
echo "infrastructure/terraform/oci/backend-oci.hcl" >> .gitignore
```

### Option C: Local Backend (Not Recommended for Teams)

Keep local state but still use GitHub Actions for deployment.

---

## Step 2: Setup GitHub Secrets

**Navigate to:** GitHub Repository → Settings → Secrets and variables → Actions

### Required Secrets

Click **New repository secret** for each:

| Secret Name | Value | How to Get |
|-------------|-------|------------|
| `OCI_TENANCY_OCID` | `ocid1.tenancy.oc1..aaaaaaaaoe2titp5eb46nexp2rozoath6jd3w6cbwt4rxkymggjc5tuf5kaq` | Already have ✅ |
| `OCI_USER_OCID` | `ocid1.user.oc1..aaaaaaaaXXXXXX` | Profile → User Settings → OCID |
| `OCI_FINGERPRINT` | `xx:xx:xx:xx:...` | API Keys page (after upload) |
| `OCI_REGION` | `us-sanjose-1` | Already have ✅ |
| `OCI_COMPARTMENT_ID` | `ocid1.compartment.oc1..aaaaaaaameyhu7sgwehn47flwdws5ayf4exg34uxut4ajthf6haqwogstzwa` | Already have ✅ |
| `OCI_PRIVATE_KEY` | (see below) | API private key content |
| `DB_ADMIN_PASSWORD` | `YourSecurePassword123!` | Choose strong password |
| `DB_WALLET_PASSWORD` | `YourWalletPassword123!` | Choose strong password |

**Optional (if using Terraform Cloud):**
| Secret Name | Value | How to Get |
|-------------|-------|------------|
| `TF_API_TOKEN` | `xxxxxxxxxxxxxxxx.atlasv1.xxxxxxxxx` | Terraform Cloud → User Settings → Tokens |

---

### How to Add OCI_PRIVATE_KEY Secret

**Option 1: Copy full key content**
```bash
# Display private key
cat ~/.oci/oci_api_key.pem

# Copy the output including:
# -----BEGIN RSA PRIVATE KEY-----
# (key content)
# -----END RSA PRIVATE KEY-----

# Paste as OCI_PRIVATE_KEY secret (preserve newlines)
```

**Option 2: Base64 encode (recommended for GitHub Actions)**
```bash
# Encode private key
cat ~/.oci/oci_api_key.pem | base64 | pbcopy

# Paste as OCI_PRIVATE_KEY secret
# Workflow will decode it automatically
```

If using base64, update workflow to decode:
```yaml
- name: Configure OCI credentials
  run: |
    mkdir -p ~/.oci
    echo "${{ secrets.OCI_PRIVATE_KEY }}" | base64 -d > ~/.oci/oci_api_key.pem
    chmod 600 ~/.oci/oci_api_key.pem
```

---

## Step 3: Configure Workflow

**File:** `.github/workflows/terraform-deploy.yml`

Already created with:
- ✅ Terraform format check
- ✅ Terraform plan on PRs (with comment)
- ✅ Terraform apply on main branch push
- ✅ Manual trigger option
- ✅ Outputs artifact upload

**Triggers:**
- **Push to main** → Runs plan + apply
- **Pull request** → Runs plan only (comments on PR)
- **Manual dispatch** → Run from Actions tab

---

## Step 4: Setup Environment Protection (Optional)

Add manual approval for production deployments:

1. **Go to:** Repository → Settings → Environments
2. **Create environment:** `production`
3. **Add protection rules:**
   - ✅ Required reviewers (select yourself or team)
   - ✅ Wait timer: 5 minutes (optional)
4. **Save**

Now GitHub Actions will wait for approval before applying changes.

---

## Step 5: Initial Setup

### 5.1 Initialize Remote State (First Time Only)

**If using Terraform Cloud:**
```bash
cd infrastructure/terraform/oci

# Update providers.tf with your organization/workspace
nano providers.tf

# Login to Terraform Cloud
terraform login

# Initialize with remote backend
terraform init

# It will prompt to migrate local state (if exists)
# Answer: yes
```

**If using OCI Object Storage:**
```bash
cd infrastructure/terraform/oci

# Ensure backend-oci.hcl is configured
terraform init -backend-config=backend-oci.hcl

# Migrate local state if exists
# Answer: yes when prompted
```

### 5.2 Commit Workflow Files

```bash
git add .github/workflows/terraform-deploy.yml
git add infrastructure/terraform/oci/providers.tf
git add infrastructure/terraform/oci/backend-oci.hcl.example
git commit -m "Add remote-first Terraform deployment with GitHub Actions"
git push origin main
```

---

## Step 6: Test the Workflow

### Test Plan (Pull Request)

```bash
# Create a test branch
git checkout -b test-terraform-workflow

# Make a small change
echo "# Test change" >> infrastructure/terraform/oci/README.md

# Commit and push
git add infrastructure/terraform/oci/README.md
git commit -m "Test: Trigger Terraform plan"
git push origin test-terraform-workflow

# Create PR on GitHub
# Check Actions tab - workflow should run
# Check PR comments - should see Terraform plan
```

### Test Apply (Main Branch)

```bash
# Merge PR to main
# Or push directly to main (if no protection)
git checkout main
git merge test-terraform-workflow
git push origin main

# Check Actions tab
# Workflow will:
# 1. Run plan
# 2. Wait for approval (if environment protection enabled)
# 3. Run apply
# 4. Upload outputs
```

---

## Step 7: Monitor Deployments

### GitHub Actions UI

**View runs:**
1. Go to repository → **Actions** tab
2. Click workflow run to see details
3. Click job to see logs

**Download outputs:**
1. Click workflow run
2. Scroll to **Artifacts**
3. Download `terraform-outputs` (JSON file with all outputs)

### Terraform Cloud UI (if using)

**View state:**
1. Go to https://app.terraform.io
2. Select workspace: `trade-journal-oci`
3. View current state, history, runs

---

## Workflow Behavior

### On Pull Request
```
1. Checkout code
2. Setup Terraform
3. Configure OCI credentials
4. terraform fmt -check
5. terraform init
6. terraform validate
7. terraform plan
8. Comment plan on PR ✍️
9. Upload plan artifact
```

**Result:** PR gets comment with Terraform plan for review

### On Push to Main
```
1. Run all PR checks (plan)
2. Wait for approval (if environment protection enabled) ⏸️
3. terraform apply
4. Save outputs
5. Upload outputs artifact 📦
6. Post deployment summary 📊
```

**Result:** Infrastructure deployed automatically

### Manual Trigger
```
Go to: Actions → Deploy OCI Infrastructure → Run workflow
Select branch → Run

Same behavior as push to main
```

---

## Benefits vs Local Deployment

| Feature | Local Deployment | Remote-First Deployment |
|---------|------------------|-------------------------|
| **State Storage** | Local file | Remote (shared) |
| **State Locking** | None | Yes (prevents conflicts) |
| **Team Collaboration** | Difficult | Easy |
| **PR Previews** | Manual | Automatic |
| **Deployment** | Manual | Automatic |
| **Audit Trail** | Limited | Full (GitHub Actions logs) |
| **Rollback** | Manual | Easy (state versions) |
| **Cost** | Free | Free |

---

## Troubleshooting

### Error: "Backend initialization required"

**Cause:** Remote backend not initialized

**Fix:**
```bash
cd infrastructure/terraform/oci
terraform init -backend-config=backend-oci.hcl  # or terraform login for TF Cloud
```

### Error: "Secret OCI_PRIVATE_KEY is not set"

**Cause:** GitHub Secret missing

**Fix:**
1. Go to Repository → Settings → Secrets
2. Add `OCI_PRIVATE_KEY` with private key content
3. Re-run workflow

### Error: "Error acquiring state lock"

**Cause:** Another process is running terraform (only with OCI Object Storage backend)

**Fix:**
- Wait for other run to complete
- Or manually break lock (use carefully):
  ```bash
  terraform force-unlock <lock-id>
  ```

### Workflow runs but doesn't apply

**Cause:** Environment protection requires approval

**Fix:**
1. Go to Actions → Click pending workflow
2. Click **Review deployments**
3. Approve

### Plan shows no changes but infrastructure not deployed

**Cause:** First deployment needs manual apply to create resources

**Fix:**
```bash
# Run first apply locally
cd infrastructure/terraform/oci
terraform apply

# Future changes will be deployed via GitHub Actions
```

---

## Security Best Practices

✅ **Never commit secrets** - Use GitHub Secrets
✅ **Use environment protection** - Require approvals for production
✅ **Review plans before apply** - Check PR comments
✅ **Limit secret access** - Only allow necessary workflows
✅ **Rotate credentials** - Update API keys every 90 days
✅ **Enable branch protection** - Require PR reviews before merge
✅ **Use least privilege** - OCI user only needs necessary permissions

---

## Advanced Configuration

### Deploy to Multiple Environments

Create separate workspaces:
- `trade-journal-oci-dev`
- `trade-journal-oci-staging`
- `trade-journal-oci-production`

Modify workflow:
```yaml
on:
  push:
    branches:
      - main        # production
      - staging     # staging
      - develop     # development
```

### Add Notifications

**Slack notification on deployment:**
```yaml
- name: Notify Slack
  if: always()
  uses: 8398a7/action-slack@v3
  with:
    status: ${{ job.status }}
    webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

**Email notification:**
```yaml
- name: Send email
  uses: dawidd6/action-send-mail@v3
  with:
    server_address: smtp.gmail.com
    server_port: 465
    username: ${{ secrets.EMAIL_USERNAME }}
    password: ${{ secrets.EMAIL_PASSWORD }}
    subject: Terraform deployment ${{ job.status }}
    body: Infrastructure deployment completed
    to: team@example.com
```

---

## Costs Breakdown

**GitHub Actions:**
- Public repos: **FREE** (unlimited)
- Private repos: **FREE** (2,000 minutes/month)
- Terraform runs: ~3-5 minutes each
- = **400-600 deployments/month free**

**Terraform Cloud:**
- Free tier: **FREE** (up to 5 users)
- Features: Remote state, locking, versioning, UI

**OCI Object Storage (for state):**
- Storage: **FREE** (10GB included)
- API requests: **FREE** (50,000/month included)
- State file size: ~100KB

**Total: $0/month** 🎉

---

## Quick Reference

### Common Commands

```bash
# View workflow logs
gh run list
gh run view <run-id>

# Re-run failed workflow
gh run rerun <run-id>

# Download outputs
gh run download <run-id> -n terraform-outputs

# Trigger manual deployment
gh workflow run terraform-deploy.yml
```

### File Locations

```
.github/workflows/terraform-deploy.yml  # GitHub Actions workflow
infrastructure/terraform/oci/
├── providers.tf                        # Remote backend configuration
├── backend-oci.hcl.example            # OCI Object Storage backend config
└── *.tf                               # Infrastructure code
```

---

## Migration Checklist

Before pushing to production:

- [ ] GitHub Secrets configured (9 secrets)
- [ ] Remote backend chosen (Terraform Cloud or OCI Object Storage)
- [ ] Backend initialized (`terraform init`)
- [ ] Local state migrated to remote (if exists)
- [ ] Workflow file committed
- [ ] Test workflow on test branch (PR triggers plan)
- [ ] Environment protection configured (optional)
- [ ] Team members added to Terraform Cloud (if using)
- [ ] Branch protection rules enabled (optional)

---

## Next Steps

1. ✅ **Setup GitHub Secrets** (15 minutes)
2. ✅ **Choose state backend** (5 minutes)
3. ✅ **Initialize remote backend** (5 minutes)
4. ✅ **Test with PR** (10 minutes)
5. ✅ **Deploy to main** (10 minutes)

After setup:
- Infrastructure changes via PR → Auto-deployed on merge
- No local Terraform runs needed
- Team can collaborate on infrastructure
- Full deployment history in GitHub Actions

---

**Document Version:** 1.0
**Last Updated:** 2026-02-10
**Status:** Production-Ready Remote-First Deployment
