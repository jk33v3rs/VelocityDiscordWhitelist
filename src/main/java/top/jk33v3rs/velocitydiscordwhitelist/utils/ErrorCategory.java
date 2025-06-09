package top.jk33v3rs.velocitydiscordwhitelist.utils;

/**
 * Error categories for the VelocityDiscordWhitelist plugin.
 * 
 * This enum defines the high-level categories of errors that can occur
 * within the plugin, helping with error classification and handling.
 */
public enum ErrorCategory {
    /**
     * Database-related errors including connection failures, SQL errors, and data corruption.
     */
    DATABASE,
    
    /**
     * Discord integration errors including API failures, authentication issues, and bot errors.
     */
    DISCORD,
    
    /**
     * Configuration errors including invalid settings, missing files, and format issues.
     */
    CONFIGURATION,
    
    /**
     * Validation errors including invalid usernames, malformed data, and constraint violations.
     */
    VALIDATION,
    
    /**
     * Authentication and authorization errors including permission failures and security violations.
     */
    SECURITY,
    
    /**
     * Network-related errors including timeouts, connection failures, and API errors.
     */
    NETWORK,
    
    /**
     * File system errors including file not found, permission denied, and I/O failures.
     */
    FILESYSTEM,
    
    /**
     * General system errors that don't fit into other categories.
     */
    SYSTEM
}