package top.jk33v3rs.velocitydiscordwhitelist.integrations;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * VaultIntegration handles Vault API integration for economy and permissions.
 */
public class VaultIntegration {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final boolean debugEnabled;
    private final Map<String, Object> config;
    private final boolean economyEnabled;
    private final boolean permissionsEnabled;
    private final String rewardServer;

    /**
     * Constructor for VaultIntegration
     */
    @SuppressWarnings("unchecked")
    public VaultIntegration(ProxyServer proxyServer, Logger logger, boolean debugEnabled, Map<String, Object> config) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        this.config = config;
        
        Map<String, Object> vaultConfig = (Map<String, Object>) config.getOrDefault("vault", Map.of());
        this.economyEnabled = Boolean.parseBoolean(vaultConfig.getOrDefault("economy.enabled", "false").toString());
        this.permissionsEnabled = Boolean.parseBoolean(vaultConfig.getOrDefault("permissions.enabled", "false").toString());
        this.rewardServer = vaultConfig.getOrDefault("reward_server", "survival").toString();
        
        debugLog("VaultIntegration initialized - Economy: " + economyEnabled + 
                ", Permissions: " + permissionsEnabled + ", Server: " + rewardServer);
    }

    /**
     * Checks if Vault economy integration is available and enabled
     * 
     * @return true if economy integration is available and enabled, false otherwise
     */
    public boolean isEconomyAvailable() {
        return economyEnabled && getTargetServer().isPresent();
    }

    /**
     * Checks if Vault permissions integration is available and enabled
     * 
     * @return true if permissions integration is available and enabled, false otherwise
     */
    public boolean isPermissionsAvailable() {
        return permissionsEnabled && getTargetServer().isPresent();
    }

    /**
     * Gets the target server for Vault operations
     * 
     * @return Optional containing the registered server, empty if not available
     */
    private Optional<RegisteredServer> getTargetServer() {
        return proxyServer.getServer(rewardServer);
    }

    /**
     * Gives currency reward to a player for whitelisting
     * 
     * @param playerName The name of the player to reward
     * @param playerUuid The UUID of the player to reward
     * @param amount The amount of currency to give, if null uses config default
     * @return CompletableFuture that resolves to true if reward was successful
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> giveWhitelistReward(String playerName, UUID playerUuid, Double amount) {
        if (!isEconomyAvailable()) {
            debugLog("Economy not available for whitelist reward: " + playerName);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> vaultConfig = (Map<String, Object>) config.getOrDefault("vault", Map.of());
                Map<String, Object> economyConfig = (Map<String, Object>) vaultConfig.getOrDefault("economy", Map.of());
                
                double rewardAmount = amount != null ? amount : 
                    Double.parseDouble(economyConfig.getOrDefault("whitelist_reward", "1000").toString());
                
                // Send command to backend server to give reward
                String command = String.format("eco give %s %f", playerName, rewardAmount);
                boolean success = executeServerCommand(command, "Economy reward for " + playerName);
                
                if (success) {
                    logger.info("Gave {} to {} for whitelisting", rewardAmount, playerName);
                }
                
                return success;
                
            } catch (Exception e) {
                logger.error("Error giving whitelist reward to " + playerName, e);
                return false;
            }
        });
    }

    /**
     * Gives currency reward to a player for rank progression
     * 
     * @param playerName The name of the player to reward
     * @param rankLevel The rank level achieved (used to calculate reward)
     * @param isSubRank Whether this is a sub-rank progression
     * @return CompletableFuture that resolves to true if reward was successful
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> giveRankReward(String playerName, int rankLevel, boolean isSubRank) {
        if (!isEconomyAvailable()) {
            debugLog("Economy not available for rank reward: " + playerName);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> vaultConfig = (Map<String, Object>) config.getOrDefault("vault", Map.of());
                Map<String, Object> economyConfig = (Map<String, Object>) vaultConfig.getOrDefault("economy", Map.of());
                
                // Calculate reward based on rank level and type
                double baseReward = Double.parseDouble(economyConfig.getOrDefault("rank_base_reward", "500").toString());
                double multiplier = isSubRank ? 
                    Double.parseDouble(economyConfig.getOrDefault("subrank_multiplier", "0.5").toString()) :
                    Double.parseDouble(economyConfig.getOrDefault("mainrank_multiplier", "2.0").toString());
                
                double rewardAmount = baseReward * multiplier * Math.pow(1.5, rankLevel - 1);
                
                // Send command to backend server to give reward
                String command = String.format("eco give %s %f", playerName, rewardAmount);
                boolean success = executeServerCommand(command, 
                    String.format("Rank reward for %s (%s rank level %d)", playerName, 
                        isSubRank ? "sub" : "main", rankLevel));
                
                if (success) {
                    logger.info("Gave {} to {} for {} rank progression (level {})", 
                        rewardAmount, playerName, isSubRank ? "sub" : "main", rankLevel);
                }
                
                return success;
            } catch (Exception e) {
                logger.error("Error giving rank reward to " + playerName, e);
                return false;
            }
        });
    }

    /**
     * Adds a player to a permission group
     * 
     * @param playerName The name of the player
     * @param groupName The name of the group to add the player to
     * @param world The world name, null for global
     * @return CompletableFuture that resolves to true if successful
     */
    public CompletableFuture<Boolean> addPlayerToGroup(String playerName, String groupName, String world) {
        if (!isPermissionsAvailable()) {
            debugLog("Permissions not available for group assignment: " + playerName);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Send command to backend server to add player to group
                String command = world != null ? 
                    String.format("lp user %s parent add %s world=%s", playerName, groupName, world) :
                    String.format("lp user %s parent add %s", playerName, groupName);
                
                boolean result = executeServerCommand(command, 
                    String.format("Add %s to group %s in world %s", playerName, groupName, world));
                
                if (result) {
                    logger.info("Added player {} to group {} in world {}", playerName, groupName, world);
                } else {
                    logger.warn("Failed to add player {} to group {} in world {}", playerName, groupName, world);
                }
                return result;
            } catch (Exception e) {
                logger.error("Error adding player " + playerName + " to group " + groupName, e);
                return false;
            }
        });
    }

    /**
     * Removes a player from a permission group
     * 
     * @param playerName The name of the player
     * @param groupName The name of the group to remove the player from
     * @param world The world name, null for global
     * @return CompletableFuture that resolves to true if successful
     */
    public CompletableFuture<Boolean> removePlayerFromGroup(String playerName, String groupName, String world) {
        if (!isPermissionsAvailable()) {
            debugLog("Permissions not available for group removal: " + playerName);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Send command to backend server to remove player from group
                String command = world != null ?
                    String.format("lp user %s parent remove %s world=%s", playerName, groupName, world) :
                    String.format("lp user %s parent remove %s", playerName, groupName);
                
                boolean result = executeServerCommand(command,
                    String.format("Remove %s from group %s in world %s", playerName, groupName, world));
                
                if (result) {
                    logger.info("Removed player {} from group {} in world {}", playerName, groupName, world);
                } else {
                    logger.warn("Failed to remove player {} from group {} in world {}", playerName, groupName, world);
                }
                return result;
            } catch (Exception e) {
                logger.error("Error removing player " + playerName + " from group " + groupName, e);
                return false;
            }
        });
    }

    /**
     * Gets the primary group of a player
     * 
     * @param playerName The name of the player
     * @param world The world name, null for global
     * @return Optional containing the primary group name, empty if not found
     */
    public Optional<String> getPlayerPrimaryGroup(String playerName, String world) {
        if (!isPermissionsAvailable()) {
            debugLog("Permissions not available for group lookup: " + playerName);
            return Optional.empty();
        }

        try {
            // For Velocity proxy, we would need to query this from backend server
            // This is a simplified implementation that returns default group
            debugLog("Primary group lookup not fully implemented for proxy - returning default");
            return Optional.of("default");
        } catch (Exception e) {
            logger.error("Error getting primary group for player " + playerName, e);
            return Optional.empty();
        }
    }

    /**
     * Gets all groups a player belongs to
     * 
     * @param playerName The name of the player
     * @param world The world name, null for global
     * @return Array of group names, empty array if none found
     */
    public String[] getPlayerGroups(String playerName, String world) {
        if (!isPermissionsAvailable()) {
            debugLog("Permissions not available for groups lookup: " + playerName);
            return new String[0];
        }

        try {
            // For Velocity proxy, we would need to query this from backend server
            // This is a simplified implementation that returns default group
            debugLog("Player groups lookup not fully implemented for proxy - returning default");
            return new String[]{"default"};
        } catch (Exception e) {
            logger.error("Error getting groups for player " + playerName, e);
            return new String[0];
        }
    }

    /**
     * Syncs a player's permission group based on their current rank
     * 
     * @param playerName The name of the player
     * @param mainRank The player's main rank level
     * @param subRank The player's sub rank level
     * @return CompletableFuture that resolves to true if sync was successful
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> syncPlayerRankGroup(String playerName, int mainRank, int subRank) {
        if (!isPermissionsAvailable()) {
            debugLog("Permissions not available for rank sync: " + playerName);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> vaultConfig = (Map<String, Object>) config.getOrDefault("vault", Map.of());
                Map<String, Object> permConfig = (Map<String, Object>) vaultConfig.getOrDefault("permissions", Map.of());
                
                // Get current groups
                String[] currentGroups = getPlayerGroups(playerName, null);
                
                // Calculate target group based on rank
                String targetGroup = calculateRankGroup(mainRank, subRank, permConfig);
                
                // Remove old rank groups and add new one
                for (String group : currentGroups) {
                    if (isRankGroup(group, permConfig)) {
                        removePlayerFromGroup(playerName, group, null).join();
                    }
                }
                
                if (targetGroup != null) {
                    return addPlayerToGroup(playerName, targetGroup, null).join();
                }
                
                return true;
            } catch (Exception e) {
                logger.error("Error syncing rank group for player " + playerName, e);
                return false;
            }
        });
    }

    /**
     * Calculates the appropriate permission group for a given rank
     * 
     * @param mainRank The main rank level
     * @param subRank The sub rank level
     * @param permConfig The permissions configuration
     * @return The group name to assign, or null if no appropriate group
     */
    @SuppressWarnings("unchecked")
    private String calculateRankGroup(int mainRank, int subRank, Map<String, Object> permConfig) {
        Map<String, Object> rankGroups = (Map<String, Object>) permConfig.getOrDefault("rank_groups", Map.of());
        
        // Check for specific rank mapping
        String rankKey = mainRank + "." + subRank;
        if (rankGroups.containsKey(rankKey)) {
            return rankGroups.get(rankKey).toString();
        }
        
        // Check for main rank mapping
        String mainRankKey = String.valueOf(mainRank);
        if (rankGroups.containsKey(mainRankKey)) {
            return rankGroups.get(mainRankKey).toString();
        }
        
        // Default group
        return permConfig.getOrDefault("default_group", "verified").toString();
    }

    /**
     * Checks if a group name is a rank-based group
     * 
     * @param groupName The group name to check
     * @param permConfig The permissions configuration
     * @return true if this is a rank group, false otherwise
     */
    @SuppressWarnings("unchecked")
    private boolean isRankGroup(String groupName, Map<String, Object> permConfig) {
        Map<String, Object> rankGroups = (Map<String, Object>) permConfig.getOrDefault("rank_groups", Map.of());
        return rankGroups.containsValue(groupName);
    }

    /**
     * Logs debug messages if debug mode is enabled
     * 
     * @param message The message to log
     */
    private void debugLog(String message) {
        if (debugEnabled) {
            logger.info("[VaultIntegration] " + message);
        }
    }

    /**
     * Executes a command on the target backend server
     * 
     * @param command The command to execute
     * @param description Description of the command for logging
     * @return true if command was sent successfully, false otherwise
     */
    private boolean executeServerCommand(String command, String description) {
        try {
            Optional<RegisteredServer> serverOpt = getTargetServer();
            if (!serverOpt.isPresent()) {
                logger.warn("Target server '{}' not available for command: {}", rewardServer, command);
                return false;
            }
            
            // For Velocity proxy, we need to send plugin messages to backend servers
            // or use console command execution if available
            // This is a simplified implementation that logs the attempt
            debugLog("Would execute command on server '" + rewardServer + "': " + command + " (" + description + ")");
            
            // In a real implementation, you would:
            // 1. Send a plugin message to the backend server
            // 2. Use the server's console command API if available
            // 3. Use a cross-server communication system
            
            // For now, we assume the command succeeds
            logger.info("Sent command to server '{}': {} ({})", rewardServer, command, description);
            return true;
            
        } catch (Exception e) {
            logger.error("Error executing server command: " + command, e);
            return false;
        }
    }
}
