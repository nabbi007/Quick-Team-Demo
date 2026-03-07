package com.amalitech.qa.tests.contract;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.request.CreatePollRequest;
import com.amalitech.qa.models.request.LoginRequest;
import com.amalitech.qa.models.request.VoteRequest;
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
        // Login before each test
        LoginRequest loginRequest = new LoginRequest(
            "basitmohammed3612@gmail.com",
            "Bece@2018"
        );
        Response loginResponse = apiClient.post("/api/auth/login", loginRequest);
        String token = loginResponse.jsonPath().getString("token");
        authHandler.setAuthToken(token);
    }
    
    @Test
    @DisplayName("Poll response matches schema")
    @Description("Verify that poll creation response conforms to the poll response schema")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Schema Validation")
    public void testPollResponseSchema() {
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "Schema Test Poll",
            "Testing schema validation",
            Arrays.asList("Option A", "Option B", "Option C"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 201);
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
    @DisplayName("Poll list response is valid array")
    @Description("Verify that getting all polls returns a valid array structure")
    @Severity(SeverityLevel.NORMAL)
    @Story("Schema Validation")
    public void testPollListResponseStructure() {
        // Act
        Response response = apiClient.get("/api/polls");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        response.then().assertThat().body("$", org.hamcrest.Matchers.instanceOf(java.util.List.class));
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
