package com.amalitech.qa.exceptions;

/**
 * Exception thrown when authentication fails during test execution.
 * Indicates that the test user could not be authenticated with the API.
 */
public class AuthenticationException extends RuntimeException {
    
    /**
     * Constructs a new AuthenticationException with the specified detail message.
     * 
     * @param message the detail message
     */
    public AuthenticationException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new AuthenticationException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
