# GitHub Secrets Setup for Function Deployment

## Existing Secrets (Already Configured)

These secrets are already set up for the `terraform-deploy.yml` workflow and will be **reused**:

- ✅ `OCI_USER_OCID`
- ✅ `OCI_TENANCY_OCID`
- ✅ `OCI_FINGERPRINT`
- ✅ `OCI_PRIVATE_KEY`
- ✅ `OCI_REGION`
- ✅ `OCI_COMPARTMENT_ID`
- ✅ `DB_ADMIN_PASSWORD`
- ✅ `DB_WALLET_PASSWORD`
- ✅ `TF_API_TOKEN`

## New Secrets Required

You only need to add **2 new secrets** for OCIR (Oracle Cloud Infrastructure Registry) authentication:

### 1. `OCI_USERNAME`

Your OCI username (NOT the full OCIR username).

**Value:**
- If you're a **local OCI user**: `your-username`
- If you're a **federated user** (Oracle Identity Cloud Service): `oracleidentitycloudservice/your-email@example.com`

**Example:**
```
john.doe@company.com
```

The workflow will automatically prepend the namespace (`axtahv0mhabt`) to create the full OCIR username: `axtahv0mhabt/john.doe@company.com`

### 2. `OCI_AUTH_TOKEN`

An OCI Auth Token for OCIR authentication (this is **different** from your API signing key).

**How to generate:**

1. Log in to OCI Console
2. Click your user profile (top right) → **User Settings**
3. Under **Resources**, click **Auth Tokens**
4. Click **Generate Token**
5. Enter description: `GitHub Actions OCIR`
6. Click **Generate Token**
7. **Copy the token immediately** (it won't be shown again)
8. Add to GitHub Secrets as `OCI_AUTH_TOKEN`

## Adding Secrets to GitHub

```bash
# Set the secrets via GitHub CLI
gh secret set OCI_USERNAME
# Paste your username when prompted

gh secret set OCI_AUTH_TOKEN
# Paste your auth token when prompted
```

**Or via GitHub UI:**

1. Go to your repository on GitHub
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add each secret

## How the Workflow Uses These Secrets

The `deploy-functions.yml` workflow:

1. **Gets namespace from Terraform state** (no hardcoding)
2. **Constructs OCIR username**: `<namespace>/<OCI_USERNAME>`
3. **Authenticates to OCIR** using `OCI_AUTH_TOKEN`
4. **Builds and pushes images** to the registry
5. **Updates functions** using existing OCI CLI secrets

## Workflow Features

### Automatic Triggers
- ✅ Detects changes to `backend/functions/**`
- ✅ Builds only changed functions (or all if shared library changes)
- ✅ Auto-increments function version
- ✅ Commits version bumps back to repo

### Manual Trigger
```bash
# Via GitHub UI
Actions → Deploy OCI Functions → Run workflow

# Via GitHub CLI
gh workflow run deploy-functions.yml
```

### Deploy Specific Functions
```bash
gh workflow run deploy-functions.yml \
  -f functions="trades-api,sync-processor"
```

## Validation

After setting up secrets, test the workflow:

```bash
# Make a small change to a function
echo "# test" >> backend/functions/trades-api/README.md

# Commit and push
git add .
git commit -m "test: trigger function deployment"
git push

# Watch the workflow
gh run watch
```

## Troubleshooting

**Error: "unauthorized: authentication failed"**
- Verify `OCI_AUTH_TOKEN` is correct
- Regenerate auth token if needed
- Check `OCI_USERNAME` format matches your account type

**Error: "function not found"**
- Ensure functions are already created in OCI
- The workflow **updates** existing functions, doesn't create new ones
- Use `fn deploy` locally first to create functions initially

**Error: "namespace not found"**
- Ensure Terraform has been applied at least once
- Check `infrastructure/terraform/oci/terraform.tfstate` exists
