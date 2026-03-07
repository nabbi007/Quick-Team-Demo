package com.amalitech.qa.tests.negative;

import com.amalitech.qa.base.BaseTest;
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

/**
 * Negative tests for invalid input handling.
 * Tests API behavior with missing, invalid, or malformed data.
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
        // Login before tests that require authentication
        LoginRequest loginRequest = new LoginRequest(
            "basitmohammed3612@gmail.com",
            "Bece@2018"
        );
        Response loginResponse = apiClient.post("/api/auth/login", loginRequest);
        if (loginResponse.getStatusCode() == 200) {
            String token = loginResponse.jsonPath().getString("token");
            authHandler.setAuthToken(token);
        }
    }
    
    @Test
    @DisplayName("Register with missing email fails")
    @Description("Verify that registration fails when email is missing")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testRegisterWithMissingEmail() {
        // Arrange
        RegisterRequest registerRequest = new RegisterRequest(
            "Test User",
            null,
            "SecurePass@123"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/register", registerRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
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
    @DisplayName("Create poll with empty question fails")
    @Description("Verify that poll creation fails when question is empty")
    @Severity(SeverityLevel.NORMAL)
    @Story("Input Validation")
    public void testCreatePollWithEmptyQuestion() {
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "",
            "Test Description",
            Arrays.asList("Option 1", "Option 2"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 400);
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
}
