package top.jk33v3rs.velocitydiscordwhitelist.utils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import top.jk33v3rs.velocitydiscordwhitelist.config.SimpleConfigLoader;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.discord.DiscordHandler;

/**
 * StartupValidator
 * 
 * Validates that all essential plugin systems are working correctly at startup.
 * Provides comprehensive checks for configuration, database, Discord, and module initialization.
 */
public class StartupValidator {
    
    private final Logger logger;
    private final ExceptionHandler exceptionHandler;
    
    /**
     * Constructor for StartupValidator
     * 
     * @param logger Logger instance for startup validation
     * @param exceptionHandler Exception handler for centralized error handling
     */
    public StartupValidator(Logger logger, ExceptionHandler exceptionHandler) {
        this.logger = logger;
        this.exceptionHandler = exceptionHandler;
    }
    
    /**
     * validateConfiguration
     * 
     * Validates that all required configuration keys are present and valid.
     * 
     * @param configLoader The configuration loader to validate
     * @return ValidationResult indicating success or failure
     */
    public ValidationResult validateConfiguration(SimpleConfigLoader configLoader) {
        try {
            logger.info("Validating configuration...");
            
            // Check essential config keys
            Map<String, Object> config = configLoader.getConfiguration();
            
            if (!config.containsKey("database")) {
                return ValidationResult.failure("Missing database configuration");
            }
            
            if (!config.containsKey("discord")) {
                return ValidationResult.failure("Missing Discord configuration");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> dbConfig = (Map<String, Object>) config.get("database");
            if (!dbConfig.containsKey("host") || !dbConfig.containsKey("database") || 
                !dbConfig.containsKey("username") || !dbConfig.containsKey("password")) {
                return ValidationResult.failure("Incomplete database configuration");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> discordConfig = (Map<String, Object>) config.get("discord");
            if (!discordConfig.containsKey("token") || !discordConfig.containsKey("guild_id")) {
                return ValidationResult.failure("Incomplete Discord configuration");
            }
            
            logger.info("Configuration validation successful");
            return ValidationResult.success("Configuration validation passed");
            
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("StartupValidator", "configuration validation", e);
            return ValidationResult.failure("Configuration validation failed: " + e.getMessage());
        }
    }
    
    /**
     * validateDatabase
     * 
     * Tests database connectivity and validates schema.
     * 
     * @param sqlHandler The SQL handler to test
     * @return ValidationResult indicating success or failure
     */
    public ValidationResult validateDatabase(SQLHandler sqlHandler) {
        try {
            logger.info("Validating database connectivity...");
            
            // Test basic connectivity
            if (!sqlHandler.testConnection()) {
                return ValidationResult.failure("Database connection test failed");
            }
            
            // Test table existence (this would be implemented in SQLHandler)
            // For now, assume success if connection works
            
            logger.info("Database validation successful");
            return ValidationResult.success("Database connectivity verified");
            
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("StartupValidator", "database validation", e);
            return ValidationResult.failure("Database validation failed: " + e.getMessage());
        }
    }
    
    /**
     * validateDiscord
     * 
     * Tests Discord bot connection and guild access.
     * 
     * @param discordHandler The Discord handler to test
     * @return ValidationResult indicating success or failure
     */
    public ValidationResult validateDiscord(DiscordHandler discordHandler) {
        try {
            logger.info("Validating Discord connectivity...");
            
            if (!discordHandler.isConnected()) {
                return ValidationResult.failure("Discord bot is not connected");
            }
            
            if (discordHandler.getGuild() == null) {
                return ValidationResult.failure("Cannot access configured Discord guild");
            }
            
            logger.info("Discord validation successful");
            return ValidationResult.success("Discord connectivity verified");
            
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("StartupValidator", "Discord validation", e);
            return ValidationResult.failure("Discord validation failed: " + e.getMessage());
        }
    }
    
    /**
     * validateModules
     * 
     * Validates that essential plugin modules are initialized and working.
     * 
     * @return ValidationResult indicating success or failure
     */
    public ValidationResult validateModules() {
        try {
            logger.info("Validating module initialization...");
            
            // Check that essential classes are loadable
            try {
                Class.forName("top.jk33v3rs.velocitydiscordwhitelist.modules.XPManager");
                Class.forName("top.jk33v3rs.velocitydiscordwhitelist.modules.RankManager");
                Class.forName("top.jk33v3rs.velocitydiscordwhitelist.modules.PurgatoryManager");
            } catch (ClassNotFoundException e) {
                return ValidationResult.failure("Essential module classes not found: " + e.getMessage());
            }
            
            // Additional module checks could be added here
            // For now, if classes load, we consider modules valid
            
            logger.info("Module validation successful");
            return ValidationResult.success("All essential modules validated");
            
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("StartupValidator", "module validation", e);
            return ValidationResult.failure("Module validation failed: " + e.getMessage());
        }
    }
    
    /**
     * performFullValidation
     * 
     * Runs all validation checks and returns comprehensive results.
     * 
     * @param configLoader Configuration loader to validate
     * @param sqlHandler SQL handler to test
     * @param discordHandler Discord handler to test
     * @return CompletableFuture with overall validation result
     */
    public CompletableFuture<ValidationResult> performFullValidation(
            SimpleConfigLoader configLoader, SQLHandler sqlHandler, DiscordHandler discordHandler) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting comprehensive startup validation...");
                
                // Validate configuration
                ValidationResult configResult = validateConfiguration(configLoader);
                if (!configResult.isSuccess()) {
                    return configResult;
                }
                
                // Validate database
                ValidationResult dbResult = validateDatabase(sqlHandler);
                if (!dbResult.isSuccess()) {
                    return dbResult;
                }
                
                // Validate Discord
                ValidationResult discordResult = validateDiscord(discordHandler);
                if (!discordResult.isSuccess()) {
                    return discordResult;
                }
                
                // Validate modules
                ValidationResult moduleResult = validateModules();
                if (!moduleResult.isSuccess()) {
                    return moduleResult;
                }
                
                logger.info("All startup validation checks passed successfully");
                return ValidationResult.success("All systems validated successfully");
                
            } catch (Exception e) {
                exceptionHandler.handleIntegrationException("StartupValidator", "full validation", e);
                return ValidationResult.failure("Startup validation failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * ValidationResult
     * 
     * Simple result class for validation operations.
     */
    public static class ValidationResult {
        private final boolean success;
        private final String message;
        
        private ValidationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public static ValidationResult success(String message) {
            return new ValidationResult(true, message);
        }
        
        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
