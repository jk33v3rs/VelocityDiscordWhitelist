// VelocityDiscordWhitelist - MIT License

// VelocityDiscordWhitelist v1.0.2
// Author: jk33v3rs based on the plugin VelocityWhitelist by Rathinosk
// Portions of code used are used under MIT licence
// DISCLAIMER: AI tools were used in the IDE used to create this plugin, which included direct (but supervised) access to code
// Please report any issues via GitHub. Pull requests are accepted.

package top.jk33v3rs.velocitydiscordwhitelist.modules;

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
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.bstats.velocity.Metrics;
import top.jk33v3rs.velocitydiscordwhitelist.commands.BrigadierCommandHandler;
import top.jk33v3rs.velocitydiscordwhitelist.config.JsonConfigLoader;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.integrations.VaultIntegration;
import top.jk33v3rs.velocitydiscordwhitelist.integrations.LuckPermsIntegration;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import top.jk33v3rs.velocitydiscordwhitelist.config.YamlConfigLoader;
import top.jk33v3rs.velocitydiscordwhitelist.utils.LoggingUtils;

/** Main VelocityDiscordWhitelist plugin class. */
@Plugin(
        id = "velocitydiscordwhitelist",
        name = "VelocityDiscordWhitelist",
        version = "1.0.2",
        url = "https://github.com/jk33v3rs/VelocityDiscordWhitelist",
        description = "A whitelist plugin for Velocity that integrates with Discord",
        authors = {"jk33v3rs"}
)
public class VelocityDiscordWhitelist {
    private final ProxyServer server;
    private final org.slf4j.Logger logger;
    private final Path dataDirectory;
    private final Path configFile;
    private final Metrics.Factory metricsFactory;      // Components
    private SQLHandler sqlHandler;
    private YamlConfigLoader configLoader;
    private Map<String, Object> config;
    private JsonConfigLoader jsonConfigLoader;
    private EnhancedPurgatoryManager purgatoryManager;
    private DiscordBotHandler discordBotHandler;
    private RewardsHandler rewardsHandler;
    private XPManager xpManager;
    private BrigadierCommandHandler commandHandler;
    private VaultIntegration vaultIntegration;
    private LuckPermsIntegration luckPermsIntegration;
    private ScheduledTask purgatoryCleanupTask;
    
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
     */
    @Subscribe
    @SuppressWarnings("unchecked")
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            // Initialize metrics
            int pluginId = 12345; // Replace with your bStats plugin ID
            metricsFactory.make(this, pluginId);
            
            // Initialize configuration
            config = loadConfig();
            Map<String, Object> generalConfig = (Map<String, Object>) config.get("general");
            if (generalConfig != null) {
                debugEnabled = Boolean.parseBoolean(generalConfig.getOrDefault("debug", "false").toString());
                Map<String, Object> sessionConfig = (Map<String, Object>) generalConfig.get("session");
                if (sessionConfig != null) {
                    Map<String, Object> timeoutConfig = (Map<String, Object>) sessionConfig.get("timeout");
                    if (timeoutConfig != null) {
                        sessionTimeoutMinutes = Integer.parseInt(timeoutConfig.getOrDefault("minutes", "30").toString());
                    }
                }
            }
            
            // Initialize JSON configurations
            jsonConfigLoader = new JsonConfigLoader(logger, dataDirectory);
            ranksConfig = jsonConfigLoader.loadConfig("ranks", "{}");
            rewardsConfig = jsonConfigLoader.loadConfig("rewards", "{}");
            
            // Initialize database connection
            sqlHandler = new SQLHandler(config, logger, debugEnabled);
            
            // Initialize Discord bot
            discordBotHandler = new DiscordBotHandler(logger, sqlHandler);
            Map<String, Object> discordConfig = (Map<String, Object>) config.get("discord");
            if (discordConfig != null && Boolean.parseBoolean(discordConfig.getOrDefault("enabled", "false").toString())) {
                // Convert config map to Properties for DiscordBotHandler
                Properties configProperties = new Properties();
                for (Map.Entry<String, Object> entry : config.entrySet()) {
                    if (entry.getValue() != null) {
                        configProperties.put(entry.getKey(), entry.getValue().toString());
                    }
                }
                discordBotHandler.initialize(configProperties)
                    .thenAccept(success -> {
                        if (!success) {
                            logger.error("Failed to initialize Discord bot");
                            return;
                        }                        // Initialize remaining components after Discord bot is ready
                        initializeIntegrations();
                        rewardsHandler = new RewardsHandler(sqlHandler, discordBotHandler, logger, debugEnabled.booleanValue(), config, vaultIntegration, luckPermsIntegration);
                        purgatoryManager = new EnhancedPurgatoryManager(logger, debugEnabled.booleanValue(), sqlHandler, sessionTimeoutMinutes);
                        
                        // Initialize XP manager
                        xpManager = new XPManager(sqlHandler, logger, debugEnabled.booleanValue(), config);
                        
                        // Initialize command handler with all required dependencies
                        commandHandler = new BrigadierCommandHandler(server, logger, purgatoryManager, rewardsHandler, xpManager, sqlHandler, debugEnabled.booleanValue());
                        commandHandler.registerCommands();
                        
                        // Start purgatory cleanup task
                        startPurgatoryCleanupTask();
                        
                        logger.info("Successfully initialized VelocityDiscordWhitelist");
                        pluginEnabled.set(true);
                    })
                    .exceptionally(ex -> {
                        logger.error("Error initializing Discord bot", ex);
                        return null;
                    });            } else {
                logger.info("Discord bot is disabled in configuration");
                initializeIntegrations();
                rewardsHandler = new RewardsHandler(sqlHandler, null, logger, debugEnabled.booleanValue(), config, vaultIntegration, luckPermsIntegration);
                purgatoryManager = new EnhancedPurgatoryManager(logger, debugEnabled.booleanValue(), sqlHandler, sessionTimeoutMinutes);
                
                // Initialize XP manager
                xpManager = new XPManager(sqlHandler, logger, debugEnabled.booleanValue(), config);
                
                // Initialize command handler with all required dependencies
                commandHandler = new BrigadierCommandHandler(server, logger, purgatoryManager, rewardsHandler, xpManager, sqlHandler, debugEnabled.booleanValue());
                commandHandler.registerCommands();
                pluginEnabled.set(true);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize plugin", e);
            pluginEnabled.set(false);
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
                ));
            }
        }
    }
      private void startPurgatoryCleanupTask() {
        // Schedule a task to periodically clean up expired sessions
        purgatoryCleanupTask = server.getScheduler()
            .buildTask(this, () -> {
                // The actual cleanup happens in the EnhancedPurgatoryManager
                debugLog("Running purgatory cleanup task");
            })
            .repeat(5, TimeUnit.MINUTES)
            .schedule();
    }
      public boolean reloadConfig() {
        try {
            config = loadConfig();
            
            // Refresh debug mode
            if (config.containsKey("debug")) {
                debugEnabled = Boolean.parseBoolean(config.get("debug").toString());
                logger.info("Debug mode is now {}", debugEnabled ? "enabled" : "disabled");
            }
            
            // Refresh session timeout
            if (config.containsKey("session.timeout.minutes")) {
                sessionTimeoutMinutes = Integer.parseInt(config.get("session.timeout.minutes").toString());
                logger.info("Verification session timeout set to {} minutes", sessionTimeoutMinutes);
            }
            
            // Reload JSON configurations
            ranksConfig = jsonConfigLoader.loadConfig("ranks", JsonConfigLoader.getDefaultRanksConfig());
            rewardsConfig = jsonConfigLoader.loadConfig("rewards", JsonConfigLoader.getDefaultRewardsConfig());
            
            // Refresh rewards handler data
            rewardsHandler.loadRankDefinitions();
            
            return true;
        } catch (Exception e) {
            logger.error("Error reloading configuration", e);
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
    }    private Map<String, Object> loadConfig() {
        try {
            Path yamlConfigFile = dataDirectory.resolve("config.yaml");
            if (Files.exists(yamlConfigFile)) {
                configLoader = new YamlConfigLoader(logger, dataDirectory);
                return configLoader.loadConfig("config");
            } else {
                saveDefaultConfig();
                configLoader = new YamlConfigLoader(logger, dataDirectory);
                return configLoader.loadConfig("config");
            }
        } catch (Exception e) {
            logger.error("Error loading configuration", e);
            return new HashMap<>();
        }    }    @SuppressWarnings("unchecked")
    private void initializeIntegrations() {
        // Initialize LuckPerms integration
        luckPermsIntegration = new LuckPermsIntegration(logger, debugEnabled, config);
        if (luckPermsIntegration.isAvailable()) {
            logger.info("LuckPerms integration enabled");
        } else {
            debugLog("LuckPerms integration not available or disabled");
        }
        
        // Initialize Vault integration
        vaultIntegration = new VaultIntegration(server, logger, debugEnabled, config);
        Map<String, Object> vaultConfig = (Map<String, Object>) config.getOrDefault("vault", Map.of());
        boolean vaultEnabled = Boolean.parseBoolean(vaultConfig.getOrDefault("enabled", "false").toString());
        
        if (vaultEnabled) {
            if (vaultIntegration.isEconomyAvailable() || vaultIntegration.isPermissionsAvailable()) {
                logger.info("Vault integration enabled - Economy: {}, Permissions: {}", 
                    vaultIntegration.isEconomyAvailable(), vaultIntegration.isPermissionsAvailable());
            } else {
                debugLog("Vault integration configured but target server not available");
            }
        } else {
            debugLog("Vault integration disabled in configuration");
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
}
