package top.jk33v3rs.velocitydiscordwhitelist.modules;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.models.BlazeAndCavesAdvancement;
import top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent;
import top.jk33v3rs.velocitydiscordwhitelist.utils.LoggingUtils;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;

/**
 * XPManager handles XP operations with rate limiting to prevent XP farming.
 */
public class XPManager {
    
    private final SQLHandler sqlHandler;
    private final Logger logger;
    private final boolean debugEnabled;
    private final ExceptionHandler exceptionHandler;
    
    // Rate limiting maps
    private final Map<String, Instant> lastEventTime;
    private final Map<String, Integer> eventCounts;
    private final Map<String, BlazeAndCavesAdvancement> blazeAndCavesMap;
    
    // XP configuration section - now properly final
    private final Map<String, Object> xpSection;
    
    // Configuration cache
    private final int maxEventsPerMinute;
    private final int maxEventsPerHour;
    private final int maxEventsPerDay;
    private final int cooldownSeconds;
    private final boolean rateLimitingEnabled;
    private final boolean blazeAndCavesEnabled;
    
    // XP modifiers from configuration
    private final double advancementModifier;
    private final double playtimeModifier;
    private final double killModifier;
    private final double breakBlockModifier;
    private final double placeBlockModifier;
    private final double craftItemModifier;
    private final double enchantItemModifier;
    private final double tradeModifier;
    private final double fishingModifier;
    private final double miningModifier;
    
    // BlazeAndCaves configuration
    private final double easyDifficultyMultiplier;
    private final double mediumDifficultyMultiplier;
    private final double hardDifficultyMultiplier;
    private final double insaneDifficultyMultiplier;
    private final double terralithBonus;
    private final double hardcoreBonus;
      /**
     * Constructor for XPManager
     * 
     * @param sqlHandler The SQL handler for database operations
     * @param logger The logger instance
     * @param debugEnabled Whether debug logging is enabled
     * @param config The configuration map containing XP and rate limiting settings
     * @param exceptionHandler The centralized exception handler
     */
    @SuppressWarnings("unchecked")
    public XPManager(SQLHandler sqlHandler, Logger logger, boolean debugEnabled, Map<String, Object> config, ExceptionHandler exceptionHandler) {
        this.sqlHandler = sqlHandler;
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        this.exceptionHandler = exceptionHandler;
        
        // Initialize collections
        this.lastEventTime = new ConcurrentHashMap<>();
        this.eventCounts = new ConcurrentHashMap<>();
        this.blazeAndCavesMap = new ConcurrentHashMap<>();
        
        // Load XP configuration section - initialize final field immediately
        this.xpSection = (Map<String, Object>) config.getOrDefault("xp", new ConcurrentHashMap<>());
        
        // Load rate limiting configuration
        Map<String, Object> rateLimitConfig = (Map<String, Object>) config.getOrDefault("xp.rate_limiting", new ConcurrentHashMap<>());
        this.rateLimitingEnabled = Boolean.parseBoolean(rateLimitConfig.getOrDefault("enabled", "true").toString());
        this.cooldownSeconds = Integer.parseInt(rateLimitConfig.getOrDefault("cooldownSeconds", "5").toString());
        this.maxEventsPerMinute = Integer.parseInt(rateLimitConfig.getOrDefault("maxEventsPerMinute", "10").toString());
        this.maxEventsPerHour = Integer.parseInt(rateLimitConfig.getOrDefault("maxEventsPerHour", "100").toString());
        this.maxEventsPerDay = Integer.parseInt(rateLimitConfig.getOrDefault("maxEventsPerDay", "500").toString());
        
        // Load XP modifiers
        Map<String, Object> modifierConfig = (Map<String, Object>) config.getOrDefault("xp.modifiers", new ConcurrentHashMap<>());
        this.advancementModifier = Double.parseDouble(modifierConfig.getOrDefault("advancement", "1.0").toString());
        this.playtimeModifier = Double.parseDouble(modifierConfig.getOrDefault("playtime", "0.1").toString());
        this.killModifier = Double.parseDouble(modifierConfig.getOrDefault("kill", "0.5").toString());
        this.breakBlockModifier = Double.parseDouble(modifierConfig.getOrDefault("break_block", "0.1").toString());
        this.placeBlockModifier = Double.parseDouble(modifierConfig.getOrDefault("place_block", "0.2").toString());
        this.craftItemModifier = Double.parseDouble(modifierConfig.getOrDefault("craft_item", "0.4").toString());
        this.enchantItemModifier = Double.parseDouble(modifierConfig.getOrDefault("enchant_item", "1.2").toString());
        this.tradeModifier = Double.parseDouble(modifierConfig.getOrDefault("trade", "0.6").toString());
        this.fishingModifier = Double.parseDouble(modifierConfig.getOrDefault("fishing", "0.4").toString());
        this.miningModifier = Double.parseDouble(modifierConfig.getOrDefault("mining", "0.3").toString());
        
        // Load BlazeAndCaves configuration
        Map<String, Object> blazeAndCavesConfig = (Map<String, Object>) config.getOrDefault("xp.blaze_and_caves", new ConcurrentHashMap<>());
        this.blazeAndCavesEnabled = Boolean.parseBoolean(blazeAndCavesConfig.getOrDefault("enabled", "true").toString());
        this.easyDifficultyMultiplier = Double.parseDouble(blazeAndCavesConfig.getOrDefault("easy_multiplier", "1.0").toString());
        this.mediumDifficultyMultiplier = Double.parseDouble(blazeAndCavesConfig.getOrDefault("medium_multiplier", "1.25").toString());
        this.hardDifficultyMultiplier = Double.parseDouble(blazeAndCavesConfig.getOrDefault("hard_multiplier", "1.5").toString());
        this.insaneDifficultyMultiplier = Double.parseDouble(blazeAndCavesConfig.getOrDefault("insane_multiplier", "2.0").toString());
        this.terralithBonus = Double.parseDouble(blazeAndCavesConfig.getOrDefault("terralith_bonus", "0.1").toString());
        this.hardcoreBonus = Double.parseDouble(blazeAndCavesConfig.getOrDefault("hardcore_bonus", "0.5").toString());
        
        // Initialize BlazeAndCaves mappings
        initializeBlazeAndCavesMappings();
        
        debugLog("XPManager initialized with rate limiting: " + rateLimitingEnabled);
    }
    
    /**
     * Processes an XP gain event with rate limiting validation
     * 
     * @param playerUuid The UUID of the player gaining XP
     * @param eventType The type of event (e.g., "ADVANCEMENT", "BLAZE_AND_CAVE")
     * @param eventSource The specific source of the XP (e.g., advancement name)
     * @param baseXP The base XP amount before modifiers
     * @param serverName The server where the event occurred
     * @param metadata Additional metadata about the event
     * @return CompletableFuture that resolves to true if XP was granted, false if rate limited
     */    public CompletableFuture<Boolean> processXPGain(String playerUuid, String eventType, String eventSource, 
                                                   int baseXP, String serverName, String metadata) {
        return CompletableFuture.supplyAsync(() -> {
            return exceptionHandler.executeWithHandling("processing XP gain for player " + playerUuid, () -> {
                // Check rate limiting first
                if (rateLimitingEnabled && !passesRateLimiting(playerUuid, eventType, eventSource)) {
                    LoggingUtils.debugLog(logger, debugEnabled, "XP gain rate limited for player " + playerUuid + " - " + eventType + ":" + eventSource);
                    return false;
                }
                
                // Calculate final XP with modifiers
                int finalXP = calculateFinalXP(eventType, eventSource, baseXP);
                
                // Create XP event
                XPEvent xpEvent = new XPEvent(playerUuid, eventType, eventSource, finalXP, 
                                            Instant.now(), serverName, metadata);
                
                // Record the event
                recordXPEvent(xpEvent);
                
                // Update rate limiting tracking
                updateRateLimitingTracking(playerUuid, eventType, eventSource);
                
                LoggingUtils.debugLog(logger, debugEnabled, "XP granted: " + finalXP + " to player " + playerUuid + " for " + eventType + ":" + eventSource);
                return true;
            }, false);
        });
    }
    
    /**
     * Checks if an XP gain event passes rate limiting rules
     * 
     * @param playerUuid The UUID of the player
     * @param eventType The type of event
     * @param eventSource The specific source of the event
     * @return True if the event passes rate limiting, false otherwise
     */    private boolean passesRateLimiting(String playerUuid, String eventType, String eventSource) {
        String rateLimitKey = playerUuid + ":" + eventType + ":" + eventSource;
        Instant now = Instant.now();
        
        // Check in-memory cooldown first (fastest check)
        if (lastEventTime.containsKey(rateLimitKey)) {
            Instant lastTime = lastEventTime.get(rateLimitKey);
            long secondsSinceLastEvent = ChronoUnit.SECONDS.between(lastTime, now);
            if (secondsSinceLastEvent < cooldownSeconds) {
                debugLog("Rate limit: Cooldown not met for " + rateLimitKey + " (" + secondsSinceLastEvent + "s < " + cooldownSeconds + "s)");
                return false;
            }
        }        // Check database for recent events (more comprehensive check)
        return exceptionHandler.executeWithHandling("checking rate limiting for " + rateLimitKey, () -> {
            try {
                // Check events in the last minute
                int eventsLastMinute = sqlHandler.getXPEventCount(playerUuid, eventType, eventSource, 
                                                                now.minus(1, ChronoUnit.MINUTES), now);
                if (eventsLastMinute >= maxEventsPerMinute) {
                    debugLog("Rate limit: Too many events in last minute for " + rateLimitKey + " (" + eventsLastMinute + " >= " + maxEventsPerMinute + ")");
                    return false;
                }
                
                // Check events in the last hour
                int eventsLastHour = sqlHandler.getXPEventCount(playerUuid, eventType, eventSource, 
                                                               now.minus(1, ChronoUnit.HOURS), now);
                if (eventsLastHour >= maxEventsPerHour) {
                    debugLog("Rate limit: Too many events in last hour for " + rateLimitKey + " (" + eventsLastHour + " >= " + maxEventsPerHour + ")");
                    return false;
                }
                
                // Check events in the last day
                int eventsLastDay = sqlHandler.getXPEventCount(playerUuid, eventType, eventSource, 
                                                              now.minus(1, ChronoUnit.DAYS), now);
                if (eventsLastDay >= maxEventsPerDay) {
                    debugLog("Rate limit: Too many events in last day for " + rateLimitKey + " (" + eventsLastDay + " >= " + maxEventsPerDay + ")");
                    return false;
                }
                
                return true;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, true); // Default to allowing the event if we can't check the database
    }
    
    /**
     * Calculates the final XP value with all applicable modifiers
     * 
     * @param eventType The type of event
     * @param eventSource The specific source of the event
     * @param baseXP The base XP amount
     * @return The final XP amount after all modifiers
     */
    private int calculateFinalXP(String eventType, String eventSource, int baseXP) {
        if ("BLAZE_AND_CAVE".equals(eventType) && blazeAndCavesEnabled) {
            BlazeAndCavesAdvancement advancement = blazeAndCavesMap.get(eventSource);
            if (advancement != null) {
                return advancement.getFinalXP(easyDifficultyMultiplier, mediumDifficultyMultiplier, 
                                            hardDifficultyMultiplier, insaneDifficultyMultiplier, 
                                            terralithBonus, hardcoreBonus);
            }
        }
        
        // Apply configured modifiers based on event type
        double modifier = switch (eventType) {
            case "ADVANCEMENT" -> advancementModifier;
            case "PLAYTIME" -> playtimeModifier;
            case "KILL" -> killModifier;
            case "BREAK_BLOCK" -> breakBlockModifier;
            case "PLACE_BLOCK" -> placeBlockModifier;
            case "CRAFT_ITEM" -> craftItemModifier;
            case "ENCHANT_ITEM" -> enchantItemModifier;
            case "TRADE" -> tradeModifier;
            case "FISHING" -> fishingModifier;
            case "MINING" -> miningModifier;
            default -> 1.0;
        };
        
        return Math.max(1, (int) Math.round(baseXP * modifier));
    }
      /**
     * Records an XP event to the database
     * 
     * @param xpEvent The XP event to record
     */
    private void recordXPEvent(XPEvent xpEvent) {
        exceptionHandler.executeWithHandling("recording XP event", () -> {
            try {
                sqlHandler.recordXPEvent(xpEvent.getPlayerUuid(), xpEvent.getEventType(), 
                                       xpEvent.getEventSource(), xpEvent.getXpGained(), 
                                       xpEvent.getTimestamp(), xpEvent.getServerName(), 
                                       xpEvent.getMetadata());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Updates rate limiting tracking for a player
     * 
     * @param playerUuid The UUID of the player
     * @param eventType The type of event
     * @param eventSource The specific source of the event
     */
    private void updateRateLimitingTracking(String playerUuid, String eventType, String eventSource) {
        String rateLimitKey = playerUuid + ":" + eventType + ":" + eventSource;
        lastEventTime.put(rateLimitKey, Instant.now());
        
        // Update event count
        eventCounts.merge(rateLimitKey, 1, Integer::sum);
    }
    
    /**
     * Initializes BlazeAndCaves advancement mappings
     */
    private void initializeBlazeAndCavesMappings() {
        if (!blazeAndCavesEnabled) {
            debugLog("BlazeAndCaves integration disabled");
            return;
        }
        
        // Sample BlazeAndCaves advancements - this would typically be loaded from a config file
        blazeAndCavesMap.put("blazeandcave:overworld/get_wood", 
            new BlazeAndCavesAdvancement("blazeandcave:overworld/get_wood", "Getting Wood", 
                                       "overworld", 10, false, false, "easy", "Get wood"));
        
        blazeAndCavesMap.put("blazeandcave:overworld/stone_age", 
            new BlazeAndCavesAdvancement("blazeandcave:overworld/stone_age", "Stone Age", 
                                       "overworld", 15, false, false, "easy", "Get stone"));
        
        blazeAndCavesMap.put("blazeandcave:nether/enter_nether", 
            new BlazeAndCavesAdvancement("blazeandcave:nether/enter_nether", "Enter the Nether", 
                                       "nether", 25, false, false, "medium", "Enter the Nether"));
        
        blazeAndCavesMap.put("blazeandcave:end/enter_end", 
            new BlazeAndCavesAdvancement("blazeandcave:end/enter_end", "Enter the End", 
                                       "end", 50, false, false, "hard", "Enter the End"));
        
        blazeAndCavesMap.put("blazeandcave:end/kill_dragon", 
            new BlazeAndCavesAdvancement("blazeandcave:end/kill_dragon", "Free the End", 
                                       "end", 100, false, false, "insane", "Kill the Ender Dragon"));
        
        // Terralith variants
        blazeAndCavesMap.put("blazeandcave:terralith/explore_biomes", 
            new BlazeAndCavesAdvancement("blazeandcave:terralith/explore_biomes", "Biome Explorer", 
                                       "terralith", 30, true, false, "medium", "Explore Terralith biomes"));
        
        // Hardcore variants
        blazeAndCavesMap.put("blazeandcave:hardcore/survive_nights", 
            new BlazeAndCavesAdvancement("blazeandcave:hardcore/survive_nights", "Night Survivor", 
                                       "hardcore", 40, false, true, "hard", "Survive nights in hardcore"));
        
        debugLog("Initialized " + blazeAndCavesMap.size() + " BlazeAndCaves advancement mappings");
    }    /**
     * Gets recent XP events for a player
     * 
     * @param playerUuid The UUID of the player
     * @param limit Maximum number of events to retrieve
     * @return CompletableFuture that resolves to a List of XPEvent objects
     */
    public CompletableFuture<List<XPEvent>> getRecentXPEvents(String playerUuid, int limit) {        return exceptionHandler.wrapAsync(
            "getting recent XP events for player " + playerUuid,
            () -> CompletableFuture.supplyAsync(() -> {
                try {
                    return sqlHandler.getRecentXPEvents(playerUuid, limit);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }),
            new ArrayList<>()
        );
    }      /**
     * Gets XP statistics for a player
     * 
     * @param playerUuid The UUID of the player
     * @param days Number of days to look back for statistics
     * @return CompletableFuture that resolves to a Map containing XP statistics
     */    public CompletableFuture<Map<String, Object>> getXPStatistics(String playerUuid, int days) {
        return exceptionHandler.wrapAsync(
            "getting XP statistics for player " + playerUuid,
            () -> CompletableFuture.supplyAsync(() -> {
                try {
                    Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
                    int totalXP = sqlHandler.getPlayerXPSince(playerUuid, since); // This throws SQLException
                    Map<String, Integer> xpBySource = sqlHandler.getPlayerXPBySource(playerUuid, since); // This does NOT throw SQLException
                    Map<String, Integer> dailyXP = sqlHandler.getDailyXPBreakdown(playerUuid, days); // This does NOT throw SQLException
                    
                    Map<String, Object> statistics = new ConcurrentHashMap<>();
                    statistics.put("totalXP", totalXP);
                    statistics.put("xpBySource", xpBySource);
                    statistics.put("dailyXP", dailyXP);
                    statistics.put("days", days);
                    statistics.put("since", since.toString());
                    
                    return statistics;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }),
            new ConcurrentHashMap<>()
        );
    }
      /**
     * Gets XP for a player since a specific time
     * 
     * @param playerUuid The UUID of the player
     * @param since The timestamp to look back from
     * @return CompletableFuture that resolves to the total XP amount since the given time
     */    public CompletableFuture<Integer> getPlayerXPSince(String playerUuid, Instant since) {
        return exceptionHandler.wrapAsync(
            "getting XP since " + since + " for player " + playerUuid,
            () -> CompletableFuture.supplyAsync(() -> {
                try {
                    return sqlHandler.getPlayerXPSince(playerUuid, since);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }),
            0
        );
    }/**
     * Gets XP breakdown by source for a player since a specific time
     * 
     * @param playerUuid The UUID of the player
     * @param since The timestamp to look back from
     * @return CompletableFuture that resolves to a Map of source to XP amount
     */    public CompletableFuture<Map<String, Integer>> getPlayerXPBySource(String playerUuid, Instant since) {
        return exceptionHandler.wrapAsync(
            "getting XP by source for player " + playerUuid,
            () -> CompletableFuture.supplyAsync(() -> {
                return sqlHandler.getPlayerXPBySource(playerUuid, since);
            }),
            new ConcurrentHashMap<>()
        );
    }/**
     * Gets daily XP breakdown for a player
     * 
     * @param playerUuid The UUID of the player
     * @param days Number of days to look back
     * @return CompletableFuture that resolves to a Map of date string to XP amount
     */
    public CompletableFuture<Map<String, Integer>> getDailyXPBreakdown(String playerUuid, int days) {
        return exceptionHandler.wrapAsync(
            "getting daily XP breakdown for player " + playerUuid,
            () -> CompletableFuture.supplyAsync(() -> {
                return sqlHandler.getDailyXPBreakdown(playerUuid, days);
            }),
            new ConcurrentHashMap<>()
        );
    }
      /**
     * getPlayerTotalXP
     * 
     * Retrieves the total XP accumulated by a player using their UUID string.
     * This method queries the database for the player's total XP amount.
     * 
     * @param playerUuid The player's UUID as a string representation
     * @return CompletableFuture<Integer> containing the total XP amount, or 0 if player not found
     */
    public CompletableFuture<Integer> getPlayerTotalXP(String playerUuid) {
        return exceptionHandler.wrapAsync(
            "getting total XP for player " + playerUuid,
            () -> CompletableFuture.supplyAsync(() -> {
                try {
                    return sqlHandler.getPlayerTotalXP(playerUuid);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }),
            0
        );
    }    /**
     * cleanupRateLimitingData
     * 
     * Clears old rate limiting data to prevent memory leaks.
     * Removes entries older than 1 day and resets event counts.
     */
    public void cleanupRateLimitingData() {
        exceptionHandler.executeWithHandling("cleaning up rate limiting data", () -> {
            Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
            lastEventTime.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
            
            // Reset event counts periodically
            eventCounts.clear();
            
            debugLog("Cleaned up rate limiting data");
        });
    }
    
    /**
     * getRateLimitingInfo
     * 
     * Gets rate limiting information for a player.
     * 
     * @param playerUuid The UUID of the player
     * @return Map containing rate limiting status information
     */
    public Map<String, Object> getRateLimitingInfo(String playerUuid) {
        Map<String, Object> info = new ConcurrentHashMap<>();
        
        info.put("rateLimitingEnabled", rateLimitingEnabled);
        info.put("cooldownSeconds", cooldownSeconds);
        info.put("maxEventsPerMinute", maxEventsPerMinute);
        info.put("maxEventsPerHour", maxEventsPerHour);
        info.put("maxEventsPerDay", maxEventsPerDay);
        
        // Get current event counts from memory
        long activeKeys = lastEventTime.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(playerUuid + ":"))
            .filter(entry -> entry.getValue().isAfter(Instant.now().minus(1, ChronoUnit.HOURS)))
            .count();
        
        info.put("activeRateLimitKeys", activeKeys);
        
        return info;
    }
    
    /**
     * debugLog
     * 
     * Logs debug messages if debug mode is enabled.
     * 
     * @param message The message to log
     */
    private void debugLog(String message) {
        LoggingUtils.debugLog(logger, debugEnabled, "[XPManager] " + message);
    }
    
    /**
     * getXPConfiguration
     * 
     * Gets the XP configuration section.
     * 
     * @return The XP configuration map
     */
    public Map<String, Object> getXPConfiguration() {
        return xpSection;
    }
    
    /**
     * isBlazeAndCavesEnabled
     * 
     * Checks if BlazeAndCaves integration is enabled.
     * 
     * @return True if BlazeAndCaves integration is enabled
     */
    public boolean isBlazeAndCavesEnabled() {
        return blazeAndCavesEnabled;
    }
    
    /**
     * getBlazeAndCavesAdvancement
     * 
     * Gets a BlazeAndCaves advancement by its namespaced key.
     * 
     * @param namespacedKey The namespaced key of the advancement
     * @return Optional containing the advancement, or empty if not found
     */
    public java.util.Optional<BlazeAndCavesAdvancement> getBlazeAndCavesAdvancement(String namespacedKey) {
        return java.util.Optional.ofNullable(blazeAndCavesMap.get(namespacedKey));
    }
}
