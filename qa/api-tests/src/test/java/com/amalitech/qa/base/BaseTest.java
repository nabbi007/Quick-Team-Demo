package com.amalitech.qa.base;

import com.amalitech.qa.client.ApiClient;
import com.amalitech.qa.client.AuthenticationHandler;
import com.amalitech.qa.config.ConfigurationManager;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.services.PerformanceMonitor;
import com.amalitech.qa.services.TestDataManager;
import com.amalitech.qa.services.UserRegistrationService;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base test class providing common setup, teardown, and initialization for all test classes.
 * Extends this class to inherit shared test infrastructure and components.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public abstract class BaseTest {
    private static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    
    protected static ConfigurationManager config;
    protected ApiClient apiClient;
    protected AuthenticationHandler authHandler;
    protected TestDataManager testDataManager;
    protected PerformanceMonitor performanceMonitor;
    protected UserRegistrationService userRegistrationService;
    protected TestUser currentTestUser;
    
    /**
     * Suite-level setup executed once before all tests in the class.
     * Initializes configuration and Rest Assured base settings.
     */
    @BeforeAll
    public static void setupSuite() {
        logger.info("=== Starting Test Suite Setup ===");
        
        // Initialize configuration manager
        config = ConfigurationManager.getInstance();
        logger.info("Configuration loaded successfully");
        
        // Configure Rest Assured defaults
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        
        logger.info("=== Test Suite Setup Complete ===");
    }
    
    /**
     * Test-level setup executed before each test method.
     * Initializes API client, authentication handler, test data manager, 
     * user registration service, and performance monitor.
     */
    @BeforeEach
    public void setupTest() {
        logger.info("--- Setting up test ---");
        
        // Initialize authentication handler
        authHandler = new AuthenticationHandler();
        
        // Initialize API client with base URL and auth handler
        apiClient = new ApiClient(config.getBaseUrl(), authHandler);
        
        // Initialize test data manager
        testDataManager = new TestDataManager(apiClient);
        
        // Initialize user registration service
        userRegistrationService = new UserRegistrationService(apiClient);
        
        // Initialize performance monitor
        performanceMonitor = new PerformanceMonitor();
        
        logger.info("Test setup complete");
    }
    
    /**
     * Test-level teardown executed after each test method.
     * Triggers test data cleanup, user cleanup, and clears authentication.
     * Ensures cleanup happens even if tests fail.
     */
    @AfterEach
    public void teardownTest() {
        logger.info("--- Tearing down test ---");
        
        try {
            // Cleanup test data
            if (testDataManager != null) {
                testDataManager.cleanupTestData();
            }
            
            // Cleanup test users
            if (userRegistrationService != null) {
                userRegistrationService.cleanupTestUsers();
            }
            
            // Clear authentication
            if (authHandler != null) {
                authHandler.clearToken();
            }
            
            logger.info("Test teardown complete");
        } catch (Exception e) {
            logger.error("Error during test teardown", e);
            // Don't rethrow - allow test to complete even if cleanup fails
        }
    }
    
    /**
     * Suite-level teardown executed once after all tests in the class.
     * Performs final cleanup operations.
     */
    @AfterAll
    public static void teardownSuite() {
        logger.info("=== Test Suite Teardown ===");
        // Add any suite-level cleanup if needed
        logger.info("=== Test Suite Complete ===");
    }
}
