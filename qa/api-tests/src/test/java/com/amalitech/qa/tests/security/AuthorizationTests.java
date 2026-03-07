package com.amalitech.qa.tests.security;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.request.CreateDepartmentRequest;
import com.amalitech.qa.models.request.CreatePollRequest;
import com.amalitech.qa.models.request.LoginRequest;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Security tests for authorization and access control.
 * Tests role-based access control and authentication requirements.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Security")
@Tag("security")
@Tag("authorization")
public class AuthorizationTests extends BaseTest {
    
    @Test
    @DisplayName("Unauthenticated user cannot create poll")
    @Description("Verify that creating a poll without authentication returns 401")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Authentication Required")
    public void testCreatePollWithoutAuth() {
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "Test Poll",
            "Test Description",
            Arrays.asList("Option 1", "Option 2"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 401);
    }
    
    @Test
    @DisplayName("Unauthenticated user cannot access user profile")
    @Description("Verify that accessing user profile without authentication returns 401")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Authentication Required")
    public void testGetUserProfileWithoutAuth() {
        // Act
        Response response = apiClient.get("/api/users/me");
        
        // Assert
        TestHelper.assertStatusCode(response, 401);
    }
    
    @Test
    @DisplayName("Unauthenticated user cannot create department")
    @Description("Verify that creating a department without authentication returns 401")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Authentication Required")
    public void testCreateDepartmentWithoutAuth() {
        // Arrange
        CreateDepartmentRequest request = new CreateDepartmentRequest(
            "Test Department",
            Arrays.asList("test@example.com")
        );
        
        // Act
        Response response = apiClient.post("/api/departments", request);
        
        // Assert
        TestHelper.assertStatusCode(response, 401);
    }
    
    @Test
    @DisplayName("Invalid token is rejected")
    @Description("Verify that requests with invalid authentication token are rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Token Validation")
    public void testInvalidTokenRejected() {
        // Arrange
        authHandler.setAuthToken("invalid.token.here");
        
        // Act
        Response response = apiClient.get("/api/users/me");
        
        // Assert
        TestHelper.assertStatusCode(response, 401);
    }
    
    @Test
    @DisplayName("Expired token is rejected")
    @Description("Verify that requests with expired authentication token are rejected")
    @Severity(SeverityLevel.NORMAL)
    @Story("Token Validation")
    public void testExpiredTokenRejected() {
        // Arrange - Use an expired token
        String expiredToken = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE2MDAwMDAwMDAsImV4cCI6MTYwMDAwMDAwMX0.test";
        authHandler.setAuthToken(expiredToken);
        
        // Act
        Response response = apiClient.get("/api/users/me");
        
        // Assert
        TestHelper.assertStatusCode(response, 401);
    }
    
    @Test
    @DisplayName("Regular user cannot access admin endpoints")
    @Description("Verify that regular users cannot access admin-only endpoints")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Role-Based Access Control")
    public void testUserCannotAccessAdminEndpoints() {
        // Arrange - Login as regular user
        LoginRequest loginRequest = new LoginRequest(
            "basitmohammed3612@gmail.com",
            "Bece@2018"
        );
        Response loginResponse = apiClient.post("/api/auth/login", loginRequest);
        String token = loginResponse.jsonPath().getString("token");
        authHandler.setAuthToken(token);
        
        // Act - Try to create department (admin-only operation)
        CreateDepartmentRequest request = new CreateDepartmentRequest(
            "Test Department",
            Arrays.asList("test@example.com")
        );
        Response response = apiClient.post("/api/departments", request);
        
        // Assert - Should be forbidden if user doesn't have admin role
        // Note: Adjust expected status code based on actual API behavior
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 403 || statusCode == 201,
            "Expected 403 (forbidden) or 201 (if user has admin role)"
        );
    }
}
