package com.amalitech.quickpoll.service;

import com.amalitech.quickpoll.config.JwtService;
import com.amalitech.quickpoll.dto.*;
import com.amalitech.quickpoll.errorhandlers.EmailAlreadyRegistered;
import com.amalitech.quickpoll.mapper.AuthMapper;
import com.amalitech.quickpoll.model.User;
import com.amalitech.quickpoll.model.enums.Role;
import com.amalitech.quickpoll.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AuthMapper authMapper;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest("John Doe", "john@example.com", "password123");
        User user = new User();
        user.setEmail("john@example.com");
        user.setFullName("John Doe");
        user.setRole(Role.USER);

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(authMapper.toUser(request)).thenReturn(user);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("token");
        when(jwtService.generateRefreshToken(anyString(), anyString())).thenReturn("refreshToken");
        when(authMapper.toAuthServiceResponse(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new AuthServiceResponse("token", "refreshToken", "john@example.com", "John Doe", "USER"));

        AuthServiceResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("token", response.getToken());
        assertEquals("refreshToken", response.getRefreshToken());
        assertEquals("john@example.com", response.getEmail());
        verify(userRepository).save(user);
    }

    @Test
    void register_EmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("John Doe", "john@example.com", "password123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(EmailAlreadyRegistered.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_Success() {
        AuthRequest request = new AuthRequest("john@example.com", "password123");
        User user = new User();
        user.setEmail("john@example.com");
        user.setFullName("John Doe");
        user.setRole(Role.USER);

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(user);
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("token");
        when(jwtService.generateRefreshToken(anyString(), anyString())).thenReturn("refreshToken");
        when(authMapper.toAuthServiceResponse(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new AuthServiceResponse("token", "refreshToken", "john@example.com", "John Doe", "USER"));

        AuthServiceResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("token", response.getToken());
        assertEquals("john@example.com", response.getEmail());
    }

    @Test
    void login_InvalidCredentials() {
        AuthRequest request = new AuthRequest("john@example.com", "wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void refreshToken_Success() {
        String refreshToken = "validRefreshToken";
        User user = new User();
        user.setEmail("john@example.com");
        user.setFullName("John Doe");
        user.setRole(Role.USER);

        when(jwtService.isTokenValid(refreshToken)).thenReturn(true);
        when(jwtService.extractEmail(refreshToken)).thenReturn("john@example.com");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("newToken");
        when(jwtService.generateRefreshToken(anyString(), anyString())).thenReturn("newRefreshToken");
        when(authMapper.toAuthServiceResponse(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new AuthServiceResponse("newToken", "newRefreshToken", "john@example.com", "John Doe", "USER"));

        AuthServiceResponse response = authService.refreshToken(refreshToken);

        assertNotNull(response);
        assertEquals("newToken", response.getToken());
        assertEquals("newRefreshToken", response.getRefreshToken());
    }

    @Test
    void refreshToken_InvalidToken() {
        String refreshToken = "invalidToken";

        when(jwtService.isTokenValid(refreshToken)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.refreshToken(refreshToken));
    }

    @Test
    void refreshToken_UserNotFound() {
        String refreshToken = "validToken";

        when(jwtService.isTokenValid(refreshToken)).thenReturn(true);
        when(jwtService.extractEmail(refreshToken)).thenReturn("nonexistent@example.com");
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.refreshToken(refreshToken));
    }
}
