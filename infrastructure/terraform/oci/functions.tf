# Functions Application with Resource Principal Auth
resource "oci_functions_application" "trade_journal_app" {
  compartment_id = var.compartment_id
  display_name   = var.app_name
  subnet_ids     = [oci_core_subnet.functions_subnet.id]

  config = {
    # Use Resource Principal for authentication
    OCI_RESOURCE_PRINCIPAL_VERSION = "2.2"

    # Database connection (functions will use resource principal to access)
    DB_OCID                  = oci_database_autonomous_database.trade_journal_db.id
    DB_CONNECTION_STRING     = oci_database_autonomous_database.trade_journal_db.connection_urls[0].apex_url
    DB_NAME                  = oci_database_autonomous_database.trade_journal_db.db_name
    DB_USER                  = "ADMIN"

    # Secret OCIDs (functions will fetch using resource principal)
    DB_PASSWORD_SECRET_OCID  = oci_vault_secret.db_admin_password.id
    APP_SECRET_OCID          = oci_vault_secret.app_secret.id

    # Object Storage (functions will use resource principal to access)
    OBJECT_STORAGE_NAMESPACE = data.oci_objectstorage_namespace.ns.namespace
    OBJECT_STORAGE_BUCKET    = oci_objectstorage_bucket.trade_journal_storage.name
    REPORTS_BUCKET           = oci_objectstorage_bucket.reports_storage.name

    # Application settings
    APP_NAME                 = var.app_name
    ENVIRONMENT              = var.environment
    REGION                   = var.region
  }

  # Network configuration
  network_security_group_ids = []

  # Logging
  syslog_url = ""

  # Tracing
  trace_config {
    is_enabled = true
  }

  # Ensure IAM policies are created first
  depends_on = [
    oci_identity_policy.functions_database_policy,
    oci_identity_policy.functions_object_storage_policy,
    oci_identity_policy.functions_secrets_policy,
    oci_identity_policy.functions_logging_policy,
    oci_identity_policy.functions_network_policy
  ]
}
