package com.amalitech.qa.models;

import java.time.LocalDateTime;

/**
 * Model class representing a test user created for testing purposes.
 * Contains credentials and metadata for test user lifecycle management.
 */
public class TestUser {
    private String userId;
    private String name;
    private String email;
    private String password;
    private String token;
    private LocalDateTime createdAt;

    public TestUser() {
        this.createdAt = LocalDateTime.now();
    }

    public TestUser(String userId, String name, String email, String password, String token) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.password = password;
        this.token = token;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "TestUser{" +
                "userId='" + userId + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
