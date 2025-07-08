package top.jk33v3rs.velocitydiscordwhitelist.integrations.blazeandcaves;

import org.slf4j.Logger;
import top.jk33v3rs.velocitydiscordwhitelist.models.BlazeAndCavesAdvancement;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BlazeAndCavesIntegration - Main integration handler for BlazeAndCaves Advancement Pack
 * 
 * This class manages the integration between the plugin and BlazeAndCaves achievements,
 * handling loading, caching, and XP calculation for achievements.
 * 
 * @author jk33v3rs
 * @version 1.0
 */
public class BlazeAndCavesIntegration {
    
    private final Logger logger;
    private final boolean enabled;
    private final Path configPath;
    
    // Achievement mappings cache
    private final Map<String, BlazeAndCavesAdvancement> achievementMappings;
    private final Map<String, Double> xpMultipliers;
    
    // Configuration
    private final double easyMultiplier;
    private final double mediumMultiplier;
    private final double hardMultiplier;
    private final double insaneMultiplier;
    private final double extremeMultiplier;
    private final double terralithBonus;
    private final double hardcoreBonus;
    
    /**
     * Constructor for BlazeAndCavesIntegration
     * 
     * @param logger The logger instance for this integration
     * @param exceptionHandler The centralized exception handler
     * @param enabled Whether the integration is enabled
     * @param configPath Path to the configuration directory
     * @param config Configuration map containing multipliers
     */
    @SuppressWarnings("unchecked")
    public BlazeAndCavesIntegration(Logger logger, boolean enabled, Path configPath, Map<String, Object> config) {
        this.logger = logger;
        this.enabled = enabled;
        this.configPath = configPath;
        
        this.achievementMappings = new ConcurrentHashMap<>();
        this.xpMultipliers = new ConcurrentHashMap<>();
        
        // Load configuration
        Map<String, Object> blazeAndCavesConfig = (Map<String, Object>) config.getOrDefault("blaze_and_caves", new ConcurrentHashMap<>());
        
        this.easyMultiplier = Double.parseDouble(blazeAndCavesConfig.getOrDefault("easy_multiplier", "1.0").toString());
        this.mediumMultiplier = Double.parseDouble(blazeAndCavesConfig.getOrDefault("medium_multiplier", "1.25").toString());
        this.hardMultiplier = Double.parseDouble(blazeAndCavesConfig.getOrDefault("hard_multiplier", "1.5").toString());
        this.insaneMultiplier = Double.parseDouble(blazeAndCavesConfig.getOrDefault("insane_multiplier", "2.0").toString());
        this.extremeMultiplier = Double.parseDouble(blazeAndCavesConfig.getOrDefault("extreme_multiplier", "3.0").toString());
        this.terralithBonus = Double.parseDouble(blazeAndCavesConfig.getOrDefault("terralith_bonus", "0.1").toString());
        this.hardcoreBonus = Double.parseDouble(blazeAndCavesConfig.getOrDefault("hardcore_bonus", "0.5").toString());
        
        if (enabled) {
            initialize();
        }
    }
    
    /**
     * Initializes the BlazeAndCaves integration
     */
    private void initialize() {
        logger.info("Initializing BlazeAndCaves integration...");
        
        try {
            // Try to load achievements from JSON file
            Map<String, BlazeAndCavesAdvancement> loadedAchievements = 
                BlazeAndCavesLoader.loadAchievements(configPath);
            
            if (!loadedAchievements.isEmpty()) {
                achievementMappings.putAll(loadedAchievements);
                logger.info("Loaded {} BlazeAndCaves achievements from JSON configuration", loadedAchievements.size());
            } else {
                // Fallback to hardcoded sample achievements
                initializeDefaultAchievements();
                logger.warn("BlazeAndCaves JSON not found, loaded {} default achievements", achievementMappings.size());
            }
            
            // Load XP multipliers
            Map<String, Double> loadedMultipliers = BlazeAndCavesLoader.loadXPMultipliers(configPath);
            xpMultipliers.putAll(loadedMultipliers);
            
            logger.info("BlazeAndCaves integration initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize BlazeAndCaves integration: {}", e.getMessage());
            initializeDefaultAchievements();
            logger.info("Loaded {} default BlazeAndCaves achievements as fallback", achievementMappings.size());
        }
    }
    
    /**
     * Initializes default BlazeAndCaves achievements as fallback
     */
    private void initializeDefaultAchievements() {
        // Sample achievements for fallback
        achievementMappings.put("blazeandcave:adventure/adventure", 
            new BlazeAndCavesAdvancement("blazeandcave:adventure/adventure", "Adventure", 
                                       "adventure", 10, false, false, "easy", "Village, pillage and exploration"));
        
        achievementMappings.put("blazeandcave:monsters/monsters", 
            new BlazeAndCavesAdvancement("blazeandcave:monsters/monsters", "Monsters", 
                                       "monsters", 10, false, false, "easy", "Battle against the monsters of darkness"));
        
        achievementMappings.put("blazeandcave:mining/mining", 
            new BlazeAndCavesAdvancement("blazeandcave:mining/mining", "Mining", 
                                       "mining", 10, false, false, "easy", "Dig deep down, mine up ore"));
        
        achievementMappings.put("blazeandcave:nether/nether", 
            new BlazeAndCavesAdvancement("blazeandcave:nether/nether", "Nether", 
                                       "nether", 15, false, false, "easy", "Bring summer clothes"));
        
        achievementMappings.put("blazeandcave:end/enter_end", 
            new BlazeAndCavesAdvancement("blazeandcave:end/enter_end", "Enter the End", 
                                       "end", 50, false, false, "hard", "Enter the End dimension"));
    }
    
    /**
     * Calculates XP for a BlazeAndCaves achievement
     * 
     * @param namespacedKey The achievement's namespaced key
     * @return Calculated XP value, or 0 if achievement not found
     */
    public int calculateXP(String namespacedKey) {
        if (!enabled) {
            return 0;
        }
        
        BlazeAndCavesAdvancement achievement = achievementMappings.get(namespacedKey);
        if (achievement == null) {
            logger.debug("Unknown BlazeAndCaves achievement: {}", namespacedKey);
            return 0;
        }
        
        double baseXP = achievement.getBaseXP();
        double difficultyMultiplier = getDifficultyMultiplier(achievement.getDifficulty());
        double variantBonus = 1.0;
        
        // Apply variant bonuses
        if (achievement.isTerralithVariant()) {
            variantBonus += terralithBonus;
        }
        if (achievement.isHardcoreVariant()) {
            variantBonus += hardcoreBonus;
        }
        
        int finalXP = (int) Math.round(baseXP * difficultyMultiplier * variantBonus);
        
        logger.debug("Calculated XP for {}: {} base × {} difficulty × {} variant = {} XP", 
                    namespacedKey, baseXP, difficultyMultiplier, variantBonus, finalXP);
        
        return finalXP;
    }
    
    /**
     * Calculates bonus XP for a specific achievement
     * 
     * @param achievementKey The namespaced key of the achievement
     * @return The calculated bonus XP for this achievement
     */
    public int calculateBonusXP(String achievementKey) {
        BlazeAndCavesAdvancement achievement = achievementMappings.get(achievementKey);
        if (achievement == null) {
            logger.warn("Unknown achievement key: {}", achievementKey);
            return 0;
        }
        
        // Calculate base XP from achievement difficulty
        double baseXP = 10.0; // Base XP for any achievement
        double difficultyMultiplier = getDifficultyMultiplier(achievement.getDifficulty());
        
        // Apply category bonuses
        double categoryBonus = 1.0;
        if (achievement.getCategory().contains("terralith")) {
            categoryBonus += terralithBonus;
        }
        if (achievement.getCategory().contains("hardcore")) {
            categoryBonus += hardcoreBonus;
        }
        
        int finalXP = (int) Math.round(baseXP * difficultyMultiplier * categoryBonus);
        return Math.max(1, finalXP); // Minimum 1 XP
    }
    
    /**
     * Gets the difficulty multiplier for a given difficulty level
     * 
     * @param difficulty The difficulty level
     * @return The multiplier value
     */
    private double getDifficultyMultiplier(String difficulty) {
        switch (difficulty.toLowerCase()) {
            case "easy": return easyMultiplier;
            case "medium": return mediumMultiplier;
            case "hard": return hardMultiplier;
            case "insane": return insaneMultiplier;
            case "extreme": return extremeMultiplier;
            default: 
                logger.warn("Unknown difficulty level: {}, using easy multiplier", difficulty);
                return easyMultiplier;
        }
    }
    
    /**
     * Gets an achievement by its namespaced key
     * 
     * @param namespacedKey The achievement's namespaced key
     * @return The achievement object, or null if not found
     */
    public BlazeAndCavesAdvancement getAchievement(String namespacedKey) {
        return achievementMappings.get(namespacedKey);
    }
    
    /**
     * Checks if the integration is enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the total number of loaded achievements
     * 
     * @return Number of achievements
     */
    public int getAchievementCount() {
        return achievementMappings.size();
    }
    
    /**
     * Gets the total number of achievements available
     * 
     * @return Total achievement count
     */
    public int getTotalAchievementCount() {
        return achievementMappings.size();
    }
    
    /**
     * Checks if an achievement exists
     * 
     * @param namespacedKey The achievement's namespaced key
     * @return true if the achievement exists
     */
    public boolean hasAchievement(String namespacedKey) {
        return achievementMappings.containsKey(namespacedKey);
    }
}
