package com.amalitech.quickpoll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateResponse {
    private String message;
    private UserProfileResponse user;
}
