package com.amalitech.quickpoll.service;

import com.amalitech.quickpoll.config.JwtService;
import com.amalitech.quickpoll.dto.*;
import com.amalitech.quickpoll.errorhandlers.EmailAlreadyRegistered;
import com.amalitech.quickpoll.mapper.AuthMapper;
import com.amalitech.quickpoll.model.User;
import com.amalitech.quickpoll.model.enums.Role;
import com.amalitech.quickpoll.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuthMapper authMapper;

    public AuthResponse register(RegisterRequest request) {
        try {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new EmailAlreadyRegistered("Email already registered");
            }
            User user = authMapper.toUser(request);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setRole("USER");
            
            String token = jwtService.generateToken(user.getEmail(), user.getRole());
            
            userRepository.save(user);
            AuthResponse response = authMapper.toAuthResponse(user);
            response.setToken(token);
            return response;
        } catch (EmailAlreadyRegistered e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }

    public AuthResponse login(AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
            User user = (User) authentication.getPrincipal();
            
            AuthResponse response = authMapper.toAuthResponse(user);
            response.setToken(jwtService.generateToken(user.getEmail(), user.getRole()));
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
    }
}
