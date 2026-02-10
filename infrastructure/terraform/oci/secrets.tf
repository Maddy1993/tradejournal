# OCI Vault and Secrets for sensitive data

# Create Vault (Free tier includes 20 secrets)
resource "oci_kms_vault" "trade_journal_vault" {
  compartment_id = var.compartment_id
  display_name   = "${var.app_name}-vault"
  vault_type     = "DEFAULT"
}

# Create Master Encryption Key
resource "oci_kms_key" "trade_journal_key" {
  compartment_id = var.compartment_id
  display_name   = "${var.app_name}-key"

  key_shape {
    algorithm = "AES"
    length    = 32
  }

  management_endpoint = oci_kms_vault.trade_journal_vault.management_endpoint
}

# Store database admin password in vault
resource "oci_vault_secret" "db_admin_password" {
  compartment_id = var.compartment_id
  vault_id       = oci_kms_vault.trade_journal_vault.id
  key_id         = oci_kms_key.trade_journal_key.id
  secret_name    = "${var.app_name}-db-admin-password"
  description    = "Database admin password for ${var.app_name}"

  secret_content {
    content_type = "BASE64"
    content      = base64encode(var.db_admin_password)
  }
}

# Store database wallet password in vault
resource "oci_vault_secret" "db_wallet_password" {
  compartment_id = var.compartment_id
  vault_id       = oci_kms_vault.trade_journal_vault.id
  key_id         = oci_kms_key.trade_journal_key.id
  secret_name    = "${var.app_name}-db-wallet-password"
  description    = "Database wallet password for ${var.app_name}"

  secret_content {
    content_type = "BASE64"
    content      = base64encode(var.db_wallet_password)
  }
}

# Store application secret (for JWT signing, etc.)
resource "oci_vault_secret" "app_secret" {
  compartment_id = var.compartment_id
  vault_id       = oci_kms_vault.trade_journal_vault.id
  key_id         = oci_kms_key.trade_journal_key.id
  secret_name    = "${var.app_name}-app-secret"
  description    = "Application secret for ${var.app_name}"

  secret_content {
    content_type = "BASE64"
    # Generate a random secret
    content      = base64encode(random_password.app_secret.result)
  }
}

# Generate random application secret
resource "random_password" "app_secret" {
  length  = 64
  special = true
}
