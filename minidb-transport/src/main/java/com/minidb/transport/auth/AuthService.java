package com.minidb.transport.auth;

/**
 * Authentication service contract.
 */
public interface AuthService {
    /** Returns true if credentials are valid. */
    boolean authenticate(String username, String password);
}

