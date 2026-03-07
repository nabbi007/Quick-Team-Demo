package com.amalitech.qa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Singleton configuration manager for loading and providing access to environment-specific settings.
 * Supports multiple environments (dev, staging, prod) with thread-safe lazy initialization.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);
    private static volatile ConfigurationManager instance;
    private Properties properties;
    private String currentEnvironment;
    
    /**
     * Private constructor to prevent direct instantiation.
     */
    private ConfigurationManager() {
        properties = new Properties();
        // Load default environment (dev) on initialization
        String env = System.getProperty("env", "dev");
        loadConfiguration(env);
    }
    
    /**
     * Gets the singleton instance of ConfigurationManager with thread-safe lazy initialization.
     * 
     * @return the singleton ConfigurationManager instance
     */
    public static ConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (ConfigurationManager.class) {
                if (instance == null) {
                    instance = new ConfigurationManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Loads configuration from environment-specific properties file.
     * 
     * @param environment the environment name (dev, staging, prod)
     * @throws ConfigurationException if configuration file is missing or cannot be loaded
     */
    public void loadConfiguration(String environment) {
        String configFile = String.format("config/%s.properties", environment);
        
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFile)) {
            if (input == null) {
                throw new ConfigurationException(
                    String.format("Configuration file not found: %s", configFile)
                );
            }
            
            properties.load(input);
            currentEnvironment = environment;
            logger.info("Successfully loaded configuration for environment: {}", environment);
            
            // Validate required properties
            validateRequiredProperties();
            
        } catch (IOException e) {
            throw new ConfigurationException(
                String.format("Failed to load configuration file: %s", configFile), e
            );
        }
    }
    
    /**
     * Validates that all required configuration properties are present.
     * 
     * @throws ConfigurationException if required properties are missing
     */
    private void validateRequiredProperties() {
        String[] requiredProperties = {
            "base.url",
            "connection.timeout",
            "response.timeout"
        };
        
        StringBuilder missingProperties = new StringBuilder();
        for (String property : requiredProperties) {
            if (!properties.containsKey(property)) {
                if (missingProperties.length() > 0) {
                    missingProperties.append(", ");
                }
                missingProperties.append(property);
            }
        }
        
        if (missingProperties.length() > 0) {
            throw new ConfigurationException(
                String.format("Missing required properties: %s", missingProperties.toString())
            );
        }
    }
    
    /**
     * Gets the base URL for the API.
     * 
     * @return the base URL
     */
    public String getBaseUrl() {
        return properties.getProperty("base.url");
    }
    
    /**
     * Gets the performance threshold for a specific endpoint type.
     * 
     * @param endpointType the endpoint type (e.g., "get", "post", "list")
     * @return the performance threshold in milliseconds
     */
    public int getPerformanceThreshold(String endpointType) {
        String key = String.format("performance.threshold.%s", endpointType);
        return Integer.parseInt(properties.getProperty(key, "2000"));
    }
    
    /**
     * Gets the current environment name.
     * 
     * @return the current environment (dev, staging, prod)
     */
    public String getEnvironment() {
        return currentEnvironment;
    }
    
    /**
     * Gets a custom property value by key.
     * 
     * @param key the property key
     * @return the property value, or null if not found
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Gets a custom property value with a default fallback.
     * 
     * @param key the property key
     * @param defaultValue the default value if property is not found
     * @return the property value or default value
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
