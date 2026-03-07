package com.amalitech.qa.tests.negative;

import com.amalitech.qa.base.BaseTest;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Negative tests for boundary conditions and edge cases.
 * Tests API behavior with extreme values and boundary inputs.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Error Handling")
@Tag("negative")
@Tag("boundary")
public class BoundaryTests extends BaseTest {
    
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
    @DisplayName("Create poll with extremely long question")
    @Description("Verify API handles extremely long poll questions appropriately")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testCreatePollWithVeryLongQuestion() {
        // Arrange - Create a 10000 character question
        String longQuestion = "A".repeat(10000);
        CreatePollRequest pollRequest = new CreatePollRequest(
            longQuestion,
            "Test Description",
            Arrays.asList("Option 1", "Option 2"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert - Should either accept or reject with 400
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 400 || statusCode == 201,
            "Expected 400 (rejected) or 201 (accepted)"
        );
    }
    
    @Test
    @DisplayName("Create poll with maximum number of options")
    @Description("Verify API handles polls with many options")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testCreatePollWithManyOptions() {
        // Arrange - Create 100 options
        List<String> manyOptions = IntStream.range(1, 101)
            .mapToObj(i -> "Option " + i)
            .collect(Collectors.toList());
        
        CreatePollRequest pollRequest = new CreatePollRequest(
            "Poll with many options",
            "Testing boundary",
            manyOptions,
            true
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 400 || statusCode == 201,
            "Expected 400 (rejected) or 201 (accepted)"
        );
        
        // Cleanup if created
        if (statusCode == 201) {
            String pollId = response.jsonPath().getString("id");
            testDataManager.getCreatedResourceIds().add(pollId);
        }
    }
    
    @Test
    @DisplayName("Register with very long name")
    @Description("Verify API handles extremely long user names")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testRegisterWithVeryLongName() {
        // Arrange
        String longName = "A".repeat(1000);
        String uniqueEmail = "testuser" + System.currentTimeMillis() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(
            longName,
            uniqueEmail,
            "SecurePass@123"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/register", registerRequest);
        
        // Assert
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 400 || statusCode == 201,
            "Expected 400 (rejected) or 201 (accepted)"
        );
    }
    
    @Test
    @DisplayName("Register with very long email")
    @Description("Verify API handles extremely long email addresses")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testRegisterWithVeryLongEmail() {
        // Arrange - Create a very long but valid email
        String longEmail = "a".repeat(200) + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(
            "Test User",
            longEmail,
            "SecurePass@123"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/register", registerRequest);
        
        // Assert
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 400 || statusCode == 201,
            "Expected 400 (rejected) or 201 (accepted)"
        );
    }
    
    @Test
    @DisplayName("Create poll with special characters in question")
    @Description("Verify API handles special characters in poll questions")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testCreatePollWithSpecialCharacters() {
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "Test!@#$%^&*()_+-=[]{}|;':\",./<>?",
            "Description with émojis 🎉🎊",
            Arrays.asList("Option 1 ✓", "Option 2 ✗"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 400 || statusCode == 201,
            "Expected 400 (rejected) or 201 (accepted)"
        );
        
        // Cleanup if created
        if (statusCode == 201) {
            String pollId = response.jsonPath().getString("id");
            testDataManager.getCreatedResourceIds().add(pollId);
        }
    }
    
    @Test
    @DisplayName("Create poll with unicode characters")
    @Description("Verify API handles unicode characters properly")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testCreatePollWithUnicodeCharacters() {
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "你好世界 مرحبا العالم Привет мир",
            "Testing unicode support",
            Arrays.asList("选项 1", "خيار 2", "Вариант 3"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 400 || statusCode == 201,
            "Expected 400 (rejected) or 201 (accepted)"
        );
        
        // Cleanup if created
        if (statusCode == 201) {
            String pollId = response.jsonPath().getString("id");
            testDataManager.getCreatedResourceIds().add(pollId);
        }
    }
    
    @Test
    @DisplayName("Create poll with null description")
    @Description("Verify API handles null description field")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testCreatePollWithNullDescription() {
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "Test Question",
            null,
            Arrays.asList("Option 1", "Option 2"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 400 || statusCode == 201,
            "Expected 400 (rejected) or 201 (accepted with null description)"
        );
        
        // Cleanup if created
        if (statusCode == 201) {
            String pollId = response.jsonPath().getString("id");
            testDataManager.getCreatedResourceIds().add(pollId);
        }
    }
}
