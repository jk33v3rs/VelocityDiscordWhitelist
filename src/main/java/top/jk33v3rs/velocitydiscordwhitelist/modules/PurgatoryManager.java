package top.jk33v3rs.velocitydiscordwhitelist.modules;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;

/**
 * PurgatoryManager
 * 
 * Simple purgatory system that manages temporary whitelist sessions.
 * Players get temporary access through Discord, then are permanently whitelisted on first join.
 */
public class PurgatoryManager {
    
    private final Logger logger;
    private final SQLHandler sqlHandler;
    private final ExceptionHandler exceptionHandler;
    private final boolean debugEnabled;
    private final int sessionTimeoutMinutes;
    
    // Active purgatory sessions (username -> session)
    private final Map<String, PurgatorySession> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * Constructor for PurgatoryManager
     * 
     * @param logger The logger instance
     * @param sqlHandler The SQL handler for database operations
     * @param exceptionHandler The exception handler
     * @param debugEnabled Whether debug logging is enabled
     * @param sessionTimeoutMinutes Session timeout in minutes
     */
    public PurgatoryManager(Logger logger, SQLHandler sqlHandler, ExceptionHandler exceptionHandler, 
                           boolean debugEnabled, int sessionTimeoutMinutes) {
        this.logger = logger;
        this.sqlHandler = sqlHandler;
        this.exceptionHandler = exceptionHandler;
        this.debugEnabled = debugEnabled;
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }
    
    /**
     * createDiscordSession
     * 
     * Creates a purgatory session from Discord /mc command.
     * Allows temporary server access until player joins and gets whitelisted.
     * 
     * @param username The Minecraft username
     * @param discordUserId The Discord user ID
     * @param discordUsername The Discord username
     * @return ValidationResult indicating success or failure
     */
    public ValidationResult createDiscordSession(String username, long discordUserId, String discordUsername) {
        try {
            // Check if already whitelisted
            if (sqlHandler.isPlayerWhitelistedByUsername(username)) {
                return ValidationResult.failure("Player is already whitelisted");
            }
            
            // Remove any existing session for this username
            activeSessions.remove(username);
            
            // Create new session
            PurgatorySession session = new PurgatorySession(username, discordUserId, discordUsername, sessionTimeoutMinutes);
            activeSessions.put(username, session);
            
            debugLog("Created purgatory session for " + username + " (Discord: " + discordUsername + ")");
            return ValidationResult.success();
            
        } catch (Exception e) {
            logger.error("Error creating purgatory session for {}", username, e);
            exceptionHandler.handleDiscordException(null, "Error creating purgatory session", e);
            return ValidationResult.failure("Internal error creating session");
        }
    }
    
    /**
     * isInPurgatory
     * 
     * Checks if a player has an active purgatory session.
     * 
     * @param username The Minecraft username
     * @return true if player has active session, false otherwise
     */
    public boolean isInPurgatory(String username) {
        PurgatorySession session = activeSessions.get(username);
        if (session == null) {
            return false;
        }
        
        // Check if session expired
        if (session.isExpired()) {
            activeSessions.remove(username);
            debugLog("Removed expired purgatory session for " + username);
            return false;
        }
        
        return true;
    }
    
    /**
     * getSession
     * 
     * Gets the purgatory session for a username.
     * 
     * @param username The Minecraft username
     * @return Optional containing the session if found and not expired
     */
    public Optional<PurgatorySession> getSession(String username) {
        PurgatorySession session = activeSessions.get(username);
        if (session == null) {
            return Optional.empty();
        }
        
        // Check if expired
        if (session.isExpired()) {
            activeSessions.remove(username);
            debugLog("Removed expired purgatory session for " + username);
            return Optional.empty();
        }
        
        return Optional.of(session);
    }
    
    /**
     * completeVerificationOnJoin
     * 
     * Completes verification when player joins server.
     * Adds them to permanent whitelist and removes purgatory session.
     * 
     * @param username The Minecraft username
     * @param playerUuid The player's UUID
     * @return true if verification completed successfully
     */
    public boolean completeVerificationOnJoin(String username, java.util.UUID playerUuid) {
        PurgatorySession session = activeSessions.get(username);
        if (session == null) {
            return false;
        }
        
        try {
            // Add to permanent whitelist
            boolean added = sqlHandler.addPlayerToWhitelist(
                playerUuid, 
                username, 
                String.valueOf(session.discordUserId), 
                session.discordUsername
            ).join();
            
            if (added) {
                // Remove from purgatory
                activeSessions.remove(username);
                logger.info("Player {} verified and added to whitelist (Discord: {})", username, session.discordUsername);
                return true;
            } else {
                logger.warn("Failed to add {} to whitelist during verification", username);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error completing verification for {}", username, e);
            exceptionHandler.handlePlayerException(null, "verification completion", e, username);
            return false;
        }
    }
    
    /**
     * removeSession
     * 
     * Removes a purgatory session.
     * 
     * @param username The Minecraft username
     */
    public void removeSession(String username) {
        PurgatorySession removed = activeSessions.remove(username);
        if (removed != null) {
            debugLog("Removed purgatory session for " + username);
        }
    }
    
    /**
     * cleanupExpiredSessions
     * 
     * Removes all expired sessions. Should be called periodically.
     * 
     * @return Number of sessions cleaned up
     */
    public int cleanupExpiredSessions() {
        int cleaned = 0;
        for (Map.Entry<String, PurgatorySession> entry : activeSessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                activeSessions.remove(entry.getKey());
                cleaned++;
            }
        }
        
        if (cleaned > 0) {
            debugLog("Cleaned up " + cleaned + " expired purgatory sessions");
        }
        
        return cleaned;
    }
    
    /**
     * getActiveSessionCount
     * 
     * Gets the number of active purgatory sessions.
     * 
     * @return Number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * debugLog
     * 
     * Logs debug messages if debug mode is enabled.
     * 
     * @param message The message to log
     */
    private void debugLog(String message) {
        if (debugEnabled) {
            logger.debug("[PurgatoryManager] {}", message);
        }
    }
    
    /**
     * PurgatorySession
     * 
     * Represents a temporary whitelist session.
     */
    public static class PurgatorySession {
        final String username;
        final long discordUserId;
        final String discordUsername;
        final Instant createdAt;
        final Instant expiresAt;
        
        public PurgatorySession(String username, long discordUserId, String discordUsername, int timeoutMinutes) {
            this.username = username;
            this.discordUserId = discordUserId;
            this.discordUsername = discordUsername;
            this.createdAt = Instant.now();
            this.expiresAt = createdAt.plusSeconds(timeoutMinutes * 60); // Convert minutes to seconds
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
        
        public String getUsername() { return username; }
        public long getDiscordUserId() { return discordUserId; }
        public String getDiscordUsername() { return discordUsername; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
    }
    
    /**
     * ValidationResult
     * 
     * Simple result class for validation operations.
     */
    public static class ValidationResult {
        private final boolean success;
        private final String errorMessage;
        
        private ValidationResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
}
