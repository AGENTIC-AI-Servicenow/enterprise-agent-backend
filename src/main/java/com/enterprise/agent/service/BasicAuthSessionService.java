package com.enterprise.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages per-user Basic Auth sessions.
 * Each user can log in with their own ServiceNow username/password.
 * Credentials are stored Base64-encoded in memory (MVP - use encrypted store in production).
 */
@Service
public class BasicAuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(BasicAuthSessionService.class);

    // userId -> Base64("username:password")
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    // userId -> username (for display/validation)
    private final Map<String, String> usernames = new ConcurrentHashMap<>();

    /**
     * Creates a new Basic Auth session for the given credentials.
     *
     * @param username ServiceNow username
     * @param password ServiceNow password
     * @return A unique userId for this session
     */
    public String createSession(String username, String password) {
        String userId = "basic_" + System.currentTimeMillis();
        String encoded = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        sessions.put(userId, encoded);
        usernames.put(userId, username);
        log.info("Basic Auth session created for user: {} -> userId: {}", username, userId);
        return userId;
    }

    /**
     * Returns the Base64-encoded "username:password" for a given userId.
     *
     * @param userId The session userId
     * @return Base64 string or null if session not found
     */
    public String getEncodedCredentials(String userId) {
        return sessions.get(userId);
    }

    /**
     * Returns the plain username associated with a userId.
     *
     * @param userId The session userId
     * @return Username or null
     */
    public String getUsername(String userId) {
        return usernames.get(userId);
    }

    /**
     * Checks if a Basic Auth session exists for the given userId.
     */
    public boolean hasSession(String userId) {
        return userId != null && sessions.containsKey(userId);
    }

    /**
     * Removes the session for the given userId.
     */
    public void invalidateSession(String userId) {
        String username = usernames.remove(userId);
        sessions.remove(userId);
        log.info("Basic Auth session invalidated for userId: {} (username: {})", userId, username);
    }

    /**
     * Returns the total number of active Basic Auth sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
