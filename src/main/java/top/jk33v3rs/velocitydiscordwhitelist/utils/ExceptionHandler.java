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
        } else if (exception instanceof SQLException) {
            SQLException sqlEx = (SQLException) exception;
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
                    "SQL error during " + operation + ": " + sqlEx.getMessage(),
                    false
                );
            }
        } else {
            logger.error("Unexpected database error during {}: {}", operation, exception.getMessage(), exception);
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
     * @param event The Discord slash command event
     * @param operation The operation being performed
     * @param exception The Discord API exception
     */
    public void handleDiscordException(SlashCommandInteractionEvent event, String operation, Throwable exception) {
        if (exception instanceof ErrorResponseException) {
            ErrorResponseException discordEx = (ErrorResponseException) exception;
            LoggingUtils.logIntegrationError(logger, "Discord API", operation, exception);
            
            switch (discordEx.getErrorCode()) {
                case 50013 -> // Missing Permissions
                    event.getHook().sendMessage("❌ Bot lacks necessary permissions to complete this action.")
                        .setEphemeral(true).queue();
                case 50001 -> // Missing Access
                    event.getHook().sendMessage("❌ Bot cannot access the required Discord resource.")
                        .setEphemeral(true).queue();
                case 50035 -> // Invalid Form Body
                    event.getHook().sendMessage("❌ Invalid command parameters provided.")
                        .setEphemeral(true).queue();
                default ->
                    event.getHook().sendMessage("❌ Discord API error occurred. Please try again later.")
                        .setEphemeral(true).queue();
            }
        } else if (exception instanceof IllegalArgumentException) {
            logger.warn("Invalid argument for Discord operation {}: {}", operation, exception.getMessage());
            event.getHook().sendMessage("❌ Invalid parameters provided: " + exception.getMessage())
                .setEphemeral(true).queue();
        } else {
            logger.error("Unexpected error during Discord operation {}: {}", operation, exception.getMessage(), exception);
            event.getHook().sendMessage("❌ An unexpected error occurred. Please contact an administrator.")
                .setEphemeral(true).queue();
        }
    }
    
    /**
     * Handles player-related exceptions with appropriate messaging
     * 
     * @param player The player involved in the operation (can be null)
     * @param operation The operation being performed
     * @param exception The exception that occurred
     * @return A user-friendly error message
     */
    public String handlePlayerException(Player player, String operation, Throwable exception) {
        String playerInfo = player != null ? player.getUsername() + " (" + player.getUniqueId() + ")" : "unknown player";
        
        if (exception instanceof IllegalArgumentException) {
            logger.warn("Invalid argument for player operation {} with {}: {}", operation, playerInfo, exception.getMessage());
            return "Invalid operation parameters. Please check your input.";
        } else if (exception instanceof IllegalStateException) {
            logger.warn("Invalid state for player operation {} with {}: {}", operation, playerInfo, exception.getMessage());
            return "Operation cannot be completed in the current state.";
        } else {
            logger.error("Error during player operation {} with {}: {}", operation, playerInfo, exception.getMessage(), exception);
            return "An error occurred while processing your request. Please try again or contact an administrator.";
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
                    
                    logger.error("Async operation failed - {}: {}", operation, actualException.getMessage(), actualException);
                    return defaultValue;
                });
        } catch (Exception e) {
            logger.error("Failed to start async operation - {}: {}", operation, e.getMessage(), e);
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
            logger.error("Operation failed - {}: {}", operation, e.getMessage(), e);
            return defaultValue;
        }
    }
    
    /**
     * Executes an operation that doesn't return a value with automatic exception handling
     * 
     * @param operation The operation description for logging
     * @param task The task to execute
     * @return True if the operation succeeded, false otherwise
     */
    public boolean executeWithHandling(String operation, Runnable task) {
        try {
            task.run();
            return true;
        } catch (Exception e) {
            logger.error("Operation failed - {}: {}", operation, e.getMessage(), e);
            return false;
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
}