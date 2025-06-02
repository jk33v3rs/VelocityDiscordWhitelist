package top.jk33v3rs.velocitydiscordwhitelist.modules;

import org.slf4j.Logger;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * EnhancedPurgatoryManager handles verification sessions and Discord account linking.
 */
public class EnhancedPurgatoryManager {
    
    /**
     * Validation session container
     */
    public class PurgatorySession {
        private final String validationCode;
        private final long expirationTime;
        private Long discordUserId;
        private String discordUsername;
        private String allowedServer;
        private int verificationAttempts;
        private boolean codeUsed;
        public static final int MAX_VERIFICATION_ATTEMPTS = 4;
        
        /**
         * Creates new PurgatorySession with validation code and expiration time
         */
        public PurgatorySession(String validationCode, long expirationTime) {
            this.validationCode = validationCode;
            this.expirationTime = expirationTime;
            this.verificationAttempts = 0;
            this.codeUsed = false;
            this.allowedServer = "hub"; // Default to hub server
        }
        
        /**
         * Constructs a new PurgatorySession with the given validation code, expiration time and Discord info
         * 
         * @param validationCode The validation code for this session
         * @param expirationTime The time when this session expires (milliseconds since epoch)
         * @param discordUserId The Discord user ID
         * @param discordUsername The Discord username
         */
        public PurgatorySession(String validationCode, long expirationTime, Long discordUserId, String discordUsername) {
            this.validationCode = validationCode;
            this.expirationTime = expirationTime;
            this.discordUserId = discordUserId;
            this.discordUsername = discordUsername;
            this.verificationAttempts = 0;
            this.codeUsed = false;
            this.allowedServer = "hub"; // Default to hub server
        }
        
        /**
         * Gets the validation code for this session
         * 
         * @return The validation code
         */
        public String getValidationCode() {
            return validationCode;
        }
        
        /**
         * Checks if this session has expired
         * 
         * @return true if the session has expired, false otherwise
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
        
        /**
         * Gets the Discord user ID
         * 
         * @return The Discord user ID, or null if not set
         */
        public Long getDiscordUserId() {
            return discordUserId;
        }
        
        /**
         * Sets the Discord user ID
         * 
         * @param discordUserId The Discord user ID
         */
        public void setDiscordUserId(Long discordUserId) {
            this.discordUserId = discordUserId;
        }
        
        /**
         * Gets the Discord username
         * 
         * @return The Discord username, or null if not set
         */
        public String getDiscordUsername() {
            return discordUsername;
        }
        
        /**
         * Sets the Discord username
         * 
         * @param discordUsername The Discord username
         */
        public void setDiscordUsername(String discordUsername) {
            this.discordUsername = discordUsername;
        }
        
        /**
         * Gets the allowed server for this session
         * 
         * @return The server name
         */
        public String getAllowedServer() {
            return allowedServer;
        }
        
        /**
         * Sets the allowed server for this session
         * 
         * @param allowedServer The server name
         */
        public void setAllowedServer(String allowedServer) {
            this.allowedServer = allowedServer;
        }
        
        /**
         * Increments and gets the verification attempts counter
         * 
         * @return The new number of verification attempts
         */
        public int incrementVerificationAttempts() {
            return ++verificationAttempts;
        }
        
        /**
         * Gets the current number of verification attempts
         * 
         * @return The number of verification attempts
         */
        public int getVerificationAttempts() {
            return verificationAttempts;
        }
        
        /**
         * Checks if this session has been used
         * 
         * @return true if the code has been successfully used, false otherwise
         */
        public boolean isCodeUsed() {
            return codeUsed;
        }
        
        /**
         * Marks this session as used
         */
        public void markAsUsed() {
            this.codeUsed = true;
        }
        
        /**
         * Gets the expiration time for this session
         * 
         * @return The expiration time in milliseconds since epoch
         */
        public long getExpirationTime() {
            return expirationTime;
        }
    }
    
    /**
     * Class representing the result of a validation operation
     */
    public class ValidationResult {
        private final boolean success;
        private final String validationCode;
        private final String errorMessage;
        
        /**
         * Constructs a new ValidationResult
         * 
         * @param success Whether the operation was successful
         * @param validationCode The validation code (may be null if not successful)
         * @param errorMessage An error message (may be null if successful)
         */
        public ValidationResult(boolean success, String validationCode, String errorMessage) {
            this.success = success;
            this.validationCode = validationCode;
            this.errorMessage = errorMessage;
        }
        
        /**
         * Checks if the operation was successful
         * 
         * @return true if successful, false otherwise
         */
        public boolean isSuccess() {
            return success;
        }
        
        /**
         * Gets the validation code
         * 
         * @return The validation code, or null if not successful
         */
        public String getValidationCode() {
            return validationCode;
        }
        
        /**
         * Gets the error message
         * 
         * @return The error message, or null if successful
         */
        public String getErrorMessage() {
            return errorMessage;
        }
    }
    
    private final ConcurrentHashMap<String, PurgatorySession> sessions;
    private final Logger logger;
    private final boolean debugEnabled;
    private final SQLHandler sqlHandler;
    private final int sessionTimeoutMinutes;
    private RewardsHandler rewardsHandler;
    private java.util.function.BiFunction<String, UUID, CompletableFuture<Boolean>> purgatoryRemovalCallback;

    /**
     * Constructs a new EnhancedPurgatoryManager
     * 
     * @param logger The logger instance
     * @param debugEnabled Whether debug logging is enabled
     * @param sqlHandler The SQL handler instance
     * @param sessionTimeoutMinutes The default timeout for sessions in minutes
     */
    public EnhancedPurgatoryManager(Logger logger, boolean debugEnabled, SQLHandler sqlHandler, int sessionTimeoutMinutes) {
        this.sessions = new ConcurrentHashMap<>();
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        this.sqlHandler = sqlHandler;
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        
        // Start periodic cleanup
        startCleanupTask();
    }
    
    /**
     * Starts the periodic cleanup task
     */
    private void startCleanupTask() {
        // This would use the Velocity scheduler in the main plugin
        // For now we'll just log that it would be started
        debugLog("Cleanup task would be started here");
    }
    
    /**
     * Creates a new validation session for a player
     * 
     * @param username The Minecraft username
     * @return ValidationResult containing the session info
     */
    public ValidationResult createSession(String username) {
        return createSession(username, sessionTimeoutMinutes);
    }
    
    /**
     * Creates a new validation session for a player with a specific timeout
     * 
     * @param username The Minecraft username
     * @param timeoutMinutes The timeout in minutes
     * @return ValidationResult containing the session info
     */
    public ValidationResult createSession(String username, int timeoutMinutes) {
        debugLog("Creating validation session for " + username + " with timeout " + timeoutMinutes + " minutes");
        
        // Check if a session already exists
        PurgatorySession existingSession = sessions.get(username);
        if (existingSession != null && !existingSession.isExpired()) {
            debugLog("Session already exists for " + username);
            return new ValidationResult(
                true, 
                existingSession.getValidationCode(), 
                "Existing session found"
            );
        }
        
        // Generate a new code
        String validationCode = generateValidationCode();
        long expirationTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(timeoutMinutes);
        PurgatorySession session = new PurgatorySession(validationCode, expirationTime);
        
        // Store the session
        sessions.put(username, session);
        
        debugLog("Created session for " + username + " with code " + validationCode);
        
        return new ValidationResult(true, validationCode, null);
    }
    
    /**
     * Creates a new validation session with Discord information
     * 
     * @param username The Minecraft username
     * @param discordUserId The Discord user ID
     * @param discordUsername The Discord username
     * @return ValidationResult containing the session info
     */
    public ValidationResult createDiscordSession(String username, long discordUserId, String discordUsername) {
        debugLog("Creating Discord validation session for " + username + " with Discord ID " + discordUserId);
        
        // Check if a session already exists
        PurgatorySession existingSession = sessions.get(username);
        if (existingSession != null && !existingSession.isExpired()) {
            // Update Discord info
            existingSession.setDiscordUserId(discordUserId);
            existingSession.setDiscordUsername(discordUsername);
            
            debugLog("Updated Discord info for existing session for " + username);
            return new ValidationResult(
                true, 
                existingSession.getValidationCode(), 
                "Existing session updated with Discord info"
            );
        }
        
        // Generate a new code
        String validationCode = generateValidationCode();
        long expirationTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(sessionTimeoutMinutes);
        PurgatorySession session = new PurgatorySession(validationCode, expirationTime, discordUserId, discordUsername);
        
        // Store the session
        sessions.put(username, session);
        
        debugLog("Created Discord session for " + username + " with code " + validationCode);
        
        return new ValidationResult(true, validationCode, null);
    }
    
    /**
     * Validates a code against a player's session
     * 
     * @param username The Minecraft username
     * @param code The validation code
     * @return true if valid, false otherwise
     */
    public boolean validateCode(String username, String code) {
        PurgatorySession session = sessions.get(username);
        if (session == null) {
            debugLog("No session found for " + username);
            return false;
        }
        
        if (session.isExpired()) {
            debugLog("Session expired for " + username);
            sessions.remove(username);
            return false;
        }
        
        if (session.isCodeUsed()) {
            debugLog("Code already used for " + username);
            return false;
        }
        
        int attempts = session.incrementVerificationAttempts();
        if (attempts > PurgatorySession.MAX_VERIFICATION_ATTEMPTS) {
            debugLog("Too many verification attempts for " + username);
            sessions.remove(username);
            return false;
        }
        
        if (session.getValidationCode().equals(code) || 
           session.getValidationCode().replace("-", "").equals(code) || 
           session.getValidationCode().equals(code.replace("-", ""))) {
            debugLog("Code validated successfully for " + username);
            return true;
        } else {
            debugLog("Invalid code for " + username + ": " + code);
            return false;
        }
    }
    
    /**
     * Completes the verification process after a successful code validation
     * 
     * @param username The Minecraft username
     * @param uuid The player's UUID
     * @return CompletableFuture that resolves to true if verification was completed successfully
     */
    public CompletableFuture<Boolean> completeVerification(String username, UUID uuid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        PurgatorySession session = sessions.get(username);
        if (session == null) {
            debugLog("No session found for " + username + " during verification completion");
            future.complete(false);
            return future;
        }
        
        if (session.isExpired()) {
            debugLog("Session expired for " + username + " during verification completion");
            sessions.remove(username);
            future.complete(false);
            return future;
        }
        
        // Mark the code as used
        session.markAsUsed();
        
        // Update database with verification state
        boolean dbUpdated = sqlHandler.updateVerificationState(uuid.toString(), "VERIFIED");
        
        // If Discord info is present, link the accounts
        Long discordUserId = session.getDiscordUserId();
        if (discordUserId != null) {
            boolean linked = sqlHandler.linkDiscordAccount(
                uuid.toString(),
                discordUserId,
                session.getDiscordUsername()
            );
            
            if (linked) {
                debugLog("Linked Discord account " + discordUserId + 
                      " to Minecraft account " + username);
            } else {
                logger.error("Failed to link Discord account to Minecraft account");
            }
        }
        
        if (dbUpdated) {
            debugLog("Completed verification for " + username);
            
            // Remove the session to lift purgatory restrictions
            sessions.remove(username);
            debugLog("Removed purgatory session for verified player: " + username);
            
            // Call purgatory removal callback if available
            if (purgatoryRemovalCallback != null) {
                purgatoryRemovalCallback.apply(username, uuid)
                    .thenAccept(removalSuccess -> {
                        if (removalSuccess) {
                            debugLog("Successfully removed purgatory restrictions for " + username);
                        } else {
                            logger.warn("Failed to remove purgatory restrictions for " + username);
                        }
                    })
                    .exceptionally(e -> {
                        logger.error("Error removing purgatory restrictions for " + username, e);
                        return null;
                    });
            }
            
            // Process rewards (if RewardsHandler is available)
            if (rewardsHandler != null) {
                rewardsHandler.handleVerificationRewards(uuid.toString(), session.getDiscordUserId())
                    .thenAccept(rewardsSuccess -> {
                        if (rewardsSuccess) {
                            debugLog("Successfully processed rewards for " + username);
                        } else {
                            logger.warn("Failed to process rewards for " + username);
                        }
                        future.complete(true);
                    })
                    .exceptionally(e -> {
                        logger.error("Error processing rewards", e);
                        future.complete(true); // Still complete the verification even if rewards fail
                        return null;
                    });
            } else {
                future.complete(true);
            }
        } else {
            logger.error("Failed to update verification state in database for " + username);
            future.complete(false);
        }
        
        return future;
    }
    
    /**
     * Binds a Discord ID to an existing validation session
     * 
     * @param username The Minecraft username
     * @param discordUserId The Discord user ID
     * @param discordUsername The Discord username
     * @return true if the binding was successful, false otherwise
     */
    public boolean bindDiscordToSession(String username, long discordUserId, String discordUsername) {
        PurgatorySession session = sessions.get(username);
        if (session == null) {
            debugLog("No session found for " + username + " when binding Discord ID");
            return false;
        }
        
        if (session.isExpired()) {
            debugLog("Session expired for " + username + " when binding Discord ID");
            sessions.remove(username);
            return false;
        }
        
        session.setDiscordUserId(discordUserId);
        session.setDiscordUsername(discordUsername);
        
        debugLog("Bound Discord ID " + discordUserId + " to session for " + username);
        return true;
    }
    
    /**
     * Gets the session for a username
     * 
     * @param username The Minecraft username
     * @return Optional containing the session, or empty if not found
     */
    public Optional<PurgatorySession> getSession(String username) {
        PurgatorySession session = sessions.get(username);
        if (session != null && !session.isExpired()) {
            return Optional.of(session);
        }
        return Optional.empty();
    }
    
    /**
     * Finds a session by validation code
     * 
     * @param code The validation code
     * @return Optional containing the username and session as a Map.Entry, or empty if not found
     */
    public Optional<Map.Entry<String, PurgatorySession>> findByCode(String code) {
        for (Map.Entry<String, PurgatorySession> entry : sessions.entrySet()) {
            if (!entry.getValue().isExpired() && entry.getValue().getValidationCode().equals(code)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Removes a session
     * 
     * @param username The Minecraft username
     */
    public void removeSession(String username) {
        sessions.remove(username);
        debugLog("Removed session for " + username);
    }
    
    /**
     * Generates a validation code
     * 
     * @return A random 6-character hex code
     */
    private String generateValidationCode() {
        StringBuilder codeBuilder = new StringBuilder();
        // Generate first three hex characters
        for (int i = 0; i < 3; i++) {
            codeBuilder.append(Integer.toHexString(ThreadLocalRandom.current().nextInt(16)).toUpperCase());
        }
        // Add the hyphen
        codeBuilder.append('-');
        // Generate last three hex characters
        for (int i = 0; i < 3; i++) {
            codeBuilder.append(Integer.toHexString(ThreadLocalRandom.current().nextInt(16)).toUpperCase());
        }
        return codeBuilder.toString();
    }
    
    /**
     * Logs debug messages if debug mode is enabled
     * 
     * @param message The message to log
     */
    private void debugLog(String message) {
        if (debugEnabled) {
            logger.info("[Purgatory] " + message);
        }
    }

    /**
     * Sets the rewards handler
     * 
     * @param rewardsHandler The rewards handler
     */
    public void setRewardsHandler(RewardsHandler rewardsHandler) {
        this.rewardsHandler = rewardsHandler;
    }
    
    /**
     * Sets the purgatory removal callback
     * 
     * @param callback The callback function to handle purgatory restriction removal
     */
    public void setPurgatoryRemovalCallback(java.util.function.BiFunction<String, UUID, CompletableFuture<Boolean>> callback) {
        this.purgatoryRemovalCallback = callback;
    }
}
