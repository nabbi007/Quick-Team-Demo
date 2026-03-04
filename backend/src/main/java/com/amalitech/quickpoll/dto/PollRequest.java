package com.amalitech.quickpoll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PollRequest {
    @NotBlank
    private String question;
    private String description;
    @NotEmpty
    private List<String> options;
    private boolean multipleChoice;
}
