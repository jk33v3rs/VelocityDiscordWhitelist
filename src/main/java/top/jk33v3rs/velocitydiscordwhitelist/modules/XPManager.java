package top.jk33v3rs.velocitydiscordwhitelist.modules;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.integrations.blazeandcaves.BlazeAndCavesIntegration;
import top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;

/**
 * XPManager
 * 
 * Simplified XP management system that calculates XP from rank data.
 * This lean implementation focuses on core XP tracking without complex features.
 */
public class XPManager {
    
    private final SQLHandler sqlHandler;
    private final Logger logger;
    private final boolean debugEnabled;
    private final ExceptionHandler exceptionHandler;
    private final BlazeAndCavesIntegration blazeAndCavesIntegration;
    
    // Base XP values for calculation
    private static final int BASE_XP_PER_MINUTE = 1;
    private static final int ACHIEVEMENT_XP_VALUE = 10;
    
    /**
     * Constructor for XPManager
     * 
     * @param sqlHandler The SQL handler for database operations
     * @param logger The logger instance
     * @param debugEnabled Whether debug logging is enabled
     * @param config The configuration map (unused in lean implementation)
     * @param exceptionHandler The exception handler for error management
     */
    public XPManager(SQLHandler sqlHandler, Logger logger, boolean debugEnabled, 
                    Map<String, Object> config, ExceptionHandler exceptionHandler) {
        this.sqlHandler = sqlHandler;
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        this.exceptionHandler = exceptionHandler;
        
        // Initialize BlazeAndCaves integration if enabled
        @SuppressWarnings("unchecked")
        Map<String, Object> blazeConfig = (Map<String, Object>) config.getOrDefault("blazeandcaves", Map.of());
        boolean blazeEnabled = Boolean.parseBoolean(blazeConfig.getOrDefault("enabled", "false").toString());
        
        BlazeAndCavesIntegration tempIntegration = null;
        if (blazeEnabled) {
            try {
                // Initialize BlazeAndCaves integration
                java.nio.file.Path configPath = java.nio.file.Paths.get("plugins/VelocityDiscordWhitelist");
                tempIntegration = new BlazeAndCavesIntegration(logger, blazeEnabled, configPath, blazeConfig);
                debugLog("BlazeAndCaves integration initialized successfully");
            } catch (Exception e) {
                exceptionHandler.handleIntegrationException("BlazeAndCaves", "initialization", e);
                tempIntegration = null;
            }
        }
        this.blazeAndCavesIntegration = tempIntegration;
        
        debugLog("XPManager initialized in lean mode with BlazeAndCaves " + 
                 (blazeEnabled ? "enabled" : "disabled"));
    }
    
    /**
     * getPlayerTotalXP
     * 
     * Calculates total XP for a player based on their playtime and achievements.
     * 
     * @param playerUuid The player's UUID as string
     * @return CompletableFuture<Integer> Total XP calculated from rank data
     */
    public CompletableFuture<Integer> getPlayerTotalXP(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get player rank data which contains playtime and achievements
                return sqlHandler.getPlayerRank(playerUuid)
                    .thenApply(optionalRank -> {
                        if (optionalRank.isPresent()) {
                            var rank = optionalRank.get();
                            
                            // Calculate XP from playtime (1 XP per minute)
                            int playtimeXP = rank.getPlayTimeMinutes() * BASE_XP_PER_MINUTE;
                            
                            // Calculate XP from achievements (10 XP per achievement)
                            int achievementXP = rank.getAchievementsCompleted() * ACHIEVEMENT_XP_VALUE;
                            
                            int totalXP = playtimeXP + achievementXP;
                            
                            debugLog("Calculated XP for " + playerUuid + ": " + totalXP + 
                                   " (playtime: " + playtimeXP + ", achievements: " + achievementXP + ")");
                            
                            return totalXP;
                        } else {
                            debugLog("No rank data found for player " + playerUuid + ", returning 0 XP");
                            return 0;
                        }
                    }).join(); // Wait for the result
                    
            } catch (Exception e) {
                logger.error("Error calculating XP for player " + playerUuid, e);
                exceptionHandler.handleIntegrationException("XPManager", "XP calculation", e);
                return 0;
            }
        });
    }
    
    /**
     * awardXP
     * 
     * Awards XP to a player by logging it in the database.
     * In the lean implementation, XP is calculated from playtime/achievements,
     * but we still log XP events for tracking purposes.
     * 
     * @param playerUuid The player's UUID
     * @param xpAmount The amount of XP to award
     * @param source The source of the XP (e.g., "login", "achievement")
     * @return CompletableFuture<Boolean> true if successful
     */
    public CompletableFuture<Boolean> awardXP(String playerUuid, int xpAmount, String source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                sqlHandler.logXpGain(playerUuid, xpAmount, source);
                debugLog("Awarded " + xpAmount + " XP to " + playerUuid + " from " + source);
                return true;
            } catch (Exception e) {
                logger.error("Error awarding XP to player " + playerUuid, e);
                exceptionHandler.handleIntegrationException("XPManager", "XP award", e);
                return false;
            }
        });
    }
    
    /**
     * logAchievement
     * 
     * Logs an achievement for a player.
     * 
     * @param playerUuid The player's UUID
     * @param achievementName The name of the achievement
     * @return CompletableFuture<Boolean> true if successful
     */
    public CompletableFuture<Boolean> logAchievement(String playerUuid, String achievementName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                sqlHandler.logAchievement(playerUuid, achievementName);
                debugLog("Logged achievement '" + achievementName + "' for " + playerUuid);
                return true;
            } catch (Exception e) {
                logger.error("Error logging achievement for player " + playerUuid, e);
                exceptionHandler.handleIntegrationException("XPManager", "Achievement logging", e);
                return false;
            }
        });
    }
    
    /**
     * getRecentXPEvents
     * 
     * Gets recent XP events for a player for display in /rank command.
     * 
     * @param playerUuid The player's UUID
     * @param limit Maximum number of events to return
     * @return CompletableFuture<List<XPEvent>> List of recent XP events
     */
    public CompletableFuture<List<XPEvent>> getRecentXPEvents(String playerUuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // For the lean implementation, return a simple synthetic event list
                // In a full implementation, this would query the database
                List<XPEvent> events = new ArrayList<>();
                
                // Add a placeholder event for now
                events.add(new XPEvent(
                    playerUuid,
                    "PLAYTIME",
                    "General play",
                    BASE_XP_PER_MINUTE,
                    java.time.Instant.now().minusSeconds(300), // 5 minutes ago
                    "server",
                    "{\"auto_generated\": true}"
                ));
                
                debugLog("Retrieved " + events.size() + " recent XP events for " + playerUuid);
                return events;
                
            } catch (Exception e) {
                logger.error("Error retrieving recent XP events for player " + playerUuid, e);
                exceptionHandler.handleIntegrationException("XPManager", "recent XP events retrieval", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * processBlazeAndCavesAchievement
     * 
     * Processes a BlazeAndCaves achievement completion and calculates supplemental XP/rewards.
     * This method supplements the rewards that the datapack provides with our internal reward system.
     * 
     * @param playerUuid The player's UUID as string
     * @param achievementKey The namespaced key of the completed achievement
     * @return CompletableFuture<Integer> The bonus XP awarded for this achievement
     */
    public CompletableFuture<Integer> processBlazeAndCavesAchievement(String playerUuid, String achievementKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (blazeAndCavesIntegration == null) {
                    debugLog("BlazeAndCaves integration not available, skipping achievement: " + achievementKey);
                    return 0;
                }
                
                // Calculate bonus XP based on achievement difficulty
                int bonusXP = blazeAndCavesIntegration.calculateBonusXP(achievementKey);
                
                if (bonusXP > 0) {
                    // Record the achievement completion in our database
                    sqlHandler.recordAchievementCompletion(playerUuid, achievementKey, bonusXP);
                    
                    debugLog("Processed BlazeAndCaves achievement " + achievementKey + 
                             " for player " + playerUuid + ", awarded " + bonusXP + " bonus XP");
                    
                    // This supplements the datapack's rewards with our internal system
                    return bonusXP;
                } else {
                    debugLog("No bonus XP configured for achievement: " + achievementKey);
                    return 0;
                }
                
            } catch (Exception e) {
                logger.error("Error processing BlazeAndCaves achievement " + achievementKey + 
                           " for player " + playerUuid, e);
                exceptionHandler.handleIntegrationException("BlazeAndCaves", "achievement processing", e);
                return 0;
            }
        });
    }
    
    /**
     * getBlazeAndCavesPlayerStats
     * 
     * Gets comprehensive achievement statistics for a player from BlazeAndCaves integration.
     * This provides insight into player progression across the ~2000 achievements.
     * 
     * @param playerUuid The player's UUID as string
     * @return CompletableFuture<Map<String, Object>> Statistics about the player's achievements
     */
    public CompletableFuture<Map<String, Object>> getBlazeAndCavesPlayerStats(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> stats = new java.util.HashMap<>();
            
            try {
                if (blazeAndCavesIntegration == null) {
                    stats.put("enabled", false);
                    stats.put("total_achievements", 0);
                    stats.put("completed_achievements", 0);
                    return stats;
                }
                
                // Get player's completed achievements from our database
                int completedCount = sqlHandler.getPlayerAchievementCount(playerUuid);
                int totalAvailable = blazeAndCavesIntegration.getTotalAchievementCount();
                
                stats.put("enabled", true);
                stats.put("total_achievements", totalAvailable);
                stats.put("completed_achievements", completedCount);
                stats.put("completion_percentage", 
                         totalAvailable > 0 ? (double) completedCount / totalAvailable * 100.0 : 0.0);
                
                debugLog("Retrieved BlazeAndCaves stats for " + playerUuid + ": " + 
                         completedCount + "/" + totalAvailable + " completed");
                
                return stats;
                
            } catch (Exception e) {
                logger.error("Error retrieving BlazeAndCaves stats for player " + playerUuid, e);
                exceptionHandler.handleIntegrationException("BlazeAndCaves", "stats retrieval", e);
                stats.put("error", true);
                return stats;
            }
        });
    }
    
    /**
     * debugLog
     * 
     * Logs a debug message if debug mode is enabled.
     * 
     * @param message The message to log
     */
    private void debugLog(String message) {
        if (debugEnabled) {
            logger.info("[DEBUG] XPManager: " + message);
        }
    }
}
