package com.amalitech.qa.tests.negative;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.models.request.CreateDepartmentRequest;
import com.amalitech.qa.models.request.CreatePollRequest;
import com.amalitech.qa.models.request.LoginRequest;
import com.amalitech.qa.models.request.RegisterRequest;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Negative tests for invalid input handling.
 * Tests API behavior with missing, invalid, or malformed data.
 * 
 * Expected Error Response Structure:
 * {
 *   "statusCode": 400,
 *   "message": "Validation failed" or specific error message,
 *   "timestamp": "ISO 8601 datetime",
 *   "path": "/api/endpoint",
 *   "details": ["field-specific error messages"] (optional)
 * }
 * 
 * Validation Rules:
 * - Required fields missing: 400 Bad Request
 * - Invalid format (email, UUID): 400 Bad Request
 * - Empty strings for required fields: 400 Bad Request
 * - Non-existent resources: 404 Not Found
 * - Error messages should mention the specific field that failed validation
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Error Handling")
@Tag("negative")
@Tag("validation")
public class InvalidInputTests extends BaseTest {
    
    @BeforeEach
    public void authenticateUser() {
        // Register and authenticate test user before tests that require authentication
        TestUser testUser = userRegistrationService.registerTestUser();
        authHandler.setAuthToken(testUser.getToken());
    }
    
    @Test
    @DisplayName("Register with missing email - should return 400 with validation error")
    @Description("Verify that registration fails when required email field is missing")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testRegisterWithMissingEmail() {
        // Expected: 400 Bad Request with error message mentioning email field
        
        // Arrange
        RegisterRequest registerRequest = new RegisterRequest(
            "Test User",
            null, // Required field missing
            "SecurePass@123"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/register", registerRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
        
        // Verify error message is present and helpful
        String errorMessage = response.jsonPath().getString("message");
        assertNotNull(errorMessage,
            "Error response should contain a message field");

        // Error should mention the email field
        String lowerMessage = errorMessage.toLowerCase();
        assertTrue(
            lowerMessage.contains("email") || lowerMessage.contains("required"),
            String.format("Error message should mention email field. Got: %s", errorMessage)
        );
    }
    
    @Test
    @DisplayName("Register with invalid email format fails")
    @Description("Verify that registration fails with invalid email format")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testRegisterWithInvalidEmail() {
        // Arrange
        RegisterRequest registerRequest = new RegisterRequest(
            "Test User",
            "not-an-email",
            "SecurePass@123"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/register", registerRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
    }
    
    @Test
    @DisplayName("Register with empty password fails")
    @Description("Verify that registration fails when password is empty")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testRegisterWithEmptyPassword() {
        // Arrange
        String uniqueEmail = "testuser" + System.currentTimeMillis() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(
            "Test User",
            uniqueEmail,
            ""
        );
        
        // Act
        Response response = apiClient.post("/api/auth/register", registerRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
    }
    
    @Test
    @DisplayName("Create poll with empty question - should return 400 with validation error")
    @Description("Verify that poll creation fails when question is empty (min length = 5)")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testCreatePollWithEmptyQuestion() {
        // Expected: 400 Bad Request - question has minLength=5 constraint
        
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "", // Empty string violates minLength=5
            "Test Description",
            Arrays.asList("Option 1", "Option 2"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
        
        // Verify error message mentions question field
        String errorMessage = response.jsonPath().getString("message");
        assertNotNull(errorMessage,
            "Error response should contain a message field");

        String lowerMessage = errorMessage.toLowerCase();
        assertTrue(
            lowerMessage.contains("question") || lowerMessage.contains("length") || lowerMessage.contains("required"),
            String.format("Error message should mention question field constraint. Got: %s", errorMessage)
        );
    }
    
    @Test
    @DisplayName("Create poll with no options fails")
    @Description("Verify that poll creation fails when no options are provided")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testCreatePollWithNoOptions() {
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "Test Question",
            "Test Description",
            new ArrayList<>(),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
    }
    
    @Test
    @DisplayName("Create poll with only one option fails")
    @Description("Verify that poll creation fails with insufficient options")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testCreatePollWithOneOption() {
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "Test Question",
            "Test Description",
            Collections.singletonList("Only Option"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
    }
    
    @Test
    @DisplayName("Create department with empty name fails")
    @Description("Verify that department creation fails when name is empty")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testCreateDepartmentWithEmptyName() {
        // Arrange
        CreateDepartmentRequest request = new CreateDepartmentRequest(
            "",
            Arrays.asList("test@example.com")
        );
        
        // Act
        Response response = apiClient.post("/api/departments", request);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
    }
    
    @Test
    @DisplayName("Create department with no emails fails")
    @Description("Verify that department creation fails when no emails are provided")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testCreateDepartmentWithNoEmails() {
        // Arrange
        CreateDepartmentRequest request = new CreateDepartmentRequest(
            "Test Department",
            new ArrayList<>()
        );
        
        // Act
        Response response = apiClient.post("/api/departments", request);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
    }
    
    @Test
    @DisplayName("Get non-existent poll returns 404")
    @Description("Verify that requesting a non-existent poll returns 404")
    @Severity(SeverityLevel.NORMAL)
    @Story("Resource Not Found")
    public void testGetNonExistentPoll() {
        // Act
        Response response = apiClient.get("/api/polls/999999");
        
        // Assert
        TestHelper.assertStatusCode(response, 404);
    }
    
    @Test
    @DisplayName("Get non-existent department returns 404")
    @Description("Verify that requesting a non-existent department returns 404")
    @Severity(SeverityLevel.NORMAL)
    @Story("Resource Not Found")
    public void testGetNonExistentDepartment() {
        // Act
        Response response = apiClient.get("/api/departments/999999");
        
        // Assert
        TestHelper.assertStatusCode(response, 404);
    }
    
    @Test
    @DisplayName("Login with missing credentials fails")
    @Description("Verify that login fails when credentials are missing")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testLoginWithMissingCredentials() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest(null, null);
        
        // Act
        Response response = apiClient.post("/api/auth/login", loginRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
    }
    
    @Test
    @DisplayName("Create poll with multiple validation errors - should return detailed error response")
    @Description("Verify that API returns helpful error details when multiple validation errors occur")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testValidationErrorDetails() {
        // Expected: 400 Bad Request with error details mentioning specific field failures
        
        // Arrange - Create poll with multiple validation errors
        CreatePollRequest invalidRequest = new CreatePollRequest(
            "", // Empty question - violates minLength=5
            "Description",
            Collections.singletonList("Only Option"), // Only 1 option - violates minItems=2
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", invalidRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
        
        // Verify error response structure
        String errorMessage = response.jsonPath().getString("message");
        assertNotNull(errorMessage,
            "Error response should contain a message field");
        
        // Check if error mentions the validation failures
        String lowerMessage = errorMessage.toLowerCase();
        String responseBody = response.getBody().asString().toLowerCase();
        
        // Should mention question issue
        boolean mentionsQuestion = lowerMessage.contains("question") || responseBody.contains("question");
        
        // Should mention options issue
        boolean mentionsOptions = lowerMessage.contains("option") || responseBody.contains("option");
        
        assertTrue(
            mentionsQuestion || mentionsOptions,
            String.format("Error should mention validation failures. Response: %s", response.getBody().asString())
        );

        // Verify error response has helpful structure
        assertNotNull(
            response.jsonPath().get("timestamp"),
            "Error response should include timestamp"
        );
    }
}
