package com.amalitech.quickpoll.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddEmailsRequest {
    @NotEmpty(message = "At least one email is required")
    private List<String> emails;
}
