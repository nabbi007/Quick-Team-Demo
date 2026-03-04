package com.amalitech.quickpoll.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class VoteRequest {
    @NotNull
    private List<Long> optionIds;
}
