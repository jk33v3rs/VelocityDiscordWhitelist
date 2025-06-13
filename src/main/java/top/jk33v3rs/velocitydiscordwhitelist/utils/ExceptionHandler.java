package top.jk33v3rs.velocitydiscordwhitelist.utils;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.velocitypowered.api.proxy.Player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

/**
 * Centralized exception handler for the VelocityDiscordWhitelist plugin.
 * 
 * This class provides standardized error handling, logging, and recovery mechanisms
 * for common exceptions that occur throughout the plugin. It ensures consistent
 * error reporting and user feedback across all modules.
 */
public class ExceptionHandler {
    
    private final Logger logger;
    private final boolean debugEnabled;
    
    /**
     * Constructor for ExceptionHandler
     * 
     * @param logger The logger instance for error reporting
     * @param debugEnabled Whether debug mode is enabled for detailed logging
     */
    public ExceptionHandler(Logger logger, boolean debugEnabled) {
        this.logger = logger;
        this.debugEnabled = debugEnabled;
    }
    
    /**
     * Handles database-related exceptions with appropriate logging and recovery
     * 
     * @param operation The operation that was being performed when the exception occurred
     * @param exception The database exception that occurred
     * @param contextInfo Additional context information for debugging
     * @return A standardized error result
     */
    public DatabaseErrorResult handleDatabaseException(String operation, Throwable exception, String contextInfo) {
        if (exception instanceof SQLTimeoutException) {
            LoggingUtils.logDatabaseError(logger, operation, exception);
            return new DatabaseErrorResult(
                DatabaseErrorType.TIMEOUT,
                "Database operation timed out during " + operation,
                true // retryable
            );
        } else if (exception instanceof SQLException sqlEx) {
            LoggingUtils.logDatabaseError(logger, operation, exception);
            
            if (debugEnabled) {
                logger.debug("SQL State: {}, Error Code: {}, Context: {}", 
                    sqlEx.getSQLState(), sqlEx.getErrorCode(), contextInfo);
            }
            
            // Check for specific SQL error codes
            if (isConnectionError(sqlEx)) {
                return new DatabaseErrorResult(
                    DatabaseErrorType.CONNECTION_FAILED,
                    "Database connection failed during " + operation,
                    true
                );
            } else if (isConstraintViolation(sqlEx)) {
                return new DatabaseErrorResult(
                    DatabaseErrorType.CONSTRAINT_VIOLATION,
                    "Data constraint violation during " + operation,
                    false
                );
            } else {
                return new DatabaseErrorResult(
                    DatabaseErrorType.GENERAL_SQL_ERROR,
                    "SQL error during " + operation + ": " + (sqlEx.getMessage() != null ? sqlEx.getMessage() : "Unknown SQL error"),
                    false
                );
            }
        } else {
            String errorMessage = exception != null && exception.getMessage() != null ? exception.getMessage() : "Unknown database error";
            logger.error("Unexpected database error during {}: {}", operation, errorMessage, exception);
            return new DatabaseErrorResult(
                DatabaseErrorType.UNEXPECTED_ERROR,
                "Unexpected error during " + operation,
                false
            );
        }
    }
    
    /**
     * Handles Discord API-related exceptions with appropriate user feedback
     * 
     * @param event The Discord slash command event (can be null)
     * @param operation The operation being performed
     * @param exception The Discord API exception
     */
    public void handleDiscordException(SlashCommandInteractionEvent event, String operation, Throwable exception) {
        if (exception instanceof ErrorResponseException errorResponse) {
            LoggingUtils.logIntegrationError(logger, "Discord API", operation, exception);
            
            String userMessage = switch (errorResponse.getErrorCode()) {
                case 50013 -> "I don't have permission to perform this action. Please check my Discord permissions.";
                case 50001 -> "I don't have access to this channel or resource.";
                case 10062 -> "This interaction has expired. Please try the command again.";
                case 40060 -> "This interaction has already been acknowledged.";
                default -> "A Discord API error occurred. Please try again later.";
            };
            
            if (event != null && !event.isAcknowledged()) {
                try {
                    event.reply(userMessage).setEphemeral(true).queue();
                } catch (Exception replyException) {
                    logger.warn("Failed to send error reply to Discord", replyException);
                }
            }
        } else {
            String errorMessage = exception != null && exception.getMessage() != null ? exception.getMessage() : "Unknown Discord error";
            logger.error("Unexpected error during Discord operation {}: {}", operation, errorMessage, exception);
        }
    }
    
    /**
     * Handles player-related exceptions with appropriate messaging
     * 
     * @param player The player involved in the operation (can be null)
     * @param operation The operation being performed
     * @param exception The exception that occurred
     * @param playerInfo Additional context about the player
     */
    public void handlePlayerException(Player player, String operation, Throwable exception, String playerInfo) {
        String errorMessage = exception != null && exception.getMessage() != null ? exception.getMessage() : "Unknown player operation error";
        logger.error("Error during player operation {} with {}: {}", operation, playerInfo, errorMessage, exception);
        
        if (player != null && player.isActive()) {
            // Send appropriate message to player based on exception type
            // This would be implemented based on your plugin's messaging system
        }
    }
    
    /**
     * Handles integration-related exceptions with appropriate logging
     * 
     * @param integration The integration name (e.g., "LuckPerms", "Vault")
     * @param operation The operation that was being performed when the exception occurred
     * @param exception The exception that occurred
     * @return A user-friendly error message
     */
    public String handleIntegrationException(String integration, String operation, Throwable exception) {
        LoggingUtils.logIntegrationError(logger, integration, operation, exception);
        
        if (exception instanceof ClassNotFoundException) {
            LoggingUtils.debugLog(logger, debugEnabled, integration + " classes not found - integration not available");
            return integration + " integration is not available";
        } else if (exception instanceof IllegalArgumentException) {
            return "Invalid parameters provided to " + integration + " integration";
        } else if (exception instanceof IllegalStateException) {
            return integration + " integration is not in a valid state";
        } else {
            return integration + " integration error occurred";
        }
    }
    
    /**
     * Executes an operation with automatic exception handling
     * 
     * @param operation Description of the operation
     * @param task The task to execute
     * @return True if the operation succeeded, false otherwise
     */
    public boolean executeWithHandling(String operation, Runnable task) {
        try {
            task.run();
            return true;
        } catch (Exception e) {
            logger.error("Operation failed: {}", operation, e);
            return false;
        }
    }
    
    /**
     * Executes an async operation with automatic exception handling
     * 
     * @param operation Description of the operation
     * @param task The async task to execute
     * @return CompletableFuture that handles exceptions appropriately
     */
    public <T> CompletableFuture<T> executeAsyncWithHandling(String operation, Supplier<CompletableFuture<T>> task) {
        try {
            return task.get().exceptionally(throwable -> {
                Throwable actualException = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                String errorMessage = actualException != null && actualException.getMessage() != null ? actualException.getMessage() : "Unknown async error";
                logger.error("Async operation failed - {}: {}", operation, errorMessage, actualException);
                return null;
            });
        } catch (Exception e) {
            logger.error("Failed to start async operation: {}", operation, e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * Wraps a CompletableFuture operation with standardized exception handling
     * 
     * @param operation The operation description for logging
     * @param futureSupplier The supplier that creates the CompletableFuture
     * @param defaultValue The default value to return on error
     * @return A CompletableFuture with exception handling applied
     */
    public <T> CompletableFuture<T> wrapAsync(String operation, Supplier<CompletableFuture<T>> futureSupplier, T defaultValue) {
        try {
            return futureSupplier.get()
                .exceptionally(throwable -> {
                    Throwable actualException = throwable instanceof CompletionException ? 
                        throwable.getCause() : throwable;
                    
                    String errorMessage = actualException != null && actualException.getMessage() != null ? 
                        actualException.getMessage() : "Unknown async error";
                    logger.error("Async operation failed - {}: {}", operation, errorMessage, actualException);
                    return defaultValue;
                });
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
            logger.error("Failed to start async operation - {}: {}", operation, errorMessage, e);
            return CompletableFuture.completedFuture(defaultValue);
        }
    }
    
    /**
     * Executes an operation with automatic exception handling and logging
     * 
     * @param operation The operation description for logging
     * @param task The task to execute
     * @param defaultValue The default value to return on error
     * @return The result of the operation or the default value on error
     */
    public <T> T executeWithHandling(String operation, Supplier<T> task, T defaultValue) {
        try {
            return task.get();
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
            logger.error("Operation failed - {}: {}", operation, errorMessage, e);
            return defaultValue;
        }
    }
    
    /**
     * Checks if a SQLException represents a connection error
     * 
     * @param sqlException The SQLException to check
     * @return True if it's a connection error, false otherwise
     */
    private boolean isConnectionError(SQLException sqlException) {
        String sqlState = sqlException.getSQLState();
        return sqlState != null && (
            sqlState.startsWith("08") || // Connection exception
            sqlState.equals("S1000") ||  // General error
            sqlException.getMessage().toLowerCase().contains("connection")
        );
    }
    
    /**
     * Checks if a SQLException represents a constraint violation
     * 
     * @param sqlException The SQLException to check
     * @return True if it's a constraint violation, false otherwise
     */
    private boolean isConstraintViolation(SQLException sqlException) {
        String sqlState = sqlException.getSQLState();
        return sqlState != null && (
            sqlState.startsWith("23") || // Integrity constraint violation
            sqlException.getMessage().toLowerCase().contains("duplicate") ||
            sqlException.getMessage().toLowerCase().contains("constraint")
        );
    }
    
    /**
     * Enumeration of database error types
     */
    public enum DatabaseErrorType {
        TIMEOUT,
        CONNECTION_FAILED,
        CONSTRAINT_VIOLATION,
        GENERAL_SQL_ERROR,
        UNEXPECTED_ERROR
    }
    
    /**
     * Represents the result of database error handling
     */
    public static class DatabaseErrorResult {
        private final DatabaseErrorType errorType;
        private final String message;
        private final boolean retryable;
        
        public DatabaseErrorResult(DatabaseErrorType errorType, String message, boolean retryable) {
            this.errorType = errorType;
            this.message = message;
            this.retryable = retryable;
        }
        
        public DatabaseErrorType getErrorType() { return errorType; }
        public String getMessage() { return message; }
        public boolean isRetryable() { return retryable; }
    }
}
