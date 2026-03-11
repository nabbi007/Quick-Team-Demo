package com.amalitech.qa.tests.security;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.models.request.CreatePollRequest;
import com.amalitech.qa.models.request.LoginRequest;
import com.amalitech.qa.models.request.RegisterRequest;
import com.amalitech.qa.utils.SecurityTestHelper;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for injection attack prevention.
 * Tests SQL injection, XSS, and other injection vulnerabilities.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Security")
@Tag("security")
@Tag("injection")
public class InjectionTests extends BaseTest {
    
    @Test
    @DisplayName("SQL injection in login email is prevented")
    @Description("Verify that SQL injection attempts in login email field are handled safely")
    @Severity(SeverityLevel.CRITICAL)
    @Story("SQL Injection Prevention")
    public void testSqlInjectionInLoginEmail() {
        // Arrange
        String sqlPayload = SecurityTestHelper.SQL_INJECTION_PAYLOADS[0]; // "' OR '1'='1"
        LoginRequest loginRequest = new LoginRequest(sqlPayload, "password");
        
        // Act
        Response response = apiClient.post("/api/auth/login", loginRequest);
        
        // Assert - Should reject malicious input or handle safely
        SecurityTestHelper.validateSqlInjectionProtection(response, sqlPayload, "email");
    }
    
    @Test
    @DisplayName("Multiple SQL injection payloads are prevented")
    @Description("Test various SQL injection patterns to ensure comprehensive protection")
    @Severity(SeverityLevel.CRITICAL)
    @Story("SQL Injection Prevention")
    public void testMultipleSqlInjectionPayloads() {
        // Test multiple SQL injection patterns
        for (String sqlPayload : SecurityTestHelper.SQL_INJECTION_PAYLOADS) {
            // Arrange
            LoginRequest loginRequest = new LoginRequest(sqlPayload, "password");
            
            // Act
            Response response = apiClient.post("/api/auth/login", loginRequest);
            
            // Assert - Should reject or handle safely
            int statusCode = response.getStatusCode();
            assertTrue(
                statusCode == 400 || statusCode == 401,
                String.format("SQL injection payload '%s' should be rejected (400) or fail authentication (401), got %d. Response: %s",
                    sqlPayload, statusCode, response.getBody().asString())
            );
        }
    }
    

    @Test
    @DisplayName("LDAP injection in email is prevented")
    @Description("Verify that LDAP injection attempts are handled safely")
    @Severity(SeverityLevel.NORMAL)
    @Story("LDAP Injection Prevention")
    public void testLdapInjectionInEmail() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest(
            "admin*)(uid=*))(|(uid=*",
            "password"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/login", loginRequest);
        
        // Assert - Should not succeed
        TestHelper.assertStatusCode(response, 400);
    }
    
    @Test
    @DisplayName("Command injection in user name is prevented")
    @Description("Verify that command injection attempts in user name are handled safely")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Command Injection Prevention")
    public void testCommandInjectionInUserName() {
        // Arrange
        String commandPayload = SecurityTestHelper.COMMAND_INJECTION_PAYLOADS[0]; // "; ls -la"
        String uniqueEmail = "testuser" + System.currentTimeMillis() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(
            commandPayload,
            uniqueEmail,
            "SecurePass@123"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/register", registerRequest);
        
        // Assert - Should either reject or sanitize
        SecurityTestHelper.validateCommandInjectionProtection(response, commandPayload, "name");
    }
    
    @Test
    @DisplayName("Multiple command injection payloads are prevented")
    @Description("Test various command injection patterns to ensure comprehensive protection")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Command Injection Prevention")
    public void testMultipleCommandInjectionPayloads() {
        // Test multiple command injection patterns
        for (int i = 0; i < SecurityTestHelper.COMMAND_INJECTION_PAYLOADS.length; i++) {
            String commandPayload = SecurityTestHelper.COMMAND_INJECTION_PAYLOADS[i];
            String uniqueEmail = "testuser" + System.currentTimeMillis() + i + "@example.com";
            
            // Arrange
            RegisterRequest registerRequest = new RegisterRequest(
                commandPayload,
                uniqueEmail,
                "SecurePass@123"
            );
            
            // Act
            Response response = apiClient.post("/api/auth/register", registerRequest);
            
            // Assert - Should either reject or sanitize
            SecurityTestHelper.validateCommandInjectionProtection(response, commandPayload, "name");
            
            // Small delay to ensure unique timestamps
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Test
    @DisplayName("Path traversal in API endpoint is prevented")
    @Description("Verify that path traversal attempts are blocked")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Path Traversal Prevention")
    public void testPathTraversalPrevention() {
        // Arrange - Register and authenticate test user
        TestUser testUser = userRegistrationService.registerTestUser();
        authHandler.setAuthToken(testUser.getToken());
        
        // Act - Try path traversal
        Response response = apiClient.get("/api/polls/../../../etc/passwd");
        
        // Assert - Should return 404 or 400, not expose file system
        int statusCode = response.getStatusCode();
        assertTrue(
            statusCode == 404 || statusCode == 400,
            "Path traversal should be blocked"
        );
    }
}
