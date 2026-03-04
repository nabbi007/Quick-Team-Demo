package com.amalitech.quickpoll.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PollResponse {
    private Long id;
    private String question;
    private String description;
    private String creatorName;
    private String status;
    private boolean multipleChoice;
    private LocalDateTime createdAt;
    private int totalVotes;
    private List<OptionResponse> options;
}
