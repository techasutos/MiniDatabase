package com.minidb.common.exception;

public class SqlException extends MiniDbException {
    public SqlException(ErrorCode code, String message) { super(code, message); }
    public SqlException(ErrorCode code, String message, Throwable cause) { super(code, message, cause); }
}

