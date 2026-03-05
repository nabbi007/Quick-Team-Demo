package com.amalitech.quickpoll.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class VoteRequest {
    @NotEmpty(message = "At least one option must be selected")
    private List<Long> optionIds;
}
