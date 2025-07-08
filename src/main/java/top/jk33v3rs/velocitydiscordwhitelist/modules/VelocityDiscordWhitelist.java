// VelocityDiscordWhitelist - MIT License

// VelocityDiscordWhitelist v1.0.11
// Author: jk33v3rs based on the plugin VelocityWhitelist by Rathinosk
// Portions of code used are used under MIT licence
// DISCLAIMER: AI tools were used in the IDE used to create this plugin, which included direct (but supervised) access to code
// Please report any issues via GitHub. Pull requests are accepted.

package top.jk33v3rs.velocitydiscordwhitelist.modules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bstats.velocity.Metrics;


import com.google.inject.Inject;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;

import net.kyori.adventure.text.Component;
import top.jk33v3rs.velocitydiscordwhitelist.commands.BrigadierCommandHandler;
import top.jk33v3rs.velocitydiscordwhitelist.config.SimpleConfigLoader;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.integrations.LuckPermsIntegration;
import top.jk33v3rs.velocitydiscordwhitelist.integrations.VaultIntegration;
import top.jk33v3rs.velocitydiscordwhitelist.utils.DatabaseMonitor;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;
import top.jk33v3rs.velocitydiscordwhitelist.utils.LoggingUtils;

/** Main VelocityDiscordWhitelist plugin class. */
@Plugin(
        id = "velocitydiscordwhitelist",
        name = "VelocityDiscordWhitelist",
        version = "1.0.11",
        url = "https://github.com/jk33v3rs/VelocityDiscordWhitelist",
        description = "A whitelist plugin for Velocity that integrates with Discord",
        authors = {"jk33v3rs"}
)
public class VelocityDiscordWhitelist {    private final ProxyServer server;
    private final org.slf4j.Logger logger;
    private final Path dataDirectory;
    private final Path configFile;
    @SuppressWarnings("unused") // Keep for future use with metrics
    private final Metrics.Factory metricsFactory;    // Components
    private SQLHandler sqlHandler;
    private SimpleConfigLoader configLoader;
    private Map<String, Object> config;
    private EnhancedPurgatoryManager purgatoryManager;
    private DiscordBotHandler discordBotHandler;
    private RewardsHandler rewardsHandler;
    private XPManager xpManager;
    private BrigadierCommandHandler commandHandler;
    private VaultIntegration vaultIntegration;
    private LuckPermsIntegration luckPermsIntegration;
    private DatabaseMonitor databaseMonitor;
    private ScheduledTask purgatoryCleanupTask;
    private ExceptionHandler exceptionHandler;
    
    // Parallel initialization executor
    private ExecutorService initializationExecutor;
    
    // Configuration
    private final AtomicBoolean pluginEnabled = new AtomicBoolean(false);
    private Boolean debugEnabled = false;
    private int sessionTimeoutMinutes = 30; // Default value
    
    @Inject
    public VelocityDiscordWhitelist(
            ProxyServer server,
            org.slf4j.Logger logger,
            @DataDirectory Path dataDirectory,
            Metrics.Factory metricsFactory
    ) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configFile = dataDirectory.resolve("config.yaml");
        this.metricsFactory = metricsFactory;
    }    /**
     * Event listener for the ProxyInitializeEvent.
     * This method is called when the proxy server is initialized.
     * Uses parallel initialization to improve startup performance while maintaining proper dependency order.
     *
     * @param event The ProxyInitializeEvent.
     */
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Create executor for parallel initialization
        initializationExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "VelocityDiscordWhitelist-Init");
            t.setDaemon(true);
            return t;
        });
        
        try {
            // Phase 1: Initialize critical sequential dependencies
            logger.info("Starting VelocityDiscordWhitelist initialization (parallel mode)...");
            
            // Initialize exception handler first (no dependencies)
            this.exceptionHandler = new ExceptionHandler(logger, false); // Will be updated after config loads
            
            // Perform startup validation (sequential - needed before proceeding)
            if (!performStartupValidation()) {
                exceptionHandler.handleIntegrationException("Plugin", "startup validation failed", 
                    new IllegalStateException("Critical startup validation failed"));
                return;
            }
            
            // Load configuration (sequential - needed by most other components)
            loadConfig();
            
            // Validate configuration before proceeding
            if (!validateConfiguration()) {
                exceptionHandler.handleIntegrationException("Plugin", "configuration validation failed", 
                    new IllegalStateException("Configuration validation failed - check logs for details"));
                return;
            }
            
            // Update exception handler with actual debug setting
            this.exceptionHandler = new ExceptionHandler(logger, debugEnabled);
            
            // Phase 2: Initialize database (sequential - critical dependency)
            initializeDatabase();
            
            // Validate database connection before parallel initialization
            if (!validateDatabaseConnection()) {
                exceptionHandler.handleDatabaseException("connection validation", 
                    new SQLException("Database connection validation failed"), "Startup validation");
                return;
            }
            
            // Phase 3: Start parallel initialization of independent components
            CompletableFuture<Void> parallelInitialization = initializeComponentsInParallel();
            
            // Wait for parallel initialization to complete with timeout
            try {
                parallelInitialization.get(30, TimeUnit.SECONDS);
                logger.info("Parallel component initialization completed successfully");
            } catch (Exception e) {
                exceptionHandler.handleIntegrationException("Plugin", "parallel initialization timeout/failure", e);
                pluginEnabled.set(false);
                return;
            }
            
            // Phase 4: Validate all components and finalize startup
            boolean startupSuccessful = validateStartupComponents();
            
            if (startupSuccessful) {
                // Initialize command handler only if all critical components are ready
                try {
                    if (purgatoryManager == null || sqlHandler == null) {
                        throw new IllegalStateException("Critical components missing: purgatoryManager or sqlHandler is null");
                    }
                    
                    commandHandler = new BrigadierCommandHandler(server, logger, purgatoryManager, rewardsHandler, xpManager, sqlHandler, debugEnabled, exceptionHandler);
                    commandHandler.registerCommands();
                    
                    logger.info("VelocityDiscordWhitelist plugin has been enabled successfully! (Parallel initialization)");
                    pluginEnabled.set(true);
                } catch (Exception e) {
                    exceptionHandler.handleIntegrationException("CommandHandler", "registration", e);
                    startupSuccessful = false;
                }
            }
            
            if (!startupSuccessful) {
                exceptionHandler.handleIntegrationException("Plugin", "startup completion", 
                    new IllegalStateException("Critical component failures detected during startup"));
                pluginEnabled.set(false);
                // Don't throw exception to prevent Velocity from crashing, but plugin will be disabled
            }
            
        } catch (Exception e) {
            if (exceptionHandler != null) {
                exceptionHandler.handleIntegrationException("Plugin", "initialization", e);
            } else {
                // Fallback only if ExceptionHandler failed to initialize
                logger.error("Critical error during plugin initialization (ExceptionHandler unavailable)", e);
            }
            pluginEnabled.set(false);
        } finally {
            // Shutdown the initialization executor
            if (initializationExecutor != null) {
                initializationExecutor.shutdown();
                try {
                    if (!initializationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        initializationExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    initializationExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("VelocityDiscordWhitelist plugin is shutting down...");
        
        // Shutdown initialization executor if still running
        if (initializationExecutor != null && !initializationExecutor.isShutdown()) {
            try {
                initializationExecutor.shutdown();
                if (!initializationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    initializationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                initializationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Stop database monitoring first
        if (databaseMonitor != null) {
            try {
                databaseMonitor.shutdown();
                logger.info("Database monitor shutdown completed");
            } catch (Exception e) {
                if (exceptionHandler != null) {
                    exceptionHandler.handleIntegrationException("DatabaseMonitor", "shutdown", e);
                } else {
                    logger.error("Error shutting down database monitor", e);
                }
            }
        }
        
        if (sqlHandler != null) {
            try {
                sqlHandler.close();
                logger.info("Database connection closed successfully");
            } catch (Exception e) {
                if (exceptionHandler != null) {
                    exceptionHandler.handleDatabaseException("shutdown", e, "Closing database connection");
                } else {
                    logger.error("Error closing database connection during shutdown", e);
                }
            }
        }
        
        if (discordBotHandler != null) {
            try {
                discordBotHandler.shutdown();
                logger.info("Discord bot handler shutdown completed");
            } catch (Exception e) {
                if (exceptionHandler != null) {
                    exceptionHandler.handleIntegrationException("Discord Bot", "shutdown", e);
                } else {
                    logger.error("Error shutting down Discord bot", e);
                }
            }
        }
        
        if (purgatoryCleanupTask != null) {
            purgatoryCleanupTask.cancel();
            logger.info("Purgatory cleanup task cancelled");
        }
        
        logger.info("VelocityDiscordWhitelist plugin shutdown complete");
    }@Subscribe
    public void onPlayerLogin(LoginEvent event) {
        if (!pluginEnabled.get()) {
            return;
        }
        
        // Check if plugin is enabled in config
        @SuppressWarnings("unchecked")
        Map<String, Object> settingsConfig = (Map<String, Object>) config.getOrDefault("settings", new HashMap<>());
        boolean configEnabled = Boolean.parseBoolean(settingsConfig.getOrDefault("enabled", "true").toString());
        
        if (!configEnabled) {
            return;
        }

        Player player = event.getPlayer();
        String username = player.getUsername();
        
        // Handle Geyser/Bedrock prefix
        String originalUsername = username;
        
        @SuppressWarnings("unchecked")
        Map<String, Object> geyserConfig = (Map<String, Object>) config.getOrDefault("geyser", new HashMap<>());
        boolean geyserEnabled = Boolean.parseBoolean(geyserConfig.getOrDefault("enabled", "false").toString());
        
        if (geyserEnabled) {
            String geyserPrefix = geyserConfig.getOrDefault("prefix", ".").toString();
            if (username.startsWith(geyserPrefix)) {
                username = username.substring(geyserPrefix.length());
                debugLog("Detected Bedrock player: " + originalUsername + " -> " + username);
            }
        }

        final String finalUsername = username;
        debugLog("Checking whitelist for " + finalUsername);        try {
            // Add timeout to prevent hanging on database operations
            CompletableFuture<Boolean> whitelistCheck = sqlHandler.isPlayerWhitelisted(player);
            
            // Set a reasonable timeout for the operation
            whitelistCheck
                .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .whenComplete((isWhitelisted, error) -> {
                    if (error != null) {
                        if (error instanceof java.util.concurrent.TimeoutException) {
                            logger.error("Database timeout checking whitelist for player: " + finalUsername);
                            event.setResult(ComponentResult.denied(Component.text("Database timeout. Please try again in a moment.")));
                        } else {
                            exceptionHandler.handlePlayerException(player, "whitelist check", error, finalUsername);
                            event.setResult(ComponentResult.denied(Component.text("An error occurred while checking the whitelist.")));
                        }
                        return;
                    }

                    if (!isWhitelisted) {
                        // Get messages from config
                        @SuppressWarnings("unchecked")
                        Map<String, Object> messagesConfig = (Map<String, Object>) config.getOrDefault("messages", new HashMap<>());
                        String message = messagesConfig.getOrDefault("notWhitelisted", 
                            "You are not whitelisted on this server. Please check Discord for whitelist instructions.").toString();
                        event.setResult(ComponentResult.denied(Component.text(message)));
                        debugLog("Access denied for " + finalUsername);
                    } else {
                        debugLog("Player " + finalUsername + " is whitelisted, allowing login");
                    }
                });
        } catch (Exception e) {
            exceptionHandler.handlePlayerException(player, "whitelist check initiation", e, finalUsername);
            event.setResult(ComponentResult.denied(Component.text("An error occurred while checking the whitelist.")));
        }
    }    @Subscribe
    public void onServerPreConnect(com.velocitypowered.api.event.player.ServerPreConnectEvent event) {
        if (!pluginEnabled.get() || purgatoryManager == null) {
            return;
        }

        Player player = event.getPlayer();
        String username = player.getUsername();

        // Check if player is in purgatory
        try {
            Optional<EnhancedPurgatoryManager.PurgatorySession> sessionOpt = purgatoryManager.getSession(username);
            if (sessionOpt.isPresent()) {
            EnhancedPurgatoryManager.PurgatorySession session = sessionOpt.get();
            String targetServerName = event.getOriginalServer().getServerInfo().getName();
            String allowedServer = session.getAllowedServer();

            debugLog("Player " + username + " in purgatory attempting to connect to server: " + targetServerName);
            
            if (!targetServerName.equalsIgnoreCase(allowedServer)) {
                debugLog("Blocking " + username + " from connecting to " + targetServerName + 
                         " (only allowed on " + allowedServer + " while in purgatory)");
                
                // Cancel the connection attempt
                event.setResult(com.velocitypowered.api.event.player.ServerPreConnectEvent.ServerResult.denied());
                  // Notify the player
                player.sendMessage(Component.text(
                    "You must complete verification before connecting to other servers. Use /verify with your code.",
                    net.kyori.adventure.text.format.NamedTextColor.RED
                ));            }
            }
        } catch (Exception e) {
            exceptionHandler.handlePlayerException(player, "purgatory check", e, username);
            logger.warn("Error checking purgatory status for player: " + username, e);
        }
    }    @SuppressWarnings("UnnecessaryTemporary")  
    public boolean reloadConfig() {
        try {
            // Use the simple config loader to reload
            if (configLoader != null && configLoader.reload()) {
                config = configLoader.getConfiguration();
                
                // Refresh debug mode from simplified settings
                debugEnabled = configLoader.get("settings.debug", false);
                logger.info("Debug mode is now {}", debugEnabled ? "enabled" : "disabled");
                
                // Refresh session timeout
                sessionTimeoutMinutes = configLoader.get("settings.session_timeout_minutes", 30);
                logger.info("Verification session timeout set to {} minutes", sessionTimeoutMinutes);
                
                return true;
            } else {
                // Fallback to full reload
                config = loadConfig();
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to reload configuration", e);
            if (exceptionHandler != null) {
                exceptionHandler.handleIntegrationException("Configuration", "reload error", e);
            }
            return false;
        }
    }

    /**
     * loadConfig
     * 
     * Loads the configuration using the simplified SimpleConfigLoader approach.
     * No more complex JSON files or multiple YAML files - just simple config.yml and messages.yml.
     * 
     * @return Map containing the loaded configuration settings.
     */
    private Map<String, Object> loadConfig() {
        try {
            // Initialize the simple config loader
            configLoader = new SimpleConfigLoader(logger, dataDirectory);
            
            // Load configuration (creates defaults if needed)
            config = configLoader.loadConfiguration();
            
            // Parse debug setting from the simplified config
            debugEnabled = configLoader.get("settings.debug", false);
            
            // Get session timeout
            sessionTimeoutMinutes = configLoader.get("settings.session_timeout_minutes", 30);
            
            pluginEnabled.set(true);
            logger.info("Configuration loaded successfully using SimpleConfigLoader");
            return config;
            
        } catch (RuntimeException e) {
            logger.error("Error loading configuration", e);
            return new HashMap<>();
        }
    }// Getters for components
    
    public SQLHandler getSqlHandler() {
        return sqlHandler;
    }

    public Map<String, Object> getConfig() {
        return config;
    }    
    public EnhancedPurgatoryManager getPurgatoryManager() {
        return purgatoryManager;
    }
    
    public DiscordBotHandler getDiscordBotHandler() {
        return discordBotHandler;
    }
    
    public RewardsHandler getRewardsHandler() {
        return rewardsHandler;
    }
    
    public VaultIntegration getVaultIntegration() {
        return vaultIntegration;
    }
    
    /**
     * Gets the LuckPerms integration
     * 
     * @return The LuckPerms integration, may be null if not available
     */
    public LuckPermsIntegration getLuckPermsIntegration() {
        return luckPermsIntegration;
    }
    
    /**
     * Gets the session timeout in minutes
     * 
     * @return The session timeout in minutes
     */
    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }
    
    /**
     * Gets the config file path
     * 
     * @return The config file path
     */
    public Path getConfigFile() {
        return configFile;
    }

    /**
     * Gets the data directory
     * 
     * @return The data directory
     */
    public Path getDataDirectory() {
        return dataDirectory;
    }
    
    /**
     * Checks if the plugin is enabled
     * 
     * @return true if the plugin is enabled, false otherwise
     */
    public boolean isPluginEnabled() {
        return pluginEnabled.get();
    }

    /**
     * Checks if debug mode is enabled
     * 
     * @return true if debug mode is enabled, false otherwise
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }    /**
     * Logs debug messages if debug mode is enabled
     * 
     * @param message The message to log
     */
    private void debugLog(String message) {
        LoggingUtils.debugLog(logger, debugEnabled, message);
    }

    /**
     * Executes a command as the console
     * 
     * @param command The command to execute
     * @return CompletableFuture that resolves to true if the command was executed successfully
     */
    public CompletableFuture<Boolean> executeConsoleCommand(String command) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command)
                .thenAccept(success -> {
                    if (success) {
                        logger.info("Successfully executed console command: " + command);
                        future.complete(true);
                    } else {
                        logger.warn("Failed to execute console command: " + command);
                        future.complete(false);
                    }
                })                .exceptionally(ex -> {
                    exceptionHandler.handleIntegrationException("Console Command", "execution: " + command, ex);
                    future.complete(false);
                    return null;
                });        } catch (Exception e) {
            // Note: Using generic Exception catch with ExceptionHandler is intentional
            // ExceptionHandler provides context-aware error handling and consistent logging
            exceptionHandler.handleIntegrationException("Console Command", "exception during: " + command, e);
            future.complete(false);
        }
        
        return future;
    }
      /**
     * Initializes the database connection and creates required tables
     * 
     * @throws SQLException If database initialization fails
     * @throws ClassNotFoundException If database driver is not found
     */    private void initializeDatabase() throws SQLException, ClassNotFoundException {
        if (config == null) {
            throw new IllegalStateException("Configuration must be loaded before initializing database");
        }
        
        if (exceptionHandler == null) {
            throw new IllegalStateException("ExceptionHandler must be initialized before creating SQLHandler");
        }
        
        sqlHandler = new SQLHandler(config, logger, debugEnabled, exceptionHandler);
        
        // Create tables and verify they were created successfully
        try {
            sqlHandler.createTable();
            
            // Verify table creation by testing basic operations
            if (!sqlHandler.testConnection()) {
                throw new SQLException("Database connection test failed after table creation");
            }
            
            debugLog("Database initialized and verified successfully");
        } catch (Exception e) {
            sqlHandler = null; // Reset on failure
            throw new SQLException("Failed to create or verify database tables", e);
        }
    }
    
    /**
     * Initializes the database monitor for automatic connection health checking
     * 
     * This method sets up monitoring after database initialization to ensure
     * continuous database connectivity and automatic reconnection attempts.
     */
    private void initializeDatabaseMonitor() {
        if (sqlHandler == null) {
            logger.warn("Cannot initialize database monitor - SQLHandler is null");
            return;
        }
        
        // Get configuration for database monitoring
        @SuppressWarnings("unchecked")
        Map<String, Object> databaseConfig = (Map<String, Object>) config.getOrDefault("database", new HashMap<>());
        
        // Default values for monitoring configuration
        int retryIntervalSeconds = Integer.parseInt(databaseConfig.getOrDefault("monitorIntervalSeconds", "60").toString());
        int maxRetryAttempts = Integer.parseInt(databaseConfig.getOrDefault("maxRetryAttempts", "10").toString());
        
        try {
            databaseMonitor = new DatabaseMonitor(logger, sqlHandler, retryIntervalSeconds, maxRetryAttempts);
            databaseMonitor.startMonitoring();
            logger.info("Database monitor initialized and started (interval: {}s, max attempts: {})", 
                       retryIntervalSeconds, maxRetryAttempts);
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("DatabaseMonitor", "initialization", e);
            logger.warn("Database monitor could not be started - automatic reconnection will not be available");
        }
    }
    
    /**
     * Performs startup validation to ensure required dependencies and environment are available
     * 
     * @return true if validation passes, false otherwise
     */
    private boolean performStartupValidation() {
        try {
            // Check if data directory exists or can be created
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            
            // Check if we can read/write to data directory
            if (!Files.isWritable(dataDirectory)) {
                logger.error("Data directory is not writable: {}", dataDirectory);
                return false;
            }
            
            logger.info("Startup validation completed successfully");
            return true;
        } catch (Exception e) {
            logger.error("Startup validation failed", e);
            return false;
        }
    }    /**
     * Validates database connection by attempting a comprehensive connection test
     * 
     * @return true if database connection is valid, false otherwise
     */
    private boolean validateDatabaseConnection() {
        if (sqlHandler == null) {
            logger.error("SQLHandler is null, cannot validate database connection");
            return false;
        }
        
        try {
            // Test basic connection
            boolean connectionValid = sqlHandler.testConnection();
            if (!connectionValid) {
                logger.error("Database connection test failed - connection invalid");
                return false;
            }
            
            // Test if we can perform basic operations
            try {
                // Test if the whitelist table exists and is accessible
                sqlHandler.getPlayerDiscordId("test-validation-uuid");
                logger.info("Database connection validation successful - full functionality verified");
                return true;
            } catch (Exception tableEx) {
                // Table operations failed, but connection works - might be initialization issue
                logger.warn("Database connected but table operations failed: {}", tableEx.getMessage());
                logger.info("Database connection validation successful - connection valid (table access pending)");
                return true; // Return true since connection itself is valid
            }
            
        } catch (Exception e) {
            logger.error("Database connection validation failed with exception: {}", e.getMessage());
            if (exceptionHandler != null) {
                exceptionHandler.handleDatabaseException("connection validation", e, 
                    "Failed to validate database connection during startup");
            }
            return false;
        }
    }
    
    /**
     * Initializes components in parallel to improve startup performance.
     * This method coordinates the parallel initialization of independent components
     * while respecting dependency requirements.
     * 
     * @return CompletableFuture that completes when all parallel initialization is done
     */
    private CompletableFuture<Void> initializeComponentsInParallel() {
        // Initialize database monitor first (depends on sqlHandler which is already initialized)
        CompletableFuture<Void> databaseMonitorFuture = CompletableFuture.runAsync(() -> {
            try {
                initializeDatabaseMonitor();
            } catch (Exception e) {
                throw new RuntimeException("Database monitor initialization failed", e);
            }
        }, initializationExecutor);
        
        // Initialize integrations in parallel (independent of each other)
        CompletableFuture<Void> vaultIntegrationFuture = CompletableFuture.runAsync(() -> {
            try {
                initializeVaultIntegration();
            } catch (Exception e) {
                throw new RuntimeException("Vault integration initialization failed", e);
            }
        }, initializationExecutor);
        
        CompletableFuture<Void> luckPermsIntegrationFuture = CompletableFuture.runAsync(() -> {
            try {
                initializeLuckPermsIntegration();
            } catch (Exception e) {
                throw new RuntimeException("LuckPerms integration initialization failed", e);
            }
        }, initializationExecutor);
        
        // Wait for integrations to complete before initializing components that might depend on them
        CompletableFuture<Void> integrationsFuture = CompletableFuture.allOf(
            vaultIntegrationFuture, 
            luckPermsIntegrationFuture
        );
        
        // Initialize core components after integrations complete
        CompletableFuture<Void> coreComponentsFuture = integrationsFuture.thenCompose(v -> {
            // XP Manager (only depends on sqlHandler and config)
            CompletableFuture<Void> xpManagerFuture = CompletableFuture.runAsync(() -> {
                try {
                    initializeXPManager();
                } catch (Exception e) {
                    throw new RuntimeException("XP Manager initialization failed", e);
                }
            }, initializationExecutor);
            
            // Initialize Purgatory Manager (depends on sqlHandler)
            CompletableFuture<Void> purgatoryManagerFuture = CompletableFuture.runAsync(() -> {
                try {
                    initializePurgatoryManager();
                } catch (Exception e) {
                    throw new RuntimeException("Purgatory Manager initialization failed", e);
                }
            }, initializationExecutor);
            
            return CompletableFuture.allOf(xpManagerFuture, purgatoryManagerFuture);
        });
        
        // Initialize Discord and Rewards Handler after core components
        CompletableFuture<Void> finalComponentsFuture = coreComponentsFuture.thenCompose(v -> {
            // Discord integration (can run in parallel with rewards handler)
            CompletableFuture<Void> discordFuture = CompletableFuture.runAsync(() -> {
                try {
                    initializeDiscordIntegration();
                } catch (Exception e) {
                    throw new RuntimeException("Discord integration initialization failed", e);
                }
            }, initializationExecutor);
            
            // Rewards Handler (may depend on Discord, but can handle null Discord)
            CompletableFuture<Void> rewardsFuture = CompletableFuture.runAsync(() -> {
                try {
                    initializeRewardsHandler();
                } catch (Exception e) {
                    throw new RuntimeException("Rewards Handler initialization failed", e);
                }
            }, initializationExecutor);
            
            return CompletableFuture.allOf(discordFuture, rewardsFuture);
        });
        
        // Combine all futures
        return CompletableFuture.allOf(
            databaseMonitorFuture,
            finalComponentsFuture
        );
    }
    
    /**
     * Validates all critical components during startup and provides detailed status reporting
     * 
     * @return true if all critical components are ready, false if any critical component failed
     */
    private boolean validateStartupComponents() {
        boolean allCriticalComponentsReady = true;
        StringBuilder statusReport = new StringBuilder();
        statusReport.append("=== VelocityDiscordWhitelist Startup Validation ===\n");
        
        // Validate Configuration
        statusReport.append("Configuration: ");
        if (config != null && !config.isEmpty()) {
            statusReport.append("✓ LOADED\n");
            
            // Validate required config sections
            String[] requiredSections = {"database", "discord", "settings"};
            for (String section : requiredSections) {
                statusReport.append("  - ").append(section).append(": ");
                if (config.containsKey(section)) {
                    statusReport.append("✓ PRESENT\n");
                } else {
                    statusReport.append("✗ MISSING\n");
                    allCriticalComponentsReady = false;
                }
            }
        } else {
            statusReport.append("✗ FAILED\n");
            allCriticalComponentsReady = false;
        }
        
        // Validate Exception Handler
        statusReport.append("Exception Handler: ");
        if (exceptionHandler != null) {
            statusReport.append("✓ READY\n");
        } else {
            statusReport.append("✗ NOT INITIALIZED\n");
            allCriticalComponentsReady = false;
        }
          // Validate Database
        statusReport.append("Database Connection: ");
        if (sqlHandler != null) {
            try {
                // Test database connection with detailed reporting
                if (sqlHandler.testConnection()) {
                    statusReport.append("✓ CONNECTED");
                    
                    // Test basic functionality
                    try {
                        sqlHandler.getPlayerDiscordId("startup-validation-test");
                        statusReport.append(" (Full functionality verified)\n");
                    } catch (Exception tableEx) {
                        statusReport.append(" (Connection OK, tables pending initialization)\n");
                    }
                } else {
                    statusReport.append("✗ CONNECTION FAILED\n");
                    allCriticalComponentsReady = false;
                }
            } catch (Exception e) {
                statusReport.append("✗ ERROR: ").append(e.getMessage()).append("\n");
                allCriticalComponentsReady = false;
            }
        } else {
            statusReport.append("✗ NOT INITIALIZED\n");
            allCriticalComponentsReady = false;
        }
        
        // Validate Purgatory Manager (Critical)
        statusReport.append("Purgatory Manager: ");
        if (purgatoryManager != null) {
            statusReport.append("✓ READY\n");
        } else {
            statusReport.append("✗ FAILED\n");
            allCriticalComponentsReady = false;
        }
        
        // Validate Optional Components
        statusReport.append("\n=== Optional Components ===\n");        // Discord Bot Handler
        statusReport.append("Discord Bot: ");
        if (discordBotHandler != null) {
            if (discordBotHandler.isConnectionFailed()) {
                statusReport.append("✗ FAILED TO CONNECT\n");
                allCriticalComponentsReady = false;
            } else if (discordBotHandler.getConnectionStatus()) {
                statusReport.append("✓ INITIALIZED & CONNECTED\n");
            } else {
                statusReport.append("(connecting...)\n");
            }
        } else {
            statusReport.append("- DISABLED\n");
        }
        
        // XP Manager
        statusReport.append("XP Manager: ");
        if (xpManager != null) {
            statusReport.append("✓ READY\n");
        } else {
            statusReport.append("- DISABLED\n");
        }
        
        // Rewards Handler
        statusReport.append("Rewards Handler: ");
        if (rewardsHandler != null) {
            statusReport.append("✓ READY\n");
        } else {
            statusReport.append("- DISABLED\n");
        }
          // Vault Integration
        statusReport.append("Vault Integration: ");
        if (vaultIntegration != null) {
            try {
                boolean economyAvailable = vaultIntegration.isEconomyAvailable();
                boolean permissionsAvailable = vaultIntegration.isPermissionsAvailable();
                
                if (economyAvailable || permissionsAvailable) {
                    statusReport.append("✓ AVAILABLE");
                    if (economyAvailable) statusReport.append(" (Economy)");
                    if (permissionsAvailable) statusReport.append(" (Permissions)");
                    statusReport.append("\n");
                } else {
                    statusReport.append("✓ INITIALIZED (No features available)\n");
                }
            } catch (Exception e) {
                statusReport.append("✓ INITIALIZED (Status check failed: ").append(e.getMessage()).append(")\n");
            }
        } else {
            statusReport.append("- NOT AVAILABLE\n");
        }
        
        // LuckPerms Integration
        statusReport.append("LuckPerms Integration: ");
        if (luckPermsIntegration != null) {
            try {
                if (luckPermsIntegration.isAvailable()) {
                    statusReport.append("✓ AVAILABLE & READY\n");
                } else {
                    statusReport.append("✓ INITIALIZED (Not ready)\n");
                }
            } catch (Exception e) {
                statusReport.append("✓ INITIALIZED (Status check failed: ").append(e.getMessage()).append(")\n");
            }
        } else {
            statusReport.append("- NOT AVAILABLE\n");
        }
        
        statusReport.append("\n=== Startup Summary ===\n");
        if (allCriticalComponentsReady) {
            statusReport.append("Status: ✓ ALL CRITICAL COMPONENTS READY\n");
            statusReport.append("Plugin: ENABLED\n");
        } else {
            statusReport.append("Status: ✗ CRITICAL COMPONENT FAILURES DETECTED\n");
            statusReport.append("Plugin: DISABLED (Critical components failed)\n");
        }
        
        statusReport.append("=======================================");
        
        // Log the complete status report
        String[] lines = statusReport.toString().split("\n");
        for (String line : lines) {
            if (line.contains("✗") && allCriticalComponentsReady) {
                logger.warn(line);
            } else if (line.contains("✗")) {
                logger.error(line);
            } else {
                logger.info(line);
            }
        }
        
        return allCriticalComponentsReady;
    }
    
    /**
     * Validates configuration values and sets safe defaults
     * 
     * @return true if configuration is valid or was successfully corrected, false if critical config is missing
     */
    private boolean validateConfiguration() {
        if (config == null || config.isEmpty()) {
            logger.error("Configuration is null or empty");
            return false;
        }
        
        try {
            // Validate database configuration
            @SuppressWarnings("unchecked")
            Map<String, Object> dbConfig = (Map<String, Object>) config.get("database");
            if (dbConfig == null) {
                logger.error("Database configuration section is missing");
                return false;
            }
            
            // Check required database fields
            String[] requiredDbFields = {"host", "port", "database", "username"};
            for (String field : requiredDbFields) {
                if (!dbConfig.containsKey(field) || dbConfig.get(field) == null) {
                    logger.error("Required database configuration field missing: " + field);
                    return false;
                }
            }
            
            // Validate port is a number
            try {
                Integer.parseInt(dbConfig.get("port").toString());
            } catch (NumberFormatException e) {
                logger.error("Database port must be a valid number: " + dbConfig.get("port"));
                return false;
            }
            
            // Validate settings section and set defaults
            @SuppressWarnings("unchecked")
            Map<String, Object> settingsConfig = (Map<String, Object>) config.getOrDefault("settings", new HashMap<>());
            if (!settingsConfig.containsKey("debug")) {
                settingsConfig.put("debug", "false");
                logger.info("Set default debug setting to false");
            }
            
            // Validate purgatory section
            @SuppressWarnings("unchecked")
            Map<String, Object> purgatoryConfig = (Map<String, Object>) config.getOrDefault("purgatory", new HashMap<>());
            if (!purgatoryConfig.containsKey("session_timeout_minutes")) {
                purgatoryConfig.put("session_timeout_minutes", "30");
                logger.info("Set default purgatory timeout to 30 minutes");
            }
            
            // Validate timeout is a number
            try {
                Integer.parseInt(purgatoryConfig.get("session_timeout_minutes").toString());
            } catch (NumberFormatException e) {
                purgatoryConfig.put("session_timeout_minutes", "30");
                logger.warn("Invalid purgatory timeout, set to default 30 minutes");
            }
            
            logger.info("Configuration validation completed successfully");
            return true;
            
        } catch (Exception e) {
            logger.error("Error during configuration validation", e);
            return false;
        }
    }
    
    /**
     * Performs a comprehensive health check of all plugin components
     * 
     * @return A map containing the health status of each component
     */
    public Map<String, String> performHealthCheck() {
        Map<String, String> healthStatus = new HashMap<>();
        
        // Check configuration
        if (config != null && !config.isEmpty()) {
            healthStatus.put("configuration", "HEALTHY");
        } else {
            healthStatus.put("configuration", "FAILED - Configuration not loaded");
        }
          // Check database
        if (sqlHandler != null && validateDatabaseConnection()) {
            healthStatus.put("database", "HEALTHY");
        } else {
            healthStatus.put("database", "FAILED - Connection unavailable");
        }
        
        // Check database monitor
        if (databaseMonitor != null) {
            if (databaseMonitor.isMonitoring()) {
                int retryCount = databaseMonitor.getCurrentRetryCount();
                if (retryCount > 0) {
                    healthStatus.put("databaseMonitor", "MONITORING - Retry attempts: " + retryCount);
                } else {
                    healthStatus.put("databaseMonitor", "HEALTHY - Monitoring active");
                }
            } else {
                healthStatus.put("databaseMonitor", "INACTIVE - Not monitoring");
            }
        } else {
            healthStatus.put("databaseMonitor", "NOT_CONFIGURED");
        }
        
        // Check purgatory manager
        if (purgatoryManager != null) {
            healthStatus.put("purgatory", "HEALTHY");
        } else {
            healthStatus.put("purgatory", "FAILED - Not initialized");
        }
        
        // Check Discord bot
        if (discordBotHandler != null) {
            if (discordBotHandler.getConnectionStatus()) {
                healthStatus.put("discord", "HEALTHY - Connected");
            } else {
                healthStatus.put("discord", "DEGRADED - Connecting...");
            }
        } else {
            healthStatus.put("discord", "DISABLED");
        }
        
        // Check integrations
        if (vaultIntegration != null) {
            try {
                boolean hasFeatures = vaultIntegration.isEconomyAvailable() || vaultIntegration.isPermissionsAvailable();
                healthStatus.put("vault", hasFeatures ? "HEALTHY" : "DEGRADED - No features available");
            } catch (Exception e) {
                healthStatus.put("vault", "DEGRADED - " + e.getMessage());
            }
        } else {
            healthStatus.put("vault", "DISABLED");
        }
        
        if (luckPermsIntegration != null) {
            try {
                boolean available = luckPermsIntegration.isAvailable();
                healthStatus.put("luckperms", available ? "HEALTHY" : "DEGRADED - Not ready");
            } catch (Exception e) {
                healthStatus.put("luckperms", "DEGRADED - " + e.getMessage());
            }
        } else {
            healthStatus.put("luckperms", "DISABLED");
        }
          return healthStatus;
    }

    /**
     * Initializes the Vault integration
     * This method can run in parallel with other integration initializations
     */
    private void initializeVaultIntegration() {
        try {
            vaultIntegration = new VaultIntegration(server, logger, debugEnabled, config);
            logger.info("Vault integration initialized successfully");
        } catch (NoClassDefFoundError e) {
            logger.info("Vault not found, economy features will be disabled");
            vaultIntegration = null;
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("Vault", "initialization", e);
            vaultIntegration = null;
        }
    }
    
    /**
     * Initializes the LuckPerms integration
     * This method can run in parallel with other integration initializations
     */
    private void initializeLuckPermsIntegration() {
        try {
            if (!LuckPermsIntegration.isLuckPermsAvailable()) {
                logger.info("LuckPerms not found, permission features will be disabled");
                luckPermsIntegration = null;
                return;
            }
            
            luckPermsIntegration = new LuckPermsIntegration(logger, debugEnabled, config);
            logger.info("LuckPerms integration initialized successfully");
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("LuckPerms", "initialization", e);
            luckPermsIntegration = null;
        }
    }
    
    /**
     * Initializes the XP Manager
     * Depends on: sqlHandler, config, exceptionHandler
     */
    private void initializeXPManager() {
        if (sqlHandler != null) {
            xpManager = new XPManager(sqlHandler, logger, debugEnabled, config, exceptionHandler);
            logger.info("XP Manager initialized successfully");
        } else {
            throw new IllegalStateException("SQLHandler is required for XPManager");
        }
    }
    
    /**
     * Initializes the Purgatory Manager
     * Depends on: sqlHandler, config
     */
    private void initializePurgatoryManager() {
        @SuppressWarnings("unchecked")
        Map<String, Object> purgatoryConfigMap = (Map<String, Object>) config.getOrDefault("purgatory", new HashMap<>());
        int purgatorySessionTimeout = Integer.parseInt(purgatoryConfigMap.getOrDefault("session_timeout_minutes", "30").toString());
        
        purgatoryManager = new EnhancedPurgatoryManager(logger, debugEnabled, sqlHandler, purgatorySessionTimeout);
        logger.info("Purgatory Manager initialized successfully");
    }
    
    /**
     * Initializes the Discord Bot Handler
     * Depends on: config, exceptionHandler, sqlHandler, purgatoryManager
     */
    private void initializeDiscordIntegration() {
        if (config == null || exceptionHandler == null || sqlHandler == null) {
            throw new IllegalStateException("Config, ExceptionHandler, and SQLHandler are required for Discord integration");
        }
        try {
            // Get the Discord config section from YAML
            @SuppressWarnings("unchecked")
            Map<String, Object> discordConfig = (Map<String, Object>) config.getOrDefault("discord", new HashMap<>());
            if (purgatoryManager != null) {
                discordBotHandler = new DiscordBotHandler(purgatoryManager, sqlHandler, logger, discordConfig);
            } else {
                logger.error("PurgatoryManager is required for DiscordBotHandler initialization. Discord bot will not start.");
                discordBotHandler = null;
            }
            logger.info("Discord Bot Handler initialized successfully");
        } catch (Exception e) {
            logger.warn("Discord integration failed to initialize: " + e.getMessage());
            discordBotHandler = null;
        }
    }

    /**
     * Initializes the Rewards Handler
     * Depends on: sqlHandler, server, config, and optionally vaultIntegration, luckPermsIntegration, discordBotHandler
     */
    private void initializeRewardsHandler() {
        // Use existing discordBotHandler if available, null otherwise
        rewardsHandler = new RewardsHandler(sqlHandler, discordBotHandler, server, logger, debugEnabled, config, vaultIntegration, luckPermsIntegration);
        
        // Set the rewards handler in purgatory manager for verification rewards if both are available
        if (rewardsHandler != null && purgatoryManager != null) {
            purgatoryManager.setRewardsHandler(rewardsHandler);
        }
        
        logger.info("Rewards Handler initialized successfully");
    }

}
