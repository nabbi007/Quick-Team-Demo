package com.amalitech.qa.exceptions;

/**
 * Exception thrown when test setup fails.
 * Indicates that the test could not be properly initialized.
 */
public class TestSetupException extends RuntimeException {
    
    /**
     * Constructs a new TestSetupException with the specified detail message.
     * 
     * @param message the detail message
     */
    public TestSetupException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new TestSetupException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public TestSetupException(String message, Throwable cause) {
        super(message, cause);
    }
}
