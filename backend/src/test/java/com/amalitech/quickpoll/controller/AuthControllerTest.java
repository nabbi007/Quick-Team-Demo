package com.amalitech.quickpoll.controller;

import com.amalitech.quickpoll.config.JwtService;
import com.amalitech.quickpoll.dto.*;
import com.amalitech.quickpoll.mapper.AuthMapper;
import com.amalitech.quickpoll.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, 
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private AuthMapper authMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private com.amalitech.quickpoll.repository.UserRepository userRepository;

    @MockBean
    private com.amalitech.quickpoll.service.CustomUserService customUserService;

    @Test
    void register_Success() throws Exception {
        RegisterRequest request = new RegisterRequest("John Doe", "john@example.com", "password123");
        AuthServiceResponse serviceResponse = new AuthServiceResponse("token", "refreshToken", "john@example.com", "John Doe", "USER");
        AuthResponse authResponse = new AuthResponse("token", "john@example.com", "John Doe", "USER");

        when(authService.register(any(RegisterRequest.class))).thenReturn(serviceResponse);
        when(authMapper.toAuthResponse(any(AuthServiceResponse.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void register_ValidationFails() throws Exception {
        RegisterRequest request = new RegisterRequest("", "invalid-email", "123");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_Success() throws Exception {
        AuthRequest request = new AuthRequest("john@example.com", "password123");
        AuthServiceResponse serviceResponse = new AuthServiceResponse("token", "refreshToken", "john@example.com", "John Doe", "USER");
        AuthResponse authResponse = new AuthResponse("token", "john@example.com", "John Doe", "USER");

        when(authService.login(any(AuthRequest.class))).thenReturn(serviceResponse);
        when(authMapper.toAuthResponse(any(AuthServiceResponse.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void login_ValidationFails() throws Exception {
        AuthRequest request = new AuthRequest("", "");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_Success() throws Exception {
        AuthServiceResponse serviceResponse = new AuthServiceResponse("newToken", "newRefreshToken", "john@example.com", "John Doe", "USER");
        AuthResponse authResponse = new AuthResponse("newToken", "john@example.com", "John Doe", "USER");

        when(authService.refreshToken(any(String.class))).thenReturn(serviceResponse);
        when(authMapper.toAuthResponse(any(AuthServiceResponse.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "validRefreshToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("newToken"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void register_EmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest("John Doe", "john@example.com", "password123");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new com.amalitech.quickpoll.errorhandlers.EmailAlreadyRegistered("Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_InvalidCredentials() throws Exception {
        AuthRequest request = new AuthRequest("john@example.com", "wrongpassword");

        when(authService.login(any(AuthRequest.class)))
                .thenThrow(new RuntimeException("Login failed: Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void refresh_InvalidToken() throws Exception {
        when(authService.refreshToken(any(String.class)))
                .thenThrow(new RuntimeException("Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "invalidToken")))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void refresh_MissingCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
