package com.zenith.trade.util;

import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Utility class for accessing OCI Vault secrets using Resource Principal authentication.
 */
public class VaultClient {

    private static final Logger logger = Logger.getLogger(VaultClient.class.getName());
    private static volatile SecretsClient secretsClient;

    /**
     * Get a secret value from OCI Vault by secret OCID.
     * Uses Resource Principal authentication (requires OCI_RESOURCE_PRINCIPAL_VERSION env var).
     *
     * @param secretOcid The OCID of the secret to retrieve
     * @return The secret value as a String
     * @throws RuntimeException if secret cannot be retrieved
     */
    public static String getSecret(String secretOcid) {
        try {
            if (secretsClient == null) {
                synchronized (VaultClient.class) {
                    if (secretsClient == null) {
                        logger.info("Initializing Secrets client with Resource Principal authentication");
                        ResourcePrincipalAuthenticationDetailsProvider provider =
                            ResourcePrincipalAuthenticationDetailsProvider.builder().build();
                        secretsClient = SecretsClient.builder().build(provider);
                    }
                }
            }

            logger.info("Fetching secret: " + secretOcid);
            GetSecretBundleRequest request = GetSecretBundleRequest.builder()
                .secretId(secretOcid)
                .build();

            GetSecretBundleResponse response = secretsClient.getSecretBundle(request);

            if (response.getSecretBundle().getSecretBundleContent() instanceof Base64SecretBundleContentDetails) {
                Base64SecretBundleContentDetails content =
                    (Base64SecretBundleContentDetails) response.getSecretBundle().getSecretBundleContent();

                String base64Content = content.getContent();
                byte[] decodedBytes = Base64.getDecoder().decode(base64Content);
                String secretValue = new String(decodedBytes, StandardCharsets.UTF_8);

                logger.info("Successfully retrieved secret");
                return secretValue;
            } else {
                throw new RuntimeException("Unexpected secret content type");
            }
        } catch (Exception e) {
            logger.severe("Failed to retrieve secret from Vault: " + e.getMessage());
            throw new RuntimeException("Failed to retrieve secret: " + secretOcid, e);
        }
    }

    /**
     * Close the secrets client. Call this during shutdown if needed.
     */
    public static void close() {
        if (secretsClient != null) {
            try {
                secretsClient.close();
                secretsClient = null;
            } catch (Exception e) {
                logger.warning("Error closing secrets client: " + e.getMessage());
            }
        }
    }
}
