package com.amalitech.qa.tests.functional;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.models.request.UpdateUserRequest;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Functional tests for user management operations.
 * Tests user profile retrieval and updates.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("User Management")
@Tag("functional")
@Tag("users")
public class UserManagementTests extends BaseTest {
    
    @BeforeEach
    public void authenticateUser() {
        // Register and authenticate test user before each test
        TestUser testUser = userRegistrationService.registerTestUser();
        authHandler.setAuthToken(testUser.getToken());
    }
    
    @Test
    @DisplayName("Get authenticated user profile")
    @Description("Verify that an authenticated user can retrieve their profile information")
    @Severity(SeverityLevel.CRITICAL)
    @Story("User Profile")
    public void testGetUserProfile() {
        // Act
        Response response = apiClient.get("/api/users/me");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseNotNull(response, "id");
        TestHelper.assertResponseNotNull(response, "email");
        TestHelper.assertResponseNotNull(response, "name");
    }
    
    @Test
    @DisplayName("Update user profile")
    @Description("Verify that an authenticated user can update their profile information")
    @Severity(SeverityLevel.NORMAL)
    @Story("User Profile")
    public void testUpdateUserProfile() {
        // Arrange
        UpdateUserRequest updateRequest = new UpdateUserRequest(
            "Updated Name " + System.currentTimeMillis()
        );
        
        // Act
        Response response = apiClient.put("/api/users", updateRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseContains(response, "name", updateRequest.getName());
    }
    
    @Test
    @DisplayName("Get user profile without authentication fails")
    @Description("Verify that accessing user profile without authentication returns 401")
    @Severity(SeverityLevel.NORMAL)
    @Story("User Profile")
    public void testGetUserProfileWithoutAuth() {
        // Arrange - Clear authentication
        authHandler.clearToken();
        
        // Act
        Response response = apiClient.get("/api/users/me");
        
        // Assert
        TestHelper.assertStatusCode(response, 401);
    }
}
