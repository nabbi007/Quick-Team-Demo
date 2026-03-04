package com.amalitech.quickpoll.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AuthRequest {
    @Email @NotBlank
    private String email;
    @NotBlank
    private String password;
}
