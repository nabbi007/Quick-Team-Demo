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
    
    private final List<String> createdResourceIds;
    private final ApiClient apiClient;
    
    /**
     * Constructs a new TestDataManager with the specified API client.
     * 
     * @param apiClient the API client for making requests
     */
    public TestDataManager(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.createdResourceIds = new ArrayList<>();
    }
    
    /**
     * Creates a test poll with default values for quick test data setup.
     * 
     * @return the created poll ID
     */
    public String createTestPollWithDefaults() {
        Map<String, Object> defaultPoll = new HashMap<>();
        defaultPoll.put("question", "Test Poll: " + UUID.randomUUID().toString());
        defaultPoll.put("description", "Test Description");
        defaultPoll.put("options", Arrays.asList("Option 1", "Option 2", "Option 3"));
        defaultPoll.put("multipleChoice", false);
        
        logger.info("Creating test poll with default data");
        
        Response response = apiClient.post("/api/polls", defaultPoll);
        
        if (response.getStatusCode() == 201 || response.getStatusCode() == 200) {
            String pollId = response.jsonPath().getString("id");
            createdResourceIds.add(pollId);
            logger.info("Test poll created with ID: {}", pollId);
            return pollId;
        } else {
            logger.error("Failed to create test poll. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody().asString());
            throw new RuntimeException("Failed to create test poll");
        }
    }
    
    /**
     * Cleans up all test data created during the test.
     * Executes even if test fails to ensure proper cleanup.
     */
    public void cleanupTestData() {
        logger.info("Starting test data cleanup. Resources to clean: {}", createdResourceIds.size());
        
        for (String resourceId : createdResourceIds) {
            try {
                logger.debug("Cleaning up resource: {}", resourceId);
                Response response = apiClient.delete("/api/polls/" + resourceId);
                
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
        logger.info("Test data cleanup complete");
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
