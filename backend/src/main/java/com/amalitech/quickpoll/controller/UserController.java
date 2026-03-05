package com.amalitech.quickpoll.controller;


import com.amalitech.quickpoll.dto.UserProfileResponse;
import com.amalitech.quickpoll.dto.UserUpdateRequest;
import com.amalitech.quickpoll.model.User;
import com.amalitech.quickpoll.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Retrieve authenticated user's profile information")
    public ResponseEntity<UserProfileResponse> getCurrentUser(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(userService.getCurrentUserProfile(user));
    }

    @PutMapping()
    @Operation(summary = "Update user profile", description = "Update authenticated user's profile information")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UserUpdateRequest request){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        UserProfileResponse response = userService.updateUser(user.getId(), request);
        return ResponseEntity.ok(response);
    }
}
