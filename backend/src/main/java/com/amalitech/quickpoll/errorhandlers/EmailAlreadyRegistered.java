package com.amalitech.quickpoll.errorhandlers;

public class EmailAlreadyRegistered extends RuntimeException {
    public EmailAlreadyRegistered(String message) {
        super(message);
    }
}
