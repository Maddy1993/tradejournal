package com.zenith.trade.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class JobStatus {
    private UUID id;
    private String type; // SYNC, etc.
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    private String message;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public JobStatus() {}

    public JobStatus(UUID id, String type, String status, String message, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static JobStatusBuilder builder() {
        return new JobStatusBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static class JobStatusBuilder {
        private UUID id;
        private String type;
        private String status;
        private String message;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        JobStatusBuilder() {}

        public JobStatusBuilder id(UUID id) { this.id = id; return this; }
        public JobStatusBuilder type(String type) { this.type = type; return this; }
        public JobStatusBuilder status(String status) { this.status = status; return this; }
        public JobStatusBuilder message(String message) { this.message = message; return this; }
        public JobStatusBuilder createdAt(OffsetDateTime createdAt) { this.createdAt = createdAt; return this; }
        public JobStatusBuilder updatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public JobStatus build() {
            return new JobStatus(id, type, status, message, createdAt, updatedAt);
        }
    }
}
