package top.jk33v3rs.velocitydiscordwhitelist.modules;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.velocitypowered.api.proxy.ProxyServer;

import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.integrations.LuckPermsIntegration;
import top.jk33v3rs.velocitydiscordwhitelist.integrations.VaultIntegration;
import top.jk33v3rs.velocitydiscordwhitelist.models.PlayerRank;
import top.jk33v3rs.velocitydiscordwhitelist.models.RankDefinition;
import top.jk33v3rs.velocitydiscordwhitelist.models.RankRewards;
import top.jk33v3rs.velocitydiscordwhitelist.utils.LoggingUtils;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;


/**
 * RewardsHandler manages player rank and reward operations.
 */
public class RewardsHandler {    private final SQLHandler sqlHandler;
    private final DiscordBotHandler discordBotHandler;
    private final ProxyServer server;
    private final Logger logger;
    private final boolean debugEnabled;
    private final ExceptionHandler exceptionHandler;
    private final Map<String, PlayerRank> playerRanksCache = new ConcurrentHashMap<>();
    private final Map<Integer, RankDefinition> rankDefinitionsCache = new ConcurrentHashMap<>();
    private final boolean rewardsEnabled;
    private final boolean ranksEnabled;
    private final VaultIntegration vaultIntegration;
      /**
     * Constructor for RewardsHandler
     *
     * @param sqlHandler The SQL handler for database operations
     * @param discordBotHandler The Discord bot handler for role management
     * @param server The ProxyServer instance for command execution
     * @param logger The logger instance
     * @param debugEnabled Whether debug logging is enabled
     * @param config The main configuration map
     * @param vaultIntegration The Vault integration (can be null)
     * @param luckPermsIntegration The LuckPerms integration (can be null)
     */    public RewardsHandler(SQLHandler sqlHandler, DiscordBotHandler discordBotHandler, ProxyServer server, Logger logger, 
                         boolean debugEnabled, Map<String, Object> config, 
                         VaultIntegration vaultIntegration, LuckPermsIntegration luckPermsIntegration) {
        this.sqlHandler = sqlHandler;
        this.discordBotHandler = discordBotHandler;
        this.server = server;
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        this.exceptionHandler = new ExceptionHandler(logger, debugEnabled);
        this.vaultIntegration = vaultIntegration;
        
        // Parse configuration settings
        @SuppressWarnings("unchecked")
        Map<String, Object> rewardsConfig = (Map<String, Object>) config.getOrDefault("rewards", new java.util.HashMap<>());
        this.rewardsEnabled = Boolean.parseBoolean(rewardsConfig.getOrDefault("enabled", "true").toString());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> ranksConfig = (Map<String, Object>) config.getOrDefault("ranks", new java.util.HashMap<>());
        this.ranksEnabled = Boolean.parseBoolean(ranksConfig.getOrDefault("enabled", "true").toString());
        
        // Initialize rank definitions after all fields are set
        initializeRankDefinitions();
    }
    /**
     * initializeRankDefinitions
     * Initializes rank definitions in a way that's safe to call from constructor.
     * This method is private and final to prevent override issues.
     */
    private void initializeRankDefinitions() {
        try {
            this.loadRankDefinitions();        } catch (RuntimeException e) {
            exceptionHandler.handleIntegrationException("RewardsHandler", "rank definitions initialization", e);
        }
    }
    
    /**
     * Loads rank definitions from the database into the cache
     * 
     * This method populates the rankDefinitionsCache with all rank definitions
     * from the database. It should be called at initialization and whenever 
     * the rank definitions are updated.
     */
    public void loadRankDefinitions() {
        try {
            List<RankDefinition> definitions = sqlHandler.getAllRankDefinitions();
            rankDefinitionsCache.clear();
            
            for (RankDefinition def : definitions) {
                // Create a composite key based on main_rank and sub_rank
                int key = (def.getMainRank() * 100) + def.getSubRank();
                rankDefinitionsCache.put(key, def);
            }
            
            LoggingUtils.debugLog(logger, debugEnabled, "Loaded " + definitions.size() + " rank definitions");
        } catch (Exception e) {
            logger.error("Failed to load rank definitions", e);
        }
    }
    
    /**
     * Gets a player's current rank information
     * 
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that resolves to the player's rank information
     */
    public CompletableFuture<PlayerRank> getPlayerRank(String playerUuid) {
        try {
            checkRanksEnabled();
            // Check cache first
            if (playerRanksCache.containsKey(playerUuid)) {
                return CompletableFuture.completedFuture(playerRanksCache.get(playerUuid));
            }
            
            CompletableFuture<PlayerRank> future = new CompletableFuture<>();
            
            // Get player rank from database asynchronously
            sqlHandler.getPlayerRank(playerUuid).thenAccept(optionalRank -> {
                try {
                    if (optionalRank.isPresent()) {
                        PlayerRank rank = optionalRank.get();
                        
                        // Validate and correct rank data to ensure it follows the official subrank progression
                        if (!rank.validateAndCorrectRankData()) {
                            debugLog("Invalid rank data for player " + playerUuid + ", using default rank");
                            // Create default rank for players with completely invalid data
                            PlayerRank correctedRank = new PlayerRank(
                                playerUuid,
                                1, // Default main rank (bystander - first in official progression)
                                1, // Default sub rank (novice)
                                rank.getJoinDate() != null ? rank.getJoinDate() : Instant.now(),
                                rank.getPlayTimeMinutes(),
                                rank.getAchievementsCompleted(),
                                rank.getLastPromotion()
                            );
                            // Save the corrected rank
                            sqlHandler.savePlayerRank(correctedRank).thenAccept(success -> {
                                if (success) {
                                    playerRanksCache.put(playerUuid, correctedRank);
                                    future.complete(correctedRank);
                                } else {
                                    logger.error("Failed to save corrected rank for " + playerUuid);
                                    future.completeExceptionally(new RuntimeException("Failed to save corrected rank"));
                                }
                            }).exceptionally(ex -> {
                                logger.error("Error saving corrected rank for " + playerUuid, ex);
                                future.completeExceptionally(ex);
                                return null;
                            });
                        } else {
                            playerRanksCache.put(playerUuid, rank);
                            future.complete(rank);
                        }
                    } else {
                        // No rank found, create default with official subrank system
                        PlayerRank defaultRank = new PlayerRank(
                            playerUuid,
                            1, // Default main rank (bystander - first in official progression)
                            1, // Default sub rank (novice - first in progression)
                            Instant.now(),
                            0, // Default play time
                            0, // Default achievements
                            null // No last promotion
                        );
                        
                        // Save the default rank to database
                        sqlHandler.savePlayerRank(defaultRank).thenAccept(success -> {
                            if (success) {
                                playerRanksCache.put(playerUuid, defaultRank);
                                future.complete(defaultRank);
                            } else {
                                logger.error("Failed to save default player rank for " + playerUuid);
                                future.completeExceptionally(new RuntimeException("Failed to save default rank"));
                            }
                        }).exceptionally(ex -> {
                            logger.error("Error saving default player rank for " + playerUuid, ex);
                            future.completeExceptionally(ex);
                            return null;
                        });
                    }
                } catch (Exception e) {
                    logger.error("Error processing player rank result for " + playerUuid, e);
                    future.completeExceptionally(e);
                }
            }).exceptionally(e -> {
                logger.error("Failed to get player rank for " + playerUuid, e);
                future.completeExceptionally(e);
                return null;
            });
            
            return future;
        } catch (IllegalStateException e) {
            CompletableFuture<PlayerRank> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
    
    /**
     * Adds experience points to a player and handles potential rank progression
     * 
     * @param playerUuid The UUID of the player
     * @param xpAmount The amount of XP to add
     * @param source The source of the XP (chat, voice, achievement, etc)
     * @return CompletableFuture that resolves to true if the player ranked up, false otherwise
     */
    public CompletableFuture<Boolean> addPlayerXp(String playerUuid, int xpAmount, String source) {
        try {
            checkRanksEnabled();
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            
            getPlayerRank(playerUuid).thenAccept(playerRank -> {
                try {
                    // Log XP gain in database
                    sqlHandler.logXpGain(playerUuid, xpAmount, source);
                    
                    // Get current rank requirements
                    RankDefinition currentRank = getRankDefinition(playerRank.getMainRank(), playerRank.getSubRank());
                    if (currentRank == null) {
                        logger.error("Could not find rank definition for player " + playerUuid + 
                            " with main rank " + playerRank.getMainRank() + 
                            " and sub rank " + playerRank.getSubRank());
                        future.complete(false);
                        return;
                    }
                    
                    // Add the XP and check for rank up
                    int newPlayTime = playerRank.getPlayTimeMinutes() + xpAmount;
                    playerRank.setPlayTimeMinutes(newPlayTime);
                    
                    // Check for rank progression
                    boolean didRankUp = checkAndProcessRankProgression(playerRank);
                    
                    // Update the player rank in database
                    sqlHandler.savePlayerRank(playerRank);
                    
                    // Update cache
                    playerRanksCache.put(playerUuid, playerRank);
                    
                    future.complete(didRankUp);                } catch (Exception e) {
                    exceptionHandler.handleIntegrationException("RewardsHandler", "adding XP for player " + playerUuid, e);
                    future.completeExceptionally(e);
                }
            }).exceptionally(e -> {
                exceptionHandler.handleIntegrationException("RewardsHandler", "getting player rank for XP addition", e);
                future.completeExceptionally(e);
                return null;
            });
            
            return future;
        } catch (IllegalStateException e) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.complete(false);
            return future;
        }
    }
    
    /**
     * Increments achievement count for a player and handles potential rank progression
     * 
     * @param playerUuid The UUID of the player
     * @param achievementName The name of the achievement earned
     * @return CompletableFuture that resolves to true if the player ranked up, false otherwise
     */
    public CompletableFuture<Boolean> addPlayerAchievement(String playerUuid, String achievementName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        getPlayerRank(playerUuid).thenAccept(playerRank -> {
            try {
                // Log achievement in database
                sqlHandler.logAchievement(playerUuid, achievementName);
                
                // Increment achievement count
                int newAchievements = playerRank.getAchievementsCompleted() + 1;
                playerRank.setAchievementsCompleted(newAchievements);
                
                // Check for rank progression
                boolean didRankUp = checkAndProcessRankProgression(playerRank);
                
                // Update the player rank in database
                sqlHandler.savePlayerRank(playerRank);
                
                // Update cache
                playerRanksCache.put(playerUuid, playerRank);
                
                future.complete(didRankUp);
            } catch (Exception e) {
                logger.error("Failed to add achievement for player " + playerUuid, e);
                future.completeExceptionally(e);
            }
        }).exceptionally(e -> {
            logger.error("Error getting player rank for achievement addition", e);
            future.completeExceptionally(e);
            return null;
        });
        
        return future;
    }
    
    /**
     * Checks if a player meets the requirements for rank progression and processes if needed
     * 
     * @param playerRank The player's current rank info
     * @return true if the player ranked up, false otherwise
     */
    private boolean checkAndProcessRankProgression(PlayerRank playerRank) {
        // Get current rank definition
        RankDefinition currentRank = getRankDefinition(playerRank.getMainRank(), playerRank.getSubRank());
        if (currentRank == null) {
            logger.error("Could not find current rank definition for rank progression check");
            return false;
        }
        
        // Check if player meets requirements for next rank
        boolean meetsTimeRequirement = playerRank.getPlayTimeMinutes() >= currentRank.getRequiredTimeMinutes();
        boolean meetsAchievementRequirement = playerRank.getAchievementsCompleted() >= currentRank.getRequiredAchievements();
        
        if (meetsTimeRequirement && meetsAchievementRequirement) {
            // Determine next rank
            RankDefinition nextRank = getNextRank(playerRank.getMainRank(), playerRank.getSubRank());
            if (nextRank == null) {
                // Player is at max rank
                debugLog("Player " + playerRank.getPlayerUuid() + " is already at max rank");
                return false;
            }
            
            // Apply promotion
            playerRank.setMainRank(nextRank.getMainRank());
            playerRank.setSubRank(nextRank.getSubRank());
            playerRank.setLastPromotion(Instant.now());
            
            // Log promotion in database
            try {
                sqlHandler.logRankPromotion(
                    playerRank.getPlayerUuid(), 
                    currentRank.getMainRank(), 
                    currentRank.getSubRank(),
                    nextRank.getMainRank(), 
                    nextRank.getSubRank(),
                    "Automatic progression"
                );
                
                // Process rank progression rewards
                processRankProgressionRewards(playerRank.getPlayerUuid(), nextRank.getMainRank(), nextRank.getSubRank(), currentRank.getSubRank() != nextRank.getSubRank());
                
                debugLog("Player " + playerRank.getPlayerUuid() + " promoted from " + 
                    RankDefinition.formatRankDisplay(currentRank.getSubRank(), currentRank.getMainRankName()) + 
                    " to " + RankDefinition.formatRankDisplay(nextRank.getSubRank(), nextRank.getMainRankName()) + 
                    " (" + nextRank.getMainRank() + "." + nextRank.getSubRank() + ")");
                
                return true;
            } catch (Exception e) {
                logger.error("Failed to log rank promotion", e);
            }
        }
        
        return false;
    }
    
    /**
     * Synchronizes roles for a player based on their current rank
     * 
     * @param playerUuid The UUID of the player
     * @param discordId The Discord ID of the player
     * @return CompletableFuture that resolves to true if sync was successful
     */
    public CompletableFuture<Boolean> syncPlayerRoles(String playerUuid, String discordId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        getPlayerRank(playerUuid).thenAccept(playerRank -> {
            try {
                // Check if the Discord guild is available through the handler
                if (discordBotHandler.getGuild() == null) {
                    logger.error("Cannot sync roles - Discord guild is not available");
                    future.complete(false);
                    return;
                }
                
                RankDefinition rankDef = getRankDefinition(playerRank.getMainRank(), playerRank.getSubRank());
                if (rankDef == null) {
                    String formattedRank = RankDefinition.formatRankDisplay(playerRank.getSubRank(), "rank" + playerRank.getMainRank());
                    logger.error("Cannot sync roles - Rank definition not found for " + formattedRank + 
                        " (" + playerRank.getMainRank() + "." + playerRank.getSubRank() + ")");
                    future.complete(false);
                    return;
                }
                
                // Get verification state for player
                Optional<String> verificationState = sqlHandler.getPlayerVerificationState(playerUuid);
                String state = verificationState.orElse("UNVERIFIED");
                
                // Convert Discord ID string to long safely
                try {
                    long discordUserId = Long.parseLong(discordId);
                    
                    // Properly delegate to DiscordBotHandler for all Discord operations
                    CompletableFuture<Boolean> updateResult = discordBotHandler.updateMemberRoles(
                        discordUserId,
                        state,
                        String.valueOf(rankDef.getDiscordRoleId())
                    );
                    
                    updateResult.thenAccept(success -> {
                        if (success) {
                            debugLog("Updated roles for player " + playerUuid + " with state " + state);
                            future.complete(true);
                        } else {
                            logger.error("Failed to update roles for player " + playerUuid);
                            future.complete(false);
                        }
                    }).exceptionally(e -> {
                        logger.error("Error during role update", e);
                        future.complete(false);
                        return null;
                    });
                } catch (NumberFormatException e) {
                    logger.error("Invalid Discord ID format: " + discordId, e);
                    future.complete(false);
                }
            } catch (Exception e) {
                logger.error("Error during role sync for player " + playerUuid, e);
                future.complete(false);
            }
        }).exceptionally(e -> {
            logger.error("Error getting player rank for role sync", e);
            future.complete(false);
            return null;
        });
        
        return future;
    }
    
    /**
     * Gets a rank definition by main rank and sub rank
     * 
     * @param mainRank The main rank ID
     * @param subRank The sub rank ID
     * @return The rank definition or null if not found
     */
    public RankDefinition getRankDefinition(int mainRank, int subRank) {
        int key = (mainRank * 100) + subRank;
        return rankDefinitionsCache.getOrDefault(key, null);
    }
    
    /**
     * Calculates the required play time in minutes for a specific rank
     * 
     * @param mainRank The main rank ID to check requirements for
     * @param subRank The sub rank ID to check requirements for
     * @return The required play time in minutes, or 0 if rank definition not found
     */
    public int calculateRequiredPlayTime(int mainRank, int subRank) {
        try {
            checkRanksEnabled();
            RankDefinition rankDef = getRankDefinition(mainRank, subRank);
            if (rankDef != null) {
                return rankDef.getRequiredTimeMinutes();
            } else {
                logger.warn("No rank definition found for main rank " + mainRank + ", sub rank " + subRank);
                return 0;
            }
        } catch (IllegalStateException e) {
            logger.debug("Ranks system is disabled, returning 0 for required play time");
            return 0;
        }
    }
    
    /**
     * Calculates the required number of achievements for a specific rank
     * 
     * @param mainRank The main rank ID to check requirements for
     * @param subRank The sub rank ID to check requirements for
     * @return The required number of achievements, or 0 if rank definition not found
     */
    public int calculateRequiredAchievements(int mainRank, int subRank) {
        try {
            checkRanksEnabled();
            RankDefinition rankDef = getRankDefinition(mainRank, subRank);
            if (rankDef != null) {
                return rankDef.getRequiredAchievements();
            } else {
                logger.warn("No rank definition found for main rank " + mainRank + ", sub rank " + subRank);
                return 0;
            }
        } catch (IllegalStateException e) {
            logger.debug("Ranks system is disabled, returning 0 for required achievements");
            return 0;
        }
    }

    /**
     * Logs debug messages if debug mode is enabled
     * 
     * @param message The message to log
     */
    private void debugLog(String message) {
        if (debugEnabled) {
            logger.info("[RewardsHandler] " + message);
        }
    }

    /**
     * Handles rewards for a player that has just been verified.
     * This should be called after a successful verification process.
     * 
     * @param playerUuid The UUID of the player
     * @param discordUserId The Discord user ID, or null if not linked
     * @return CompletableFuture that resolves to true if rewards were given successfully
     */
    public CompletableFuture<Boolean> handleVerificationRewards(String playerUuid, Long discordUserId) {
        try {
            checkRewardsEnabled();
            debugLog("Processing verification rewards for player " + playerUuid);
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            
            // Create/update player rank record for the newly verified player
            getPlayerRank(playerUuid).thenAccept(playerRank -> {
                // Update verification time
                playerRank.setVerifiedAt(Instant.now());
                
                // Save updated rank
                sqlHandler.savePlayerRank(playerRank)
                    .thenAccept(success -> {
                        if (success) {
                            debugLog("Updated verification timestamp for player " + playerUuid);
                            
                            // Sync Discord roles if Discord ID is available
                            if (discordUserId != null) {
                                String discordIdStr = String.valueOf(discordUserId);
                                syncPlayerRoles(playerUuid, discordIdStr)
                                    .thenAccept(syncSuccess -> {
                                        if (syncSuccess) {
                                            debugLog("Synced Discord roles for verified player " + playerUuid);
                                        } else {
                                            logger.warn("Failed to sync Discord roles for verified player " + playerUuid);
                                        }
                                        // Continue with rewards regardless of Discord sync result
                                        processRankBasedRewards(playerRank, future);
                                    });
                            } else {
                                // No Discord ID, just process rewards
                                processRankBasedRewards(playerRank, future);
                            }
                        } else {
                            logger.error("Failed to update player rank for rewards: " + playerUuid);
                            future.complete(false);
                        }
                    }).exceptionally(e -> {
                        logger.error("Error saving player rank for verification rewards", e);
                        future.complete(false);
                        return null;
                    });
            }).exceptionally(e -> {
                logger.error("Error processing verification rewards", e);
                future.complete(false);
                return null;
            });
            
            return future;
        } catch (IllegalStateException e) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.complete(false);
            return future;
        }
    }
    
    /**
     * Processes rank-based rewards for a player
     * 
     * @param playerRank The player's rank information
     * @param future The CompletableFuture to complete
     */
    private void processRankBasedRewards(PlayerRank playerRank, CompletableFuture<Boolean> future) {
        try {
            RankDefinition currentRank = getRankDefinition(
                playerRank.getMainRank(), 
                playerRank.getSubRank()
            );
            
            if (currentRank != null && currentRank.getRewards() != null) {
                debugLog("Processing rewards for rank: " + currentRank.getRankName());
                
                // Get the player's Minecraft username from UUID
                String playerUuid = playerRank.getPlayerUuid();
                Optional<String> playerNameOpt = sqlHandler.getPlayerNameFromUuid(playerUuid);
                
                if (playerNameOpt.isPresent()) {
                    String playerName = playerNameOpt.get();
                    RankRewards rewards = currentRank.getRewards();
                    
                    // Process economy rewards if enabled
                    if (rewards.getEconomyAmount() > 0) {
                        processEconomyReward(playerName, rewards.getEconomyAmount());
                    }
                    
                    // Process command rewards
                    if (rewards.getCommands() != null && !rewards.getCommands().isEmpty()) {
                        processCommandRewards(playerName, rewards.getCommands(), currentRank.getRankName());
                    }
                    
                    // Log the reward processing
                    sqlHandler.logRewardProcessing(
                        playerUuid,
                        currentRank.getMainRank(),
                        currentRank.getSubRank(),
                        rewards.getEconomyAmount(),
                        rewards.getCommands() != null ? rewards.getCommands().size() : 0
                    );
                    
                    future.complete(true);
                } else {
                    logger.warn("Could not find player name for UUID: " + playerUuid);
                    future.complete(false);
                }
            } else {
                debugLog("No rank definition or rewards found for processing");
                future.complete(true); // Still succeed even without rewards
            }
        } catch (Exception e) {
            logger.error("Error processing rank-based rewards", e);
            future.complete(false);
        }
    }

    /**
     * Processes economy rewards for a player
     * 
     * @param playerName The name of the player
     * @param amount The amount of currency to reward
     */
    private void processEconomyReward(String playerName, int amount) {
        if (amount <= 0) {
            return;
        }
        
        try {
            // Send command to give economy via console command
            // This uses a common economy command format that works with most economy plugins
            String economyCommand = "eco give " + playerName + " " + amount;
            sendConsoleCommand(economyCommand);
            
            debugLog("Processed economy reward of " + amount + " for player " + playerName);
        } catch (Exception e) {
            logger.error("Failed to process economy reward for " + playerName, e);
        }
    }

    /**
     * Processes command rewards for a player
     * 
     * @param playerName The name of the player
     * @param commands The list of commands to execute
     * @param rankName The name of the rank for placeholder replacement
     */
    private void processCommandRewards(String playerName, List<String> commands, String rankName) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        
        try {
            for (String command : commands) {
                // Replace placeholders in command
                String processedCommand = command
                    .replace("%player%", playerName)
                    .replace("%rank%", rankName);
                
                // Execute the command
                sendConsoleCommand(processedCommand);
                
                debugLog("Executed command reward for " + playerName + ": " + processedCommand);
            }
        } catch (Exception e) {
            logger.error("Failed to process command rewards for " + playerName, e);
        }
    }    /**
     * Sends a command to the console for execution
     * 
     * @param command The command to execute
     */
    private void sendConsoleCommand(String command) {
        try {
            // Delegate console command execution to the main plugin via the server
            server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
        } catch (Exception e) {
            exceptionHandler.handleIntegrationException("RewardsHandler", "console command execution: " + command, e);
        }
    }

    /**
     * Checks if rewards are enabled
     */
    private void checkRewardsEnabled() throws IllegalStateException {
        if (!rewardsEnabled) {
            throw new IllegalStateException("Rewards system is disabled in config");
        }
    }
    
    /**
     * Checks if ranks are enabled
     */
    private void checkRanksEnabled() throws IllegalStateException {
        if (!ranksEnabled) {
            throw new IllegalStateException("Rank system is disabled in config");
        }
    }
    
    /**
     * Gets a formatted rank display string for a player
     * Uses the official subrank progression system to create display names like "novice wanderer"
     * 
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that resolves to the formatted rank string
     */
    public CompletableFuture<String> getFormattedPlayerRank(String playerUuid) {
        return getPlayerRank(playerUuid).thenApply(playerRank -> {
            if (playerRank == null) {
                return "unknown";
            }
            
            RankDefinition rankDef = getRankDefinition(playerRank.getMainRank(), playerRank.getSubRank());
            if (rankDef != null) {
                // Use the rank definition's name if available
                return rankDef.getFormattedRankName();
            } else {
                // Fallback to generating the name from IDs
                String mainRankName = getMainRankNameById(playerRank.getMainRank());
                return RankDefinition.formatRankDisplay(playerRank.getSubRank(), mainRankName);
            }
        });
    }
    
    /**
     * Gets the main rank name by ID using the official main rank progression system
     * This is a fallback method when rank definitions are not available
     * 
     * @param mainRankId The main rank ID (1-25)
     * @return The main rank name
     */
    private String getMainRankNameById(int mainRankId) {
        return RankDefinition.getMainRankNameById(mainRankId);
    }

    /**
     * getNextRank
     * Returns the next RankDefinition for the given mainRank and subRank.
     * If there is no next rank, returns null.
     *
     * @param mainRank The current main rank
     * @param subRank The current sub rank
     * @return The next RankDefinition, or null if at max rank
     */
    public RankDefinition getNextRank(int mainRank, int subRank) {
        // Try next subRank in the same mainRank
        int nextSubRankKey = (mainRank * 100) + (subRank + 1);
        if (rankDefinitionsCache.containsKey(nextSubRankKey)) {
            return rankDefinitionsCache.get(nextSubRankKey);
        }
        // Try first subRank of next mainRank
        int nextMainRankKey = ((mainRank + 1) * 100) + 1;
        if (rankDefinitionsCache.containsKey(nextMainRankKey)) {
            return rankDefinitionsCache.get(nextMainRankKey);
        }
        // No next rank found
        return null;
    }
    
    /**
     * Processes rank progression rewards using Vault and LuckPerms integrations
     * 
     * @param playerUuid The UUID of the player
     * @param newMainRank The new main rank level
     * @param newSubRank The new sub rank level
     * @param isSubRankProgression Whether this is a sub-rank progression (vs main rank)
     */
    private void processRankProgressionRewards(String playerUuid, int newMainRank, int newSubRank, boolean isSubRankProgression) {
        if (!rewardsEnabled) {
            debugLog("Rewards are disabled, skipping rank progression rewards");
            return;
        }
        
        try {
            // Give Vault economy rewards if available
            if (vaultIntegration != null && vaultIntegration.isEconomyAvailable()) {
                vaultIntegration.giveRankReward(playerUuid, newMainRank, isSubRankProgression)
                    .thenAccept(success -> {
                        if (success) {
                            debugLog("Gave Vault economy reward for rank progression to " + playerUuid);
                        } else {
                            debugLog("Failed to give Vault economy reward to " + playerUuid);
                        }
                    })
                                .exceptionally(ex -> {
                                    logger.error("Error giving Vault economy reward to " + playerUuid, ex);
                                    return null;
                                });
                        }
                    } catch (Exception e) {
                        logger.error("Error processing rank progression rewards for " + playerUuid, e);
                    }
                }
            }
 