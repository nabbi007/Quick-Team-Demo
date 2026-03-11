package com.amalitech.qa.services;

import com.amalitech.qa.client.ApiClient;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages test data creation, tracking, and cleanup for API tests.
 * Ensures test data isolation and automatic cleanup after test execution.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class TestDataManager {
    private static final Logger logger = LoggerFactory.getLogger(TestDataManager.class);
    
    // Resource type constants
    public static final String RESOURCE_TYPE_POLL = "poll";
    public static final String RESOURCE_TYPE_USER = "user";
    public static final String RESOURCE_TYPE_DEPARTMENT = "department";
    
    private final List<String> createdResourceIds;
    private final Map<String, String> resourceTypeMap; // resourceId -> resourceType
    private final ApiClient apiClient;
    
    /**
     * Constructs a new TestDataManager with the specified API client.
     * 
     * @param apiClient the API client for making requests
     */
    public TestDataManager(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.createdResourceIds = new ArrayList<>();
        this.resourceTypeMap = new HashMap<>();
    }
    
    /**
     * Creates a test poll with default values for quick test data setup.
     * Includes all required fields per API specification:
     * - title (required)
     * - question (required)
     * - description (optional)
     * - options (required)
     * - maxSelections (required)
     * - anonymous (required)
     * - departmentIds (required)
     * - expiresAt (required)
     * 
     * @return the created poll ID
     */
    public String createTestPollWithDefaults() {
        Map<String, Object> defaultPoll = new HashMap<>();
        defaultPoll.put("title", "Test Poll " + System.currentTimeMillis());
        defaultPoll.put("question", "Test Question: " + UUID.randomUUID().toString());
        defaultPoll.put("description", "Test Description");
        defaultPoll.put("options", Arrays.asList("Option 1", "Option 2", "Option 3"));
        defaultPoll.put("maxSelections", 1);
        defaultPoll.put("anonymous", false);
        defaultPoll.put("departmentIds", Arrays.asList(1)); // Default department ID
        
        // Set expiration to 7 days from now
        java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusDays(7);
        defaultPoll.put("expiresAt", expiresAt.toString());
        
        logger.info("Creating test poll with default data");
        
        Response response = apiClient.post("/api/polls", defaultPoll);
        
        if (response.getStatusCode() == 201 || response.getStatusCode() == 200) {
            String pollId = response.jsonPath().getString("id");
            if (pollId != null) {
                trackResource(RESOURCE_TYPE_POLL, pollId);
                logger.info("Test poll created with ID: {}", pollId);
                return pollId;
            } else {
                // Try getting id as integer
                Integer pollIdInt = response.jsonPath().getInt("id");
                if (pollIdInt != null) {
                    String pollIdStr = String.valueOf(pollIdInt);
                    trackResource(RESOURCE_TYPE_POLL, pollIdStr);
                    logger.info("Test poll created with ID: {}", pollIdStr);
                    return pollIdStr;
                }
            }
        }
        
        logger.error("Failed to create test poll. Status: {}, Body: {}",
                response.getStatusCode(), response.getBody().asString());
        throw new RuntimeException("Failed to create test poll. Status: " + response.getStatusCode() + 
                ", Response: " + response.getBody().asString());
    }
    
    /**
     * Creates a test poll with authentication token.
     * 
     * @param authToken Authentication token
     * @return the created poll ID
     */
    public String createTestPollWithAuth(String authToken) {
        // The ApiClient will automatically use the token from AuthenticationHandler
        // This method is provided for explicit test data creation
        return createTestPollWithDefaults();
    }
    
    /**
     * Tracks a resource for cleanup.
     * 
     * @param resourceType Type of resource (poll, user, department)
     * @param resourceId ID of the resource
     */
    public void trackResource(String resourceType, String resourceId) {
        if (resourceId != null && !resourceId.isEmpty()) {
            createdResourceIds.add(resourceId);
            resourceTypeMap.put(resourceId, resourceType);
            logger.debug("Tracking {} resource with ID: {}", resourceType, resourceId);
        }
    }
    
    /**
     * Cleans up all test data created during the test.
     * Executes even if test fails to ensure proper cleanup.
     * Handles multiple resource types (polls, users, departments).
     */
    public void cleanupTestData() {
        cleanupAllResources();
    }
    
    /**
     * Cleans up all tracked resources.
     * Uses resource type to determine the appropriate cleanup endpoint.
     */
    public void cleanupAllResources() {
        logger.info("Starting test data cleanup. Resources to clean: {}", createdResourceIds.size());
        
        for (String resourceId : createdResourceIds) {
            try {
                String resourceType = resourceTypeMap.getOrDefault(resourceId, RESOURCE_TYPE_POLL);
                String endpoint = getCleanupEndpoint(resourceType, resourceId);
                
                logger.debug("Cleaning up {} resource: {}", resourceType, resourceId);
                Response response = apiClient.delete(endpoint);
                
                if (response.getStatusCode() == 204 || response.getStatusCode() == 200 || response.getStatusCode() == 404) {
                    logger.debug("Resource {} cleaned up successfully", resourceId);
                } else {
                    logger.warn("Unexpected status code {} when cleaning up resource {}",
                            response.getStatusCode(), resourceId);
                }
            } catch (Exception e) {
                logger.warn("Failed to cleanup resource {}: {}", resourceId, e.getMessage());
            }
        }
        
        createdResourceIds.clear();
        resourceTypeMap.clear();
        logger.info("Test data cleanup complete");
    }
    
    /**
     * Gets the appropriate cleanup endpoint for a resource type.
     * 
     * @param resourceType the type of resource
     * @param resourceId the resource ID
     * @return the cleanup endpoint path
     */
    private String getCleanupEndpoint(String resourceType, String resourceId) {
        switch (resourceType) {
            case RESOURCE_TYPE_POLL:
                return "/api/polls/" + resourceId;
            case RESOURCE_TYPE_USER:
                return "/api/users/" + resourceId;
            case RESOURCE_TYPE_DEPARTMENT:
                return "/api/departments/" + resourceId;
            default:
                logger.warn("Unknown resource type: {}, defaulting to poll endpoint", resourceType);
                return "/api/polls/" + resourceId;
        }
    }
    
    /**
     * Gets the list of created resource IDs.
     * 
     * @return list of resource IDs
     */
    public List<String> getCreatedResourceIds() {
        return createdResourceIds;
    }
}
