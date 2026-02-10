# IAM Policies for Resource Principal Authentication

# Dynamic group for Functions
resource "oci_identity_dynamic_group" "functions_dynamic_group" {
  compartment_id = var.tenancy_ocid
  name           = "${var.app_name}-functions-dg"
  description    = "Dynamic group for ${var.app_name} functions"

  matching_rule = "ALL {resource.type = 'fnfunc', resource.compartment.id = '${var.compartment_id}'}"
}

# Policy for Functions to access Autonomous Database
resource "oci_identity_policy" "functions_database_policy" {
  compartment_id = var.compartment_id
  name           = "${var.app_name}-functions-db-policy"
  description    = "Allow functions to access Autonomous Database"

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to read autonomous-databases in compartment id ${var.compartment_id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to use autonomous-databases in compartment id ${var.compartment_id}",
  ]
}

# Policy for Functions to access Object Storage
resource "oci_identity_policy" "functions_object_storage_policy" {
  compartment_id = var.compartment_id
  name           = "${var.app_name}-functions-storage-policy"
  description    = "Allow functions to access Object Storage"

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to read buckets in compartment id ${var.compartment_id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to manage objects in compartment id ${var.compartment_id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to read objectstorage-namespaces in compartment id ${var.compartment_id}",
  ]
}

# Policy for Functions to access Secrets (for database passwords)
resource "oci_identity_policy" "functions_secrets_policy" {
  compartment_id = var.compartment_id
  name           = "${var.app_name}-functions-secrets-policy"
  description    = "Allow functions to read secrets"

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to read secret-bundles in compartment id ${var.compartment_id}",
  ]
}

# Policy for Functions to write logs
resource "oci_identity_policy" "functions_logging_policy" {
  compartment_id = var.compartment_id
  name           = "${var.app_name}-functions-logging-policy"
  description    = "Allow functions to write logs"

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to use log-content in compartment id ${var.compartment_id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to read log-groups in compartment id ${var.compartment_id}",
  ]
}

# Policy for Functions networking
resource "oci_identity_policy" "functions_network_policy" {
  compartment_id = var.compartment_id
  name           = "${var.app_name}-functions-network-policy"
  description    = "Allow functions to use VCN resources"

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to use virtual-network-family in compartment id ${var.compartment_id}",
  ]
}

# Optional: Policy for Functions to invoke other functions
resource "oci_identity_policy" "functions_invoke_policy" {
  compartment_id = var.compartment_id
  name           = "${var.app_name}-functions-invoke-policy"
  description    = "Allow functions to invoke other functions"

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to use fn-function in compartment id ${var.compartment_id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.functions_dynamic_group.name} to use fn-invocation in compartment id ${var.compartment_id}",
  ]
}
