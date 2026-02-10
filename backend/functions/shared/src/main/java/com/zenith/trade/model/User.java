package com.zenith.trade.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class User {
    private UUID id;
    private String email;
    private String userSecret;
    private OffsetDateTime createdAt;

    public User() {}

    public User(UUID id, String email, String userSecret, OffsetDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.userSecret = userSecret;
        this.createdAt = createdAt;
    }

    public static UserBuilder builder() {
        return new UserBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUserSecret() { return userSecret; }
    public void setUserSecret(String userSecret) { this.userSecret = userSecret; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public static class UserBuilder {
        private UUID id;
        private String email;
        private String userSecret;
        private OffsetDateTime createdAt;

        UserBuilder() {}

        public UserBuilder id(UUID id) { this.id = id; return this; }
        public UserBuilder email(String email) { this.email = email; return this; }
        public UserBuilder userSecret(String userSecret) { this.userSecret = userSecret; return this; }
        public UserBuilder createdAt(OffsetDateTime createdAt) { this.createdAt = createdAt; return this; }

        public User build() {
            return new User(id, email, userSecret, createdAt);
        }
    }
}
