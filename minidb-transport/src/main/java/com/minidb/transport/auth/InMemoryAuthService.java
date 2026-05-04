package com.minidb.transport.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory authentication service backed by SHA-256 hashed passwords.
 *
 * Default admin account is created on first boot if no users are registered.
 * In production, integrate with a persistent UserRepository backed by the catalog.
 */
public class InMemoryAuthService implements AuthService {

    private final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();

    public InMemoryAuthService() {
        // default credentials — override in production config
        addUser("admin", "minidb");
    }

    /**
     * Register a user. Password is stored as SHA-256 hex digest.
     */
    public void addUser(String username, String plainPassword) {
        users.put(username, sha256(plainPassword));
    }

    /**
     * Remove a user.
     */
    public void removeUser(String username) {
        users.remove(username);
    }

    @Override
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        String stored = users.get(username);
        return stored != null && stored.equals(sha256(password));
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

