// VelocityDiscordWhitelist - MIT License

// VelocityDiscordWhitelist v1.0.2
// Author: jk33v3rs based on the plugin VelocityWhitelist by Rathinosk
// Portions of code used are used under MIT licence
// DISCLAIMER: AI tools were used in the IDE used to create this plugin, which included direct (but supervised) access to code
// Please report any issues via GitHub. Pull requests are accepted.

package top.jk33v3rs.velocitydiscordwhitelist.modules;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bstats.velocity.Metrics;

import com.google.gson.JsonObject;
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
import top.jk33v3rs.velocitydiscordwhitelist.config.JsonConfigLoader;
import top.jk33v3rs.velocitydiscordwhitelist.config.YamlConfigLoader;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.integrations.LuckPermsIntegration;
import top.jk33v3rs.velocitydiscordwhitelist.integrations.VaultIntegration;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;
import top.jk33v3rs.velocitydiscordwhitelist.utils.LoggingUtils;

/** Main VelocityDiscordWhitelist plugin class. */
@Plugin(
        id = "velocitydiscordwhitelist",
        name = "VelocityDiscordWhitelist",
        version = "1.0.5",
        url = "https://github.com/jk33v3rs/VelocityDiscordWhitelist",
        description = "A whitelist plugin for Velocity that integrates with Discord",
        authors = {"jk33v3rs"}
)
public class VelocityDiscordWhitelist {    private final ProxyServer server;
    private final org.slf4j.Logger logger;
    private final Path dataDirectory;
    private final Path configFile;
    @SuppressWarnings("unused") // Keep for future use with metrics
    private final Metrics.Factory metricsFactory;// Components
    private SQLHandler sqlHandler;
    private YamlConfigLoader configLoader;
    private Map<String, Object> config;
    private JsonConfigLoader jsonConfigLoader;
    private EnhancedPurgatoryManager purgatoryManager;
    private DiscordBotHandler discordBotHandler;
    private RewardsHandler rewardsHandler;
    private XPManager xpManager;
    private BrigadierCommandHandler commandHandler;    private VaultIntegration vaultIntegration;
    private LuckPermsIntegration luckPermsIntegration;
    private ScheduledTask purgatoryCleanupTask;
    private ExceptionHandler exceptionHandler;
    
    // Configuration
    private final AtomicBoolean pluginEnabled = new AtomicBoolean(false);
    private Boolean debugEnabled = false;
    private int sessionTimeoutMinutes = 30; // Default value
      // JSON configurations
    private JsonObject ranksConfig;
    private JsonObject rewardsConfig;
    
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
     * It registers commands, loads the configuration, and initializes database.
     *
     * @param event The ProxyInitializeEvent.
     */    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            // Initialize exception handler first for proper error handling during startup
            this.exceptionHandler = new ExceptionHandler(logger, false); // Will be updated after config loads
            
            // Perform startup validation
            if (!performStartupValidation()) {
                exceptionHandler.handleIntegrationException("Plugin", "startup validation failed", 
                    new IllegalStateException("Critical startup validation failed"));
                return;
            }
              loadConfig();
            
            // Validate configuration before proceeding
            if (!validateConfiguration()) {
                exceptionHandler.handleIntegrationException("Plugin", "configuration validation failed", 
                    new IllegalStateException("Configuration validation failed - check logs for details"));
                return;
            }
            
            // Update exception handler with actual debug setting
            this.exceptionHandler = new ExceptionHandler(logger, debugEnabled);
            
            initializeDatabase();
              // Validate database connection
            if (!validateDatabaseConnection()) {
                exceptionHandler.handleDatabaseException("connection validation", 
                    new SQLException("Database connection validation failed"), "Startup validation");
                return;
            }
            
            // Initialize integration handlers with proper error handling
            initializeIntegrations();
            
            // Initialize core components with validation
            initializeCoreComponents();
            
            // Initialize Discord bot handler if enabled
            initializeDiscordIntegration();            // Validate all components before enabling the plugin
            boolean startupSuccessful = validateStartupComponents();
            
            if (startupSuccessful) {
                // Initialize command handler only if all critical components are ready
                try {
                    if (purgatoryManager == null || sqlHandler == null) {
                        throw new IllegalStateException("Critical components missing: purgatoryManager or sqlHandler is null");
                    }
                    
                    commandHandler = new BrigadierCommandHandler(server, logger, purgatoryManager, rewardsHandler, xpManager, sqlHandler, debugEnabled, exceptionHandler);
                    commandHandler.registerCommands();
                    
                    // Register event listeners
                    server.getEventManager().register(this, this);
                    
                    logger.info("VelocityDiscordWhitelist plugin has been enabled successfully!");
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
        }
    }    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("VelocityDiscordWhitelist plugin is shutting down...");
        
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
                // Discord bot will shutdown automatically with JDA
                logger.info("Discord bot handler shutdown");
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
    }@SuppressWarnings("UnnecessaryTemporary")  
    public boolean reloadConfig() {
        try {
            config = loadConfig();
              // Refresh debug mode from settings
            @SuppressWarnings("unchecked")
            Map<String, Object> settingsConfig = (Map<String, Object>) config.getOrDefault("settings", new HashMap<>());
            if (settingsConfig.containsKey("debug")) {
                String debugValueStr = settingsConfig.get("debug").toString();
                debugEnabled = Boolean.parseBoolean(debugValueStr);
                logger.info("Debug mode is now {}", debugEnabled ? "enabled" : "disabled");
            }
            
            // Refresh session timeout
            @SuppressWarnings("unchecked")
            Map<String, Object> sessionConfig = (Map<String, Object>) config.getOrDefault("session", new HashMap<>());
            if (sessionConfig.containsKey("timeout")) {
                sessionTimeoutMinutes = Integer.parseInt(sessionConfig.get("timeout").toString());
                logger.info("Verification session timeout set to {} minutes", sessionTimeoutMinutes);
            }
            
            // Reload JSON configurations
            ranksConfig = jsonConfigLoader.loadConfig("ranks", JsonConfigLoader.getDefaultRanksConfig());
            rewardsConfig = jsonConfigLoader.loadConfig("rewards", JsonConfigLoader.getDefaultRewardsConfig());
            
            // Refresh rewards handler data
            if (rewardsHandler != null) {
                rewardsHandler.loadRankDefinitions();
            }
            
            return true;        } catch (NumberFormatException e) {
            exceptionHandler.handleIntegrationException("Configuration", "reload - parsing error", e);
            return false;
        }
    }/**
     * Saves the default configuration file if it doesn't exist
     */
    private void saveDefaultConfig() {
        try {
            Path yamlConfigFile = dataDirectory.resolve("config.yaml");
            Files.createDirectories(dataDirectory);
            if (!Files.exists(yamlConfigFile)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yaml");
                     OutputStream out = Files.newOutputStream(yamlConfigFile)) {
                    if (in == null) {
                        logger.error("Default config.yaml not found in resources");
                    } else {
                        // Copy from resource
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            out.write(buffer, 0, read);
                        }
                        logger.info("Default config.yaml created.");
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error saving default configuration", e);
        }
    }    /**
     * loadConfig     * Loads the configuration from the YAML file and sets up default values.
     * @return Map containing the loaded configuration settings.
     */
    @SuppressWarnings("UnnecessaryTemporary")
    private Map<String, Object> loadConfig() {
        try {
            Path yamlConfigFile = dataDirectory.resolve("config.yaml");
            if (Files.exists(yamlConfigFile)) {
                configLoader = new YamlConfigLoader(logger, dataDirectory);
                config = configLoader.loadConfig("config");
            } else {
                saveDefaultConfig();
                configLoader = new YamlConfigLoader(logger, dataDirectory);
                config = configLoader.loadConfig("config");
            }            
            // Parse debug setting properly from settings section
            @SuppressWarnings("unchecked")
            Map<String, Object> settingsConfig = (Map<String, Object>) config.getOrDefault("settings", new HashMap<>());
            String debugValueStr = settingsConfig.getOrDefault("debug", "false").toString();
            debugEnabled = Boolean.parseBoolean(debugValueStr);
            
            // Initialize JSON config loader
            jsonConfigLoader = new JsonConfigLoader(logger, dataDirectory);
            
            // Load JSON configurations
            ranksConfig = jsonConfigLoader.loadConfig("ranks", JsonConfigLoader.getDefaultRanksConfig());
            rewardsConfig = jsonConfigLoader.loadConfig("rewards", JsonConfigLoader.getDefaultRewardsConfig());
            
            pluginEnabled.set(true);
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
    
    public JsonObject getRanksConfig() {
        return ranksConfig;
    }
      
    public JsonObject getRewardsConfig() {
        return rewardsConfig;
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
    }
      /**
     * Validates database connection by attempting a simple connection test
     * 
     * @return true if database connection is valid, false otherwise
     */
    private boolean validateDatabaseConnection() {
        if (sqlHandler == null) {
            logger.error("SQLHandler is null, cannot validate database connection");
            return false;
        }
        
        try {
            boolean connectionValid = sqlHandler.testConnection();
            if (connectionValid) {
                logger.info("Database connection validation successful");
            } else {
                logger.error("Database connection validation failed");
            }
            return connectionValid;
        } catch (Exception e) {
            logger.error("Database connection validation failed with exception", e);
            return false;
        }
    }
    
    /**
     * Initializes integration handlers with proper error handling
     */
    private void initializeIntegrations() {
        // Initialize Vault integration
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
        
        // Initialize LuckPerms integration
        try {
            luckPermsIntegration = new LuckPermsIntegration(logger, debugEnabled, config);
            logger.info("LuckPerms integration initialized successfully");
        } catch (NoClassDefFoundError e) {
            logger.info("LuckPerms not found, permission features will be disabled");
            luckPermsIntegration = null;
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("LuckPerms", "initialization", e);
            luckPermsIntegration = null;
        }
    }
    
    /**
     * Initializes core plugin components
     */
    private void initializeCoreComponents() {
        try {            // Initialize XP Manager
            if (sqlHandler != null) {
                xpManager = new XPManager(sqlHandler, logger, debugEnabled, config, exceptionHandler);
                logger.info("XP Manager initialized successfully");
            } else {
                throw new IllegalStateException("SQLHandler is required for XPManager");
            }
            
            // Initialize Rewards Handler
            if (discordBotHandler != null) {
                rewardsHandler = new RewardsHandler(sqlHandler, discordBotHandler, logger, debugEnabled, config, vaultIntegration, luckPermsIntegration);
            } else {
                rewardsHandler = new RewardsHandler(sqlHandler, null, logger, debugEnabled, config, vaultIntegration, luckPermsIntegration);
            }
            logger.info("Rewards Handler initialized successfully");
            
            // Initialize Purgatory Manager
            @SuppressWarnings("unchecked")
            Map<String, Object> purgatoryConfigMap = (Map<String, Object>) config.getOrDefault("purgatory", new HashMap<>());
            int purgatorySessionTimeout = Integer.parseInt(purgatoryConfigMap.getOrDefault("session_timeout_minutes", "30").toString());            purgatoryManager = new EnhancedPurgatoryManager(logger, debugEnabled, sqlHandler, purgatorySessionTimeout);
            
            // Set the rewards handler in purgatory manager for verification rewards
            if (rewardsHandler != null) {
                purgatoryManager.setRewardsHandler(rewardsHandler);
            }
            
            logger.info("Purgatory Manager initialized successfully");
            
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("Core Components", "initialization", e);
        }
    }
    
    /**
     * Initializes Discord integration if enabled
     */
    private void initializeDiscordIntegration() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> discordConfig = (Map<String, Object>) config.get("discord");
            
            if (discordConfig == null) {
                logger.info("Discord configuration not found, Discord features will be disabled");
                return;
            }
            
            boolean discordEnabled = Boolean.parseBoolean(discordConfig.getOrDefault("enabled", "false").toString());
            
            if (!discordEnabled) {
                logger.info("Discord integration is disabled in configuration");
                return;
            }
            
            // Initialize Discord bot handler
            try {
                discordBotHandler = new DiscordBotHandler(logger, sqlHandler);
                
                // Set the purgatory manager in the Discord bot handler
                if (purgatoryManager != null) {
                    discordBotHandler.setPurgatoryManager(purgatoryManager);
                }
                
                // Initialize the Discord bot with the configuration
                Properties discordProperties = new Properties();
                for (Map.Entry<String, Object> entry : discordConfig.entrySet()) {
                    if (entry.getValue() != null) {
                        discordProperties.setProperty("discord." + entry.getKey(), entry.getValue().toString());
                    }
                }
                  // Initialize the Discord bot asynchronously
                discordBotHandler.initialize(discordProperties)
                    .thenAccept(success -> {                        if (success) {
                            logger.info("Discord bot successfully connected and initialized");
                        } else {
                            logger.warn("Discord bot initialization failed or was disabled");
                            discordBotHandler = null; // Clear failed handler
                        }
                    })
                    .exceptionally(ex -> {
                        exceptionHandler.handleIntegrationException("Discord Bot", "initialization", ex);
                        discordBotHandler = null; // Clear failed handler
                        return null;
                    });
                
                logger.info("Discord bot handler initialized");
            } catch (Exception e) {
                exceptionHandler.handleIntegrationException("Discord Bot Handler", "initialization", e);
                discordBotHandler = null;
            }
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("Discord Integration", "initialization", e);
        }
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
                // Test database connection
                if (sqlHandler.testConnection()) {
                    statusReport.append("✓ CONNECTED\n");
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
        statusReport.append("\n=== Optional Components ===\n");
        
        // Discord Bot Handler
        statusReport.append("Discord Bot: ");
        if (discordBotHandler != null) {
            statusReport.append("✓ INITIALIZED");
            if (discordBotHandler.isConnected()) {
                statusReport.append(" & CONNECTED\n");
            } else {
                statusReport.append(" (connecting...)\n");
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
            statusReport.append("✓ AVAILABLE\n");
        } else {
            statusReport.append("- NOT AVAILABLE\n");
        }
        
        // LuckPerms Integration
        statusReport.append("LuckPerms Integration: ");
        if (luckPermsIntegration != null) {
            statusReport.append("✓ AVAILABLE\n");
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

}
