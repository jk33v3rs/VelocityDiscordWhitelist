package top.jk33v3rs.velocitydiscordwhitelist.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * YamlConfigLoader manages YAML configuration files for the plugin.
 */
public class YamlConfigLoader {
    
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<String, Map<String, Object>> loadedConfigs = new LinkedHashMap<>();
    private final Yaml yaml;
    
    /**
     * Constructor for YamlConfigLoader
     */
    public YamlConfigLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.yaml = new Yaml(options);
    }
      /**
     * Loads or creates a YAML configuration file
     */
    public Map<String, Object> loadConfig(String fileName) {
        String fullFileName = fileName + ".yaml";
        Path configPath = dataDirectory.resolve(fullFileName);
        
        // Create data directory if it doesn't exist
        try {
            Files.createDirectories(dataDirectory);        } catch (IOException e) {
            logger.error("Failed to create data directory: " + dataDirectory, e);
            return new LinkedHashMap<>();
        }
        
        // Create default config if it doesn't exist
        if (!Files.exists(configPath)) {
            try {
                createDefaultConfig(configPath);
            } catch (IOException e) {
                logger.error("Failed to create default config file: " + fullFileName, e);
                return new LinkedHashMap<>();
            }
        }
        
        // Load the config
        try (InputStream input = Files.newInputStream(configPath)) {
            Map<String, Object> config = yaml.load(input);
            loadedConfigs.put(fileName, config);
            return config;
        } catch (IOException e) {
            logger.error("Failed to load config file: " + fullFileName, e);
            return new LinkedHashMap<>();
        }
    }
    
    /**
     * Creates the default configuration file
     * 
     * @param configPath The path to create the config file at
     * @throws IOException If file creation fails
     */
    private void createDefaultConfig(Path configPath) throws IOException {
        Map<String, Object> defaultConfig = new LinkedHashMap<>();
        
        // Database configuration
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("url", "jdbc:mysql://localhost:3306/discord_whitelist");
        database.put("username", "root");
        database.put("password", "password");
        
        Map<String, Object> connectionPool = new LinkedHashMap<>();
        connectionPool.put("maximum_pool_size", 10);
        connectionPool.put("minimum_idle", 5);
        connectionPool.put("connection_timeout", 30000);
        connectionPool.put("idle_timeout", 600000);
        connectionPool.put("max_lifetime", 1800000);
        database.put("connection_pool", connectionPool);
        
        defaultConfig.put("database", database);
        
        // Discord configuration
        Map<String, Object> discord = new LinkedHashMap<>();
        discord.put("token", "YOUR_BOT_TOKEN_HERE");
        discord.put("guild_id", "123456789012345678");
        discord.put("verification_channel", "123456789012345678");
          Map<String, Object> roles = new LinkedHashMap<>();
        roles.put("admin", "987654321098765432");
        roles.put("verified", "765432109876543210");
        discord.put("roles", roles);
        
        defaultConfig.put("discord", discord);
        
        // Plugin Settings
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("debug", false);
        settings.put("use_purgatory", true);
        settings.put("purgatory_server", "lobby");
        settings.put("allow_reconnect", true);
        settings.put("max_verification_attempts", 3);
        defaultConfig.put("settings", settings);
          // Session Configuration
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("timeout", 15);
        defaultConfig.put("session", session);
        
        // Purgatory Configuration
        Map<String, Object> purgatory = new LinkedHashMap<>();
        purgatory.put("session_timeout_minutes", 30);
        defaultConfig.put("purgatory", purgatory);
          // Geyser Integration
        Map<String, Object> geyser = new LinkedHashMap<>();
        geyser.put("enabled", false);
        geyser.put("handle_bedrock_players", true);
        geyser.put("auto_verify_bedrock", false);
        geyser.put("prefix", ".");
        defaultConfig.put("geyser", geyser);
        
        // Vault Integration
        Map<String, Object> vault = new LinkedHashMap<>();
        Map<String, Object> economy = new LinkedHashMap<>();
        economy.put("enabled", false);
        economy.put("whitelist_reward", 100.0);
        economy.put("rank_progression_reward", 50.0);
        vault.put("economy", economy);
        
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("enabled", false);
        vault.put("permissions", permissions);
        
        vault.put("reward_server", "survival");
        defaultConfig.put("vault", vault);
          // LuckPerms Integration
        Map<String, Object> luckperms = new LinkedHashMap<>();
        luckperms.put("enabled", false);
        luckperms.put("default_group", "verified");
        luckperms.put("managed_groups", java.util.Arrays.asList(
            "bystander", "onlooker", "wanderer", "traveller", "explorer", "verified"
        ));
        
        Map<String, Object> discordRoleMappings = new LinkedHashMap<>();
        discordRoleMappings.put("discord-role-id-1", "luckperms-group-1");
        discordRoleMappings.put("discord-role-id-2", "luckperms-group-2");
        luckperms.put("discord_role_mappings", discordRoleMappings);
        
        Map<String, Object> rankMappings = new LinkedHashMap<>();
        rankMappings.put("1.0", "bystander");
        rankMappings.put("1.1", "onlooker");
        rankMappings.put("2.0", "wanderer");
        rankMappings.put("2.1", "traveller");
        rankMappings.put("3.0", "explorer");
        rankMappings.put("3.1", "verified");
        luckperms.put("rank_mappings", rankMappings);
        
        Map<String, Object> discordMappings = new LinkedHashMap<>();
        discordMappings.put("123456789012345678", "special_group");
        luckperms.put("discord_mappings", discordMappings);
        
        defaultConfig.put("luckperms", luckperms);
        
        // Rewards Configuration
        Map<String, Object> rewards = new LinkedHashMap<>();
        rewards.put("enabled", true);
        defaultConfig.put("rewards", rewards);
        
        // Ranks Configuration  
        Map<String, Object> ranks = new LinkedHashMap<>();
        ranks.put("enabled", true);
        defaultConfig.put("ranks", ranks);
          // XP System Configuration
        Map<String, Object> xp = new LinkedHashMap<>();
        xp.put("enabled", true);
        
        Map<String, Object> rateLimiting = new LinkedHashMap<>();
        rateLimiting.put("enabled", true);
        rateLimiting.put("maxEventsPerMinute", 10);
        rateLimiting.put("maxEventsPerHour", 100);
        rateLimiting.put("maxEventsPerDay", 500);
        rateLimiting.put("cooldownSeconds", 5);
        xp.put("rate_limiting", rateLimiting);
        
        Map<String, Object> modifiers = new LinkedHashMap<>();
        modifiers.put("advancement", 1.0);
        modifiers.put("playtime", 0.5);
        modifiers.put("kill", 0.8);
        modifiers.put("break_block", 0.3);
        modifiers.put("place_block", 0.2);
        modifiers.put("craft_item", 0.4);
        modifiers.put("enchant_item", 1.2);
        modifiers.put("trade", 0.6);
        modifiers.put("fishing", 0.4);
        modifiers.put("mining", 0.3);
        xp.put("modifiers", modifiers);
          Map<String, Object> blazeAndCaves = new LinkedHashMap<>();
        blazeAndCaves.put("enabled", true);
        blazeAndCaves.put("easy_multiplier", 1.0);
        blazeAndCaves.put("medium_multiplier", 1.25);
        blazeAndCaves.put("hard_multiplier", 1.5);
        blazeAndCaves.put("insane_multiplier", 2.0);
        blazeAndCaves.put("terralith_bonus", 0.1);
        blazeAndCaves.put("hardcore_bonus", 0.5);
        
        xp.put("blaze_and_caves", blazeAndCaves);
        defaultConfig.put("xp", xp);
        
        // Message configuration
        Map<String, Object> messages = new LinkedHashMap<>();
        messages.put("verification_code", "Your verification code is: %code%");
        messages.put("verification_success", "Verification successful! Welcome to the server!");
        messages.put("verification_failed", "Verification failed. Please try again.");
        messages.put("session_expired", "Your verification session has expired. Please request a new code.");
        messages.put("already_verified", "You are already verified on this server.");
        messages.put("purgatory_message", "You are in purgatory mode. Please verify your Discord account to continue.");
        messages.put("invalid_code", "Invalid verification code. Please check and try again.");
        messages.put("max_attempts_exceeded", "Maximum verification attempts exceeded. Please request a new code.");
        messages.put("discord_link_success", "Your Discord account has been successfully linked!");
        messages.put("discord_already_linked", "Your Discord account is already linked to another player.");
        messages.put("whitelist_added", "You have been added to the whitelist!");
        messages.put("whitelist_removed", "You have been removed from the whitelist.");
        messages.put("not_whitelisted", "You are not whitelisted on this server.");
        messages.put("database_error", "A database error occurred. Please try again later.");
        messages.put("permission_denied", "You do not have permission to use this command.");
        messages.put("player_not_found", "Player not found.");
        messages.put("discord_user_not_found", "Discord user not found.");
        messages.put("command_usage", "Usage: %usage%");
        messages.put("reload_success", "Configuration reloaded successfully!");
        messages.put("reload_failed", "Failed to reload configuration. Check console for errors.");
        defaultConfig.put("messages", messages);
        
        // Save the default config
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            yaml.dump(defaultConfig, writer);
        }
    }
      /**
     * Gets a configuration value from the loaded config
     * 
     * @param fileName The name of the config file (without .yaml extension)
     * @param path The path to the value, using dot notation (e.g., "database.host")
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The configuration value, or the default value if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String fileName, String path, T defaultValue) {
        Map<String, Object> config = loadedConfigs.get(fileName);
        if (config == null) {
            return defaultValue;
        }
        
        String[] parts = path.split("\\.");
        Object current = config;
        
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return defaultValue;
            }
            current = ((Map<?, ?>) current).get(part);
            if (current == null) {
                return defaultValue;
            }
        }
        
        try {
            return (T) current;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
      /**
     * Gets the database configuration section
     * 
     * @param fileName The name of the config file (without .yaml extension)
     * @return A map containing the database configuration
     */
    /**
     * getDatabaseConfig
     * 
     * Retrieves the database configuration section from the loaded config.
     * 
     * @param fileName The name of the config file (without .yaml extension)
     * @return A map containing the database configuration, or an empty map if not found
     */
    public Map<String, Object> getDatabaseConfig(String fileName) {
        return get(fileName, "database", new LinkedHashMap<>());
    }
}
