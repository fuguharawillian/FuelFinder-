package com.fuelfinder.modules.auth.service;

public class AccountForbiddenException extends RuntimeException {

    public AccountForbiddenException(String message) {
        super(message);
    }
}
