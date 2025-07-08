package top.jk33v3rs.velocitydiscordwhitelist.integrations.blazeandcaves;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.jk33v3rs.velocitydiscordwhitelist.models.BlazeAndCavesAdvancement;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * BlazeAndCavesLoader - Utility class for loading BlazeAndCaves achievements from JSON
 * 
 * This class loads the comprehensive BlazeAndCaves achievement data from the 
 * blazeandcaves-achievements.json file and provides it to the XPManager.
 * 
 * @author jk33v3rs
 * @version 1.0
 */
public class BlazeAndCavesLoader {
    
    private static final String ACHIEVEMENTS_FILE = "blazeandcaves-achievements.json";
    
    /**
     * Loads all BlazeAndCaves achievements from the JSON configuration file
     * 
     * @param configPath The path to the configuration directory
     * @return Map of achievement namespaced keys to BlazeAndCavesAdvancement objects
     * @throws IOException if the JSON file cannot be read
     */
    public static Map<String, BlazeAndCavesAdvancement> loadAchievements(Path configPath) throws IOException {
        Map<String, BlazeAndCavesAdvancement> achievements = new HashMap<>();
        
        Path achievementsFile = configPath.resolve(ACHIEVEMENTS_FILE);
        if (!achievementsFile.toFile().exists()) {
            // File not found, return empty map
            return achievements;
        }
        
        try (FileReader reader = new FileReader(achievementsFile.toFile())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject achievementsJson = root.getAsJsonObject("achievements");
            
            if (achievementsJson != null) {
                for (String key : achievementsJson.keySet()) {
                    JsonObject achievement = achievementsJson.getAsJsonObject(key);
                    
                    String namespacedKey = achievement.get("namespacedKey").getAsString();
                    String displayName = achievement.get("displayName").getAsString();
                    String description = achievement.get("description").getAsString();
                    String category = achievement.get("category").getAsString();
                    int baseXP = achievement.get("baseXP").getAsInt();
                    String difficulty = achievement.get("difficulty").getAsString();
                    boolean isTerralithVariant = achievement.get("isTerralithVariant").getAsBoolean();
                    boolean isHardcoreVariant = achievement.get("isHardcoreVariant").getAsBoolean();
                    
                    BlazeAndCavesAdvancement adv = new BlazeAndCavesAdvancement(
                        namespacedKey, displayName, category, baseXP, 
                        isTerralithVariant, isHardcoreVariant, difficulty, description
                    );
                    
                    achievements.put(namespacedKey, adv);
                }
                
                // Successfully loaded achievements
            }
        } catch (Exception e) {
            // Failed to parse, throw IOException
            throw new IOException("Failed to load BlazeAndCaves achievements", e);
        }
        
        return achievements;
    }
    
    /**
     * Loads XP multiplier configuration from the JSON file
     * 
     * @param configPath The path to the configuration directory
     * @return Map containing XP multiplier configuration
     * @throws IOException if the JSON file cannot be read
     */
    public static Map<String, Double> loadXPMultipliers(Path configPath) throws IOException {
        Map<String, Double> multipliers = new HashMap<>();
        
        // Set defaults
        multipliers.put("easy", 1.0);
        multipliers.put("medium", 1.25);
        multipliers.put("hard", 1.5);
        multipliers.put("insane", 2.0);
        multipliers.put("extreme", 3.0);
        multipliers.put("terralith_bonus", 0.1);
        multipliers.put("hardcore_bonus", 0.5);
        
        Path achievementsFile = configPath.resolve(ACHIEVEMENTS_FILE);
        if (!achievementsFile.toFile().exists()) {
            return multipliers; // Return defaults
        }
        
        try (FileReader reader = new FileReader(achievementsFile.toFile())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject config = root.getAsJsonObject("configuration");
            
            if (config != null && config.has("xp_system")) {
                JsonObject xpSystem = config.getAsJsonObject("xp_system");
                JsonObject blazeAndCaves = xpSystem.getAsJsonObject("blaze_and_caves");
                
                if (blazeAndCaves != null) {
                    if (blazeAndCaves.has("easy_multiplier")) {
                        multipliers.put("easy", blazeAndCaves.get("easy_multiplier").getAsDouble());
                    }
                    if (blazeAndCaves.has("medium_multiplier")) {
                        multipliers.put("medium", blazeAndCaves.get("medium_multiplier").getAsDouble());
                    }
                    if (blazeAndCaves.has("hard_multiplier")) {
                        multipliers.put("hard", blazeAndCaves.get("hard_multiplier").getAsDouble());
                    }
                    if (blazeAndCaves.has("insane_multiplier")) {
                        multipliers.put("insane", blazeAndCaves.get("insane_multiplier").getAsDouble());
                    }
                    if (blazeAndCaves.has("extreme_multiplier")) {
                        multipliers.put("extreme", blazeAndCaves.get("extreme_multiplier").getAsDouble());
                    }
                    if (blazeAndCaves.has("terralith_bonus")) {
                        multipliers.put("terralith_bonus", blazeAndCaves.get("terralith_bonus").getAsDouble());
                    }
                    if (blazeAndCaves.has("hardcore_bonus")) {
                        multipliers.put("hardcore_bonus", blazeAndCaves.get("hardcore_bonus").getAsDouble());
                    }
                }
            }
        } catch (Exception e) {
            // Failed to load, use defaults
        }
        
        return multipliers;
    }
    
    /**
     * Checks if BlazeAndCaves integration is available
     * 
     * @param configPath The path to the configuration directory
     * @return true if the achievements file exists and is readable
     */
    public static boolean isAvailable(Path configPath) {
        Path achievementsFile = configPath.resolve(ACHIEVEMENTS_FILE);
        return achievementsFile.toFile().exists() && achievementsFile.toFile().canRead();
    }
}
