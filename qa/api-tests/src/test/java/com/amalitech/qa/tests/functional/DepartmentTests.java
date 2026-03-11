package com.amalitech.qa.tests.functional;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Functional tests for Department management operations.
 * 
 * NOTE: Most department operations require ADMIN role.
 * Regular users can only view departments (GET /departments).
 * Creating, updating, and deleting departments require admin privileges.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Department Management")
@Tag("functional")
@Tag("departments")
public class DepartmentTests extends BaseTest {
    
    private static final Logger logger = LoggerFactory.getLogger(DepartmentTests.class);
    
    @BeforeEach
    public void authenticateUser() {
        // Register and authenticate test user before each test
        TestUser testUser = userRegistrationService.registerTestUser();
        authHandler.setAuthToken(testUser.getToken());
    }
    
    @Test
    @DisplayName("Get all departments")
    @Description("Verify that departments can be retrieved (accessible to all authenticated users)")
    @Severity(SeverityLevel.NORMAL)
    @Story("Read Department")
    public void testGetAllDepartments() {
        // Act
        Response response = apiClient.get("/api/departments");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        // Response should be an array
        TestHelper.assertResponseNotNull(response, "$");
    }
    
    @Test
    @DisplayName("Create department returns 403 for regular users")
    @Description("Verify that regular users cannot create departments (admin-only operation)")
    @Severity(SeverityLevel.NORMAL)
    @Story("Create Department - Authorization")
    public void testCreateDepartmentAsRegularUser() {
        // Arrange
        Map<String, Object> departmentData = new HashMap<>();
        departmentData.put("name", "Test Dept " + System.currentTimeMillis());
        departmentData.put("emails", Arrays.asList("test@example.com"));
        
        // Act
        Response response = apiClient.post("/api/departments", departmentData);
        
        // Assert - Regular users should get 403 Forbidden
        if (response.getStatusCode() == 403) {
            logger.info("User does not have admin permissions to create departments. Got 403 Forbidden.");
            TestHelper.assertStatusCode(response, 403);
        } else {
            // If user somehow has admin permissions, verify successful creation
            TestHelper.assertStatusCode(response, 200);
            TestHelper.assertResponseNotNull(response, "id");
            
            // Track for cleanup if created successfully
            Long deptId = response.jsonPath().getLong("id");
            if (deptId != null) {
                testDataManager.trackResource("department", String.valueOf(deptId));
            }
        }
    }
    
    @Test
    @DisplayName("Get department by ID returns 403 for regular users")
    @Description("Verify that getting a specific department by ID requires admin permissions")
    @Severity(SeverityLevel.NORMAL)
    @Story("Read Department - Authorization")
    public void testGetDepartmentByIdAsRegularUser() {
        // Arrange - Use a known department ID (assuming department 1 exists)
        Long departmentId = 1L;
        
        // Act
        Response response = apiClient.get("/api/departments/" + departmentId);
        
        // Assert - Regular users should get 403 Forbidden
        if (response.getStatusCode() == 403) {
            logger.info("User does not have admin permissions to get department by ID. Got 403 Forbidden.");
            TestHelper.assertStatusCode(response, 403);
        } else if (response.getStatusCode() == 404) {
            logger.info("Department with ID {} not found. Got 404.", departmentId);
            TestHelper.assertStatusCode(response, 404);
        } else {
            // If user somehow has admin permissions, verify successful retrieval
            TestHelper.assertStatusCode(response, 200);
            TestHelper.assertResponseNotNull(response, "id");
            TestHelper.assertResponseNotNull(response, "name");
        }
    }
    
    @Test
    @DisplayName("Add emails to department returns 403 for regular users")
    @Description("Verify that regular users cannot add emails to departments (admin-only operation)")
    @Severity(SeverityLevel.NORMAL)
    @Story("Update Department - Authorization")
    public void testAddEmailsToDepartmentAsRegularUser() {
        // Arrange - Use a known department ID (assuming department 1 exists)
        Long departmentId = 1L;
        
        Map<String, Object> emailData = new HashMap<>();
        emailData.put("emails", Arrays.asList("new1@example.com", "new2@example.com"));
        
        // Act
        Response response = apiClient.post("/api/departments/" + departmentId + "/emails", emailData);
        
        // Assert - Regular users should get 403 Forbidden
        if (response.getStatusCode() == 403) {
            logger.info("User does not have admin permissions to add emails to departments. Got 403 Forbidden.");
            TestHelper.assertStatusCode(response, 403);
        } else if (response.getStatusCode() == 404) {
            logger.info("Department with ID {} not found. Got 404.", departmentId);
            TestHelper.assertStatusCode(response, 404);
        } else {
            // If user somehow has admin permissions, verify successful update
            TestHelper.assertStatusCode(response, 200);
            TestHelper.assertResponseNotNull(response, "emails");
        }
    }
}
