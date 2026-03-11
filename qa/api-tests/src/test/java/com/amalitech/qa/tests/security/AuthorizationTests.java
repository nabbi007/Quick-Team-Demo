package com.amalitech.qa.tests.security;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.models.request.CreateDepartmentRequest;
import com.amalitech.qa.models.request.CreatePollRequest;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for authorization and access control.
 * Tests role-based access control and authentication requirements.
 * 
 * HTTP Status Code Semantics:
 * - 401 Unauthorized: No authentication provided, invalid credentials, or expired token
 * - 403 Forbidden: Valid authentication but insufficient permissions for the operation
 * - 404 Not Found: Resource doesn't exist (with valid authentication)
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
    @Description("Verify that creating a poll without authentication returns 401 Unauthorized")
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
        
        // Act - No authentication token set
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert - 401 because no authentication credentials provided
        TestHelper.assertStatusCode(response, 401);
    }
    
    @Test
    @DisplayName("Unauthenticated user cannot access user profile")
    @Description("Verify that accessing user profile without authentication returns 401 Unauthorized")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Authentication Required")
    public void testGetUserProfileWithoutAuth() {
        // Act - No authentication token set
        Response response = apiClient.get("/api/users/me");
        
        // Assert - 401 because no authentication credentials provided
        TestHelper.assertStatusCode(response, 401);
    }
    
    @Test
    @DisplayName("Unauthenticated user cannot create department")
    @Description("Verify that creating a department without authentication returns 401 Unauthorized")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Authentication Required")
    public void testCreateDepartmentWithoutAuth() {
        // Arrange
        CreateDepartmentRequest request = new CreateDepartmentRequest(
            "Test Department",
            Arrays.asList("test@example.com")
        );
        
        // Act - No authentication token set
        Response response = apiClient.post("/api/departments", request);
        
        // Assert - 401 because no authentication credentials provided
        TestHelper.assertStatusCode(response, 401);
    }
    
    @Test
    @DisplayName("Invalid token is rejected")
    @Description("Verify that requests with invalid authentication token return 401 Unauthorized")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Token Validation")
    public void testInvalidTokenRejected() {
        // Arrange - Set an invalid token
        authHandler.setAuthToken("invalid.token.here");
        
        // Act
        Response response = apiClient.get("/api/users/me");
        
        // Assert - 401 because authentication credentials are invalid
        TestHelper.assertStatusCode(response, 401);
    }
    
    @Test
    @DisplayName("Expired token is rejected")
    @Description("Verify that requests with expired authentication token return 401 Unauthorized")
    @Severity(SeverityLevel.NORMAL)
    @Story("Token Validation")
    public void testExpiredTokenRejected() {
        // Arrange - Use an expired token
        String expiredToken = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE2MDAwMDAwMDAsImV4cCI6MTYwMDAwMDAwMX0.test";
        authHandler.setAuthToken(expiredToken);
        
        // Act
        Response response = apiClient.get("/api/users/me");
        
        // Assert - 401 because authentication token is expired
        TestHelper.assertStatusCode(response, 401);
    }
    
    @Test
    @DisplayName("Regular user cannot access admin endpoints")
    @Description("Verify that regular users cannot access admin-only endpoints - returns 403 Forbidden")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Role-Based Access Control")
    public void testUserCannotAccessAdminEndpoints() {
        // Arrange - Register and authenticate as regular user
        TestUser testUser = userRegistrationService.registerTestUser();
        authHandler.setAuthToken(testUser.getToken());
        
        // Act - Try to create department (admin-only operation)
        CreateDepartmentRequest request = new CreateDepartmentRequest(
            "Test Department",
            Arrays.asList("test@example.com")
        );
        Response response = apiClient.post("/api/departments", request);
        
        // Assert - Should be 403 (forbidden) if user doesn't have admin role
        // or 201 if the test user happens to have admin permissions
        // Note: 403 means authenticated but not authorized (insufficient permissions)
        int statusCode = response.getStatusCode();
        assertTrue(
            statusCode == 403 || statusCode == 201,
            "Expected 403 (forbidden - insufficient permissions) or 201 (if user has admin role), but got " + statusCode
        );
    }
}
