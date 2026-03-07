package com.amalitech.qa.models.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request model for updating user information.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class UpdateUserRequest {
    
    @NotBlank(message = "Name is required")
    private String name;
    
    public UpdateUserRequest() {
    }
    
    public UpdateUserRequest(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}
