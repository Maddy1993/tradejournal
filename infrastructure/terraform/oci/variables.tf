# OCI Authentication Variables
# These are populated by GitHub Actions via TF_VAR_* environment variables from secrets

variable "tenancy_ocid" {
  description = "OCI Tenancy OCID"
  type        = string
}

variable "user_ocid" {
  description = "OCI User OCID"
  type        = string
}

variable "fingerprint" {
  description = "API Key Fingerprint"
  type        = string
}

variable "private_key" {
  description = "OCI API private key content (PEM format)"
  type        = string
  sensitive   = true
}

variable "region" {
  description = "OCI Region"
  type        = string
}

variable "compartment_id" {
  description = "OCI Compartment OCID"
  type        = string
}

# Database Configuration
variable "db_admin_password" {
  description = "Autonomous Database admin password"
  type        = string
  sensitive   = true
}

variable "db_wallet_password" {
  description = "Autonomous Database wallet password"
  type        = string
  sensitive   = true
}

# Application Configuration
variable "app_name" {
  description = "Application name"
  type        = string
  default     = "trade-journal"
}

variable "environment" {
  description = "Environment (production, staging, development)"
  type        = string
  default     = "production"
}

# Network Configuration
variable "vcn_cidr_block" {
  description = "CIDR block for VCN"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR block for public subnet"
  type        = string
  default     = "10.0.1.0/24"
}

variable "private_subnet_cidr" {
  description = "CIDR block for private/functions subnet"
  type        = string
  default     = "10.0.2.0/24"
}
