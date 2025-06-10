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
     * Handles Discord API exceptions with proper error translation.
     * 
     * Converts Discord ErrorResponseException instances into DiscordException
     * with appropriate error codes and user-friendly messages.
     * 
     * @param logger The logger instance for error reporting
     * @param operation The operation that failed (for context)
     * @param e The ErrorResponseException that occurred
     * @return A DiscordException with proper error context
     */
    public static DiscordException handleDiscordError(Logger logger, String operation, ErrorResponseException e) {
        ErrorCode errorCode = translateDiscordException(e);
        String message = String.format("Discord API operation failed: %s", operation);
        
        DiscordException discordException = new DiscordException(
            errorCode,
            message,
            e.getErrorCode(),
            e.getMeaning(),
            e.getErrorResponse(),
            e
        );
        
        logger.error(discordException.getFormattedMessage(), e);
        return discordException;
    }
    
    /**
     * Handles general exceptions with proper error translation.
     * 
     * Converts general exceptions into VelocityDiscordWhitelistException
     * with appropriate error codes and user-friendly messages.
     * 
     * @param logger The logger instance for error reporting
     * @param operation The operation that failed (for context)
     * @param e The exception that occurred
     * @return A VelocityDiscordWhitelistException with proper error context
     */
    public static VelocityDiscordWhitelistException handleGeneralError(Logger logger, String operation, Throwable e) {
        ErrorCode errorCode = translateGeneralException(e);
        String message = String.format("Operation failed: %s", operation);
        
        VelocityDiscordWhitelistException generalException = new VelocityDiscordWhitelistException(
            errorCode,
            message,
            e
        );
        
        logger.error(generalException.getFormattedMessage(), e);
        return generalException;
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
            Exception lastException = null;
            long delay = DEFAULT_RETRY_DELAY_MS;
            
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    if (attempt > 0) {
                        logger.info("Retrying operation '{}' (attempt {}/{})", operationName, attempt, maxRetries);
                    }
                    
                    return operation.get();
                } catch (Exception e) {
                    lastException = e;
                    
                    // Check if the error is retryable
                    if (!isRetryableException(e) || attempt >= maxRetries) {
                        break;
                    }
                    
                    logger.warn("Operation '{}' failed on attempt {}, retrying in {}ms: {}", 
                        operationName, attempt + 1, delay, e.getMessage());
                    
                    try {
                        Thread.sleep(delay);
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
            handleGeneralError(logger, operationName, e);
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
        
        String message = e.getMessage().toLowerCase();
        if (message.contains("timeout")) {
            return ErrorCode.DATABASE_TIMEOUT;
        } else if (message.contains("connection")) {
            return ErrorCode.DATABASE_CONNECTION_FAILED;
        } else if (message.contains("duplicate") || message.contains("constraint")) {
            return ErrorCode.DATABASE_CONSTRAINT_VIOLATION;
        }
        
        return ErrorCode.DATABASE_OPERATION_FAILED;
    }
    
    /**
     * Translates Discord API exceptions into appropriate ErrorCode.
     * 
     * @param e The Discord API exception to translate
     * @return The appropriate ErrorCode for the Discord error
     */
    private static ErrorCode translateDiscordException(ErrorResponseException e) {
        int errorCode = e.getErrorCode();
        
        switch (errorCode) {
            case 401:
                return ErrorCode.DISCORD_UNAUTHORIZED;
            case 403:
                return ErrorCode.DISCORD_FORBIDDEN;
            case 404:
                return ErrorCode.DISCORD_NOT_FOUND;
            case 429:
                return ErrorCode.DISCORD_RATE_LIMITED;
            case 500:
            case 502:
            case 503:
            case 504:
                return ErrorCode.DISCORD_SERVER_ERROR;
            default:
                return ErrorCode.DISCORD_API_ERROR;
        }
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
     * Checks if an exception is retryable.
     * 
     * @param e The exception to check
     * @return True if the exception represents a retryable error
     */
    private static boolean isRetryableException(Exception e) {
        if (e instanceof SQLException sqlException) {
            String sqlState = sqlException.getSQLState();
            // Connection errors and timeouts are retryable
            return sqlState != null && (sqlState.startsWith("08") || sqlState.equals("HYT00"));
        }
        
        if (e instanceof ErrorResponseException discordException) {
            int errorCode = discordException.getErrorCode();
            // Rate limits and server errors are retryable
            return errorCode == 429 || (errorCode >= 500 && errorCode < 600);
        }
        
        // Most other exceptions are not retryable
        return false;
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
}
