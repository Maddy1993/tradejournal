# Virtual Cloud Network (VCN)
resource "oci_core_vcn" "trade_journal_vcn" {
  compartment_id = var.compartment_id
  cidr_blocks    = [var.vcn_cidr_block]
  display_name   = "${var.app_name}-vcn"
  dns_label      = "tradejournal"
}

# Internet Gateway for public subnet
resource "oci_core_internet_gateway" "trade_journal_igw" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-igw"
  enabled        = true
}

# NAT Gateway for private subnet
resource "oci_core_nat_gateway" "trade_journal_nat" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-nat"
}

# Service Gateway for OCI services
resource "oci_core_service_gateway" "trade_journal_sg" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-sg"

  services {
    service_id = data.oci_core_services.all_oci_services.services[0].id
  }
}

# Data source for OCI services
data "oci_core_services" "all_oci_services" {
  filter {
    name   = "name"
    values = ["All .* Services In Oracle Services Network"]
    regex  = true
  }
}

# Route Table for Public Subnet
resource "oci_core_route_table" "public_route_table" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-public-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.trade_journal_igw.id
  }
}

# Route Table for Private/Functions Subnet
resource "oci_core_route_table" "private_route_table" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-private-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_nat_gateway.trade_journal_nat.id
  }

  route_rules {
    destination       = data.oci_core_services.all_oci_services.services[0].cidr_block
    destination_type  = "SERVICE_CIDR_BLOCK"
    network_entity_id = oci_core_service_gateway.trade_journal_sg.id
  }
}

# Security List for Public Subnet
resource "oci_core_security_list" "public_security_list" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-public-sl"

  # Allow HTTPS inbound
  ingress_security_rules {
    protocol    = "6" # TCP
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    stateless   = false

    tcp_options {
      min = 443
      max = 443
    }
  }

  # Allow HTTP inbound (for redirects)
  ingress_security_rules {
    protocol    = "6" # TCP
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    stateless   = false

    tcp_options {
      min = 80
      max = 80
    }
  }

  # Allow all outbound
  egress_security_rules {
    protocol         = "all"
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    stateless        = false
  }
}

# Security List for Functions Subnet
resource "oci_core_security_list" "functions_security_list" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trade_journal_vcn.id
  display_name   = "${var.app_name}-functions-sl"

  # Allow inbound from VCN
  ingress_security_rules {
    protocol    = "all"
    source      = var.vcn_cidr_block
    source_type = "CIDR_BLOCK"
    stateless   = false
  }

  # Allow all outbound
  egress_security_rules {
    protocol         = "all"
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    stateless        = false
  }
}

# Public Subnet (for API Gateway)
resource "oci_core_subnet" "public_subnet" {
  compartment_id             = var.compartment_id
  vcn_id                     = oci_core_vcn.trade_journal_vcn.id
  cidr_block                 = var.public_subnet_cidr
  display_name               = "${var.app_name}-public-subnet"
  dns_label                  = "public"
  prohibit_public_ip_on_vnic = false
  route_table_id             = oci_core_route_table.public_route_table.id
  security_list_ids          = [oci_core_security_list.public_security_list.id]
}

# Functions Subnet (private)
resource "oci_core_subnet" "functions_subnet" {
  compartment_id             = var.compartment_id
  vcn_id                     = oci_core_vcn.trade_journal_vcn.id
  cidr_block                 = var.private_subnet_cidr
  display_name               = "${var.app_name}-functions-subnet"
  dns_label                  = "functions"
  prohibit_public_ip_on_vnic = true
  route_table_id             = oci_core_route_table.private_route_table.id
  security_list_ids          = [oci_core_security_list.functions_security_list.id]
}
