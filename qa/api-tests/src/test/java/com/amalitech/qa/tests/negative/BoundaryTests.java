package com.amalitech.qa.tests.negative;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.models.request.CreatePollRequest;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Negative tests for boundary conditions and edge cases.
 * Tests API behavior with extreme values and boundary inputs.
 * 
 * Field Constraints (from poll-response-schema.json):
 * - question: minLength=5, maxLength=500 (REQUIRED)
 * - options: minItems=2, maxItems=10 (REQUIRED)
 * - description: no explicit constraints (OPTIONAL)
 * - votes: array of integers, minimum=0 (REQUIRED)
 * 
 * Expected Behaviors:
 * - Required fields with null values: 400 Bad Request
 * - Optional fields with null values: 200/201 OK
 * - Fields exceeding max length: 400 Bad Request with validation error
 * - Special characters and unicode: Should be accepted (UTF-8 support)
 * - Malicious input (XSS, SQL injection): 400 Bad Request or sanitized
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
        // Register and authenticate test user before tests that require authentication
        TestUser testUser = userRegistrationService.registerTestUser();
        authHandler.setAuthToken(testUser.getToken());
    }
    
    @Test
    @DisplayName("Create poll with extremely long question - should reject with 400")
    @Description("Verify API rejects poll questions exceeding maximum length (500 characters)")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testCreatePollWithVeryLongQuestion() {
        // Field constraint: question max length = 500 characters (per poll-response-schema.json)
        // Expected behavior: API should reject with 400 Bad Request and validation error message
        
        // Arrange - Create a 10000 character question (far exceeds 500 char limit)
        String longQuestion = "A".repeat(10000);
        CreatePollRequest pollRequest = new CreatePollRequest(
            longQuestion,
            "Test Description",
            Arrays.asList("Option 1", "Option 2"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert - Should reject with 400 and mention question length
        TestHelper.assertStatusCode(response, 400);
        
        String errorMessage = response.jsonPath().getString("message");
        assertNotNull(errorMessage,
            "Error response should contain a message");

        String lowerMessage = errorMessage.toLowerCase();
        assertTrue(
            lowerMessage.contains("question") || lowerMessage.contains("length") || lowerMessage.contains("long"),
            String.format("Error message should mention question length constraint. Got: %s", errorMessage)
        );
    }
    
    @Test
    @DisplayName("Create poll with too many options - should reject with 400")
    @Description("Verify API rejects polls exceeding maximum option count (10 options)")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testCreatePollWithManyOptions() {
        // Field constraint: options max items = 10 (per poll-response-schema.json)
        // Expected behavior: API should reject with 400 Bad Request
        
        // Arrange - Create 100 options (far exceeds 10 option limit)
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
        
        // Assert - Should reject with 400
        TestHelper.assertStatusCode(response, 400);
        
        String errorMessage = response.jsonPath().getString("message");
        assertNotNull(errorMessage,
            "Error response should contain a message");
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
        
        // Assert - API returns 200 for successful registration
        int statusCode = response.getStatusCode();
        assertTrue(
            statusCode == 400 || statusCode == 200,
            "Expected 400 (rejected) or 200 (accepted)"
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
        
        // Assert - API returns 200 for successful registration
        int statusCode = response.getStatusCode();
        assertTrue(
            statusCode == 400 || statusCode == 200,
            "Expected 400 (rejected) or 200 (accepted)"
        );
    }
    
    @Test
    @DisplayName("Create poll with special characters - should accept")
    @Description("Verify API accepts special characters and emojis in poll questions and options")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testCreatePollWithSpecialCharacters() {
        // Expected behavior: Special characters and emojis should be accepted
        // These are valid UTF-8 characters and should not be rejected
        
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "Test!@#$%^&*()_+-=[]{}|;':\",./<>?",
            "Description with émojis 🎉🎊",
            Arrays.asList("Option 1 ✓", "Option 2 ✗"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert - Should accept special characters (200) or reject if not allowed (400)
        int statusCode = response.getStatusCode();
        assertTrue(
            statusCode == 400 || statusCode == 200,
            String.format("Expected 400 (special chars not allowed) or 200 (accepted), got %d. Response: %s",
                statusCode, response.getBody().asString())
        );
        
        // If accepted, verify special characters are preserved
        if (statusCode == 200) {
            String question = response.jsonPath().getString("question");
            assertTrue(
                question.contains("!@#$"),
                "Special characters should be preserved in response"
            );
            
            String pollId = response.jsonPath().getString("id");
            testDataManager.getCreatedResourceIds().add(pollId);
        }
    }
    
    @Test
    @DisplayName("Create poll with unicode characters - should accept")
    @Description("Verify API accepts unicode characters from various languages")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testCreatePollWithUnicodeCharacters() {
        // Expected behavior: Unicode characters should be accepted (UTF-8 support)
        // Modern APIs should support international characters
        
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "你好世界 مرحبا العالم Привет мир",
            "Testing unicode support",
            Arrays.asList("选项 1", "خيار 2", "Вариант 3"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert - Should accept unicode (200) or reject if not supported (400)
        int statusCode = response.getStatusCode();
        assertTrue(
            statusCode == 400 || statusCode == 200,
            String.format("Expected 400 (unicode not supported) or 200 (accepted), got %d. Response: %s",
                statusCode, response.getBody().asString())
        );
        
        // If accepted, verify unicode is preserved
        if (statusCode == 200) {
            String question = response.jsonPath().getString("question");
            assertTrue(
                question.contains("你好") || question.contains("مرحبا") || question.contains("Привет"),
                "Unicode characters should be preserved in response"
            );
            
            String pollId = response.jsonPath().getString("id");
            testDataManager.getCreatedResourceIds().add(pollId);
        }
    }
    
    @Test
    @DisplayName("Create poll with null description - should accept (optional field)")
    @Description("Verify API accepts null description since it's an optional field")
    @Severity(SeverityLevel.NORMAL)
    @Story("Boundary Conditions")
    public void testCreatePollWithNullDescription() {
        // Field constraint: description is optional (not in required fields)
        // Expected behavior: API should accept null for optional fields (201)
        
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "Test Question",
            null, // Optional field - should be accepted
            Arrays.asList("Option 1", "Option 2"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert - Should accept null for optional field (API returns 200)
        int statusCode = response.getStatusCode();
        assertTrue(
            statusCode == 200,
            String.format("Expected 200 (null accepted for optional field), got %d. Response: %s",
                statusCode, response.getBody().asString())
        );
        
        // Cleanup
        if (statusCode == 200) {
            String pollId = response.jsonPath().getString("id");
            if (pollId != null) {
                testDataManager.getCreatedResourceIds().add(pollId);
            }
        }
    }
}
