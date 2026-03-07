package com.amalitech.quickpoll.service;

import com.amalitech.quickpoll.config.JwtService;
import com.amalitech.quickpoll.dto.*;
import com.amalitech.quickpoll.errorhandlers.EmailAlreadyRegistered;
import com.amalitech.quickpoll.errorhandlers.InvalidTokenException;
import com.amalitech.quickpoll.errorhandlers.UserNotFoundException;
import com.amalitech.quickpoll.mapper.AuthMapper;
import com.amalitech.quickpoll.model.User;
import com.amalitech.quickpoll.model.enums.Role;
import com.amalitech.quickpoll.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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

    public AuthServiceResponse register(RegisterRequest request) {
        try {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new EmailAlreadyRegistered("Email already registered");
            }
            User user = authMapper.toUser(request);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setRole(Role.USER);
            userRepository.save(user);
            
            String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
            String refreshToken = jwtService.generateRefreshToken(user.getEmail(), user.getRole().name());
            
            return authMapper.toAuthServiceResponse(token, refreshToken, user.getEmail(), user.getFullName(), user.getRole().name());
        } catch (EmailAlreadyRegistered e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }

    public AuthServiceResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        if(!authentication.isAuthenticated()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        User user = (User) authentication.getPrincipal();

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail(), user.getRole().name());
        return authMapper.toAuthServiceResponse(token, refreshToken, user.getEmail(), user.getFullName(), user.getRole().name());
    }

    public AuthServiceResponse refreshToken(String refreshToken) {
           if (!jwtService.isTokenValid(refreshToken)) {
               throw new InvalidTokenException("Invalid refresh token");
           }

           String email = jwtService.extractEmail(refreshToken);
           User user = userRepository.findByEmail(email)
                   .orElseThrow(() -> new UserNotFoundException("User not found"));

           String newToken = jwtService.generateToken(user.getEmail(), user.getRole().name());
           String newRefreshToken = jwtService.generateRefreshToken(user.getEmail(), user.getRole().name());

           return authMapper.toAuthServiceResponse(newToken, newRefreshToken, user.getEmail(), user.getFullName(), user.getRole().name());
       }
}
