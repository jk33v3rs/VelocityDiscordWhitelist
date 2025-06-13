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
        version = "1.0.4",
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
    }

    /**
     * Event listener for the ProxyInitializeEvent.
     * This method is called when the proxy server is initialized.
     * It registers commands, loads the configuration, and initializes database.
     *
     * @param event The ProxyInitializeEvent.
     */    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            loadConfig();
            initializeDatabase();
            
            // Initialize exception handler after config is loaded
            this.exceptionHandler = new ExceptionHandler(logger, debugEnabled);
            
            // Initialize integration handlers
            try {
                vaultIntegration = new VaultIntegration(server, logger, debugEnabled, config);
            } catch (NoClassDefFoundError e) {
                vaultIntegration = null;
            }
            
            try {
                luckPermsIntegration = new LuckPermsIntegration(logger, debugEnabled, config);
            } catch (NoClassDefFoundError e) {
                logger.warn("LuckPerms not found, permission features will be disabled");
                luckPermsIntegration = null;
            }

            // Initialize Discord bot handler if enabled
            @SuppressWarnings("unchecked")
            Map<String, Object> discordConfig = (Map<String, Object>) config.get("discord");
            boolean discordEnabled = Boolean.parseBoolean(discordConfig.getOrDefault("enabled", "false").toString());
            
            if (discordEnabled) {
                try {
                    // Use the correct constructor that exists in DiscordBotHandler
                    discordBotHandler = new DiscordBotHandler(logger, sqlHandler);
                    logger.info("Discord bot handler initialized");
                } catch (IllegalArgumentException | SecurityException e) {
                    logger.error("Failed to initialize Discord bot handler", e);
                    discordBotHandler = null;
                }
            }

            // Initialize other components
            xpManager = new XPManager(sqlHandler, logger, debugEnabled, config);
            
            if (discordBotHandler != null) {
                rewardsHandler = new RewardsHandler(sqlHandler, discordBotHandler, logger, debugEnabled, config, vaultIntegration, luckPermsIntegration);
            } else {
                rewardsHandler = new RewardsHandler(sqlHandler, null, logger, debugEnabled, config, vaultIntegration, luckPermsIntegration);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> purgatoryConfigMap = (Map<String, Object>) config.getOrDefault("purgatory", new HashMap<>());
            int purgatorySessionTimeout = Integer.parseInt(purgatoryConfigMap.getOrDefault("session_timeout_minutes", "30").toString());
            purgatoryManager = new EnhancedPurgatoryManager(logger, debugEnabled, sqlHandler, purgatorySessionTimeout);
            
            // Set the purgatory manager in the Discord bot handler after it's created
            if (discordBotHandler != null) {
                discordBotHandler.setPurgatoryManager(purgatoryManager);
                
                // Initialize the Discord bot with the configuration
                Properties discordProperties = new Properties();
                for (Map.Entry<String, Object> entry : discordConfig.entrySet()) {
                    if (entry.getValue() != null) {
                        discordProperties.setProperty("discord." + entry.getKey(), entry.getValue().toString());
                    }
                }
                
                // Initialize the Discord bot asynchronously
                discordBotHandler.initialize(discordProperties)
                    .thenAccept(success -> {
                        if (success) {
                            logger.info("Discord bot successfully connected and initialized");
                        } else {
                            logger.warn("Discord bot initialization failed or was disabled");
                        }
                    })
                    .exceptionally(ex -> {
                        logger.error("Error during Discord bot initialization", ex);
                        return null;
                    });
            }
            
            commandHandler = new BrigadierCommandHandler(server, logger, purgatoryManager, rewardsHandler, xpManager, sqlHandler, debugEnabled);
            
            // Register event listeners
            server.getEventManager().register(this, this);
              logger.info("VelocityDiscordWhitelist plugin has been enabled!");
            
        } catch (ClassNotFoundException | SQLException e) {
            exceptionHandler.handleIntegrationException("Plugin", "initialization", e);
            // Don't disable the plugin completely, just log the error
        }
    }    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (sqlHandler != null) {
            sqlHandler.close();
        }
        
        if (commandHandler != null) {
            commandHandler.registerCommands();
        }
        
        if (purgatoryCleanupTask != null) {
            purgatoryCleanupTask.cancel();
        }
    }    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        if (!pluginEnabled.get() || !Boolean.parseBoolean(config.get("enabled").toString())) {
            return;
        }

        Player player = event.getPlayer();
        String username = player.getUsername();
        
        // Handle Geyser/Bedrock prefix
        String originalUsername = username;
        
        if (Boolean.parseBoolean(config.get("geyser.enabled").toString())) {
            String geyserPrefix = config.get("geyser.prefix").toString();
            if (username.startsWith(geyserPrefix)) {
                username = username.substring(geyserPrefix.length());
                debugLog("Detected Bedrock player: " + originalUsername + " -> " + username);
            }
        }

        final String finalUsername = username;
        debugLog("Checking whitelist for " + finalUsername);

        try {
            sqlHandler.isPlayerWhitelisted(player)
                .whenComplete((isWhitelisted, error) -> {
                    if (error != null) {
                        logger.error("Error checking whitelist for player " + finalUsername, error);
                        event.setResult(ComponentResult.denied(Component.text("An error occurred while checking the whitelist.")));
                        return;
                    }

                    if (!isWhitelisted) {
                        String message = config.get("messages.notWhitelisted").toString();
                        event.setResult(ComponentResult.denied(Component.text(message)));
                        debugLog("Access denied for " + finalUsername);
                    } else {
                        debugLog("Player " + finalUsername + " is whitelisted, allowing login");
                    }
                });
        } catch (Exception e) {
            logger.error("Error initiating whitelist check for player " + finalUsername, e);
            event.setResult(ComponentResult.denied(Component.text("An error occurred while checking the whitelist.")));
        }
    }
      @Subscribe
    public void onServerPreConnect(com.velocitypowered.api.event.player.ServerPreConnectEvent event) {
        if (!pluginEnabled.get()) {
            return;
        }

        Player player = event.getPlayer();
        String username = player.getUsername();

        // Check if player is in purgatory
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
    }
    @SuppressWarnings("UnnecessaryTemporary")  
    public boolean reloadConfig() {
        try {
            config = loadConfig();
              // Refresh debug mode
            if (config.containsKey("debug")) {                String debugValueStr = (String) config.get("debug");
                debugEnabled = Boolean.parseBoolean(debugValueStr);
                logger.info("Debug mode is now {}", debugEnabled ? "enabled" : "disabled");
            }
            
            // Refresh session timeout
            if (config.containsKey("session.timeout.minutes")) {
                sessionTimeoutMinutes = Integer.parseInt((String) config.get("session.timeout.minutes"));
                logger.info("Verification session timeout set to {} minutes", sessionTimeoutMinutes);
            }
            
            // Reload JSON configurations
            ranksConfig = jsonConfigLoader.loadConfig("ranks", JsonConfigLoader.getDefaultRanksConfig());
            rewardsConfig = jsonConfigLoader.loadConfig("rewards", JsonConfigLoader.getDefaultRewardsConfig());
            
            // Refresh rewards handler data
            rewardsHandler.loadRankDefinitions();
            
            return true;        } catch (NumberFormatException e) {
            logger.error("Error parsing configuration values", e);
            return false;
        }
    }    /**
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
            // Parse debug setting properly
            @SuppressWarnings("unchecked")
            Map<String, Object> generalConfig = (Map<String, Object>) config.getOrDefault("general", new HashMap<>());
            String debugValueStr = (String) generalConfig.getOrDefault("debug", "false");
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
            return new HashMap<>();        }
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
                })
                .exceptionally(ex -> {
                    logger.error("Error executing console command: " + command, ex);
                    future.complete(false);
                    return null;
                });
        } catch (Exception e) {
            logger.error("Exception executing console command: " + command, e);
            future.complete(false);
        }
        
        return future;
    }
    
    /**
     * Initializes the database connection and creates required tables
     * 
     * @throws SQLException If database initialization fails
     * @throws ClassNotFoundException If database driver is not found
     */
    private void initializeDatabase() throws SQLException, ClassNotFoundException {
        if (config == null) {
            throw new IllegalStateException("Configuration must be loaded before initializing database");
        }
        
        sqlHandler = new SQLHandler(config, logger, debugEnabled);
        sqlHandler.createTable();
        
        debugLog("Database initialized successfully");
    }
}
