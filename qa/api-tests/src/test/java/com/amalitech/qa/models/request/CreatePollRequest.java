package com.amalitech.qa.models.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request model for creating a new poll.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class CreatePollRequest {
    
    @NotNull(message = "Question is required")
    @Size(min = 5, max = 500, message = "Question must be between 5 and 500 characters")
    private String question;
    
    private String description;
    
    @NotNull(message = "Options are required")
    @Size(min = 2, max = 10, message = "Poll must have between 2 and 10 options")
    private List<String> options;
    
    private Boolean multipleChoice;
    
    public CreatePollRequest() {
    }
    
    public CreatePollRequest(String question, String description, List<String> options, Boolean multipleChoice) {
        this.question = question;
        this.description = description;
        this.options = options;
        this.multipleChoice = multipleChoice;
    }
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getOptions() {
        return options;
    }
    
    public void setOptions(List<String> options) {
        this.options = options;
    }
    
    public Boolean getMultipleChoice() {
        return multipleChoice;
    }
    
    public void setMultipleChoice(Boolean multipleChoice) {
        this.multipleChoice = multipleChoice;
    }
}
