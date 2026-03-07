package com.amalitech.qa.tests.functional;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
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
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Poll Management")
@Tag("functional")
@Tag("crud")
public class PollCrudTests extends BaseTest {
    
    @Test
    @DisplayName("Create a new poll with valid data")
    @Description("Verify that a poll can be created successfully with valid data")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Create Poll")
    public void testCreatePoll() {
        // Arrange
        Map<String, Object> pollData = new HashMap<>();
        pollData.put("question", "What is your favorite testing framework?");
        pollData.put("options", Arrays.asList("JUnit", "TestNG", "Cucumber"));
        pollData.put("createdBy", "test_user");
        
        // Act
        Response response = apiClient.post("/polls", pollData);
        
        // Assert
        TestHelper.assertStatusCode(response, 201);
        TestHelper.assertResponseNotNull(response, "id");
        TestHelper.assertResponseContains(response, "question", "What is your favorite testing framework?");
        TestHelper.assertCollectionSize(response, "options", 3);
        
        // Track for cleanup
        String pollId = response.jsonPath().getString("id");
        testDataManager.getCreatedResourceIds().add(pollId);
    }
    
    @Test
    @DisplayName("Get poll by ID")
    @Description("Verify that a poll can be retrieved by its ID")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Read Poll")
    public void testGetPollById() {
        // Arrange - Create a test poll first
        String pollId = testDataManager.createTestPollWithDefaults();
        
        // Act
        Response response = apiClient.get("/polls/" + pollId);
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseContains(response, "id", pollId);
        TestHelper.assertResponseNotNull(response, "question");
        TestHelper.assertResponseNotNull(response, "options");
    }
    
    @Test
    @DisplayName("Get all polls")
    @Description("Verify that all polls can be retrieved")
    @Severity(SeverityLevel.NORMAL)
    @Story("Read Poll")
    public void testGetAllPolls() {
        // Arrange - Create some test polls
        testDataManager.createTestPollWithDefaults();
        testDataManager.createTestPollWithDefaults();
        
        // Act
        Response response = apiClient.get("/polls");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        // Verify response is an array
        response.then().assertThat().body("$", org.hamcrest.Matchers.instanceOf(java.util.List.class));
    }
    
    @Test
    @DisplayName("Update an existing poll")
    @Description("Verify that a poll can be updated with new data")
    @Severity(SeverityLevel.NORMAL)
    @Story("Update Poll")
    public void testUpdatePoll() {
        // Arrange - Create a test poll first
        String pollId = testDataManager.createTestPollWithDefaults();
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("question", "Updated: What is your favorite API testing tool?");
        updateData.put("options", Arrays.asList("Rest Assured", "Postman", "SoapUI", "Karate"));
        
        // Act
        Response response = apiClient.put("/polls/" + pollId, updateData);
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseContains(response, "question", "Updated: What is your favorite API testing tool?");
        TestHelper.assertCollectionSize(response, "options", 4);
    }
    
    @Test
    @DisplayName("Partially update a poll using PATCH")
    @Description("Verify that a poll can be partially updated using PATCH")
    @Severity(SeverityLevel.NORMAL)
    @Story("Update Poll")
    public void testPatchPoll() {
        // Arrange - Create a test poll first
        String pollId = testDataManager.createTestPollWithDefaults();
        
        Map<String, Object> patchData = new HashMap<>();
        patchData.put("question", "Patched: What is your preferred programming language?");
        
        // Act
        Response response = apiClient.patch("/polls/" + pollId, patchData);
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseContains(response, "question", "Patched: What is your preferred programming language?");
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
        Response deleteResponse = apiClient.delete("/polls/" + pollId);
        
        // Assert
        TestHelper.assertStatusCode(deleteResponse, 204);
        
        // Verify poll is deleted by trying to get it
        Response getResponse = apiClient.get("/polls/" + pollId);
        TestHelper.assertStatusCode(getResponse, 404);
        
        // Remove from cleanup list since we already deleted it
        testDataManager.getCreatedResourceIds().remove(pollId);
    }
}
