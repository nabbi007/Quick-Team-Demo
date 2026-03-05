package com.amalitech.quickpoll.errorhandlers;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
