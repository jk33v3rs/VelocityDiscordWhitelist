package top.jk33v3rs.velocitydiscordwhitelist.utils;

/**
 * Base exception class for VelocityDiscordWhitelist plugin.
 * 
 * This class provides a standardized exception hierarchy for the plugin,
 * including error codes, user-friendly messages, and proper error categorization.
 * All custom exceptions in the plugin should extend this base class.
 */
public class VelocityDiscordWhitelistException extends Exception {
    
    private final ErrorCode errorCode;
    private final String userMessage;
    private final boolean isRetryable;
    
    /**
     * Constructor for VelocityDiscordWhitelistException.
     * 
     * Creates a new exception with the specified error code and message.
     * The user message will be derived from the error code if not provided.
     * 
     * @param errorCode The specific error code for this exception
     * @param message The detailed technical message for logging
     */
    public VelocityDiscordWhitelistException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = errorCode.getUserMessage();
        this.isRetryable = errorCode.isRetryable();
    }
    
    /**
     * Constructor for VelocityDiscordWhitelistException with custom user message.
     * 
     * Creates a new exception with a custom user-friendly message that overrides
     * the default message from the error code.
     * 
     * @param errorCode The specific error code for this exception
     * @param message The detailed technical message for logging
     * @param userMessage The user-friendly message to display
     */
    public VelocityDiscordWhitelistException(ErrorCode errorCode, String message, String userMessage) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.isRetryable = errorCode.isRetryable();
    }
    
    /**
     * Constructor for VelocityDiscordWhitelistException with cause.
     * 
     * Creates a new exception wrapping another exception with proper error code
     * and message context.
     * 
     * @param errorCode The specific error code for this exception
     * @param message The detailed technical message for logging
     * @param cause The underlying exception that caused this error
     */
    public VelocityDiscordWhitelistException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.userMessage = errorCode.getUserMessage();
        this.isRetryable = errorCode.isRetryable();
    }
    
    /**
     * Constructor for VelocityDiscordWhitelistException with cause and custom user message.
     * 
     * Creates a new exception wrapping another exception with a custom user-friendly
     * message that overrides the default message from the error code.
     * 
     * @param errorCode The specific error code for this exception
     * @param message The detailed technical message for logging
     * @param userMessage The user-friendly message to display
     * @param cause The underlying exception that caused this error
     */
    public VelocityDiscordWhitelistException(ErrorCode errorCode, String message, String userMessage, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.isRetryable = errorCode.isRetryable();
    }
    
    /**
     * Gets the error code for this exception.
     * 
     * @return The ErrorCode enum value representing the type of error
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    /**
     * Gets the user-friendly message for this exception.
     * 
     * This message is safe to display to end users and should not contain
     * sensitive technical details or stack traces.
     * 
     * @return The user-friendly error message
     */
    public String getUserMessage() {
        return userMessage;
    }
    
    /**
     * Checks if this error is retryable.
     * 
     * Retryable errors are typically temporary issues like network problems
     * or database connection timeouts that may succeed if attempted again.
     * 
     * @return true if the operation can be retried, false otherwise
     */
    public boolean isRetryable() {
        return isRetryable;
    }
    
    /**
     * Gets the error category for this exception.
     * 
     * @return The ErrorCategory enum value representing the category of error
     */
    public ErrorCategory getCategory() {
        return errorCode.getCategory();
    }
    
    /**
     * Creates a formatted error message for logging.
     * 
     * This method creates a comprehensive error message that includes the error code,
     * category, user message, and technical details for proper logging.
     * 
     * @return A formatted error message suitable for logging
     */
    public String getFormattedMessage() {
        return String.format("[%s:%s] %s - %s", 
            errorCode.getCategory().name(), 
            errorCode.name(), 
            userMessage, 
            getMessage());
    }
}