package com.amalitech.qa.tests.functional;

import com.amalitech.qa.base.BaseTest;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.models.request.AddEmailsRequest;
import com.amalitech.qa.models.request.CreateDepartmentRequest;
import com.amalitech.qa.utils.TestHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Functional tests for department management operations.
 * Tests department creation, retrieval, and email management.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
@Epic("QuickPoll API")
@Feature("Department Management")
@Tag("functional")
@Tag("departments")
public class DepartmentTests extends BaseTest {
    
    @BeforeEach
    public void authenticateAdmin() {
        // Register and authenticate test user before each test
        // Note: This test may require admin permissions - adjust based on API behavior
        TestUser testUser = userRegistrationService.registerTestUser();
        authHandler.setAuthToken(testUser.getToken());
    }
    
    @Test
    @DisplayName("Create a new department")
    @Description("Verify that an admin can create a new department with emails")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Department Creation")
    public void testCreateDepartment() {
        // Arrange
        List<String> emails = Arrays.asList(
            "dept1@example.com",
            "dept2@example.com",
            "dept3@example.com"
        );
        CreateDepartmentRequest request = new CreateDepartmentRequest(
            "Test Department " + System.currentTimeMillis(),
            emails
        );
        
        // Act
        Response response = apiClient.post("/api/departments", request);
        
        // Assert
        TestHelper.assertStatusCode(response, 201);
        TestHelper.assertResponseNotNull(response, "id");
        TestHelper.assertResponseContains(response, "name", request.getName());
        TestHelper.assertCollectionSize(response, "emails", 3);
    }
    
    @Test
    @DisplayName("Get all departments")
    @Description("Verify that all departments can be retrieved")
    @Severity(SeverityLevel.NORMAL)
    @Story("Department Retrieval")
    public void testGetAllDepartments() {
        // Act
        Response response = apiClient.get("/api/departments");
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        response.then().assertThat().body("$", org.hamcrest.Matchers.instanceOf(List.class));
    }
    
    @Test
    @DisplayName("Get single department by ID")
    @Description("Verify that a specific department can be retrieved by its ID")
    @Severity(SeverityLevel.NORMAL)
    @Story("Department Retrieval")
    public void testGetDepartmentById() {
        // Arrange - Create a department first
        List<String> emails = Arrays.asList("test@example.com");
        CreateDepartmentRequest createRequest = new CreateDepartmentRequest(
            "Test Dept " + System.currentTimeMillis(),
            emails
        );
        Response createResponse = apiClient.post("/api/departments", createRequest);
        Long departmentId = createResponse.jsonPath().getLong("id");
        
        // Act
        Response response = apiClient.get("/api/departments/" + departmentId);
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseContains(response, "id", departmentId);
        TestHelper.assertResponseNotNull(response, "name");
        TestHelper.assertResponseNotNull(response, "emails");
    }
    
    @Test
    @DisplayName("Add emails to department")
    @Description("Verify that emails can be added to an existing department")
    @Severity(SeverityLevel.NORMAL)
    @Story("Department Email Management")
    public void testAddEmailsToDepartment() {
        // Arrange - Create a department first
        List<String> initialEmails = Arrays.asList("initial@example.com");
        CreateDepartmentRequest createRequest = new CreateDepartmentRequest(
            "Email Test Dept " + System.currentTimeMillis(),
            initialEmails
        );
        Response createResponse = apiClient.post("/api/departments", createRequest);
        Long departmentId = createResponse.jsonPath().getLong("id");
        
        // Prepare new emails to add
        List<String> newEmails = Arrays.asList(
            "newemail1@example.com",
            "newemail2@example.com"
        );
        AddEmailsRequest addEmailsRequest = new AddEmailsRequest(newEmails);
        
        // Act
        Response response = apiClient.post("/api/departments/" + departmentId + "/emails", addEmailsRequest);
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseNotNull(response, "emails");
    }
}
