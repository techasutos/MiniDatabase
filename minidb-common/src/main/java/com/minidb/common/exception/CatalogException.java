package com.minidb.common.exception;

public class CatalogException extends MiniDbException {
    public CatalogException(ErrorCode code, String message) { super(code, message); }
    public CatalogException(ErrorCode code, String message, Throwable cause) { super(code, message, cause); }
}

