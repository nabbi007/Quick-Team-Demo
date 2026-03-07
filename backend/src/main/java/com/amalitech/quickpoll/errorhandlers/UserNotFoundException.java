package com.amalitech.quickpoll.errorhandlers;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
