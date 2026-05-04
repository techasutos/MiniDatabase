package com.minidb.common.exception;

/**
 * Base exception for all MiniDB engine errors.
 * All module-level exceptions extend this class, enabling
 * fine-grained catch blocks across the engine.
 */
public class MiniDbException extends RuntimeException {

    private final ErrorCode code;

    public MiniDbException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public MiniDbException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "[" + code + "] " + getMessage();
    }
}

