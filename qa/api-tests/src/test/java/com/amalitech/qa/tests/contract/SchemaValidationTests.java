package com.amalitech.qa.tests.contract;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.models.request.CreatePollRequest;
import com.amalitech.qa.models.request.VoteRequest;
import com.amalitech.qa.utils.SchemaValidator;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for API response schema validation.
 * Validates that API responses conform to defined JSON schemas.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Contract Testing")
@Tag("contract")
@Tag("schema")
public class SchemaValidationTests extends BaseTest {
    
    @BeforeEach
    public void authenticateUser() {
        // Register and authenticate test user before each test
        TestUser testUser = userRegistrationService.registerTestUser();
        authHandler.setAuthToken(testUser.getToken());
    }
    
    @Test
    @DisplayName("Poll response matches schema")
    @Description("Verify that poll creation response conforms to the poll response schema")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Schema Validation")
    public void testPollResponseSchema() {
        // Arrange - Use proper poll creation request with all required fields
        Map<String, Object> pollRequest = new HashMap<>();
        pollRequest.put("title", "Schema Test Poll");
        pollRequest.put("question", "Testing schema validation");
        pollRequest.put("description", "Testing schema validation");
        pollRequest.put("options", Arrays.asList("Option A", "Option B"));
        pollRequest.put("maxSelections", 1);
        pollRequest.put("anonymous", false);
        pollRequest.put("departmentIds", Arrays.asList(1));
        pollRequest.put("expiresAt", "2026-12-31T23:59:59Z");
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert - API returns 200 OK per OpenAPI spec
        TestHelper.assertStatusCode(response, 200);
        response.then().assertThat().body(
            JsonSchemaValidator.matchesJsonSchema(
                new File("src/test/resources/schemas/poll-response-schema.json")
            )
        );
    }
    
    @Test
    @DisplayName("Error response matches schema")
    @Description("Verify that error responses conform to the error response schema")
    @Severity(SeverityLevel.NORMAL)
    @Story("Schema Validation")
    public void testErrorResponseSchema() {
        // Arrange - Clear auth to trigger error
        authHandler.clearToken();
        
        // Act
        Response response = apiClient.get("/api/users/me");
        
        // Assert
        TestHelper.assertStatusCode(response, 401);
        response.then().assertThat().body(
            JsonSchemaValidator.matchesJsonSchema(
                new File("src/test/resources/schemas/error-response-schema.json")
            )
        );
    }
    
    @Test
    @DisplayName("Vote response matches schema")
    @Description("Verify that vote response conforms to the vote response schema")
    @Severity(SeverityLevel.NORMAL)
    @Story("Schema Validation")
    public void testVoteResponseSchema() {
        // Arrange - Create a poll first
        String pollId = testDataManager.createTestPollWithDefaults();
        
        VoteRequest voteRequest = new VoteRequest(pollId, 0, "test_voter");
        
        // Act
        Response response = apiClient.post("/api/votes", voteRequest);
        
        // Assert
        int statusCode = response.getStatusCode();
        if (statusCode == 201 || statusCode == 200) {
            response.then().assertThat().body(
                JsonSchemaValidator.matchesJsonSchema(
                    new File("src/test/resources/schemas/vote-response-schema.json")
                )
            );
        }
    }
    
    @Test
    @DisplayName("Poll list response matches paginated schema")
    @Description("Verify that getting my entitled polls returns a paginated response structure with content array and pagination metadata")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Schema Validation")
    public void testPollListPaginatedResponseSchema() {
        // Act - Use /polls/my-polls endpoint for regular users (GET /polls is admin-only)
        Response response = apiClient.get("/api/polls/my-polls");
        
        // Assert
        TestHelper.assertStatusCode(response, 201);
        
        // Validate paginated response structure (Spring Data Page format)
        SchemaValidator.validatePaginatedResponse(response, 
            "src/test/resources/schemas/poll-response-schema.json");
        
        // Verify pagination fields are present and valid
        response.then()
            .body("content", instanceOf(List.class))
            .body("totalPages", notNullValue())
            .body("totalElements", greaterThanOrEqualTo(0));
        
        // Validate against full paginated schema
        response.then().assertThat().body(
            JsonSchemaValidator.matchesJsonSchema(
                new File("src/test/resources/schemas/paginated-poll-response-schema.json")
            )
        );
    }
    
    @Test
    @DisplayName("Simple array fails paginated validation")
    @Description("Verify that a simple array response is correctly rejected by paginated validator")
    @Severity(SeverityLevel.NORMAL)
    @Story("Schema Validation")
    public void testSimpleArrayRejection() {
        // This test verifies that the validator correctly identifies non-paginated responses
        // We'll use the department endpoint which returns a simple array
        Response response = apiClient.get("/api/departments");
        
        TestHelper.assertStatusCode(response, 200);
        
        // Verify it's a simple array (not paginated)
        Object body = response.jsonPath().get("$");
        assertInstanceOf(List.class, body, "Department response should be a simple array");
        
        // Verify that paginated validation correctly fails for simple arrays
        boolean validationFailed = SchemaValidator.validateSimpleArrayRejection(response);
        assertTrue(validationFailed, 
            "Paginated validator should reject simple array responses");
    }
    
    @Test
    @DisplayName("Department response has required fields")
    @Description("Verify that department response contains all required fields")
    @Severity(SeverityLevel.NORMAL)
    @Story("Schema Validation")
    public void testDepartmentResponseStructure() {
        // Act
        Response response = apiClient.get("/api/departments");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        
        // Verify it's an array
        response.then().assertThat().body("$", org.hamcrest.Matchers.instanceOf(java.util.List.class));
        
        // If there are departments, verify structure
        if (response.jsonPath().getList("$").size() > 0) {
            TestHelper.assertResponseNotNull(response, "[0].id");
            TestHelper.assertResponseNotNull(response, "[0].name");
            TestHelper.assertResponseNotNull(response, "[0].emails");
        }
    }
}
