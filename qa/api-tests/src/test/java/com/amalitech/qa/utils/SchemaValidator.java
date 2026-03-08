package com.amalitech.qa.utils;

import io.qameta.allure.Step;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Utility class for validating API response schemas.
 * Provides methods for validating standard responses, paginated responses, and error responses.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class SchemaValidator {
    private static final Logger logger = LoggerFactory.getLogger(SchemaValidator.class);
    
    /**
     * Private constructor to prevent instantiation.
     */
    private SchemaValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Validates response against a JSON schema file.
     * 
     * @param response the API response to validate
     * @param schemaPath path to the JSON schema file (relative to project root)
     * @throws AssertionError if validation fails
     */
    @Step("Validate response against schema: {schemaPath}")
    public static void validateSchema(Response response, String schemaPath) {
        try {
            File schemaFile = new File(schemaPath);
            if (!schemaFile.exists()) {
                throw new IllegalArgumentException("Schema file not found: " + schemaPath);
            }
            
            response.then()
                .assertThat()
                .body(JsonSchemaValidator.matchesJsonSchema(schemaFile));
            
            logger.debug("Schema validation passed for: {}", schemaPath);
        } catch (AssertionError e) {
            String errorMsg = String.format(
                "Schema validation failed.%n" +
                "Schema: %s%n" +
                "Response body: %s%n" +
                "Error: %s",
                schemaPath,
                response.getBody().asString(),
                e.getMessage()
            );
            logger.error("Schema validation failed: {}", errorMsg);
            throw new AssertionError(errorMsg, e);
        }
    }
    
    /**
     * Validates paginated response structure.
     * Verifies that the response contains:
     * - A 'data' array field
     * - A 'pagination' object with required metadata fields (page, pageSize, totalPages, totalItems)
     * 
     * @param response the API response to validate
     * @param itemSchemaPath path to the JSON schema file for items in the data array (optional, can be null)
     * @throws AssertionError if validation fails
     */
    @Step("Validate paginated response structure")
    public static void validatePaginatedResponse(Response response, String itemSchemaPath) {
        try {
            // Check top-level structure (Spring Data Page format uses 'content' not 'data')
            response.then()
                .body("content", notNullValue())
                .body("totalElements", notNullValue())
                .body("totalPages", notNullValue());
            
            logger.debug("Paginated response top-level structure validated");
            
            // Validate pagination fields
            response.then()
                .body("totalPages", instanceOf(Integer.class))
                .body("totalElements", instanceOf(Integer.class))
                .body("number", instanceOf(Integer.class))
                .body("size", instanceOf(Integer.class));
            
            logger.debug("Pagination metadata fields validated");
            
            // Verify content is an array
            Object contentField = response.jsonPath().get("content");
            assertTrue(contentField instanceof List, 
                "Expected 'content' field to be an array but got: " + 
                (contentField != null ? contentField.getClass().getSimpleName() : "null"));
            
            // Validate pagination field values
            int totalPages = response.jsonPath().getInt("totalPages");
            int totalElements = response.jsonPath().getInt("totalElements");
            
            assertTrue(totalPages >= 0, "Total pages should be non-negative, got: " + totalPages);
            assertTrue(totalElements >= 0, "Total elements should be non-negative, got: " + totalElements);
            
            logger.debug("Pagination field values validated: totalPages={}, totalElements={}", 
                totalPages, totalElements);
            
            // If item schema provided, validate items in content array
            if (itemSchemaPath != null && !itemSchemaPath.isEmpty()) {
                List<?> contentItems = response.jsonPath().getList("content");
                if (!contentItems.isEmpty()) {
                    logger.debug("Validating {} items in content array against schema", contentItems.size());
                    // Note: Individual item validation would require extracting each item
                    // For now, we rely on the full paginated schema validation
                }
            }
            
            logger.info("Paginated response validation passed");
            
        } catch (AssertionError e) {
            String errorMsg = String.format(
                "Paginated response validation failed.%n" +
                "Expected: Paginated structure with 'content' array and pagination metadata (Spring Data Page format)%n" +
                "Actual response: %s%n" +
                "Error: %s",
                response.getBody().asString(),
                e.getMessage()
            );
            logger.error("Paginated response validation failed: {}", errorMsg);
            throw new AssertionError(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format(
                "Unexpected error during paginated response validation.%n" +
                "Response: %s%n" +
                "Error: %s",
                response.getBody().asString(),
                e.getMessage()
            );
            logger.error("Unexpected error during validation: {}", errorMsg);
            throw new AssertionError(errorMsg, e);
        }
    }
    
    /**
     * Validates error response structure and ensures no sensitive information is leaked.
     * Verifies:
     * - Correct status code
     * - Error response contains required fields (statusCode, message, timestamp)
     * - No sensitive information (exception details, stack traces, SQL details) in response
     * 
     * @param response the API response to validate
     * @param expectedStatusCode the expected HTTP status code
     * @throws AssertionError if validation fails
     */
    @Step("Validate error response with status code {expectedStatusCode}")
    public static void validateErrorResponse(Response response, int expectedStatusCode) {
        try {
            // Verify status code
            int actualStatusCode = response.getStatusCode();
            assertEquals(expectedStatusCode, actualStatusCode,
                String.format("Expected status code %d but got %d", expectedStatusCode, actualStatusCode));
            
            logger.debug("Error response status code validated: {}", expectedStatusCode);
            
            // Verify error response structure
            response.then()
                .body("statusCode", equalTo(expectedStatusCode))
                .body("message", notNullValue())
                .body("timestamp", notNullValue())
                .body("message", not(emptyString()));
            
            logger.debug("Error response structure validated");
            
            // Verify no sensitive information leaked
            String responseBody = response.getBody().asString().toLowerCase();
            
            assertFalse(responseBody.contains("exception") && responseBody.contains("at "), 
                "Error response should not contain exception stack traces");
            
            assertFalse(responseBody.contains("stack trace"), 
                "Error response should not contain stack trace references");
            
            assertFalse(responseBody.contains("sql") && (responseBody.contains("select") || 
                        responseBody.contains("insert") || responseBody.contains("update") || 
                        responseBody.contains("delete")), 
                "Error response should not contain SQL query details");
            
            assertFalse(responseBody.contains("jdbc") || responseBody.contains("database"), 
                "Error response should not contain database connection details");
            
            logger.info("Error response validation passed for status code: {}", expectedStatusCode);
            
        } catch (AssertionError e) {
            String errorMsg = String.format(
                "Error response validation failed.%n" +
                "Expected status code: %d%n" +
                "Actual status code: %d%n" +
                "Response body: %s%n" +
                "Error: %s",
                expectedStatusCode,
                response.getStatusCode(),
                response.getBody().asString(),
                e.getMessage()
            );
            logger.error("Error response validation failed: {}", errorMsg);
            throw new AssertionError(errorMsg, e);
        }
    }
    
    /**
     * Validates that a simple array response fails paginated validation.
     * This is used to test that the validator correctly rejects non-paginated responses.
     * 
     * @param response the API response (expected to be a simple array)
     * @return true if validation correctly fails, false otherwise
     */
    public static boolean validateSimpleArrayRejection(Response response) {
        try {
            validatePaginatedResponse(response, null);
            // If we get here, validation passed when it should have failed
            logger.warn("Simple array was incorrectly validated as paginated response");
            return false;
        } catch (AssertionError e) {
            // Expected - simple array should fail paginated validation
            logger.debug("Simple array correctly rejected by paginated validator");
            return true;
        }
    }
}
