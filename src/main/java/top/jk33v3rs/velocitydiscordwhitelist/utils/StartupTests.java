package top.jk33v3rs.velocitydiscordwhitelist.utils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import top.jk33v3rs.velocitydiscordwhitelist.config.SimpleConfigLoader;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.discord.DiscordHandler;

/**
 * StartupTests
 * 
 * Provides comprehensive startup validation to ensure all plugin systems are working correctly.
 * Runs essential checks for configuration, database, Discord, and module initialization.
 */
public class StartupTests {
    
    private final Logger logger;
    private final StartupValidator validator;
    
    /**
     * Constructor for StartupTests
     * 
     * @param logger Logger instance for startup testing
     * @param exceptionHandler Exception handler for centralized error handling
     */
    public StartupTests(Logger logger, ExceptionHandler exceptionHandler) {
        this.logger = logger;
        this.validator = new StartupValidator(logger, exceptionHandler);
    }
    
    /**
     * runAllStartupTests
     * 
     * Runs the complete suite of startup tests and reports results.
     * 
     * @param configLoader The configuration loader to test
     * @param sqlHandler The database handler to test
     * @param discordHandler The Discord handler to test
     * @return CompletableFuture<Boolean> true if all tests pass, false otherwise
     */
    public CompletableFuture<Boolean> runAllStartupTests(SimpleConfigLoader configLoader, 
                                                        SQLHandler sqlHandler, 
                                                        DiscordHandler discordHandler) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting comprehensive plugin validation...");
            
            boolean allPassed = true;
            int totalTests = 0;
            int passedTests = 0;
            
            // Test 1: Configuration validation
            totalTests++;
            logger.info("Test 1/4: Configuration validation");
            StartupValidator.ValidationResult configResult = validator.validateConfiguration(configLoader);
            if (configResult.isSuccess()) {
                logger.info("✓ Configuration validation passed");
                passedTests++;
            } else {
                logger.error("✗ Configuration validation failed: {}", configResult.getMessage());
                allPassed = false;
            }
            
            // Test 2: Database connectivity
            totalTests++;
            logger.info("Test 2/4: Database connectivity");
            StartupValidator.ValidationResult dbResult = validator.validateDatabase(sqlHandler);
            if (dbResult.isSuccess()) {
                logger.info("✓ Database validation passed");
                passedTests++;
            } else {
                logger.error("✗ Database validation failed: {}", dbResult.getMessage());
                allPassed = false;
            }
            
            // Test 3: Discord bot validation
            totalTests++;
            logger.info("Test 3/4: Discord bot connectivity");
            StartupValidator.ValidationResult discordResult = validator.validateDiscord(discordHandler);
            if (discordResult.isSuccess()) {
                logger.info("✓ Discord validation passed");
                passedTests++;
            } else {
                logger.error("✗ Discord validation failed: {}", discordResult.getMessage());
                allPassed = false;
            }
            
            // Test 4: Module initialization
            totalTests++;
            logger.info("Test 4/4: Module initialization");
            StartupValidator.ValidationResult moduleResult = validator.validateModules();
            if (moduleResult.isSuccess()) {
                logger.info("✓ Module validation passed");
                passedTests++;
            } else {
                logger.error("✗ Module validation failed: {}", moduleResult.getMessage());
                allPassed = false;
            }
            
            // Final report
            logger.info("Startup validation complete: {}/{} tests passed", passedTests, totalTests);
            if (allPassed) {
                logger.info("✓ All startup tests passed - Plugin is ready for operation");
            } else {
                logger.error("✗ {} startup tests failed - Plugin may not function correctly", 
                           totalTests - passedTests);
            }
            
            return allPassed;
        });
    }
    
    /**
     * runQuickHealthCheck
     * 
     * Runs a quick health check of essential systems.
     * 
     * @param sqlHandler The database handler to test
     * @param discordHandler The Discord handler to test
     * @return CompletableFuture<Boolean> true if health check passes, false otherwise
     */
    public CompletableFuture<Boolean> runQuickHealthCheck(SQLHandler sqlHandler, DiscordHandler discordHandler) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Running quick health check...");
            
            // Quick database ping
            boolean dbOk = sqlHandler.testConnection();
            logger.info("Database: {}", dbOk ? "✓ OK" : "✗ FAILED");
            
            // Quick Discord check
            boolean discordOk = discordHandler != null && discordHandler.isConnected();
            logger.info("Discord: {}", discordOk ? "✓ OK" : "✗ FAILED");
            
            boolean healthy = dbOk && discordOk;
            logger.info("Health check: {}", healthy ? "✓ HEALTHY" : "✗ ISSUES DETECTED");
            
            return healthy;
        });
    }
}
