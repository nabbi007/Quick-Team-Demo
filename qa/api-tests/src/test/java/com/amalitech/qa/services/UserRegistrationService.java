package com.amalitech.qa.services;

import com.amalitech.qa.client.ApiClient;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.models.request.RegisterRequest;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing test user lifecycle - creation, credential storage, and cleanup.
 * Generates unique test users with timestamp-based email addresses to prevent conflicts
 * in parallel test execution.
 */
public class UserRegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(UserRegistrationService.class);
    
    private final ApiClient apiClient;
    private final List<TestUser> createdUsers;
    private TestUser currentTestUser;
    
    private static final String DEFAULT_PASSWORD = "SecurePass@123";
    private static final String DEFAULT_NAME = "Test User";
    private static final String EMAIL_TEMPLATE = "testuser_%d@test.example.com";
    
    public UserRegistrationService(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.createdUsers = new ArrayList<>();
    }
    
    /**
     * Registers a new test user with unique credentials.
     * Email is generated using timestamp to ensure uniqueness across parallel tests.
     * 
     * @return TestUser object containing credentials and token
     */
    public TestUser registerTestUser() {
        long timestamp = System.currentTimeMillis();
        String email = String.format(EMAIL_TEMPLATE, timestamp);
        
        return registerTestUser(DEFAULT_NAME, email, DEFAULT_PASSWORD);
    }
    
    /**
     * Registers a test user with specific credentials.
     * 
     * @param name User's full name
     * @param email User's email (must be unique)
     * @param password User's password
     * @return TestUser object containing credentials and token
     */
    public TestUser registerTestUser(String name, String email, String password) {
        logger.info("Registering test user with email: {}", email);
        
        try {
            RegisterRequest registerRequest = new RegisterRequest(name, email, password);
            Response response = apiClient.post("/api/auth/register", registerRequest);
            
            if (response.getStatusCode() == 200 || response.getStatusCode() == 201) {
                TestUser testUser = new TestUser();
                testUser.setName(name);
                testUser.setEmail(email);
                testUser.setPassword(password);
                
                // Extract token from response
                String token = response.jsonPath().getString("token");
                testUser.setToken(token);
                
                // Extract user ID if available
                String userId = response.jsonPath().getString("userId");
                if (userId == null) {
                    userId = response.jsonPath().getString("id");
                }
                testUser.setUserId(userId);
                
                // Store for cleanup
                createdUsers.add(testUser);
                currentTestUser = testUser;
                
                logger.info("Successfully registered test user: {}", email);
                return testUser;
            } else {
                logger.error("Failed to register test user. Status: {}, Body: {}", 
                    response.getStatusCode(), response.getBody().asString());
                throw new RuntimeException(
                    "Could not register test user. API returned: " + response.getStatusCode()
                );
            }
        } catch (Exception e) {
            logger.error("Exception during test user registration for email: {}", email, e);
            throw new RuntimeException("Test setup failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the most recently created test user.
     * 
     * @return TestUser object or null if no users have been created
     */
    public TestUser getCurrentTestUser() {
        return currentTestUser;
    }
    
    /**
     * Gets all created test users.
     * 
     * @return List of TestUser objects
     */
    public List<TestUser> getCreatedUsers() {
        return new ArrayList<>(createdUsers);
    }
    
    /**
     * Cleans up all test users created during the test.
     * Uses best-effort cleanup - continues even if some deletions fail.
     * Logs warnings for failed cleanups but doesn't throw exceptions.
     */
    public void cleanupTestUsers() {
        if (createdUsers.isEmpty()) {
            logger.debug("No test users to cleanup");
            return;
        }
        
        logger.info("Starting cleanup of {} test users", createdUsers.size());
        List<String> failedCleanups = new ArrayList<>();
        
        for (TestUser user : createdUsers) {
            try {
                if (user.getUserId() != null) {
                    Response response = apiClient.delete("/api/users/" + user.getUserId());
                    
                    // 204 No Content or 404 Not Found are both acceptable
                    if (response.getStatusCode() != 204 && response.getStatusCode() != 404) {
                        logger.warn("Failed to cleanup user {}: status {}", 
                            user.getEmail(), response.getStatusCode());
                        failedCleanups.add(user.getEmail());
                    } else {
                        logger.debug("Successfully cleaned up user: {}", user.getEmail());
                    }
                } else {
                    logger.warn("Cannot cleanup user {} - no user ID available", user.getEmail());
                    failedCleanups.add(user.getEmail());
                }
            } catch (Exception e) {
                logger.warn("Exception during cleanup of user {}: {}", 
                    user.getEmail(), e.getMessage());
                failedCleanups.add(user.getEmail());
            }
        }
        
        if (!failedCleanups.isEmpty()) {
            logger.warn("Failed to cleanup {} users: {}", 
                failedCleanups.size(), failedCleanups);
        } else {
            logger.info("Successfully cleaned up all {} test users", createdUsers.size());
        }
        
        // Clear the list regardless of cleanup success
        createdUsers.clear();
        currentTestUser = null;
    }
    
    /**
     * Cleans up a specific test user.
     * 
     * @param user the TestUser to cleanup
     */
    public void cleanupTestUser(TestUser user) {
        if (user == null) {
            return;
        }
        
        try {
            if (user.getUserId() != null) {
                Response response = apiClient.delete("/api/users/" + user.getUserId());
                
                if (response.getStatusCode() == 204 || response.getStatusCode() == 404) {
                    logger.debug("Successfully cleaned up user: {}", user.getEmail());
                    createdUsers.remove(user);
                    if (currentTestUser != null && currentTestUser.equals(user)) {
                        currentTestUser = null;
                    }
                } else {
                    logger.warn("Failed to cleanup user {}: status {}", 
                        user.getEmail(), response.getStatusCode());
                }
            }
        } catch (Exception e) {
            logger.warn("Exception during cleanup of user {}: {}", 
                user.getEmail(), e.getMessage());
        }
    }
}
