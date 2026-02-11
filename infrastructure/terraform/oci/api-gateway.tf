# API Gateway
resource "oci_apigateway_gateway" "trade_journal_gateway" {
  compartment_id = var.compartment_id
  endpoint_type  = "PUBLIC"
  subnet_id      = oci_core_subnet.public_subnet.id
  display_name   = "${var.app_name}-gateway"
}

# API Deployment
resource "oci_apigateway_deployment" "trade_journal_deployment" {
  compartment_id = var.compartment_id
  gateway_id     = oci_apigateway_gateway.trade_journal_gateway.id
  path_prefix    = "/api/v1"
  display_name   = "${var.app_name}-deployment"

  specification {
    # Logging policies
    logging_policies {
      access_log {
        is_enabled = true
      }
      execution_log {
        is_enabled = true
        log_level  = "INFO"
      }
    }

    # Request policies
    request_policies {
      cors {
        allowed_origins = ["*"]  # Update with actual frontend domains in production
        allowed_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"]
        allowed_headers = ["*"]
        exposed_headers = ["*"]
        is_allow_credentials_enabled = true
        max_age_in_seconds = 3600
      }
    }

    # Example route to Functions
    # Add actual routes based on your Functions
    routes {
      path    = "/health"
      methods = ["GET"]

      backend {
        type = "HTTP_BACKEND"
        
        url = "https://example.com/health"  # Placeholder - update with actual function invoke URL
      }

      request_policies {}
      response_policies {}
    }
  }

  depends_on = [
    oci_functions_application.trade_journal_app
  ]
}
