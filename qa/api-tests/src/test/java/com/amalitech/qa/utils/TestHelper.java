package com.amalitech.qa.utils;

import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

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
     * Asserts that a date string matches the expected format.
     * 
     * @param dateString the date string to validate
     * @param format the expected date format pattern
     */
    public static void assertDateFormat(String dateString, String format) {
        assertNotNull(dateString, "Date string should not be null");
        
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            LocalDateTime.parse(dateString, formatter);
            logger.debug("Date format assertion passed for: {}", dateString);
        } catch (DateTimeParseException e) {
            fail(String.format("Date string '%s' does not match expected format '%s'",
                    dateString, format));
        }
    }
    
    /**
     * Asserts that a string is a valid UUID.
     * 
     * @param uuid the string to validate as UUID
     */
    public static void assertValidUUID(String uuid) {
        assertNotNull(uuid, "UUID should not be null");
        
        try {
            UUID.fromString(uuid);
            logger.debug("Valid UUID assertion passed for: {}", uuid);
        } catch (IllegalArgumentException e) {
            fail(String.format("String '%s' is not a valid UUID", uuid));
        }
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
     * Asserts that a response field is a positive integer.
     * 
     * @param response the API response
     * @param key the JSON path key
     */
    public static void assertPositiveInteger(Response response, String key) {
        Integer value = response.jsonPath().getInt(key);
        assertNotNull(value, String.format("Field '%s' should not be null", key));
        assertTrue(value > 0,
                String.format("Field '%s' should be positive but got %d", key, value));
        logger.debug("Positive integer assertion passed for key: {}", key);
    }
}
