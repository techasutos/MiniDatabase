package com.minidb.common.exception;

public class StorageException extends MiniDbException {
    public StorageException(ErrorCode code, String message) { super(code, message); }
    public StorageException(ErrorCode code, String message, Throwable cause) { super(code, message, cause); }
}

