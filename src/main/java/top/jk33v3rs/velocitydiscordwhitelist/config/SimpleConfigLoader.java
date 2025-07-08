package top.jk33v3rs.velocitydiscordwhitelist.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * SimpleConfigLoader
 * 
 * A simplified configuration loader inspired by Spicord's design patterns.
 * This class manages only essential configuration with clean defaults and validation.
 * 
 * Design Principles:
 * - Single config.yml file only
 * - Minimal required settings
 * - Clear validation at startup
 * - Auto-generation of messages.yml if missing
 * - No JSON config files
 * - Programmatic rank handling
 */
public class SimpleConfigLoader {
    
    private final Logger logger;
    private final Path dataDirectory;
    private final Yaml yaml;
    private Map<String, Object> config;
    private Map<String, Object> messages;
    
    /**
     * Constructor for SimpleConfigLoader
     * 
     * @param logger The logger instance for logging configuration events
     * @param dataDirectory The plugin data directory path
     */
    public SimpleConfigLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        
        // Configure YAML with clean formatting
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        this.yaml = new Yaml(options);
    }
    
    /**
     * loadConfiguration
     * 
     * Loads the main configuration file, creating defaults if it doesn't exist.
     * This method handles config.yml creation and validation.
     * 
     * @return Map containing the loaded configuration
     * @throws RuntimeException if configuration cannot be loaded or created
     */
    public Map<String, Object> loadConfiguration() {
        try {
            // Ensure data directory exists
            Files.createDirectories(dataDirectory);
            
            Path configPath = dataDirectory.resolve("config.yml");
            
            // Create default config if it doesn't exist
            if (!Files.exists(configPath)) {
                createDefaultConfig(configPath);
                logger.info("Created default config.yml - please configure your settings");
            }
            
            // Load the configuration
            try (InputStream input = Files.newInputStream(configPath)) {
                config = yaml.load(input);
                if (config == null) {
                    config = new HashMap<>();
                }
            }
            
            // Validate configuration
            if (!validateConfiguration()) {
                throw new RuntimeException("Configuration validation failed");
            }
            
            // Load or create messages
            loadMessages();
            
            logger.info("Configuration loaded successfully");
            return config;
            
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
            throw new RuntimeException("Configuration loading failed", e);
        }
    }
    
    /**
     * createDefaultConfig
     * 
     * Creates the default config.yml file with minimal essential settings.
     * Inspired by Spicord's simple configuration approach.
     * 
     * @param configPath The path where the config file should be created
     * @throws IOException if file creation fails
     */
    private void createDefaultConfig(Path configPath) throws IOException {
        Map<String, Object> defaultConfig = new HashMap<>();
        
        // Database Configuration - Required
        Map<String, Object> database = new HashMap<>();
        database.put("host", "localhost");
        database.put("port", 3306);
        database.put("database", "discord_whitelist");
        database.put("username", "root");
        database.put("password", "password");
        defaultConfig.put("database", database);
        
        // Discord Bot Configuration - Required
        Map<String, Object> discord = new HashMap<>();
        discord.put("enabled", true);
        discord.put("token", "YOUR_BOT_TOKEN_HERE");
        discord.put("guild_id", "123456789012345678");
        discord.put("verification_channel", "123456789012345678");
        
        Map<String, Object> roles = new HashMap<>();
        roles.put("admin", "987654321098765432");
        roles.put("verified", "765432109876543210");
        discord.put("roles", roles);
        defaultConfig.put("discord", discord);
        
        // Core Settings
        Map<String, Object> settings = new HashMap<>();
        settings.put("debug", false);
        settings.put("session_timeout_minutes", 30);
        settings.put("max_verification_attempts", 3);
        settings.put("purgatory_server", "lobby");
        defaultConfig.put("settings", settings);
        
        // Geyser Support (Bedrock Edition)
        Map<String, Object> geyser = new HashMap<>();
        geyser.put("enabled", false);
        geyser.put("prefix", ".");
        defaultConfig.put("geyser", geyser);
        
        // LuckPerms Integration
        Map<String, Object> luckperms = new HashMap<>();
        luckperms.put("enabled", false);
        luckperms.put("default_group", "verified");
        defaultConfig.put("luckperms", luckperms);
        
        // Save the configuration
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            yaml.dump(defaultConfig, writer);
        }
    }
    
    /**
     * loadMessages
     * 
     * Loads or creates the messages.yml file with default messages.
     * This file is only created if it doesn't exist on the server.
     * 
     * @throws IOException if messages file cannot be loaded or created
     */
    private void loadMessages() throws IOException {
        Path messagesPath = dataDirectory.resolve("messages.yml");
        
        if (!Files.exists(messagesPath)) {
            createDefaultMessages(messagesPath);
            logger.info("Created default messages.yml");
        }
        
        // Load messages
        try (InputStream input = Files.newInputStream(messagesPath)) {
            messages = yaml.load(input);
            if (messages == null) {
                messages = new HashMap<>();
            }
        }
    }
    
    /**
     * createDefaultMessages
     * 
     * Creates the default messages.yml file with all required messages.
     * 
     * @param messagesPath The path where the messages file should be created
     * @throws IOException if file creation fails
     */
    private void createDefaultMessages(Path messagesPath) throws IOException {
        Map<String, Object> defaultMessages = new HashMap<>();
        
        // Basic Messages
        defaultMessages.put("not_whitelisted", "You are not whitelisted on this server. Please check Discord for whitelist instructions.");
        defaultMessages.put("verification_code", "Your verification code is: %code%");
        defaultMessages.put("verification_success", "Verification successful! Welcome to the server!");
        defaultMessages.put("verification_failed", "Verification failed. Please try again.");
        defaultMessages.put("session_expired", "Your verification session has expired. Please request a new code.");
        defaultMessages.put("invalid_code", "Invalid verification code. Please check and try again.");
        defaultMessages.put("max_attempts_exceeded", "Maximum verification attempts exceeded. Please request a new code.");
        defaultMessages.put("already_verified", "You are already verified on this server.");
        defaultMessages.put("discord_link_success", "Your Discord account has been successfully linked!");
        
        // Command Messages
        defaultMessages.put("no_permission", "You don't have permission to use this command!");
        defaultMessages.put("player_not_found", "Player not found!");
        defaultMessages.put("command_usage", "Usage: %usage%");
        defaultMessages.put("reload_success", "Configuration reloaded successfully!");
        defaultMessages.put("reload_failed", "Failed to reload configuration. Check console for errors.");
        
        // Error Messages
        defaultMessages.put("database_error", "A database error occurred. Please try again later.");
        defaultMessages.put("discord_error", "Discord integration error. Please try again later.");
        
        // Save the messages
        try (Writer writer = Files.newBufferedWriter(messagesPath, StandardCharsets.UTF_8)) {
            yaml.dump(defaultMessages, writer);
        }
    }
    
    /**
     * validateConfiguration
     * 
     * Validates the loaded configuration for required fields and correct types.
     * Similar to Spicord's validation approach.
     * 
     * @return true if configuration is valid, false otherwise
     */
    private boolean validateConfiguration() {
        if (config == null || config.isEmpty()) {
            logger.error("Configuration is null or empty");
            return false;
        }
        
        // Validate database section
        @SuppressWarnings("unchecked")
        Map<String, Object> dbConfig = (Map<String, Object>) config.get("database");
        if (dbConfig == null) {
            logger.error("Database configuration section is missing");
            return false;
        }
        
        String[] requiredDbFields = {"host", "port", "database", "username", "password"};
        for (String field : requiredDbFields) {
            if (!dbConfig.containsKey(field)) {
                logger.error("Required database field missing: {}", field);
                return false;
            }
        }
        
        // Validate Discord section
        @SuppressWarnings("unchecked")
        Map<String, Object> discordConfig = (Map<String, Object>) config.get("discord");
        if (discordConfig == null) {
            logger.error("Discord configuration section is missing");
            return false;
        }
        
        boolean discordEnabled = Boolean.parseBoolean(discordConfig.getOrDefault("enabled", "false").toString());
        if (discordEnabled) {
            String[] requiredDiscordFields = {"token", "guild_id", "verification_channel"};
            for (String field : requiredDiscordFields) {
                Object value = discordConfig.get(field);
                if (value == null || value.toString().trim().isEmpty() || value.toString().contains("YOUR_")) {
                    logger.error("Discord is enabled but required field is missing or not configured: {}", field);
                    return false;
                }
            }
        }
        
        logger.info("Configuration validation passed");
        return true;
    }
    
    /**
     * get
     * 
     * Gets a configuration value using dot notation path.
     * 
     * @param path The configuration path (e.g., "database.host")
     * @param defaultValue The default value to return if path doesn't exist
     * @return The configuration value or default value
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String path, T defaultValue) {
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
            logger.warn("Type mismatch for config path: {} - returning default", path);
            return defaultValue;
        }
    }
    
    /**
     * getMessage
     * 
     * Gets a message from the messages configuration.
     * 
     * @param key The message key
     * @param defaultMessage The default message if key doesn't exist
     * @return The message text
     */
    public String getMessage(String key, String defaultMessage) {
        if (messages == null) {
            return defaultMessage;
        }
        
        Object message = messages.get(key);
        return message != null ? message.toString() : defaultMessage;
    }
    
    /**
     * isDiscordEnabled
     * 
     * Checks if Discord integration is enabled and properly configured.
     * 
     * @return true if Discord integration is enabled and configured
     */
    public boolean isDiscordEnabled() {
        return get("discord.enabled", false) && 
               !get("discord.token", "").toString().contains("YOUR_") &&
               !get("discord.guild_id", "").toString().contains("123456");
    }
    
    /**
     * getConfiguration
     * 
     * Returns the loaded configuration map.
     * 
     * @return The configuration map
     */
    public Map<String, Object> getConfiguration() {
        return config;
    }
    
    /**
     * reload
     * 
     * Reloads the configuration from disk.
     * 
     * @return true if reload was successful, false otherwise
     */
    public boolean reload() {
        try {
            loadConfiguration();
            logger.info("Configuration reloaded successfully");
            return true;
        } catch (Exception e) {
            logger.error("Failed to reload configuration", e);
            return false;
        }
    }
}
