package top.jk33v3rs.velocitydiscordwhitelist.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

/**
 * DatabaseConfigValidator validates database configuration and connectivity.
 * 
 * This class provides methods to validate database configuration parameters
 * and test basic connectivity before initializing connection pools.
 */
public class DatabaseConfigValidator {
    
    private final ExceptionHandler exceptionHandler;
    
    /**
     * Constructor for DatabaseConfigValidator
     * 
     * @param exceptionHandler The exception handler for centralized error handling
     */
    public DatabaseConfigValidator(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
    
    /**
     * Validates the database configuration parameters
     * 
     * @param config The configuration map containing database settings
     * @return ValidationResult indicating success or failure with details
     */
    @SuppressWarnings("unchecked")
    public ValidationResult validateConfig(Map<String, Object> config) {
        if (config == null) {
            return new ValidationResult(false, "Configuration cannot be null");
        }
        
        Map<String, Object> dbConfig = (Map<String, Object>) config.get("database");
        if (dbConfig == null) {
            return new ValidationResult(false, "Database configuration is missing from config.yml");
        }
        
        // Validate required fields
        String host = dbConfig.getOrDefault("host", "localhost").toString();
        if (host.trim().isEmpty()) {
            return new ValidationResult(false, "Database host cannot be empty");
        }
        
        String portStr = dbConfig.getOrDefault("port", 3306).toString();
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                return new ValidationResult(false, "Database port must be between 1 and 65535, got: " + port);
            }
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "Database port must be a valid number, got: " + portStr);
        }
        
        String database = dbConfig.getOrDefault("database", "minecraft").toString();
        if (database.trim().isEmpty()) {
            return new ValidationResult(false, "Database name cannot be empty");
        }
        
        String username = dbConfig.getOrDefault("username", "root").toString();
        if (username.trim().isEmpty()) {
            return new ValidationResult(false, "Database username cannot be empty");
        }
        
        // Password can be empty, so we don't validate it
        // String password = dbConfig.getOrDefault("password", "").toString();
        
        return new ValidationResult(true, String.format(
            "Configuration valid for %s:%d/%s with user '%s'", 
            host, port, database, username));
    }
    
    /**
     * Tests basic connectivity to the database using a simple connection
     * 
     * @param config The configuration map containing database settings
     * @return ValidationResult indicating connectivity success or failure
     */
    @SuppressWarnings("unchecked")
    public ValidationResult testConnectivity(Map<String, Object> config) {
        Map<String, Object> dbConfig = (Map<String, Object>) config.get("database");
        
        String host = dbConfig.getOrDefault("host", "localhost").toString();
        int port = Integer.parseInt(dbConfig.getOrDefault("port", 3306).toString());
        String database = dbConfig.getOrDefault("database", "minecraft").toString();
        String username = dbConfig.getOrDefault("username", "root").toString();
        String password = dbConfig.getOrDefault("password", "").toString();
        
        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s", host, port, database);
        
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            return new ValidationResult(false, "MariaDB JDBC driver not found. Ensure mariadb-java-client is in the classpath.");
        }
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            if (conn != null && conn.isValid(5)) {
                return new ValidationResult(true, "Successfully connected to database at " + host + ":" + port);
            } else {
                return new ValidationResult(false, "Connection established but is not valid");
            }
        } catch (SQLException e) {
            ExceptionHandler.DatabaseErrorResult errorResult = exceptionHandler.handleDatabaseException(
                "connectivity test", e, "Testing connection to " + jdbcUrl);
            
            String userFriendlyMessage;
            switch (errorResult.getErrorType()) {
                case CONNECTION_FAILED:
                    userFriendlyMessage = String.format(
                        "Cannot connect to database at %s:%d. Please verify:\n" +
                        "1. Database server is running\n" +
                        "2. Host and port are correct\n" +
                        "3. Firewall allows connection\n" +
                        "4. Network connectivity is working", 
                        host, port);
                    break;
                case TIMEOUT:
                    userFriendlyMessage = String.format(
                        "Connection to %s:%d timed out. The server may be overloaded or unreachable.", 
                        host, port);
                    break;
                default:
                    if (e.getMessage().toLowerCase().contains("access denied")) {
                        userFriendlyMessage = String.format(
                            "Access denied for user '%s'. Please verify username and password are correct.", 
                            username);
                    } else if (e.getMessage().toLowerCase().contains("unknown database")) {
                        userFriendlyMessage = String.format(
                            "Database '%s' does not exist. Please create it or verify the database name.", 
                            database);
                    } else {
                        userFriendlyMessage = "Database connection failed: " + e.getMessage();
                    }
                    break;
            }
            
            return new ValidationResult(false, userFriendlyMessage);
        }
    }
    
    /**
     * Represents the result of a validation operation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        /**
         * Constructor for ValidationResult
         * 
         * @param valid Whether the validation passed
         * @param message Descriptive message about the validation result
         */
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        /**
         * Gets whether the validation passed
         * 
         * @return True if validation passed, false otherwise
         */
        public boolean isValid() { 
            return valid; 
        }
        
        /**
         * Gets the validation result message
         * 
         * @return Descriptive message about the validation result
         */
        public String getMessage() { 
            return message; 
        }
    }
}
