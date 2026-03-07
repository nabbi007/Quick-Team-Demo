package com.amalitech.qa.config;

/**
 * Exception thrown when configuration loading or validation fails.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class ConfigurationException extends RuntimeException {
    
    /**
     * Constructs a new ConfigurationException with the specified detail message.
     * 
     * @param message the detail message
     */
    public ConfigurationException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new ConfigurationException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
