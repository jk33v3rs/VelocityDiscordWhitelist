package top.jk33v3rs.velocitydiscordwhitelist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

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
            
            return config;
        } catch (IOException e) {
            logger.error("Failed to load config file: " + fullFileName, e);
            
            // Try to parse default config as fallback
            try {
                JsonObject fallback = JsonParser.parseString(defaultConfig).getAsJsonObject();
                loadedConfigs.put(fullFileName, fallback);
                return fallback;
            } catch (Exception ex) {
                logger.error("Failed to parse default config", ex);
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
     * Gets the default ranks configuration
     * 
     * @return The default ranks configuration JSON
     */
    public static String getDefaultRanksConfig() {
        return "{\n" +
            "  \"main_ranks\": [\n" +
            "    {\n" +
            "      \"id\": 1,\n" +
            "      \"name\": \"bystander\",\n" +
            "      \"description\": \"First rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 0,\n" +
            "      \"required_achievements\": 0\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 2,\n" +
            "      \"name\": \"onlooker\",\n" +
            "      \"description\": \"Second rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 60,\n" +
            "      \"required_achievements\": 1\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 3,\n" +
            "      \"name\": \"wanderer\",\n" +
            "      \"description\": \"Third rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 120,\n" +
            "      \"required_achievements\": 2\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 4,\n" +
            "      \"name\": \"traveller\",\n" +
            "      \"description\": \"Fourth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 240,\n" +
            "      \"required_achievements\": 3\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 5,\n" +
            "      \"name\": \"explorer\",\n" +
            "      \"description\": \"Fifth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 480,\n" +
            "      \"required_achievements\": 5\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 6,\n" +
            "      \"name\": \"adventurer\",\n" +
            "      \"description\": \"Sixth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 720,\n" +
            "      \"required_achievements\": 8\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 7,\n" +
            "      \"name\": \"surveyor\",\n" +
            "      \"description\": \"Seventh rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 1080,\n" +
            "      \"required_achievements\": 12\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 8,\n" +
            "      \"name\": \"navigator\",\n" +
            "      \"description\": \"Eighth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 1440,\n" +
            "      \"required_achievements\": 16\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 9,\n" +
            "      \"name\": \"journeyman\",\n" +
            "      \"description\": \"Ninth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 2160,\n" +
            "      \"required_achievements\": 20\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 10,\n" +
            "      \"name\": \"pathfinder\",\n" +
            "      \"description\": \"Tenth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 2880,\n" +
            "      \"required_achievements\": 25\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 11,\n" +
            "      \"name\": \"trailblazer\",\n" +
            "      \"description\": \"Eleventh rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 3600,\n" +
            "      \"required_achievements\": 30\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 12,\n" +
            "      \"name\": \"pioneer\",\n" +
            "      \"description\": \"Twelfth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 4320,\n" +
            "      \"required_achievements\": 35\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 13,\n" +
            "      \"name\": \"craftsman\",\n" +
            "      \"description\": \"Thirteenth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 5040,\n" +
            "      \"required_achievements\": 40\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 14,\n" +
            "      \"name\": \"specialist\",\n" +
            "      \"description\": \"Fourteenth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 5760,\n" +
            "      \"required_achievements\": 45\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 15,\n" +
            "      \"name\": \"artisan\",\n" +
            "      \"description\": \"Fifteenth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 6480,\n" +
            "      \"required_achievements\": 50\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 16,\n" +
            "      \"name\": \"veteran\",\n" +
            "      \"description\": \"Sixteenth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 7200,\n" +
            "      \"required_achievements\": 55\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 17,\n" +
            "      \"name\": \"sage\",\n" +
            "      \"description\": \"Seventeenth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 7920,\n" +
            "      \"required_achievements\": 60\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 18,\n" +
            "      \"name\": \"luminary\",\n" +
            "      \"description\": \"Eighteenth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 8640,\n" +
            "      \"required_achievements\": 65\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 19,\n" +
            "      \"name\": \"titan\",\n" +
            "      \"description\": \"Nineteenth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 9360,\n" +
            "      \"required_achievements\": 70\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 20,\n" +
            "      \"name\": \"legend\",\n" +
            "      \"description\": \"Twentieth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 10080,\n" +
            "      \"required_achievements\": 75\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 21,\n" +
            "      \"name\": \"eternal\",\n" +
            "      \"description\": \"Twenty-first rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 10800,\n" +
            "      \"required_achievements\": 80\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 22,\n" +
            "      \"name\": \"ascendant\",\n" +
            "      \"description\": \"Twenty-second rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 11520,\n" +
            "      \"required_achievements\": 85\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 23,\n" +
            "      \"name\": \"celestial\",\n" +
            "      \"description\": \"Twenty-third rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 12240,\n" +
            "      \"required_achievements\": 90\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 24,\n" +
            "      \"name\": \"divine\",\n" +
            "      \"description\": \"Twenty-fourth rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 12960,\n" +
            "      \"required_achievements\": 95\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 25,\n" +
            "      \"name\": \"deity\",\n" +
            "      \"description\": \"Final rank in the official progression system\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"required_time\": 14400,\n" +
            "      \"required_achievements\": 100,\n" +
            "      \"is_special\": true\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 28,\n" +
            "      \"name\": \"booster\",\n" +
            "      \"description\": \"Custom supporter rank - Discord Nitro Booster\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"is_special\": true\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 35,\n" +
            "      \"name\": \"mod\",\n" +
            "      \"description\": \"Elevated moderator rank\",\n" +
            "      \"discord_role_id\": 0,\n" +
            "      \"is_special\": true\n" +
            "    }\n" +
            "  ],\n" +
            "  \"sub_ranks\": [\n" +
            "    {\n" +
            "      \"id\": 1,\n" +
            "      \"name\": \"novice\",\n" +
            "      \"description\": \"First sub-rank in the official progression system\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 2,\n" +
            "      \"name\": \"apprentice\",\n" +
            "      \"description\": \"Second sub-rank in the official progression system\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 3,\n" +
            "      \"name\": \"adept\",\n" +
            "      \"description\": \"Third sub-rank in the official progression system\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 4,\n" +
            "      \"name\": \"master\",\n" +
            "      \"description\": \"Fourth sub-rank in the official progression system\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 5,\n" +
            "      \"name\": \"heroic\",\n" +
            "      \"description\": \"Fifth sub-rank in the official progression system\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 6,\n" +
            "      \"name\": \"mythic\",\n" +
            "      \"description\": \"Sixth sub-rank in the official progression system\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": 7,\n" +
            "      \"name\": \"immortal\",\n" +
            "      \"description\": \"Final sub-rank in the official progression system\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"state_roles\": {\n" +
            "    \"UNVERIFIED\": 0,\n" +
            "    \"PURGATORY\": 0,\n" +
            "    \"VERIFIED\": 0\n" +
            "  },\n" +
            "  \"progression\": {\n" +
            "    \"xp_sources\": {\n" +
            "      \"CHAT_MESSAGE\": 1,\n" +
            "      \"VOICE_MINUTE\": 2,\n" +
            "      \"ACHIEVEMENT\": 10,\n" +
            "      \"DISCORD_REACTION\": 1,\n" +
            "      \"EVENT_PARTICIPATION\": 25\n" +
            "    },\n" +
            "    \"subrank_multiplier\": 1.5,\n" +
            "    \"mainrank_multiplier\": 2.0\n" +
            "  }\n" +
            "}";
    }
    
    /**
     * Gets the default rewards configuration
     * 
     * @return The default rewards configuration JSON
     */
    public static String getDefaultRewardsConfig() {
        return "{\n" +
            "  \"reward_types\": [\n" +
            "    {\n" +
            "      \"id\": \"item\",\n" +
            "      \"name\": \"Item Reward\",\n" +
            "      \"description\": \"Gives the player a specific item\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"money\",\n" +
            "      \"name\": \"Money Reward\",\n" +
            "      \"description\": \"Gives the player in-game currency\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"permission\",\n" +
            "      \"name\": \"Permission Reward\",\n" +
            "      \"description\": \"Grants the player a specific permission\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"command\",\n" +
            "      \"name\": \"Command Reward\",\n" +
            "      \"description\": \"Executes a command on behalf of the player\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"rank_rewards\": {\n" +
            "    \"Wanderer\": [\n" +
            "      {\n" +
            "        \"type\": \"permission\",\n" +
            "        \"value\": \"essentials.home.2\",\n" +
            "        \"description\": \"Allows setting 2 homes\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"Traveller\": [\n" +
            "      {\n" +
            "        \"type\": \"permission\",\n" +
            "        \"value\": \"essentials.home.3\",\n" +
            "        \"description\": \"Allows setting 3 homes\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"type\": \"money\",\n" +
            "        \"value\": \"100\",\n" +
            "        \"description\": \"100 coins reward\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"Explorer\": [\n" +
            "      {\n" +
            "        \"type\": \"permission\",\n" +
            "        \"value\": \"essentials.home.5\",\n" +
            "        \"description\": \"Allows setting 5 homes\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"type\": \"money\",\n" +
            "        \"value\": \"250\",\n" +
            "        \"description\": \"250 coins reward\"\n" +
            "      }\n" +
            "    ]\n" +
            "  },\n" +
            "  \"subrank_rewards\": {\n" +
            "    \"Adept\": [\n" +
            "      {\n" +
            "        \"type\": \"money\",\n" +
            "        \"value\": \"50\",\n" +
            "        \"description\": \"50 coins reward\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"Master\": [\n" +
            "      {\n" +
            "        \"type\": \"item\",\n" +
            "        \"value\": \"diamond 5\",\n" +
            "        \"description\": \"5 diamonds reward\"\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";
    }
}
