# Autonomous Database for Trade Journal
resource "oci_database_autonomous_database" "trade_journal_db" {
  compartment_id           = var.compartment_id
  db_name                  = "TRADEJRNL"
  display_name             = "${var.app_name}-db"
  db_workload              = "OLTP" # Transaction Processing
  
  # Resource allocation
  cpu_core_count           = 1
  data_storage_size_in_tbs = 1
  
  # Auto-scaling
  is_auto_scaling_enabled  = true
  
  # Password configuration
  admin_password           = var.db_admin_password
  
  # License
  license_model            = "LICENSE_INCLUDED"
  
  # Network access - allow from anywhere (secured by authentication)
  # For production, consider using private endpoint
  whitelisted_ips          = []
  
  # Backup configuration
  is_auto_scaling_for_storage_enabled = true
  
  # Free tier - set to true if using always free tier
  # is_free_tier = true  # Uncomment for free tier
  
  # Lifecycle configuration
  is_mtls_connection_required = false  # Set to true for mutual TLS
}

# Database Wallet (for secure connections)
# Note: The wallet is automatically generated and can be downloaded
# Functions will use connection strings from the database resource
