# Get Object Storage Namespace
data "oci_objectstorage_namespace" "ns" {
  compartment_id = var.compartment_id
}

# Main Storage Bucket (for user uploads, trade data, etc.)
resource "oci_objectstorage_bucket" "trade_journal_storage" {
  compartment_id = var.compartment_id
  namespace      = data.oci_objectstorage_namespace.ns.namespace
  name           = "${var.app_name}-storage"
  access_type    = "NoPublicAccess"
  
  # Versioning for data protection
  versioning     = "Enabled"
  
  # Auto-tiering to reduce costs
  auto_tiering   = "InfrequentAccess"
  
  # Storage tier
  storage_tier   = "Standard"
}

# Reports Storage Bucket (for generated reports, analytics, etc.)
resource "oci_objectstorage_bucket" "reports_storage" {
  compartment_id = var.compartment_id
  namespace      = data.oci_objectstorage_namespace.ns.namespace
  name           = "${var.app_name}-reports"
  access_type    = "NoPublicAccess"
  
  # Versioning for data protection
  versioning     = "Enabled"
  
  # Auto-tiering to reduce costs for infrequently accessed reports
  auto_tiering   = "InfrequentAccess"
  
  # Storage tier
  storage_tier   = "Standard"
}
