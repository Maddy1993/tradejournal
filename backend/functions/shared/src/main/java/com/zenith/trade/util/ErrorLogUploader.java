package com.zenith.trade.util;

import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Utility class to upload error logs to OCI Object Storage.
 * This acts as a sidecar for error reporting when the function fails.
 */
public class ErrorLogUploader {

    private static final Logger logger = Logger.getLogger(ErrorLogUploader.class.getName());
    private static volatile ObjectStorageClient objectStorageClient;
    private static final String DEFAULT_BUCKET_NAME = "trade-journal-storage";

    /**
     * Upload an error log to Object Storage.
     *
     * @param functionName The name of the function reporting the error
     * @param errorContent The content of the error log (stack trace, context, etc.)
     */
    public static void uploadErrorLog(String functionName, String errorContent) {
        try {
            initClient();

            // Determine bucket name from env var or default
            String bucketName = System.getenv("STORAGE_BUCKET_NAME");
            if (bucketName == null || bucketName.isEmpty()) {
                bucketName = DEFAULT_BUCKET_NAME;
            }

            // Determine namespace
            String namespace = System.getenv("OCI_NAMESPACE");
            if (namespace == null || namespace.isEmpty()) {
                 // Fallback: try to get namespace from client if possible, or log warning
                 // For now, let's assume it's passed or we fetch it. 
                 // Actually, fetching it requires another call. 
                 try {
                     namespace = objectStorageClient.getNamespace(com.oracle.bmc.objectstorage.requests.GetNamespaceRequest.builder().build()).getValue();
                 } catch (Exception e) {
                     logger.warning("Failed to fetch namespace: " + e.getMessage());
                     return; 
                 }
            }

            String timestamp = Instant.now().toString().replace(":", "-");
            String objectName = String.format("errors/%s/%s-%s.log", functionName, timestamp, UUID.randomUUID().toString());

            logger.info("Uploading error log to bucket: " + bucketName + ", object: " + objectName);

            byte[] contentBytes = errorContent.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(contentBytes);

            PutObjectRequest request = PutObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(objectName)
                .putObjectBody(inputStream)
                .contentLength((long) contentBytes.length)
                .contentType("text/plain")
                .build();

            PutObjectResponse response = objectStorageClient.putObject(request);
            logger.info("Successfully uploaded error log. OPC Request ID: " + response.getOpcRequestId());

        } catch (Exception e) {
            logger.severe("Failed to upload error log to Object Storage: " + e.getMessage());
            // We do NOT rethrow, as this is a sidecar operation and we want the original error to be returned to the caller
            e.printStackTrace();
        }
    }

    private static void initClient() {
        if (objectStorageClient == null) {
            synchronized (ErrorLogUploader.class) {
                if (objectStorageClient == null) {
                    try {
                        logger.info("Initializing ObjectStorage client with Resource Principal authentication");
                        ResourcePrincipalAuthenticationDetailsProvider provider =
                            ResourcePrincipalAuthenticationDetailsProvider.builder().build();
                        objectStorageClient = ObjectStorageClient.builder().build(provider);
                    } catch (Exception e) {
                        logger.severe("Failed to initialize ObjectStorage client: " + e.getMessage());
                        throw e;
                    }
                }
            }
        }
    }
}
