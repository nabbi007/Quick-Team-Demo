package com.amalitech.qa.tests.functional;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Functional tests for Poll CRUD operations.
 * Tests creating, reading, updating, and deleting polls via the API.
 * 
 * NOTE: All poll operations require authentication. Users must be logged in before creating/accessing polls.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Poll Management")
@Tag("functional")
@Tag("crud")
public class PollCrudTests extends BaseTest {
    
    @BeforeEach
    public void authenticateUser() {
        // Register and authenticate test user before each test
        // Required: Users must be logged in for all poll operations
        TestUser testUser = userRegistrationService.registerTestUser();
        authHandler.setAuthToken(testUser.getToken());
    }
    
    @Test
    @DisplayName("Create a new poll with valid data")
    @Description("Verify that a poll can be created successfully with valid data")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Create Poll")
    public void testCreatePoll() {
        // Arrange
        Map<String, Object> pollData = new HashMap<>();
        pollData.put("title", "Testing Framework Poll");
        pollData.put("question", "What is your favorite testing framework?");
        pollData.put("description", "A poll about testing frameworks");
        pollData.put("options", Arrays.asList("JUnit", "TestNG"));
        pollData.put("maxSelections", 1);
        pollData.put("anonymous", false);
        pollData.put("departmentIds", Arrays.asList(1));
        pollData.put("expiresAt", "2026-12-31T23:59:59Z");
        
        // Act
        Response response = apiClient.post("/api/polls", pollData);
        
        // Assert - API returns 200 OK per OpenAPI spec
        TestHelper.assertStatusCode(response, 201);
        TestHelper.assertResponseNotNull(response, "id");
        TestHelper.assertResponseContains(response, "question", "What is your favorite testing framework?");
        
        // Track for cleanup
        String pollId = response.jsonPath().getString("id");
        testDataManager.trackResource("poll", pollId);
    }
    
    @Test
    @DisplayName("Get poll by ID")
    @Description("Verify that a poll can be retrieved by its ID")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Read Poll")
    public void testGetPollById() {
        // Arrange - Create a test poll first
        Integer pollId = Integer.valueOf(testDataManager.createTestPollWithDefaults());
        
        // Act
        Response response = apiClient.get("/api/polls/" + pollId);
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseContains(response, "id", pollId);
        TestHelper.assertResponseNotNull(response, "question");
    }
    
    @Test
    @DisplayName("Get my entitled polls")
    @Description("Verify that user can retrieve polls they are entitled to participate in")
    @Severity(SeverityLevel.NORMAL)
    @Story("Read Poll")
    public void testGetMyPolls() {
        // Arrange - Create some test polls
        testDataManager.createTestPollWithDefaults();
        testDataManager.createTestPollWithDefaults();
        
        // Act - Use /polls/my-polls endpoint for regular users
        Response response = apiClient.get("/api/polls/my-polls");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        // Response should be a paginated object with content array
        TestHelper.assertResponseNotNull(response, "content");
    }
    
    
    @Test
    @DisplayName("Get poll results")
    @Description("Verify that poll creator can retrieve poll results with vote counts")
    @Severity(SeverityLevel.NORMAL)
    @Story("Read Poll")
    public void testGetPollResults() {
        // Arrange - Create a test poll first
        String pollId = testDataManager.createTestPollWithDefaults();
        
        // Act - Use GET /polls/{id}/results endpoint
        Response response = apiClient.get("/api/polls/" + pollId + "/results");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseNotNull(response, "id");
        TestHelper.assertResponseNotNull(response, "totalVotes");
        TestHelper.assertResponseNotNull(response, "options");
    }
    
    @Test
    @DisplayName("Delete a poll")
    @Description("Verify that a poll can be deleted successfully")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Delete Poll")
    public void testDeletePoll() {
        // Arrange - Create a test poll first
        String pollId = testDataManager.createTestPollWithDefaults();
        
        // Act
        Response deleteResponse = apiClient.delete("/api/polls/" + pollId);
        
        // Assert - API returns 200 instead of 204
        TestHelper.assertStatusCode(deleteResponse, 204);
        
        // Verify poll is deleted by trying to get it
        Response getResponse = apiClient.get("/api/polls/" + pollId);
        TestHelper.assertStatusCode(getResponse, 404);
        
        // Remove from cleanup list since we already deleted it
        testDataManager.getCreatedResourceIds().remove(pollId);
    }
}
