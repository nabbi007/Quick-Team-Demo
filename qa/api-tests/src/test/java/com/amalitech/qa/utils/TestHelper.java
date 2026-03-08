package com.amalitech.qa.utils;

import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Utility class providing reusable assertion and validation methods for API testing.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class TestHelper {
    private static final Logger logger = LoggerFactory.getLogger(TestHelper.class);
    
    /**
     * Private constructor to prevent instantiation.
     */
    private TestHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Asserts that the response status code matches the expected code.
     * 
     * @param response the API response
     * @param expectedCode the expected status code
     */
    @Step("Assert response status code is {expectedCode}")
    public static void assertStatusCode(Response response, int expectedCode) {
        int actualCode = response.getStatusCode();
        assertEquals(expectedCode, actualCode,
                String.format("Expected status code %d but got %d. Response body: %s",
                        expectedCode, actualCode, response.getBody().asString()));
        logger.debug("Status code assertion passed: {}", expectedCode);
    }
    
    /**
     * Asserts that the response contains a specific key with the expected value.
     * 
     * @param response the API response
     * @param key the JSON path key
     * @param expectedValue the expected value
     */
    @Step("Assert response contains {key} = {expectedValue}")
    public static void assertResponseContains(Response response, String key, Object expectedValue) {
        Object actualValue = response.jsonPath().get(key);
        assertEquals(expectedValue, actualValue,
                String.format("Expected '%s' to be '%s' but got '%s'", key, expectedValue, actualValue));
        logger.debug("Response contains assertion passed for key: {}", key);
    }
    
    /**
     * Asserts that the response contains a specific key and its value is not null.
     * 
     * @param response the API response
     * @param key the JSON path key
     */
    @Step("Assert response field {key} is not null")
    public static void assertResponseNotNull(Response response, String key) {
        Object value = response.jsonPath().get(key);
        assertNotNull(value,
                String.format("Expected '%s' to be present and not null in response", key));
        logger.debug("Response not null assertion passed for key: {}", key);
    }
    
    /**
     * Asserts that the error response contains the expected error message.
     * 
     * @param response the API response
     * @param expectedMessage the expected error message (can be partial match)
     */
    public static void assertErrorMessage(Response response, String expectedMessage) {
        String actualMessage = response.jsonPath().getString("message");
        assertNotNull(actualMessage, "Error message should be present in response");
        assertTrue(actualMessage.contains(expectedMessage),
                String.format("Expected error message to contain '%s' but got '%s'",
                        expectedMessage, actualMessage));
        logger.debug("Error message assertion passed");
    }
    
    /**
     * Asserts that a collection in the response has the expected size.
     * 
     * @param response the API response
     * @param jsonPath the JSON path to the collection
     * @param expectedSize the expected collection size
     */
    public static void assertCollectionSize(Response response, String jsonPath, int expectedSize) {
        int actualSize = response.jsonPath().getList(jsonPath).size();
        assertEquals(expectedSize, actualSize,
                String.format("Expected collection at '%s' to have size %d but got %d",
                        jsonPath, expectedSize, actualSize));
        logger.debug("Collection size assertion passed for path: {}", jsonPath);
    }
    
    /**
     * Asserts that the response time is within the specified threshold.
     * 
     * @param response the API response
     * @param thresholdMs the maximum acceptable response time in milliseconds
     */
    public static void assertResponseTime(Response response, long thresholdMs) {
        long actualTime = response.getTime();
        assertTrue(actualTime <= thresholdMs,
                String.format("Response time %d ms exceeded threshold of %d ms",
                        actualTime, thresholdMs));
        logger.debug("Response time assertion passed: {} ms (threshold: {} ms)", actualTime, thresholdMs);
    }
    
    /**
     * Asserts that a response field is a non-empty string.
     * 
     * @param response the API response
     * @param key the JSON path key
     */
    public static void assertNonEmptyString(Response response, String key) {
        String value = response.jsonPath().getString(key);
        assertNotNull(value, String.format("Field '%s' should not be null", key));
        assertFalse(value.trim().isEmpty(),
                String.format("Field '%s' should not be empty", key));
        logger.debug("Non-empty string assertion passed for key: {}", key);
    }
    
    /**
     * Asserts that authentication is required for the endpoint.
     * Verifies that the response returns 401 Unauthorized when no authentication is provided.
     * 
     * HTTP 401 Unauthorized indicates that the request lacks valid authentication credentials.
     * This is different from 403 Forbidden, which means the user is authenticated but lacks permissions.
     * 
     * @param response the API response
     */
    @Step("Assert authentication is required (401 Unauthorized)")
    public static void assertAuthenticationRequired(Response response) {
        int actualCode = response.getStatusCode();
        assertEquals(401, actualCode,
                String.format("Expected 401 Unauthorized (authentication required) but got %d. " +
                        "Response body: %s", actualCode, response.getBody().asString()));
        logger.debug("Authentication required assertion passed (401 Unauthorized)");
    }
    
    /**
     * Asserts that authorization failed for the endpoint.
     * Verifies that the response returns 403 Forbidden when the user is authenticated but lacks permissions.
     * 
     * HTTP 403 Forbidden indicates that the server understood the request but refuses to authorize it.
     * The user is authenticated (has valid credentials) but doesn't have sufficient permissions.
     * This is different from 401 Unauthorized, which means no valid authentication was provided.
     * 
     * @param response the API response
     */
    @Step("Assert authorization failed (403 Forbidden)")
    public static void assertAuthorizationFailed(Response response) {
        int actualCode = response.getStatusCode();
        assertEquals(403, actualCode,
                String.format("Expected 403 Forbidden (insufficient permissions) but got %d. " +
                        "Response body: %s", actualCode, response.getBody().asString()));
        logger.debug("Authorization failed assertion passed (403 Forbidden)");
    }
}
