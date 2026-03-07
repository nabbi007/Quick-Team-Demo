package com.amalitech.quickpoll.mapper;

import com.amalitech.quickpoll.dto.AuthResponse;
import com.amalitech.quickpoll.dto.AuthServiceResponse;
import com.amalitech.quickpoll.dto.RegisterRequest;
import com.amalitech.quickpoll.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {
    
    @Mapping(target = "fullName", source = "name")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "password", ignore = true)
    User toUser(RegisterRequest request);
    
    AuthServiceResponse toAuthServiceResponse(String token, String refreshToken, String email, String name, String role);
    
    AuthResponse toAuthResponse(AuthServiceResponse serviceResponse);
}
