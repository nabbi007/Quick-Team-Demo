package com.amalitech.qa.tests.functional;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.models.request.LoginRequest;
import com.amalitech.qa.models.request.RegisterRequest;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Functional tests for authentication operations.
 * Tests user registration, login, and token refresh functionality.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Authentication")
@Tag("functional")
@Tag("auth")
public class AuthenticationTests extends BaseTest {
    
    @Test
    @DisplayName("User can register with valid credentials")
    @Description("Verify that a new user can register successfully with valid name, email, and password")
    @Severity(SeverityLevel.CRITICAL)
    @Story("User Registration")
    public void testUserRegistration() {
        // Arrange
        String uniqueEmail = "testuser" + System.currentTimeMillis() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(
            "Test User",
            uniqueEmail,
            "SecurePass@123"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/register", registerRequest);
        
        // Assert - API actually returns 201 Created (not 200 as in OpenAPI spec)
        TestHelper.assertStatusCode(response, 201);
        TestHelper.assertResponseNotNull(response, "token");
        TestHelper.assertNonEmptyString(response, "token");
    }
    
    @Test
    @DisplayName("User can login with valid credentials")
    @Description("Verify that a registered user can login successfully")
    @Severity(SeverityLevel.CRITICAL)
    @Story("User Login")
    public void testUserLogin() {
        // Arrange - Register a test user first
        TestUser testUser = userRegistrationService.registerTestUser();
        
        LoginRequest loginRequest = new LoginRequest(
            testUser.getEmail(),
            testUser.getPassword()
        );
        
        // Act
        Response response = apiClient.post("/api/auth/login", loginRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseNotNull(response, "token");
        TestHelper.assertNonEmptyString(response, "token");
    }
    
    @Test
    @DisplayName("Login fails with invalid credentials")
    @Description("Verify that login fails when invalid credentials are provided")
    @Severity(SeverityLevel.NORMAL)
    @Story("User Login")
    public void testLoginWithInvalidCredentials() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest(
            "invalid@example.com",
            "WrongPassword123"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/login", loginRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 401);
    }
    
}
