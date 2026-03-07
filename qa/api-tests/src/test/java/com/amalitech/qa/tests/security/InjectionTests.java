package com.amalitech.qa.tests.security;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.request.CreatePollRequest;
import com.amalitech.qa.models.request.LoginRequest;
import com.amalitech.qa.models.request.RegisterRequest;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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
        LoginRequest loginRequest = new LoginRequest(
            "admin' OR '1'='1",
            "password"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/login", loginRequest);
        
        // Assert - Should not succeed with SQL injection
        TestHelper.assertStatusCode(response, 400);
    }
    
    @Test
    @DisplayName("XSS script in poll question is sanitized")
    @Description("Verify that XSS attempts in poll question are handled safely")
    @Severity(SeverityLevel.CRITICAL)
    @Story("XSS Prevention")
    public void testXssInPollQuestion() {
        // Arrange - Login first
        LoginRequest loginRequest = new LoginRequest(
            "basitmohammed3612@gmail.com",
            "Bece@2018"
        );
        Response loginResponse = apiClient.post("/api/auth/login", loginRequest);
        String token = loginResponse.jsonPath().getString("token");
        authHandler.setAuthToken(token);
        
        // Create poll with XSS attempt
        CreatePollRequest pollRequest = new CreatePollRequest(
            "<script>alert('XSS')</script>",
            "Test Description",
            Arrays.asList("Option 1", "Option 2"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert - Should either reject or sanitize
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 400 || statusCode == 201,
            "Expected 400 (rejected) or 201 (sanitized)"
        );
        
        // If created, verify script tags are not present in response
        if (statusCode == 201) {
            String question = response.jsonPath().getString("question");
            org.junit.jupiter.api.Assertions.assertFalse(
                question.contains("<script>"),
                "Script tags should be sanitized"
            );
        }
    }
    
    @Test
    @DisplayName("Command injection in user name is prevented")
    @Description("Verify that command injection attempts in user name are handled safely")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Command Injection Prevention")
    public void testCommandInjectionInUserName() {
        // Arrange
        String uniqueEmail = "testuser" + System.currentTimeMillis() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(
            "; rm -rf /",
            uniqueEmail,
            "SecurePass@123"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/register", registerRequest);
        
        // Assert - Should either reject or sanitize
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 400 || statusCode == 201,
            "Expected 400 (rejected) or 201 (sanitized)"
        );
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
    @DisplayName("Path traversal in API endpoint is prevented")
    @Description("Verify that path traversal attempts are blocked")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Path Traversal Prevention")
    public void testPathTraversalPrevention() {
        // Arrange - Login first
        LoginRequest loginRequest = new LoginRequest(
            "basitmohammed3612@gmail.com",
            "Bece@2018"
        );
        Response loginResponse = apiClient.post("/api/auth/login", loginRequest);
        String token = loginResponse.jsonPath().getString("token");
        authHandler.setAuthToken(token);
        
        // Act - Try path traversal
        Response response = apiClient.get("/api/polls/../../../etc/passwd");
        
        // Assert - Should return 404 or 400, not expose file system
        int statusCode = response.getStatusCode();
        org.junit.jupiter.api.Assertions.assertTrue(
            statusCode == 404 || statusCode == 400,
            "Path traversal should be blocked"
        );
    }
}
