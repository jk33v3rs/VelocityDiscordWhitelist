package top.jk33v3rs.velocitydiscordwhitelist.modules;

import org.slf4j.Logger;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent;
import top.jk33v3rs.velocitydiscordwhitelist.models.BlazeAndCavesAdvancement;
import top.jk33v3rs.velocitydiscordwhitelist.utils.LoggingUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;

/**
 * XPManager handles XP operations with rate limiting to prevent XP farming.
 */
public class XPManager {
    
    private final SQLHandler sqlHandler;
    private final Logger logger;
    private final boolean debugEnabled;
    private final Map<String, Object> config;
    
    // Rate limiting maps
    private final Map<String, Instant> lastEventTime;
    private final Map<String, Integer> eventCounts;
    private final Map<String, BlazeAndCavesAdvancement> blazeAndCavesMap;
    
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
     */
    @SuppressWarnings("unchecked")
    public XPManager(SQLHandler sqlHandler, Logger logger, boolean debugEnabled, Map<String, Object> config) {
        this.sqlHandler = sqlHandler;
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        this.config = config;
        
        // Initialize rate limiting maps
        this.lastEventTime = new ConcurrentHashMap<>();
        this.eventCounts = new ConcurrentHashMap<>();
        this.blazeAndCavesMap = new ConcurrentHashMap<>();
        
        // Load rate limiting configuration
        Map<String, Object> xpConfig = (Map<String, Object>) config.getOrDefault("xp", new ConcurrentHashMap<>());
        Map<String, Object> rateLimitConfig = (Map<String, Object>) xpConfig.getOrDefault("rateLimiting", new ConcurrentHashMap<>());
        
        this.rateLimitingEnabled = Boolean.parseBoolean(rateLimitConfig.getOrDefault("enabled", "true").toString());
        this.maxEventsPerMinute = Integer.parseInt(rateLimitConfig.getOrDefault("maxEventsPerMinute", "10").toString());
        this.maxEventsPerHour = Integer.parseInt(rateLimitConfig.getOrDefault("maxEventsPerHour", "100").toString());
        this.maxEventsPerDay = Integer.parseInt(rateLimitConfig.getOrDefault("maxEventsPerDay", "500").toString());
        this.cooldownSeconds = Integer.parseInt(rateLimitConfig.getOrDefault("cooldownSeconds", "5").toString());
        
        // Load XP modifier configuration
        Map<String, Object> modifierConfig = (Map<String, Object>) xpConfig.getOrDefault("modifiers", new ConcurrentHashMap<>());
        this.advancementModifier = Double.parseDouble(modifierConfig.getOrDefault("advancement", "1.0").toString());
        this.playtimeModifier = Double.parseDouble(modifierConfig.getOrDefault("playtime", "0.5").toString());
        this.killModifier = Double.parseDouble(modifierConfig.getOrDefault("kill", "0.8").toString());
        this.breakBlockModifier = Double.parseDouble(modifierConfig.getOrDefault("break_block", "0.3").toString());
        this.placeBlockModifier = Double.parseDouble(modifierConfig.getOrDefault("place_block", "0.2").toString());
        this.craftItemModifier = Double.parseDouble(modifierConfig.getOrDefault("craft_item", "0.4").toString());
        this.enchantItemModifier = Double.parseDouble(modifierConfig.getOrDefault("enchant_item", "1.2").toString());
        this.tradeModifier = Double.parseDouble(modifierConfig.getOrDefault("trade", "0.6").toString());
        this.fishingModifier = Double.parseDouble(modifierConfig.getOrDefault("fishing", "0.4").toString());
        this.miningModifier = Double.parseDouble(modifierConfig.getOrDefault("mining", "0.3").toString());
        
        // Load BlazeAndCaves configuration
        Map<String, Object> blazeConfig = (Map<String, Object>) xpConfig.getOrDefault("blazeAndCaves", new ConcurrentHashMap<>());
        this.blazeAndCavesEnabled = Boolean.parseBoolean(blazeConfig.getOrDefault("enabled", "true").toString());
        
        // Load difficulty multipliers
        Map<String, Object> difficultyConfig = (Map<String, Object>) blazeConfig.getOrDefault("difficultyMultipliers", new ConcurrentHashMap<>());
        this.easyDifficultyMultiplier = Double.parseDouble(difficultyConfig.getOrDefault("easy", "1.0").toString());
        this.mediumDifficultyMultiplier = Double.parseDouble(difficultyConfig.getOrDefault("medium", "1.25").toString());
        this.hardDifficultyMultiplier = Double.parseDouble(difficultyConfig.getOrDefault("hard", "1.5").toString());
        this.insaneDifficultyMultiplier = Double.parseDouble(difficultyConfig.getOrDefault("insane", "2.0").toString());
        
        // Load variant bonuses
        Map<String, Object> variantConfig = (Map<String, Object>) blazeConfig.getOrDefault("variantBonuses", new ConcurrentHashMap<>());
        this.terralithBonus = Double.parseDouble(variantConfig.getOrDefault("terralith", "0.1").toString());
        this.hardcoreBonus = Double.parseDouble(variantConfig.getOrDefault("hardcore", "0.5").toString());
        
        // Initialize BlazeAndCaves advancement mappings
        if (blazeAndCavesEnabled) {
            initializeBlazeAndCavesMappings();
        }
        
        LoggingUtils.debugLog(logger, debugEnabled, "XPManager initialized with rate limiting " + (rateLimitingEnabled ? "enabled" : "disabled"));
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
     */
    public CompletableFuture<Boolean> processXPGain(String playerUuid, String eventType, String eventSource, 
                                                   int baseXP, String serverName, String metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
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
                
            } catch (Exception e) {
                logger.error("Error processing XP gain for player " + playerUuid, e);
                return false;
            }
        });
    }
    
    /**
     * Checks if an XP gain event passes rate limiting rules
     * 
     * @param playerUuid The UUID of the player
     * @param eventType The type of event
     * @param eventSource The specific source of the event
     * @return True if the event passes rate limiting, false otherwise
     */
    private boolean passesRateLimiting(String playerUuid, String eventType, String eventSource) {
        String rateLimitKey = playerUuid + ":" + eventType + ":" + eventSource;
        Instant now = Instant.now();
        
        // Check cooldown period (prevents rapid-fire farming)
        Instant lastEvent = lastEventTime.get(rateLimitKey);
        if (lastEvent != null) {
            long secondsSinceLastEvent = ChronoUnit.SECONDS.between(lastEvent, now);
            if (secondsSinceLastEvent < cooldownSeconds) {
                debugLog("Rate limit: Cooldown not met for " + rateLimitKey + " (" + secondsSinceLastEvent + "s < " + cooldownSeconds + "s)");
                return false;
            }
        }
        
        // Check database for recent events (more comprehensive check)
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
            
        } catch (Exception e) {
            logger.error("Error checking rate limiting for " + rateLimitKey, e);
            // Fail safe - allow the event if we can't check the database
            return true;
        }
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
        
        return (int) (baseXP * modifier);
    }
    
    /**
     * Records an XP event to the database
     * 
     * @param xpEvent The XP event to record
     */
    private void recordXPEvent(XPEvent xpEvent) {
        try {
            sqlHandler.saveXPEvent(xpEvent);
            debugLog("XP event recorded: " + xpEvent.toString());
        } catch (Exception e) {
            logger.error("Failed to record XP event: " + xpEvent.toString(), e);
        }
    }
    
    /**
     * Updates rate limiting tracking data
     * 
     * @param playerUuid The UUID of the player
     * @param eventType The type of event
     * @param eventSource The specific source of the event
     */
    private void updateRateLimitingTracking(String playerUuid, String eventType, String eventSource) {
        String rateLimitKey = playerUuid + ":" + eventType + ":" + eventSource;
        lastEventTime.put(rateLimitKey, Instant.now());
        eventCounts.merge(rateLimitKey, 1, Integer::sum);
    }
    
    /**
     * Initializes the BlazeAndCaves advancement mappings
     * This method loads all BlazeAndCaves 1.21 advancements with their XP values
     */
    private void initializeBlazeAndCavesMappings() {
        LoggingUtils.debugLog(logger, debugEnabled, "Initializing BlazeAndCaves advancement mappings for 1.21");
        
        // Load from configuration if available, otherwise use defaults
        Map<String, Object> xpSection;
        Object xpObj = config.getOrDefault("xp", new ConcurrentHashMap<>());
        if (xpObj instanceof Map<?, ?>) {
            xpSection = (Map<String, Object>) xpObj;
        } else {
            xpSection = new ConcurrentHashMap<>();
        }
        Map<String, Object> blazeConfig = (Map<String, Object>) xpSection.getOrDefault("blazeAndCaves", new ConcurrentHashMap<>());
        
        Map<String, Object> advancements = (Map<String, Object>) blazeConfig.getOrDefault("advancements", new ConcurrentHashMap<>());
        
        if (advancements.isEmpty()) {
            // Load default BlazeAndCaves 1.21 mappings
            loadDefaultBlazeAndCavesMappings();
        } else {
            // Load from configuration
            loadBlazeAndCavesFromConfig(advancements);
        }
        
        LoggingUtils.debugLog(logger, debugEnabled, "Loaded " + blazeAndCavesMap.size() + " BlazeAndCaves advancement mappings");
    }
    
    /**
     * Loads default BlazeAndCaves 1.21 advancement mappings
     * This includes the most common and important advancements from the datapack
     */
    private void loadDefaultBlazeAndCavesMappings() {
        // Sample of important BlazeAndCaves advancements - in production this would be a complete list
        List<BlazeAndCavesAdvancement> defaultAdvancements = new ArrayList<>();
        
        // Easy advancements (10-25 XP)
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:overworld/wooden_tools", "Wooden Tools", "overworld", 10, false, false, "easy", "Craft all wooden tools"));
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:overworld/stone_tools", "Stone Tools", "overworld", 15, false, false, "easy", "Craft all stone tools"));
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:overworld/iron_tools", "Iron Tools", "overworld", 20, false, false, "easy", "Craft all iron tools"));
        
        // Medium advancements (25-50 XP)
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:overworld/diamond_tools", "Diamond Tools", "overworld", 35, false, false, "medium", "Craft all diamond tools"));
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:overworld/netherite_tools", "Netherite Tools", "overworld", 50, false, false, "medium", "Craft all netherite tools"));
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:overworld/master_trader", "Master Trader", "overworld", 40, false, false, "medium", "Trade with every villager profession"));
        
        // Hard advancements (50-100 XP)
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:end/elytra_superhero", "Elytra Superhero", "end", 75, false, false, "hard", "Fly 10000 blocks with elytra"));
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:nether/wither_boss", "Wither Boss", "nether", 80, false, false, "hard", "Defeat the Wither"));
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:end/dragon_slayer", "Dragon Slayer", "end", 90, false, false, "hard", "Defeat the Ender Dragon"));
        
        // Insane advancements (100+ XP)
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:end/the_end_complete", "The End Complete", "end", 150, false, false, "insane", "Complete all End advancements"));
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:overworld/all_blocks", "All Blocks", "overworld", 200, false, false, "insane", "Collect every block in the game"));
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:overworld/perfectionist", "Perfectionist", "overworld", 500, false, false, "insane", "Complete every advancement"));
        
        // Terralith-specific advancements (10% bonus)
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:terralith/biome_explorer", "Biome Explorer", "terralith", 60, true, false, "medium", "Visit all Terralith biomes"));
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:terralith/structure_hunter", "Structure Hunter", "terralith", 45, true, false, "medium", "Find all Terralith structures"));
        
        // Hardcore variants (50% bonus)
        defaultAdvancements.add(new BlazeAndCavesAdvancement("blazeandcave:hardcore/hardcore_master", "Hardcore Master", "hardcore", 100, false, true, "insane", "Complete hardcore challenges"));
        
        // Add all advancements to the map
        for (BlazeAndCavesAdvancement advancement : defaultAdvancements) {
            blazeAndCavesMap.put(advancement.getNamespacedKey(), advancement);
        }
    }
    
    /**
     * Loads BlazeAndCaves advancement mappings from configuration
     * 
     * @param advancements The advancement configuration map
     */
    @SuppressWarnings("unchecked")
    private void loadBlazeAndCavesFromConfig(Map<String, Object> advancements) {
        for (Map.Entry<String, Object> entry : advancements.entrySet()) {
            try {
                String namespacedKey = entry.getKey();
                Map<String, Object> advConfig = (Map<String, Object>) entry.getValue();
                
                String displayName = advConfig.getOrDefault("displayName", namespacedKey).toString();
                String category = advConfig.getOrDefault("category", "overworld").toString();
                int baseXP = Integer.parseInt(advConfig.getOrDefault("baseXP", "10").toString());
                boolean isTerralith = Boolean.parseBoolean(advConfig.getOrDefault("isTerralithVariant", "false").toString());
                boolean isHardcore = Boolean.parseBoolean(advConfig.getOrDefault("isHardcoreVariant", "false").toString());
                String difficulty = advConfig.getOrDefault("difficulty", "easy").toString();
                String description = advConfig.getOrDefault("description", "").toString();
                
                BlazeAndCavesAdvancement advancement = new BlazeAndCavesAdvancement(
                    namespacedKey, displayName, category, baseXP, isTerralith, isHardcore, difficulty, description
                );
                
                blazeAndCavesMap.put(namespacedKey, advancement);
                
            } catch (Exception e) {
                logger.error("Failed to load BlazeAndCaves advancement: " + entry.getKey(), e);
            }
        }
    }
    
    /**
     * Gets the total XP for a player
     * 
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that resolves to the player's total XP
     */
    public CompletableFuture<Integer> getPlayerTotalXP(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sqlHandler.getPlayerTotalXP(playerUuid);
            } catch (Exception e) {
                logger.error("Error getting total XP for player " + playerUuid, e);
                return 0;
            }
        });
    }
    
    /**
     * Gets recent XP events for a player
     * 
     * @param playerUuid The UUID of the player
     * @param limit The maximum number of events to return
     * @return CompletableFuture that resolves to a list of recent XP events
     */
    public CompletableFuture<List<XPEvent>> getRecentXPEvents(String playerUuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sqlHandler.getRecentXPEvents(playerUuid, limit);
            } catch (Exception e) {
                logger.error("Error getting recent XP events for player " + playerUuid, e);
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * Clears rate limiting data older than 24 hours
     * This method should be called periodically to prevent memory leaks
     */
    public void cleanupRateLimitingData() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
        lastEventTime.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        debugLog("Cleaned up old rate limiting data");
    }
    
    /**
     * Gets a BlazeAndCaves advancement by its namespaced key
     * 
     * @param namespacedKey The namespaced key of the advancement
     * @return The BlazeAndCaves advancement, or null if not found
     */
    public BlazeAndCavesAdvancement getBlazeAndCavesAdvancement(String namespacedKey) {
        return blazeAndCavesMap.get(namespacedKey);
    }
    
    /**
     * Logs a debug message if debug mode is enabled
     * 
     * @param message The message to log
     */
    private void debugLog(String message) {
        if (debugEnabled) {
            logger.info("[XPManager] " + message);
        }
    }
}
