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

# IAM Outputs
output "dynamic_group_id" {
  description = "Functions Dynamic Group OCID"
  value       = oci_identity_dynamic_group.functions_dynamic_group.id
}

output "dynamic_group_name" {
  description = "Functions Dynamic Group name"
  value       = oci_identity_dynamic_group.functions_dynamic_group.name
}

# Secrets Outputs
output "vault_id" {
  description = "Vault OCID"
  value       = oci_kms_vault.trade_journal_vault.id
}

output "vault_crypto_endpoint" {
  description = "Vault crypto endpoint"
  value       = oci_kms_vault.trade_journal_vault.crypto_endpoint
}

output "vault_management_endpoint" {
  description = "Vault management endpoint"
  value       = oci_kms_vault.trade_journal_vault.management_endpoint
}

output "db_password_secret_id" {
  description = "Database password secret OCID"
  value       = oci_vault_secret.db_admin_password.id
}

output "app_secret_id" {
  description = "Application secret OCID"
  value       = oci_vault_secret.app_secret.id
}

# Summary Output (for easy reference)
output "deployment_summary" {
  description = "Summary of deployed resources"
  value = {
    api_endpoint           = "https://${oci_apigateway_gateway.trade_journal_gateway.hostname}${oci_apigateway_deployment.trade_journal_deployment.path_prefix}"
    functions_app          = oci_functions_application.trade_journal_app.display_name
    database               = oci_database_autonomous_database.trade_journal_db.db_name
    storage_namespace      = data.oci_objectstorage_namespace.ns.namespace
    dynamic_group          = oci_identity_dynamic_group.functions_dynamic_group.name
    authentication_method  = "Resource Principal (OCI_RESOURCE_PRINCIPAL_VERSION=2.2)"
    secrets_in_vault       = "db-admin-password, app-secret"
  }
}
