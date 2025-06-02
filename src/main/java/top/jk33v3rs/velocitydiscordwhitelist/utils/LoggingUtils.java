// This file is part of VelocityWhitelist.

package top.jk33v3rs.velocitydiscordwhitelist.utils;

import org.slf4j.Logger;

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
}
