package com.amalitech.quickpoll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PollRequest {
    @NotBlank(message = "Question is required")
    private String question;
    private String description;
    @NotEmpty(message = "At least one option is required")
    private List<String> options;
    private boolean multipleChoice;
}
