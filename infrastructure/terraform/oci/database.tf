# Autonomous Database for Trade Journal
resource "oci_database_autonomous_database" "trade_journal_db" {
  compartment_id           = var.compartment_id
  db_name                  = "TRADEJRNL"
  display_name             = "${var.app_name}-db"
  db_workload              = "OLTP" # Transaction Processing
  
  # Resource allocation - Free Tier is limited to 1 OCPU and 20GB storage
  cpu_core_count           = 1
  data_storage_size_in_tbs = 1 
  
  # Auto-scaling (Must be disabled for Free Tier)
  is_auto_scaling_enabled  = false
  
  # Password configuration
  admin_password           = var.db_admin_password
  
  # License
  license_model            = "LICENSE_INCLUDED"
  
  # Network access - allow from anywhere (secured by authentication)
  # For production, consider using private endpoint
  whitelisted_ips          = ["0.0.0.0/0"]
  
  # Backup configuration
  is_auto_scaling_for_storage_enabled = false
  
  # Free tier - must be explicitly enabled
  is_free_tier = true

  # Lifecycle configuration
  is_mtls_connection_required = false  # Set to true for mutual TLS

  # Ignore changes to attributes managed by OCI for Always Free tier
  # Free tier databases have fixed resource allocations that can't be modified
  lifecycle {
    ignore_changes = [
      cpu_core_count,              # Always Free tier manages this internally
      data_storage_size_in_tbs,    # Fixed at 20GB for Always Free
      is_auto_scaling_enabled,     # Always disabled for Free tier
      is_auto_scaling_for_storage_enabled
    ]
  }
}

# Database Wallet (for secure connections)
# Note: The wallet is automatically generated and can be downloaded
# Functions will use connection strings from the database resource
