package top.jk33v3rs.velocitydiscordwhitelist.config;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * JsonConfigLoader manages JSON configuration files for the plugin.
 */
public class JsonConfigLoader {
    
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<String, JsonObject> loadedConfigs = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Constructor for JsonConfigLoader
     */
    public JsonConfigLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }
    
    /**
     * Loads or creates a JSON configuration file
     */
    public JsonObject loadConfig(String fileName, String defaultConfig) {
        String fullFileName = fileName + ".json";
        Path configPath = dataDirectory.resolve(fullFileName);
        
        // Check if config already loaded
        if (loadedConfigs.containsKey(fullFileName)) {
            return loadedConfigs.get(fullFileName);
        }
        
        try {
            // Create directory if it doesn't exist
            Files.createDirectories(dataDirectory);
            
            // Check if file exists, create with defaults if not
            if (!Files.exists(configPath)) {
                try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                    writer.write(defaultConfig);
                }
                logger.info("Created default config file: " + fullFileName);
            }
            
            // Read the file
            String content = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
            JsonObject config = JsonParser.parseString(content).getAsJsonObject();
            
            // Store in cache
            loadedConfigs.put(fullFileName, config);
            
            return config;        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            logger.error("Failed to load JSON config file: " + fullFileName + " - " + e.getClass().getSimpleName(), e);
            
            // Try to parse default config as fallback
            try {
                JsonObject fallback = JsonParser.parseString(defaultConfig).getAsJsonObject();
                loadedConfigs.put(fullFileName, fallback);
                return fallback;            } catch (IllegalStateException | JsonSyntaxException ex) {
                logger.error("Failed to parse default JSON config for fallback: " + fullFileName + " - " + ex.getClass().getSimpleName(), ex);
                throw new RuntimeException("Failed to load or create config file: " + fullFileName, e);
            }
        }
    }
    
    /**
     * Saves a JSON configuration to disk
     * 
     * @param fileName The name of the file (without .json extension)
     * @param config The JSON object to save
     * @return true if save was successful, false otherwise
     */
    public boolean saveConfig(String fileName, JsonObject config) {
        String fullFileName = fileName + ".json";
        Path configPath = dataDirectory.resolve(fullFileName);
        
        try {
            // Create directory if it doesn't exist
            Files.createDirectories(dataDirectory);
            
            // Write config to file
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }
            
            // Update cache
            loadedConfigs.put(fullFileName, config);
            
            return true;
        } catch (IOException e) {
            logger.error("Failed to save config file: " + fullFileName, e);
            return false;
        }
    }
    
    /**
     * Gets the default configuration as JSON string
     * 
     * @return The default configuration JSON
     */
    public static String getDefaultConfig() {
        return """
            {
              "database": {
                "host": "localhost",
                "port": 3306,
                "database": "whitelist",
                "username": "root",
                "password": "",
                "useSSL": false,
                "connectionTimeout": 30000
              },
              "discord": {
                "token": "",
                "guildId": "",
                "approvedChannelIds": "",
                "init_timeout": 30,
                "roles": {
                  "verified": "",
                  "unverified": "",
                  "purgatory": ""
                }
              },
              "whitelist": {
                "enablePurgatory": true,
                "purgatoryTimeout": 300,
                "enableRanks": true,
                "enableRewards": true
              },
              "security": {
                "enableLogging": true,
                "enableAudit": true,
                "maxConnectionAttempts": 3
              }
            }""";
    }
    
    /**
     * Gets the default ranks configuration
     * 
     * @return The default ranks configuration JSON
     */
    public static String getDefaultRanksConfig() {
        return """
            {
              "main_ranks": [
                {
                  "id": 1,
                  "name": "bystander",
                  "description": "First rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 0,
                  "required_achievements": 0
                },
                {
                  "id": 2,
                  "name": "onlooker",
                  "description": "Second rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 60,
                  "required_achievements": 1
                },
                {
                  "id": 3,
                  "name": "wanderer",
                  "description": "Third rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 120,
                  "required_achievements": 2
                },
                {
                  "id": 4,
                  "name": "traveller",
                  "description": "Fourth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 240,
                  "required_achievements": 3
                },
                {
                  "id": 5,
                  "name": "explorer",
                  "description": "Fifth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 480,
                  "required_achievements": 5
                },
                {
                  "id": 6,
                  "name": "adventurer",
                  "description": "Sixth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 720,
                  "required_achievements": 8
                },
                {
                  "id": 7,
                  "name": "surveyor",
                  "description": "Seventh rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 1080,
                  "required_achievements": 12
                },
                {
                  "id": 8,
                  "name": "navigator",
                  "description": "Eighth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 1440,
                  "required_achievements": 16
                },
                {
                  "id": 9,
                  "name": "journeyman",
                  "description": "Ninth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 2160,
                  "required_achievements": 20
                },
                {
                  "id": 10,
                  "name": "pathfinder",
                  "description": "Tenth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 2880,
                  "required_achievements": 25
                },
                {
                  "id": 11,
                  "name": "trailblazer",
                  "description": "Eleventh rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 3600,
                  "required_achievements": 30
                },
                {
                  "id": 12,
                  "name": "pioneer",
                  "description": "Twelfth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 4320,
                  "required_achievements": 35
                },
                {
                  "id": 13,
                  "name": "craftsman",
                  "description": "Thirteenth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 5040,
                  "required_achievements": 40
                },
                {
                  "id": 14,
                  "name": "specialist",
                  "description": "Fourteenth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 5760,
                  "required_achievements": 45
                },
                {
                  "id": 15,
                  "name": "artisan",
                  "description": "Fifteenth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 6480,
                  "required_achievements": 50
                },
                {
                  "id": 16,
                  "name": "veteran",
                  "description": "Sixteenth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 7200,
                  "required_achievements": 55
                },
                {
                  "id": 17,
                  "name": "sage",
                  "description": "Seventeenth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 7920,
                  "required_achievements": 60
                },
                {
                  "id": 18,
                  "name": "luminary",
                  "description": "Eighteenth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 8640,
                  "required_achievements": 65
                },
                {
                  "id": 19,
                  "name": "titan",
                  "description": "Nineteenth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 9360,
                  "required_achievements": 70
                },
                {
                  "id": 20,
                  "name": "legend",
                  "description": "Twentieth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 10080,
                  "required_achievements": 75
                },
                {
                  "id": 21,
                  "name": "eternal",
                  "description": "Twenty-first rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 10800,
                  "required_achievements": 80
                },
                {
                  "id": 22,
                  "name": "ascendant",
                  "description": "Twenty-second rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 11520,
                  "required_achievements": 85
                },
                {
                  "id": 23,
                  "name": "celestial",
                  "description": "Twenty-third rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 12240,
                  "required_achievements": 90
                },
                {
                  "id": 24,
                  "name": "divine",
                  "description": "Twenty-fourth rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 12960,
                  "required_achievements": 95
                },
                {
                  "id": 25,
                  "name": "deity",
                  "description": "Final rank in the official progression system",
                  "discord_role_id": 0,
                  "required_time": 14400,
                  "required_achievements": 100,
                  "is_special": true
                },
                {
                  "id": 28,
                  "name": "booster",
                  "description": "Custom supporter rank - Discord Nitro Booster",
                  "discord_role_id": 0,
                  "is_special": true
                },
                {
                  "id": 35,
                  "name": "mod",
                  "description": "Elevated moderator rank",
                  "discord_role_id": 0,
                  "is_special": true
                }
              ],
              "sub_ranks": [
                {
                  "id": 1,
                  "name": "novice",
                  "description": "First sub-rank in the official progression system"
                },
                {
                  "id": 2,
                  "name": "apprentice",
                  "description": "Second sub-rank in the official progression system"
                },
                {
                  "id": 3,
                  "name": "adept",
                  "description": "Third sub-rank in the official progression system"
                },
                {
                  "id": 4,
                  "name": "master",
                  "description": "Fourth sub-rank in the official progression system"
                },
                {
                  "id": 5,
                  "name": "heroic",
                  "description": "Fifth sub-rank in the official progression system"
                },
                {
                  "id": 6,
                  "name": "mythic",
                  "description": "Sixth sub-rank in the official progression system"
                },
                {
                  "id": 7,
                  "name": "immortal",
                  "description": "Final sub-rank in the official progression system"
                }
              ],
              "state_roles": {
                "UNVERIFIED": 0,
                "PURGATORY": 0,
                "VERIFIED": 0
              },
              "progression": {
                "xp_sources": {
                  "CHAT_MESSAGE": 1,
                  "VOICE_MINUTE": 2,
                  "ACHIEVEMENT": 10,
                  "DISCORD_REACTION": 1,
                  "EVENT_PARTICIPATION": 25
                },
                "subrank_multiplier": 1.5,
                "mainrank_multiplier": 2.0
              }
            }""";
    }
    
    /**
     * Gets the default rewards configuration
     * 
     * @return The default rewards configuration JSON
     */
    public static String getDefaultRewardsConfig() {
        return """
            {
              "reward_types": [
                {
                  "id": "item",
                  "name": "Item Reward",
                  "description": "Gives the player a specific item"
                },
                {
                  "id": "money",
                  "name": "Money Reward",
                  "description": "Gives the player in-game currency"
                },
                {
                  "id": "permission",
                  "name": "Permission Reward",
                  "description": "Grants the player a specific permission"
                },
                {
                  "id": "command",
                  "name": "Command Reward",
                  "description": "Executes a command on behalf of the player"
                }
              ],
              "rank_rewards": {
                "Wanderer": [
                  {
                    "type": "permission",
                    "value": "essentials.home.2",
                    "description": "Allows setting 2 homes"
                  }
                ],
                "Traveller": [
                  {
                    "type": "permission",
                    "value": "essentials.home.3",
                    "description": "Allows setting 3 homes"
                  },
                  {
                    "type": "money",
                    "value": "100",
                    "description": "100 coins reward"
                  }
                ],
                "Explorer": [
                  {
                    "type": "permission",
                    "value": "essentials.home.5",
                    "description": "Allows setting 5 homes"
                  },
                  {
                    "type": "money",
                    "value": "250",
                    "description": "250 coins reward"
                  }
                ]
              },
              "subrank_rewards": {
                "Adept": [
                  {
                    "type": "money",
                    "value": "50",
                    "description": "50 coins reward"
                  }
                ],
                "Master": [
                  {
                    "type": "item",
                    "value": "diamond 5",
                    "description": "5 diamonds reward"
                  }
                ]
              }
            }""";
    }
}
