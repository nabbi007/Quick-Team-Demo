package com.amalitech.qa.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request model for creating a department.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class CreateDepartmentRequest {
    
    @NotBlank(message = "Department name is required")
    private String name;
    
    @NotEmpty(message = "At least one email is required")
    private List<String> emails;
    
    public CreateDepartmentRequest() {
    }
    
    public CreateDepartmentRequest(String name, List<String> emails) {
        this.name = name;
        this.emails = emails;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public List<String> getEmails() {
        return emails;
    }
    
    public void setEmails(List<String> emails) {
        this.emails = emails;
    }
}
