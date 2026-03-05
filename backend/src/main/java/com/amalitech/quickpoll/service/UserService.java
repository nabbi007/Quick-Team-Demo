package com.amalitech.quickpoll.service;

import com.amalitech.quickpoll.dto.UserProfileResponse;
import com.amalitech.quickpoll.dto.UserUpdateRequest;
import com.amalitech.quickpoll.errorhandlers.UserNotFoundException;
import com.amalitech.quickpoll.mapper.UserMapper;
import com.amalitech.quickpoll.model.User;
import com.amalitech.quickpoll.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return userMapper.toProfileResponse(user);
    }

    public UserProfileResponse getCurrentUserProfile(User user) {
        return userMapper.toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateUser(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        userMapper.updateUserFromRequest(request, user);
        
        User updatedUser = userRepository.save(user);
        return userMapper.toProfileResponse(updatedUser);
    }
}
