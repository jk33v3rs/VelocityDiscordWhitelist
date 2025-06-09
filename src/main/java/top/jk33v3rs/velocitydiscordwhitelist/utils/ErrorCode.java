package top.jk33v3rs.velocitydiscordwhitelist.utils;

/**
 * Specific error codes for the VelocityDiscordWhitelist plugin.
 * 
 * This enum defines all possible error conditions that can occur within the plugin,
 * providing structured error handling with user-friendly messages and retry logic.
 */
public enum ErrorCode {
    
    // Database Errors (1000-1999)
    DATABASE_CONNECTION_FAILED(ErrorCategory.DATABASE, "Database connection failed", true),
    DATABASE_QUERY_FAILED(ErrorCategory.DATABASE, "Database query execution failed", false),
    DATABASE_TRANSACTION_FAILED(ErrorCategory.DATABASE, "Database transaction failed", true),
    DATABASE_TIMEOUT(ErrorCategory.DATABASE, "Database operation timed out", true),
    DATABASE_CONSTRAINT_VIOLATION(ErrorCategory.DATABASE, "Database constraint violation", false),
    DATABASE_MIGRATION_FAILED(ErrorCategory.DATABASE, "Database migration failed", false),
    
    // Discord Errors (2000-2999)
    DISCORD_CONNECTION_FAILED(ErrorCategory.DISCORD, "Failed to connect to Discord", true),
    DISCORD_API_ERROR(ErrorCategory.DISCORD, "Discord API error occurred", true),
    DISCORD_AUTHENTICATION_FAILED(ErrorCategory.DISCORD, "Discord authentication failed", false),
    DISCORD_PERMISSION_DENIED(ErrorCategory.DISCORD, "Insufficient Discord permissions", false),
    DISCORD_USER_NOT_FOUND(ErrorCategory.DISCORD, "Discord user not found", false),
    DISCORD_CHANNEL_NOT_FOUND(ErrorCategory.DISCORD, "Discord channel not found", false),
    DISCORD_RATE_LIMITED(ErrorCategory.DISCORD, "Discord API rate limit exceeded", true),
    DISCORD_BOT_OFFLINE(ErrorCategory.DISCORD, "Discord bot is offline", true),
    
    // Configuration Errors (3000-3999)
    CONFIG_FILE_NOT_FOUND(ErrorCategory.CONFIGURATION, "Configuration file not found", false),
    CONFIG_INVALID_FORMAT(ErrorCategory.CONFIGURATION, "Invalid configuration format", false),
    CONFIG_MISSING_REQUIRED_FIELD(ErrorCategory.CONFIGURATION, "Required configuration field missing", false),
    CONFIG_INVALID_VALUE(ErrorCategory.CONFIGURATION, "Invalid configuration value", false),
    CONFIG_LOAD_FAILED(ErrorCategory.CONFIGURATION, "Failed to load configuration", false),
    CONFIG_SAVE_FAILED(ErrorCategory.CONFIGURATION, "Failed to save configuration", true),
    
    // Validation Errors (4000-4999)
    VALIDATION_INVALID_USERNAME(ErrorCategory.VALIDATION, "Invalid Minecraft username", false),
    VALIDATION_INVALID_UUID(ErrorCategory.VALIDATION, "Invalid player UUID", false),
    VALIDATION_INVALID_DISCORD_ID(ErrorCategory.VALIDATION, "Invalid Discord ID", false),
    VALIDATION_USERNAME_TOO_LONG(ErrorCategory.VALIDATION, "Username is too long", false),
    VALIDATION_USERNAME_TOO_SHORT(ErrorCategory.VALIDATION, "Username is too short", false),
    VALIDATION_INVALID_CHARACTERS(ErrorCategory.VALIDATION, "Username contains invalid characters", false),
    VALIDATION_DUPLICATE_ENTRY(ErrorCategory.VALIDATION, "Duplicate entry detected", false),
    
    // Security Errors (5000-5999)
    SECURITY_UNAUTHORIZED_ACCESS(ErrorCategory.SECURITY, "Unauthorized access attempt", false),
    SECURITY_INVALID_TOKEN(ErrorCategory.SECURITY, "Invalid authentication token", false),
    SECURITY_SESSION_EXPIRED(ErrorCategory.SECURITY, "Session has expired", false),
    SECURITY_PERMISSION_DENIED(ErrorCategory.SECURITY, "Permission denied", false),
    SECURITY_RATE_LIMITED(ErrorCategory.SECURITY, "Rate limit exceeded", true),
    SECURITY_SUSPICIOUS_ACTIVITY(ErrorCategory.SECURITY, "Suspicious activity detected", false),
    
    // Network Errors (6000-6999)
    NETWORK_CONNECTION_TIMEOUT(ErrorCategory.NETWORK, "Network connection timed out", true),
    NETWORK_CONNECTION_REFUSED(ErrorCategory.NETWORK, "Network connection refused", true),
    NETWORK_HOST_UNREACHABLE(ErrorCategory.NETWORK, "Network host unreachable", true),
    NETWORK_SSL_ERROR(ErrorCategory.NETWORK, "SSL/TLS connection error", true),
    NETWORK_DNS_RESOLUTION_FAILED(ErrorCategory.NETWORK, "DNS resolution failed", true),
    
    // Filesystem Errors (7000-7999)
    FILESYSTEM_FILE_NOT_FOUND(ErrorCategory.FILESYSTEM, "File not found", false),
    FILESYSTEM_PERMISSION_DENIED(ErrorCategory.FILESYSTEM, "File permission denied", false),
    FILESYSTEM_DISK_FULL(ErrorCategory.FILESYSTEM, "Disk space full", true),
    FILESYSTEM_IO_ERROR(ErrorCategory.FILESYSTEM, "File input/output error", true),
    FILESYSTEM_DIRECTORY_NOT_FOUND(ErrorCategory.FILESYSTEM, "Directory not found", false),
    
    // System Errors (8000-8999)
    SYSTEM_OUT_OF_MEMORY(ErrorCategory.SYSTEM, "System out of memory", true),
    SYSTEM_THREAD_INTERRUPTED(ErrorCategory.SYSTEM, "System thread interrupted", true),
    SYSTEM_INITIALIZATION_FAILED(ErrorCategory.SYSTEM, "System initialization failed", false),
    SYSTEM_SHUTDOWN_ERROR(ErrorCategory.SYSTEM, "System shutdown error", false),
    SYSTEM_UNKNOWN_ERROR(ErrorCategory.SYSTEM, "An unknown error occurred", false);
    
    private final ErrorCategory category;
    private final String userMessage;
    private final boolean retryable;
    
    /**
     * Constructor for ErrorCode enum values.
     * 
     * Creates a new error code with the specified category, user message, and retry flag.
     * 
     * @param category The error category this code belongs to
     * @param userMessage The user-friendly message for this error
     * @param retryable Whether operations that fail with this error can be retried
     */
    ErrorCode(ErrorCategory category, String userMessage, boolean retryable) {
        this.category = category;
        this.userMessage = userMessage;
        this.retryable = retryable;
    }
    
    /**
     * Gets the error category for this error code.
     * 
     * @return The ErrorCategory enum value representing the category of error
     */
    public ErrorCategory getCategory() {
        return category;
    }
    
    /**
     * Gets the user-friendly message for this error code.
     * 
     * This message is safe to display to end users and should not contain
     * sensitive technical details.
     * 
     * @return The user-friendly error message
     */
    public String getUserMessage() {
        return userMessage;
    }
    
    /**
     * Checks if errors with this code are retryable.
     * 
     * Retryable errors are typically temporary issues that may succeed
     * if the operation is attempted again after a short delay.
     * 
     * @return true if operations can be retried, false otherwise
     */
    public boolean isRetryable() {
        return retryable;
    }
    
    /**
     * Gets a numeric error code for this error.
     * 
     * The numeric code is based on the ordinal position in the enum,
     * offset by the category's base value for easier identification.
     * 
     * @return A unique numeric error code
     */
    public int getNumericCode() {
        return ordinal() + 1000;
    }
}