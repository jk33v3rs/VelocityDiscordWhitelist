package top.jk33v3rs.velocitydiscordwhitelist.utils;

/**
 * Discord-specific exception for the VelocityDiscordWhitelist plugin.
 * 
 * This exception is thrown when Discord API operations fail, providing specific
 * error handling for Discord-related issues including API errors, rate limiting,
 * authentication failures, and permission issues.
 */
public class DiscordException extends VelocityDiscordWhitelistException {
    
    private final int discordErrorCode;
    private final String discordMessage;
    private final long retryAfter;
    private final String endpoint;
    
    /**
     * Constructor for DiscordException with Discord API error details.
     * 
     * Creates a Discord exception with specific API error information
     * for proper debugging and error tracking.
     * 
     * @param errorCode The plugin error code for this exception
     * @param message The detailed technical message for logging
     * @param discordErrorCode The Discord API error code
     * @param discordMessage The Discord API error message
     */
    public DiscordException(ErrorCode errorCode, String message, int discordErrorCode, String discordMessage) {
        super(errorCode, message);
        this.discordErrorCode = discordErrorCode;
        this.discordMessage = discordMessage;
        this.retryAfter = -1;
        this.endpoint = null;
    }
    
    /**
     * Constructor for DiscordException with rate limiting information.
     * 
     * Creates a Discord exception that includes rate limiting details
     * for proper retry handling.
     * 
     * @param errorCode The plugin error code for this exception
     * @param message The detailed technical message for logging
     * @param discordErrorCode The Discord API error code
     * @param discordMessage The Discord API error message
     * @param retryAfter The number of milliseconds to wait before retrying
     */
    public DiscordException(ErrorCode errorCode, String message, int discordErrorCode, String discordMessage, long retryAfter) {
        super(errorCode, message);
        this.discordErrorCode = discordErrorCode;
        this.discordMessage = discordMessage;
        this.retryAfter = retryAfter;
        this.endpoint = null;
    }
    
    /**
     * Constructor for DiscordException with endpoint context.
     * 
     * Creates a Discord exception that includes the API endpoint that failed,
     * which is useful for debugging endpoint-specific issues.
     * 
     * @param errorCode The plugin error code for this exception
     * @param message The detailed technical message for logging
     * @param discordErrorCode The Discord API error code
     * @param discordMessage The Discord API error message
     * @param endpoint The Discord API endpoint that caused the error
     */
    public DiscordException(ErrorCode errorCode, String message, int discordErrorCode, String discordMessage, String endpoint) {
        super(errorCode, message);
        this.discordErrorCode = discordErrorCode;
        this.discordMessage = discordMessage;
        this.retryAfter = -1;
        this.endpoint = endpoint;
    }
    
    /**
     * Constructor for DiscordException with full context.
     * 
     * Creates a Discord exception with complete error context including
     * the underlying cause, API error details, rate limit info, and endpoint.
     * 
     * @param errorCode The plugin error code for this exception
     * @param message The detailed technical message for logging
     * @param discordErrorCode The Discord API error code
     * @param discordMessage The Discord API error message
     * @param retryAfter The number of milliseconds to wait before retrying
     * @param endpoint The Discord API endpoint that caused the error
     * @param cause The underlying exception that caused this error
     */
    public DiscordException(ErrorCode errorCode, String message, int discordErrorCode, String discordMessage, 
                           long retryAfter, String endpoint, Throwable cause) {
        super(errorCode, message, cause);
        this.discordErrorCode = discordErrorCode;
        this.discordMessage = discordMessage;
        this.retryAfter = retryAfter;
        this.endpoint = endpoint;
    }
    
    /**
     * Constructor for DiscordException with cause only.
     * 
     * Creates a Discord exception wrapping an underlying exception
     * while preserving the original exception context.
     * 
     * @param errorCode The plugin error code for this exception
     * @param message The detailed technical message for logging
     * @param cause The underlying exception that caused this error
     */
    public DiscordException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.discordErrorCode = -1;
        this.discordMessage = null;
        this.retryAfter = -1;
        this.endpoint = null;
    }
    
    /**
     * Gets the Discord API error code.
     * 
     * @return The Discord API error code, or -1 if not available
     */
    public int getDiscordErrorCode() {
        return discordErrorCode;
    }
    
    /**
     * Gets the Discord API error message.
     * 
     * @return The Discord API error message, or null if not available
     */
    public String getDiscordMessage() {
        return discordMessage;
    }
    
    /**
     * Gets the retry-after value for rate limited requests.
     * 
     * @return The number of milliseconds to wait before retrying, or -1 if not rate limited
     */
    public long getRetryAfter() {
        return retryAfter;
    }
    
    /**
     * Gets the Discord API endpoint that caused this error.
     * 
     * @return The API endpoint, or null if not available
     */
    public String getEndpoint() {
        return endpoint;
    }
    
    /**
     * Checks if this is a rate limiting error.
     * 
     * @return true if this is a rate limit error, false otherwise
     */
    public boolean isRateLimited() {
        return getErrorCode() == ErrorCode.DISCORD_RATE_LIMITED ||
               discordErrorCode == 429;
    }
    
    /**
     * Checks if this is an authentication error.
     * 
     * @return true if this is an authentication error, false otherwise
     */
    public boolean isAuthenticationError() {
        return getErrorCode() == ErrorCode.DISCORD_AUTHENTICATION_FAILED ||
               discordErrorCode == 401;
    }
    
    /**
     * Checks if this is a permission error.
     * 
     * @return true if this is a permission error, false otherwise
     */
    public boolean isPermissionError() {
        return getErrorCode() == ErrorCode.DISCORD_PERMISSION_DENIED ||
               discordErrorCode == 403;
    }
    
    /**
     * Checks if this is a "not found" error.
     * 
     * @return true if this is a not found error, false otherwise
     */
    public boolean isNotFoundError() {
        return getErrorCode() == ErrorCode.DISCORD_USER_NOT_FOUND ||
               getErrorCode() == ErrorCode.DISCORD_CHANNEL_NOT_FOUND ||
               discordErrorCode == 404;
    }
}