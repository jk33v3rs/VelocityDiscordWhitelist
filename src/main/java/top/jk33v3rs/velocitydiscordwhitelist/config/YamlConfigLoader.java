package top.jk33v3rs.velocitydiscordwhitelist.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

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
        String fullFileName = fileName + ".yml";
        Path configPath = dataDirectory.resolve(fullFileName);
        
        // Create data directory if it doesn't exist
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            logger.error("Failed to create data directory", e);
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
        database.put("host", "localhost");
        database.put("port", 3306);
        database.put("database", "whitelist");
        database.put("username", "root");
        database.put("password", "password");
        defaultConfig.put("database", database);
        
        // Discord configuration
        Map<String, Object> discord = new LinkedHashMap<>();
        discord.put("token", "your-bot-token-here");
        discord.put("guild-id", "your-guild-id-here");
        discord.put("channel-id", "your-channel-id-here");
        defaultConfig.put("discord", discord);
        
        // Message configuration
        Map<String, Object> messages = new LinkedHashMap<>();
        messages.put("prefix", "&8[&bWhitelist&8]&r ");
        messages.put("not-whitelisted", "&cYou are not whitelisted on this server!");
        messages.put("no-permission", "&cYou don't have permission to use this command!");
        defaultConfig.put("messages", messages);
        
        // Save the default config
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            yaml.dump(defaultConfig, writer);
        }
    }
    
    /**
     * Gets a configuration value from the loaded config
     * 
     * @param fileName The name of the config file (without .yml extension)
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
     * @param fileName The name of the config file (without .yml extension)
     * @return A map containing the database configuration
     */
    /**
     * getDatabaseConfig
     * 
     * Retrieves the database configuration section from the loaded config.
     * 
     * @param fileName The name of the config file (without .yml extension)
     * @return A map containing the database configuration, or an empty map if not found
     */
    public Map<String, Object> getDatabaseConfig(String fileName) {
        return get(fileName, "database", new LinkedHashMap<>());
    }
}
