package top.jk33v3rs.velocitydiscordwhitelist.utils;

import org.slf4j.Logger;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

/**
 * Enhanced error handler utility for the VelocityDiscordWhitelist plugin.
 * 
 * This utility provides centralized error handling, automatic retry logic,
 * proper exception translation, and standardized error reporting across
 * the entire plugin.
 */
public class ErrorHandler {
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 1000;
    private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;
    
    /**
     * Handles database exceptions with proper error translation.
     * 
     * Converts SQLException instances into DatabaseException with appropriate
     * error codes and user-friendly messages.
     * 
     * @param logger The logger instance for error reporting
     * @param operation The operation that failed (for context)
     * @param e The SQLException that occurred
     * @return A DatabaseException with proper error context
     */
    public static DatabaseException handleDatabaseError(Logger logger, String operation, SQLException e) {
        ErrorCode errorCode = translateSqlException(e);
        String message = String.format("Database operation failed: %s", operation);
        
        DatabaseException dbException = new DatabaseException(
            errorCode, 
            message, 
            e.getSQLState(), 
            e.getErrorCode(), 
            null, 
            e
        );
        
        logger.error(dbException.getFormattedMessage(), e);
        return dbException;
    }
    
    /**
     * Handles database exceptions with SQL query context.
     * 
     * Converts SQLException instances into DatabaseException with SQL query
     * context for better debugging.
     * 
     * @param logger The logger instance for error reporting
     * @param operation The operation that failed (for context)
     * @param query The SQL query that caused the error
     * @param e The SQLException that occurred
     * @return A DatabaseException with proper error context
     */
    public static DatabaseException handleDatabaseError(Logger logger, String operation, String query, SQLException e) {
        ErrorCode errorCode = translateSqlException(e);
        String message = String.format("Database operation failed: %s - Query: %s", operation, sanitizeQuery(query));
        
        DatabaseException dbException = new DatabaseException(
            errorCode, 
            message, 
            e.getSQLState(), 
            e.getErrorCode(), 
            sanitizeQuery(query), 
            e
        );
        
        logger.error(dbException.getFormattedMessage(), e);
        return dbException;
    }
    
    /**
     * Handles Discord API exceptions with proper error translation.
     * 
     * Converts Discord API exceptions into DiscordException with appropriate
     * error codes and retry information.
     * 
     * @param logger The logger instance for error reporting
     * @param operation The operation that failed (for context)
     * @param e The Discord API exception that occurred
     * @return A DiscordException with proper error context
     */
    public static DiscordException handleDiscordError(Logger logger, String operation, ErrorResponseException e) {
        ErrorCode errorCode = translateDiscordException(e);
        String message = String.format("Discord operation failed: %s", operation);
        
        DiscordException discordException = new DiscordException(
            errorCode, 
            message, 
            e.getErrorCode(), 
            e.getMeaning(), 
            e.getErrorCode() == 429 ? extractRetryAfter(e) : -1,
            extractEndpoint(e),
            e
        );
        
        logger.error(discordException.getFormattedMessage(), e);
        return discordException;
    }
    
    /**
     * Handles general exceptions with proper error translation.
     * 
     * Converts general exceptions into VelocityDiscordWhitelistException with
     * appropriate error codes based on the exception type.
     * 
     * @param logger The logger instance for error reporting
     * @param operation The operation that failed (for context)
     * @param e The exception that occurred
     * @return A VelocityDiscordWhitelistException with proper error context
     */
    public static VelocityDiscordWhitelistException handleGeneralError(Logger logger, String operation, Throwable e) {
        ErrorCode errorCode = translateGeneralException(e);
        String message = String.format("Operation failed: %s", operation);
        
        VelocityDiscordWhitelistException exception = new VelocityDiscordWhitelistException(
            errorCode, 
            message, 
            e
        );
        
        logger.error(exception.getFormattedMessage(), e);
        return exception;
    }
    
    /**
     * Executes an operation with automatic retry logic.
     * 
     * Attempts to execute the provided operation, automatically retrying
     * on retryable errors with exponential backoff.
     * 
     * @param <T> The return type of the operation
     * @param logger The logger instance for error reporting
     * @param operation The operation to execute
     * @param operationName The name of the operation (for logging)
     * @return A CompletableFuture containing the operation result
     */
    public static <T> CompletableFuture<T> executeWithRetry(Logger logger, Supplier<T> operation, String operationName) {
        return executeWithRetry(logger, operation, operationName, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Executes an operation with automatic retry logic and custom retry count.
     * 
     * Attempts to execute the provided operation, automatically retrying
     * on retryable errors with exponential backoff.
     * 
     * @param <T> The return type of the operation
     * @param logger The logger instance for error reporting
     * @param operation The operation to execute
     * @param operationName The name of the operation (for logging)
     * @param maxRetries The maximum number of retry attempts
     * @return A CompletableFuture containing the operation result
     */
    public static <T> CompletableFuture<T> executeWithRetry(Logger logger, Supplier<T> operation, String operationName, int maxRetries) {
        return CompletableFuture.supplyAsync(() -> {
            int attempts = 0;
            long delay = DEFAULT_RETRY_DELAY_MS;
            
            while (attempts <= maxRetries) {
                try {
                    return operation.get();
                } catch (Exception e) {
                    attempts++;
                    
                    VelocityDiscordWhitelistException wrappedException = wrapException(logger, operationName, e);
                    
                    if (attempts > maxRetries || !wrappedException.isRetryable()) {
                        logger.error("Operation '{}' failed after {} attempts", operationName, attempts);
                        throw new RuntimeException(wrappedException);
                    }
                    
                    logger.warn("Operation '{}' failed (attempt {}/{}), retrying in {}ms: {}", 
                        operationName, attempts, maxRetries + 1, delay, wrappedException.getUserMessage());
                    
                    try {
                        TimeUnit.MILLISECONDS.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(new VelocityDiscordWhitelistException(
                            ErrorCode.SYSTEM_THREAD_INTERRUPTED, 
                            "Operation interrupted during retry", 
                            ie
                        ));
                    }
                    
                    delay = (long) (delay * RETRY_BACKOFF_MULTIPLIER);
                }
            }
            
            throw new RuntimeException("Unreachable code");
        });
    }
    
    /**
     * Safely executes an operation and handles all exceptions.
     * 
     * Executes the provided operation and converts any exceptions into
     * proper VelocityDiscordWhitelistException instances.
     * 
     * @param <T> The return type of the operation
     * @param logger The logger instance for error reporting
     * @param operation The operation to execute
     * @param operationName The name of the operation (for logging)
     * @param defaultValue The default value to return on error
     * @return The operation result or default value on error
     */
    public static <T> T executeSafely(Logger logger, Supplier<T> operation, String operationName, T defaultValue) {
        try {
            return operation.get();
        } catch (Exception e) {
            VelocityDiscordWhitelistException wrappedException = wrapException(logger, operationName, e);
            logger.warn("Operation '{}' failed safely, returning default value: {}", 
                operationName, wrappedException.getUserMessage());
            return defaultValue;
        }
    }
    
    /**
     * Translates SQLException into appropriate ErrorCode.
     * 
     * @param e The SQLException to translate
     * @return The appropriate ErrorCode for the SQL error
     */
    private static ErrorCode translateSqlException(SQLException e) {
        String sqlState = e.getSQLState();
        
        if (sqlState != null) {
            if (sqlState.startsWith("08")) {
                return ErrorCode.DATABASE_CONNECTION_FAILED;
            } else if (sqlState.equals("HYT00")) {
                return ErrorCode.DATABASE_TIMEOUT;
            } else if (sqlState.startsWith("23")) {
                return ErrorCode.DATABASE_CONSTRAINT_VIOLATION;
            } else if (sqlState.startsWith("40")) {
                return ErrorCode.DATABASE_TRANSACTION_FAILED;
            }
        }
        
        return ErrorCode.DATABASE_QUERY_FAILED;
    }
    
    /**
     * Translates Discord API exceptions into appropriate ErrorCode.
     * 
     * @param e The Discord API exception to translate
     * @return The appropriate ErrorCode for the Discord error
     */
    private static ErrorCode translateDiscordException(ErrorResponseException e) {
        return switch (e.getErrorCode()) {
            case 401 -> ErrorCode.DISCORD_AUTHENTICATION_FAILED;
            case 403 -> ErrorCode.DISCORD_PERMISSION_DENIED;
            case 404 -> ErrorCode.DISCORD_USER_NOT_FOUND;
            case 429 -> ErrorCode.DISCORD_RATE_LIMITED;
            default -> ErrorCode.DISCORD_API_ERROR;
        };
    }
    
    /**
     * Translates general exceptions into appropriate ErrorCode.
     * 
     * @param e The exception to translate
     * @return The appropriate ErrorCode for the exception
     */
    private static ErrorCode translateGeneralException(Throwable e) {
        if (e instanceof SQLException sqlException) {
            return translateSqlException(sqlException);
        } else if (e instanceof ErrorResponseException discordException) {
            return translateDiscordException(discordException);
        } else if (e instanceof InterruptedException) {
            return ErrorCode.SYSTEM_THREAD_INTERRUPTED;
        } else if (e instanceof OutOfMemoryError) {
            return ErrorCode.SYSTEM_OUT_OF_MEMORY;
        } else if (e instanceof SecurityException) {
            return ErrorCode.SECURITY_PERMISSION_DENIED;
        } else if (e instanceof IllegalArgumentException) {
            return ErrorCode.VALIDATION_INVALID_CHARACTERS;
        } else {
            return ErrorCode.SYSTEM_UNKNOWN_ERROR;
        }
    }
    
    /**
     * Wraps an exception in the appropriate custom exception type.
     * 
     * @param logger The logger instance for error reporting
     * @param operationName The name of the operation that failed
     * @param e The exception to wrap
     * @return A VelocityDiscordWhitelistException wrapping the original exception
     */
    private static VelocityDiscordWhitelistException wrapException(Logger logger, String operationName, Throwable e) {
        if (e instanceof VelocityDiscordWhitelistException velocityException) {
            return velocityException;
        } else if (e instanceof SQLException sqlException) {
            return handleDatabaseError(logger, operationName, sqlException);
        } else if (e instanceof ErrorResponseException discordException) {
            return handleDiscordError(logger, operationName, discordException);
        } else {
            return handleGeneralError(logger, operationName, e);
        }
    }
    
    /**
     * Sanitizes SQL queries for logging by removing sensitive data.
     * 
     * @param query The SQL query to sanitize
     * @return A sanitized version of the query safe for logging
     */
    private static String sanitizeQuery(String query) {
        if (query == null) {
            return null;
        }
        
        // Remove potential sensitive data from queries
        return query.replaceAll("'[^']*'", "'***'")
                   .replaceAll("\\b\\d{10,}\\b", "***"); // Remove long numbers (like Discord IDs)
    }
    
    /**
     * Extracts retry-after value from Discord rate limit response.
     * 
     * @param e The Discord API exception
     * @return The retry-after value in milliseconds, or -1 if not available
     */
    private static long extractRetryAfter(ErrorResponseException e) {
        try {
            // Try to extract from the exception message or response
            String message = e.getMessage();
            if (message != null && message.contains("retry after")) {
                // Parse retry time from message if available
                String[] parts = message.split("retry after ");
                if (parts.length > 1) {
                    String timeStr = parts[1].split(" ")[0];
                    return Long.parseLong(timeStr) * 1000; // Convert seconds to milliseconds
                }
            }
        } catch (NumberFormatException ignored) {
            // Ignore parsing errors
        }
        return 5000; // Default 5 second retry for rate limits
    }
    
    /**
     * Extracts API endpoint from Discord exception.
     * 
     * @param e The Discord API exception
     * @return The API endpoint, or null if not available
     */
    private static String extractEndpoint(ErrorResponseException e) {
        try {
            // Extract endpoint information from the exception if available
            String message = e.getMessage();
            if (message != null && message.contains("https://")) {
                return message.substring(message.indexOf("https://"));
            }
        } catch (Exception ignored) {
            // Ignore extraction errors
        }
        return null;
    }
}