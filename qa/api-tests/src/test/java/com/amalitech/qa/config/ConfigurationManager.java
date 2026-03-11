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
    
    /**
     * Private constructor to prevent direct instantiation.
     */
    private ConfigurationManager() {
        properties = new Properties();
        loadConfiguration();
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
     * Loads configuration from the application properties file.
     * 
     * @throws ConfigurationException if configuration file is missing or cannot be loaded
     */
    public void loadConfiguration() {
        String configFile = "config/application.properties";
        
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFile)) {
            if (input == null) {
                throw new ConfigurationException(
                    String.format("Configuration file not found: %s", configFile)
                );
            }
            
            properties.load(input);
            logger.info("Successfully loaded configuration from: {}", configFile);
            
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
            "api.base.url",
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
        return properties.getProperty("api.base.url");
    }
    
    /**
     * Gets the connection timeout in milliseconds.
     * 
     * @return the connection timeout
     */
    public int getConnectionTimeout() {
        return Integer.parseInt(properties.getProperty("connection.timeout", "10000"));
    }
    
    /**
     * Gets the response timeout in milliseconds.
     * 
     * @return the response timeout
     */
    public int getResponseTimeout() {
        return Integer.parseInt(properties.getProperty("response.timeout", "20000"));
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
