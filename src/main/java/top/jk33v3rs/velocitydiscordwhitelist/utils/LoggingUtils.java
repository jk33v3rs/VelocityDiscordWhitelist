// This file is part of VelocityWhitelist.

package top.jk33v3rs.velocitydiscordwhitelist.utils;

import org.slf4j.Logger;
import java.sql.SQLException;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

/**
 * Centralized logging utility for the VelocityDiscordWhitelist plugin.
 */
public class LoggingUtils {

    /**
     * Logs a debug message if debug mode is enabled
     */
    public static void debugLog(Logger logger, boolean debugEnabled, String message) {
        if (debugEnabled) {
            logger.info("[DEBUG] " + message);
        }
    }

    /**
     * Logs an informational message
     */
    public static void info(Logger logger, String message) {
        logger.info(message);
    }

    /**
     * Logs a warning message
     */
    public static void warn(Logger logger, String message) {
        logger.warn(message);
    }

    /**
     * Logs a warning message with exception
     * 
     * @param logger The SLF4J logger instance
     * @param message The warning message to log
     * @param throwable The exception to log
     */
    public static void warn(Logger logger, String message, Throwable throwable) {
        logger.warn(message, throwable);
    }

    /**
     * Logs an error message
     * 
     * @param logger The SLF4J logger instance
     * @param message The error message to log
     */
    public static void error(Logger logger, String message) {
        logger.error(message);
    }

    /**
     * Logs an error message with exception
     * 
     * @param logger The SLF4J logger instance
     * @param message The error message to log
     * @param throwable The exception to log
     */
    public static void error(Logger logger, String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    /**
     * Logs a concise error message for database operations
     * 
     * @param logger The SLF4J logger instance
     * @param operation The database operation that failed
     * @param throwable The exception that occurred
     */
    public static void logDatabaseError(Logger logger, String operation, Throwable throwable) {
        error(logger, "Database error during " + operation + ": " + throwable.getMessage(), throwable);
    }

    /**
     * Logs a concise error message for integration operations
     * 
     * @param logger The SLF4J logger instance
     * @param integration The integration name (e.g., "LuckPerms", "Vault", "Discord")
     * @param operation The operation that failed
     * @param throwable The exception that occurred
     */
    public static void logIntegrationError(Logger logger, String integration, String operation, Throwable throwable) {
        error(logger, integration + " integration error during " + operation + ": " + throwable.getMessage(), throwable);
    }

    /**
     * Logs a concise error message for command operations
     * 
     * @param logger The SLF4J logger instance
     * @param command The command that failed
     * @param throwable The exception that occurred
     */
    public static void logCommandError(Logger logger, String command, Throwable throwable) {
        error(logger, "Command error executing " + command + ": " + throwable.getMessage(), throwable);
    }

    /**
     * Logs a concise error message for configuration operations
     * 
     * @param logger The SLF4J logger instance
     * @param operation The configuration operation that failed
     * @param throwable The exception that occurred
     */
    public static void logConfigError(Logger logger, String operation, Throwable throwable) {
        error(logger, "Configuration error during " + operation + ": " + throwable.getMessage(), throwable);
    }

    // Enhanced error logging methods that integrate with the new error handling system

    /**
     * Logs a structured error using the enhanced error handling system.
     * 
     * This method logs errors with proper formatting and context using the
     * VelocityDiscordWhitelistException error structure.
     * 
     * @param logger The SLF4J logger instance
     * @param exception The VelocityDiscordWhitelistException to log
     */
    public static void logStructuredError(Logger logger, VelocityDiscordWhitelistException exception) {
        logger.error(exception.getFormattedMessage(), exception);
    }

    /**
     * Logs a database error with enhanced context and error code mapping.
     * 
     * This method uses the ErrorHandler to properly translate SQL exceptions
     * into structured DatabaseException instances with appropriate error codes.
     * 
     * @param logger The SLF4J logger instance
     * @param operation The database operation that failed
     * @param sqlException The SQLException that occurred
     */
    public static void logEnhancedDatabaseError(Logger logger, String operation, SQLException sqlException) {
        DatabaseException dbException = ErrorHandler.handleDatabaseError(logger, operation, sqlException);
        // Error is already logged by ErrorHandler, but we can add additional context
        if (dbException.isConnectionError()) {
            logger.warn("Database connection issue detected. Consider checking connection pool settings.");
        } else if (dbException.isTimeoutError()) {
            logger.warn("Database timeout detected. Consider increasing timeout settings or checking query performance.");
        } else if (dbException.isConstraintViolation()) {
            logger.warn("Database constraint violation. Check data integrity and validation logic.");
        }
    }

    /**
     * Logs a Discord API error with enhanced context and rate limit handling.
     * 
     * This method uses the ErrorHandler to properly translate Discord API exceptions
     * into structured DiscordException instances with rate limiting information.
     * 
     * @param logger The SLF4J logger instance
     * @param operation The Discord operation that failed
     * @param discordException The Discord API exception that occurred
     */
    public static void logEnhancedDiscordError(Logger logger, String operation, ErrorResponseException discordException) {
        DiscordException exception = ErrorHandler.handleDiscordError(logger, operation, discordException);
        // Error is already logged by ErrorHandler, but we can add additional context
        if (exception.isRateLimited()) {
            long retryAfter = exception.getRetryAfter();
            if (retryAfter > 0) {
                logger.warn("Discord rate limit exceeded. Retry after {} milliseconds.", retryAfter);
            } else {
                logger.warn("Discord rate limit exceeded. Consider implementing backoff strategy.");
            }
        } else if (exception.isAuthenticationError()) {
            logger.warn("Discord authentication failed. Check bot token and permissions.");
        } else if (exception.isPermissionError()) {
            logger.warn("Discord permission denied. Check bot permissions in the target server/channel.");
        } else if (exception.isNotFoundError()) {
            logger.warn("Discord resource not found. Verify user/channel/guild IDs are correct.");
        }
    }

    /**
     * Logs a validation error with user-friendly messaging.
     * 
     * This method logs validation errors with both technical details for debugging
     * and user-friendly messages that can be safely displayed.
     * 
     * @param logger The SLF4J logger instance
     * @param operation The validation operation that failed
     * @param errorCode The specific validation error code
     * @param input The input that failed validation (sanitized for logging)
     */
    public static void logValidationError(Logger logger, String operation, ErrorCode errorCode, String input) {
        VelocityDiscordWhitelistException validationException = new VelocityDiscordWhitelistException(
            errorCode, 
            String.format("Validation failed during %s: input='%s'", operation, sanitizeForLogging(input))
        );
        
        logger.warn(validationException.getFormattedMessage());
        
        // Additional context based on validation error type
        switch (errorCode) {
            case VALIDATION_INVALID_USERNAME -> 
                logger.info("Username validation help: Minecraft usernames must be 3-16 characters, alphanumeric and underscores only.");
            case VALIDATION_INVALID_UUID -> 
                logger.info("UUID validation help: UUIDs must be in standard format (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).");
            case VALIDATION_INVALID_DISCORD_ID -> 
                logger.info("Discord ID validation help: Discord IDs must be numeric and 17-19 digits long.");
            case VALIDATION_USERNAME_TOO_LONG -> 
                logger.info("Username length help: Minecraft usernames cannot exceed 16 characters.");
            case VALIDATION_USERNAME_TOO_SHORT -> 
                logger.info("Username length help: Minecraft usernames must be at least 3 characters.");
            default -> {
                // No additional help for other validation errors
            }
        }
    }

    /**
     * Logs a security-related error with appropriate severity.
     * 
     * Security errors are logged with high priority and may trigger additional
     * monitoring or alerting mechanisms.
     * 
     * @param logger The SLF4J logger instance
     * @param operation The security operation that failed
     * @param errorCode The specific security error code
     * @param context Additional context about the security event
     */
    public static void logSecurityError(Logger logger, String operation, ErrorCode errorCode, String context) {
        VelocityDiscordWhitelistException securityException = new VelocityDiscordWhitelistException(
            errorCode, 
            String.format("Security violation during %s: %s", operation, context)
        );
        
        // Security errors are always logged as errors, regardless of the specific type
        logger.error("[SECURITY] " + securityException.getFormattedMessage());
        
        // Additional security-specific logging
        switch (errorCode) {
            case SECURITY_UNAUTHORIZED_ACCESS -> 
                logger.warn("[SECURITY] Unauthorized access attempt detected. Review access controls.");
            case SECURITY_RATE_LIMITED -> 
                logger.warn("[SECURITY] Rate limiting triggered. Monitor for potential abuse.");
            case SECURITY_SUSPICIOUS_ACTIVITY -> 
                logger.error("[SECURITY] Suspicious activity detected. Consider immediate review.");
            default -> 
                logger.warn("[SECURITY] Security event logged. Review if necessary.");
        }
    }

    /**
     * Sanitizes input for safe logging by removing potential sensitive information.
     * 
     * This method removes or masks potentially sensitive data from inputs before
     * including them in log messages.
     * 
     * @param input The input string to sanitize
     * @return A sanitized version safe for logging
     */
    private static String sanitizeForLogging(String input) {
        if (input == null) {
            return "null";
        }
        
        // Limit length to prevent log spam
        if (input.length() > 100) {
            input = input.substring(0, 97) + "...";
        }
        
        // Remove potential sensitive patterns
        return input.replaceAll("\\b\\d{15,19}\\b", "***DISCORD_ID***") // Discord IDs
                   .replaceAll("\\b[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\b", "***UUID***") // UUIDs
                   .replaceAll("\\b[A-Za-z0-9+/]{20,}={0,2}\\b", "***TOKEN***"); // Potential tokens
    }
}
