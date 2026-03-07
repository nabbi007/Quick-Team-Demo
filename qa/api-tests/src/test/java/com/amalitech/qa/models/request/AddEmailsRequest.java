package com.amalitech.qa.models.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request model for adding emails to a department.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class AddEmailsRequest {
    
    @NotEmpty(message = "At least one email is required")
    private List<String> emails;
    
    public AddEmailsRequest() {
    }
    
    public AddEmailsRequest(List<String> emails) {
        this.emails = emails;
    }
    
    public List<String> getEmails() {
        return emails;
    }
    
    public void setEmails(List<String> emails) {
        this.emails = emails;
    }
}
