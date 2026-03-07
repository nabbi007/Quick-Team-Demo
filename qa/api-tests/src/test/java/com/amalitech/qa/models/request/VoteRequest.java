package com.amalitech.qa.models.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request model for casting a vote on a poll.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class VoteRequest {
    
    @NotNull(message = "Poll ID is required")
    private String pollId;
    
    @NotNull(message = "Option index is required")
    @Min(value = 0, message = "Option index must be non-negative")
    private Integer optionIndex;
    
    @NotNull(message = "Voter ID is required")
    private String voterId;
    
    public VoteRequest() {
    }
    
    public VoteRequest(String pollId, Integer optionIndex, String voterId) {
        this.pollId = pollId;
        this.optionIndex = optionIndex;
        this.voterId = voterId;
    }
    
    public String getPollId() {
        return pollId;
    }
    
    public void setPollId(String pollId) {
        this.pollId = pollId;
    }
    
    public Integer getOptionIndex() {
        return optionIndex;
    }
    
    public void setOptionIndex(Integer optionIndex) {
        this.optionIndex = optionIndex;
    }
    
    public String getVoterId() {
        return voterId;
    }
    
    public void setVoterId(String voterId) {
        this.voterId = voterId;
    }
}
