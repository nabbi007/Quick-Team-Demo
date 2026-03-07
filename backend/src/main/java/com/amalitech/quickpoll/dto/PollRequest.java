package com.amalitech.quickpoll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PollRequest {
    @NotBlank(message = "Question is required")
    private String question;
    private String description;
    @NotEmpty(message = "At least one option is required")
    private List<String> options;
    private boolean multipleChoice;
    @NotNull(message = "Anonymity must be specified")
    private Boolean anonymous;
    @NotEmpty(message = "At least one department must be invited")
    private List<Long> departmentIds;
    @NotNull(message = "Expiration date is required")
    private LocalDateTime expiresAt;
}
