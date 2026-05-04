package com.minidb.common.exception;

public class TransactionException extends MiniDbException {
    public TransactionException(ErrorCode code, String message) { super(code, message); }
    public TransactionException(ErrorCode code, String message, Throwable cause) { super(code, message, cause); }
}

