package com.minidb.common.exception;

public class AuthException extends MiniDbException {
    public AuthException(ErrorCode code, String message) { super(code, message); }
}

