package com.amalitech.quickpoll.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AuthServiceResponse {
    private String token;
    private String refreshToken;
    private String email;
    private String name;
    private String role;
}
