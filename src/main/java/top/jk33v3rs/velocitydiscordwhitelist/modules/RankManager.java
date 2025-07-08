package top.jk33v3rs.velocitydiscordwhitelist.modules;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.discord.DiscordHandler;
import top.jk33v3rs.velocitydiscordwhitelist.integrations.LuckPermsIntegration;
import top.jk33v3rs.velocitydiscordwhitelist.models.PlayerRank;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;

/**
 * RankManager
 * 
 * Lean implementation of the rank progression system with exactly 32 rank combinations.
 * Manages progression through 25 main ranks × 7 sub-ranks = 175 total rank positions.
 * 
 * Main Ranks (25): bystander → onlooker → wanderer → ... → deity
 * Sub-Ranks (7): Novice → Apprentice → Adept → Master → Heroic → Mythic → Immortal
 * 
 * Progression: Complete sub-rank 7 (Immortal) → advance to next main rank sub-rank 1 (Novice)
 * Starting: bystander.1 (Novice), Final: deity.7 (Immortal)
 */
public class RankManager {
    
    private final Logger logger;
    private final SQLHandler sqlHandler;
    private final DiscordHandler discordHandler;
    private final LuckPermsIntegration luckPermsIntegration;
    private final ExceptionHandler exceptionHandler;
    private final boolean debugEnabled;
    
    // 25 Main Ranks in exact progression order
    private static final List<String> MAIN_RANKS = Arrays.asList(
        "bystander", "onlooker", "wanderer", "traveller", "explorer",
        "adventurer", "surveyor", "navigator", "journeyman", "pathfinder",
        "trailblazer", "pioneer", "craftsman", "specialist", "artisan",
        "veteran", "sage", "luminary", "titan", "legend",
        "eternal", "ascendant", "celestial", "divine", "deity"
    );
    
    // 7 Sub-Ranks in exact progression order
    private static final List<String> SUB_RANKS = Arrays.asList(
        "Novice", "Apprentice", "Adept", "Master", "Heroic", "Mythic", "Immortal"
    );
    
    // Starting rank: bystander.1 (Novice)
    private static final int STARTING_MAIN_RANK = 1;
    private static final int STARTING_SUB_RANK = 1;
    
    // Final rank: deity.7 (Immortal)  
    private static final int FINAL_MAIN_RANK = 25;
    private static final int FINAL_SUB_RANK = 7;
    
    /**
     * Constructor for RankManager
     * 
     * @param logger The logger instance
     * @param sqlHandler The SQL handler for database operations
     * @param discordHandler The Discord handler for role management (optional)
     * @param luckPermsIntegration The LuckPerms integration (optional)
     * @param exceptionHandler The exception handler
     * @param debugEnabled Whether debug logging is enabled
     */
    public RankManager(Logger logger, SQLHandler sqlHandler, DiscordHandler discordHandler,
                      LuckPermsIntegration luckPermsIntegration, ExceptionHandler exceptionHandler,
                      boolean debugEnabled) {
        this.logger = logger;
        this.sqlHandler = sqlHandler;
        this.discordHandler = discordHandler;
        this.luckPermsIntegration = luckPermsIntegration;
        this.exceptionHandler = exceptionHandler;
        this.debugEnabled = debugEnabled;
        
        debugLog("RankManager initialized with " + MAIN_RANKS.size() + " main ranks and " + 
                SUB_RANKS.size() + " sub-ranks (" + (MAIN_RANKS.size() * SUB_RANKS.size()) + " total combinations)");
    }
    
    /**
     * getPlayerRank
     * 
     * Gets the current rank for a player.
     * 
     * @param playerUuid The player's UUID
     * @return CompletableFuture<PlayerRank> The player's current rank
     */
    public CompletableFuture<PlayerRank> getPlayerRank(String playerUuid) {
        return sqlHandler.getPlayerRank(playerUuid).thenApply(optionalRank -> {
            if (optionalRank.isPresent()) {
                return optionalRank.get();
            } else {
                // Return starting rank for new players
                return createStartingRank(playerUuid);
            }
        });
    }
    
    /**
     * promotePlayer
     * 
     * Promotes a player to the next rank in the progression system.
     * 
     * @param playerUuid The player's UUID
     * @param reason The reason for the promotion
     * @return CompletableFuture<Boolean> true if promotion was successful
     */
    public CompletableFuture<Boolean> promotePlayer(String playerUuid, String reason) {
        return getPlayerRank(playerUuid).thenCompose(currentRank -> {
            try {
                // Calculate next rank
                final int[] nextRank = calculateNextRank(currentRank.getMainRank(), currentRank.getSubRank());
                final int nextMainRank = nextRank[0];
                final int nextSubRank = nextRank[1];
                
                // Check if player is already at final rank
                if (nextMainRank > FINAL_MAIN_RANK) {
                    debugLog("Player " + playerUuid + " is already at final rank (deity.7)");
                    return CompletableFuture.completedFuture(false);
                }
                
                // Create new rank
                PlayerRank newRank = new PlayerRank(
                    playerUuid,
                    nextMainRank,
                    nextSubRank,
                    currentRank.getJoinDate(),
                    currentRank.getPlayTimeMinutes(),
                    currentRank.getAchievementsCompleted(),
                    java.time.Instant.now() // Update last promotion time
                );
                
                // Save to database
                return sqlHandler.savePlayerRank(newRank).thenCompose(success -> {
                    if (success) {
                        debugLog("Promoted " + playerUuid + " to " + formatRank(nextMainRank, nextSubRank) + 
                               " (reason: " + reason + ")");
                        
                        // Update external systems
                        return updateExternalSystems(playerUuid, newRank).thenApply(updateSuccess -> {
                            return success; // Return original save success
                        });
                    } else {
                        logger.error("Failed to save rank promotion for " + playerUuid);
                        return CompletableFuture.completedFuture(false);
                    }
                });
                
            } catch (Exception e) {
                logger.error("Error promoting player " + playerUuid, e);
                exceptionHandler.handleIntegrationException("RankManager", "player promotion", e);
                return CompletableFuture.completedFuture(false);
            }
        });
    }
    
    /**
     * formatRank
     * 
     * Formats a rank into human-readable string (e.g., "bystander Novice", "deity Immortal").
     * 
     * @param mainRank The main rank number (1-25)
     * @param subRank The sub-rank number (1-7)
     * @return Formatted rank string
     */
    public String formatRank(int mainRank, int subRank) {
        if (mainRank < 1 || mainRank > MAIN_RANKS.size() || subRank < 1 || subRank > SUB_RANKS.size()) {
            return "Unknown Rank";
        }
        
        String mainRankName = MAIN_RANKS.get(mainRank - 1);
        String subRankName = SUB_RANKS.get(subRank - 1);
        
        return mainRankName + " " + subRankName;
    }
    
    /**
     * getRankProgress
     * 
     * Gets progress information for a player's current rank.
     * 
     * @param playerRank The player's current rank
     * @return String describing rank progress
     */
    public String getRankProgress(PlayerRank playerRank) {
        int mainRank = playerRank.getMainRank();
        int subRank = playerRank.getSubRank();
        
        // Calculate total progress through all ranks
        int totalRanksCompleted = ((mainRank - 1) * SUB_RANKS.size()) + (subRank - 1);
        int totalPossibleRanks = MAIN_RANKS.size() * SUB_RANKS.size();
        
        double progressPercent = (double) totalRanksCompleted / totalPossibleRanks * 100;
        
        return String.format("Rank %d/%d (%.1f%% complete)", 
                           totalRanksCompleted + 1, totalPossibleRanks, progressPercent);
    }
    
    /**
     * createStartingRank
     * 
     * Creates a starting rank for a new player (bystander.1 Novice).
     * 
     * @param playerUuid The player's UUID
     * @return PlayerRank starting rank
     */
    private PlayerRank createStartingRank(String playerUuid) {
        return new PlayerRank(
            playerUuid,
            STARTING_MAIN_RANK,
            STARTING_SUB_RANK,
            java.time.Instant.now(),
            0, // No playtime yet
            0, // No achievements yet
            java.time.Instant.now()
        );
    }
    
    /**
     * updateExternalSystems
     * 
     * Updates LuckPerms and Discord roles when a player's rank changes.
     * 
     * @param playerUuid The player's UUID
     * @param newRank The player's new rank
     * @return CompletableFuture<Boolean> true if updates were successful
     */
    private CompletableFuture<Boolean> updateExternalSystems(String playerUuid, PlayerRank newRank) {
        final CompletableFuture<Boolean> luckPermsUpdate;
        final CompletableFuture<Boolean> discordUpdate = CompletableFuture.completedFuture(true);
        
        // Update LuckPerms if available
        if (luckPermsIntegration != null) {
            String rankName = MAIN_RANKS.get(newRank.getMainRank() - 1);
            luckPermsUpdate = luckPermsIntegration.setPlayerPrimaryGroup(UUID.fromString(playerUuid), rankName);
        } else {
            luckPermsUpdate = CompletableFuture.completedFuture(true);
        }
        
        // Update Discord roles if available
        if (discordHandler != null) {
            // Discord role updates would be implemented here
            // For now, just return successful
        }
        
        return CompletableFuture.allOf(luckPermsUpdate, discordUpdate)
            .thenApply(v -> luckPermsUpdate.join() && discordUpdate.join());
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
            logger.info("[DEBUG] RankManager: " + message);
        }
    }
    
    /**
     * calculateNextRank
     * 
     * Calculates the next rank in progression.
     * 
     * @param currentMainRank Current main rank (1-25)
     * @param currentSubRank Current sub-rank (1-7)
     * @return int array [nextMainRank, nextSubRank]
     */
    private int[] calculateNextRank(int currentMainRank, int currentSubRank) {
        int nextMainRank = currentMainRank;
        int nextSubRank = currentSubRank + 1;
        
        // Check if we need to advance to next main rank
        if (nextSubRank > FINAL_SUB_RANK) {
            nextSubRank = 1; // Reset to Novice
            nextMainRank++; // Advance main rank
        }
        
        return new int[]{nextMainRank, nextSubRank};
    }

    /**
     * Gets the list of all main ranks
     * 
     * @return List of main rank names
     */
    public static List<String> getMainRanks() {
        return MAIN_RANKS;
    }
    
    /**
     * Gets the list of all sub-ranks
     * 
     * @return List of sub-rank names
     */
    public static List<String> getSubRanks() {
        return SUB_RANKS;
    }
}
