package com.amalitech.qa.tests.performance;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.request.CreatePollRequest;
import com.amalitech.qa.models.request.LoginRequest;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Performance tests for API response times.
 * Validates that API endpoints respond within acceptable time thresholds.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Performance")
@Tag("performance")
@Tag("response-time")
public class ResponseTimeTests extends BaseTest {
    
    private static final long LOGIN_THRESHOLD_MS = 2000;
    private static final long GET_THRESHOLD_MS = 1000;
    private static final long POST_THRESHOLD_MS = 2000;
    
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
    @DisplayName("Login response time is within threshold")
    @Description("Verify that login endpoint responds within acceptable time")
    @Severity(SeverityLevel.NORMAL)
    @Story("Response Time")
    public void testLoginResponseTime() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest(
            "basitmohammed3612@gmail.com",
            "Bece@2018"
        );
        
        // Act
        Response response = apiClient.post("/api/auth/login", loginRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseTime(response, LOGIN_THRESHOLD_MS);
        
        // Track performance
        performanceMonitor.recordResponseTime("/api/auth/login", response);
        
        Allure.addAttachment("Response Time", response.getTime() + " ms");
    }
    
    @Test
    @DisplayName("Get user profile response time is within threshold")
    @Description("Verify that user profile endpoint responds within acceptable time")
    @Severity(SeverityLevel.NORMAL)
    @Story("Response Time")
    public void testGetUserProfileResponseTime() {
        // Act
        Response response = apiClient.get("/api/users/me");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseTime(response, GET_THRESHOLD_MS);
        
        // Track performance
        performanceMonitor.recordResponseTime("/api/users/me", response);
        
        Allure.addAttachment("Response Time", response.getTime() + " ms");
    }
    
    @Test
    @DisplayName("Create poll response time is within threshold")
    @Description("Verify that poll creation endpoint responds within acceptable time")
    @Severity(SeverityLevel.NORMAL)
    @Story("Response Time")
    public void testCreatePollResponseTime() {
        // Arrange
        CreatePollRequest pollRequest = new CreatePollRequest(
            "Performance Test Poll",
            "Testing response time",
            Arrays.asList("Fast", "Slow"),
            false
        );
        
        // Act
        Response response = apiClient.post("/api/polls", pollRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 201);
        TestHelper.assertResponseTime(response, POST_THRESHOLD_MS);
        
        // Track performance
        performanceMonitor.recordResponseTime("/api/polls", response);
        
        Allure.addAttachment("Response Time", response.getTime() + " ms");
        
        // Cleanup
        if (response.getStatusCode() == 201) {
            String pollId = response.jsonPath().getString("id");
            testDataManager.getCreatedResourceIds().add(pollId);
        }
    }
    
    @Test
    @DisplayName("Get all polls response time is within threshold")
    @Description("Verify that getting all polls responds within acceptable time")
    @Severity(SeverityLevel.NORMAL)
    @Story("Response Time")
    public void testGetAllPollsResponseTime() {
        // Act
        Response response = apiClient.get("/api/polls");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseTime(response, GET_THRESHOLD_MS);
        
        // Track performance
        performanceMonitor.recordResponseTime("/api/polls", response);
        
        Allure.addAttachment("Response Time", response.getTime() + " ms");
    }
    
    @Test
    @DisplayName("Get departments response time is within threshold")
    @Description("Verify that getting departments responds within acceptable time")
    @Severity(SeverityLevel.NORMAL)
    @Story("Response Time")
    public void testGetDepartmentsResponseTime() {
        // Act
        Response response = apiClient.get("/api/departments");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseTime(response, GET_THRESHOLD_MS);
        
        // Track performance
        performanceMonitor.recordResponseTime("/api/departments", response);
        
        Allure.addAttachment("Response Time", response.getTime() + " ms");
    }
}
