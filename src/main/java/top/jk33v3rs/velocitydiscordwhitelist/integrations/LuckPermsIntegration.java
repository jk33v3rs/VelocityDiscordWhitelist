package top.jk33v3rs.velocitydiscordwhitelist.integrations;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;
import top.jk33v3rs.velocitydiscordwhitelist.utils.LoggingUtils;

/**
 * LuckPerms integration for permission management
 */
public class LuckPermsIntegration {
    private final Object luckPerms;
    private final Object userManager;
    private final Logger logger;
    private final boolean debugEnabled;
    private final Map<String, Object> config;
    private final boolean enabled;
    private final ExceptionHandler exceptionHandler;

    /**
     * LuckPermsIntegration Constructor
     * 
     * @param logger Logger instance for this integration
     * @param debugEnabled Debug mode flag to enable verbose logging
     * @param config Configuration map containing LuckPerms settings
     */
    @SuppressWarnings("unchecked")
    public LuckPermsIntegration(Logger logger, boolean debugEnabled, Map<String, Object> config) {
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        this.config = config;
        this.exceptionHandler = new ExceptionHandler(logger, debugEnabled);
        
        Map<String, Object> luckPermsConfig = (Map<String, Object>) config.getOrDefault("luckperms", Map.of());
        this.enabled = Boolean.parseBoolean(luckPermsConfig.getOrDefault("enabled", "false").toString());
        
        Object luckPermsInstance = null;
        Object userManagerInstance = null;
        
        if (enabled) {
            try {
                Class<?> luckPermsProviderClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                luckPermsInstance = luckPermsProviderClass.getMethod("get").invoke(null);
                userManagerInstance = luckPermsInstance.getClass().getMethod("getUserManager").invoke(luckPermsInstance);
                LoggingUtils.debugLog(logger, debugEnabled, "LuckPerms integration initialized successfully");
            } catch (ClassNotFoundException e) {
                exceptionHandler.handleIntegrationException("LuckPerms", "initialization - classes not found", e);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                exceptionHandler.handleIntegrationException("LuckPerms", "initialization", e);
            }
        }
        
        this.luckPerms = luckPermsInstance;
        this.userManager = userManagerInstance;
        
        // Debug logging moved to avoid overridable method call in constructor
        LoggingUtils.debugLog(logger, debugEnabled, "LuckPermsIntegration initialized - Available: " + (enabled && luckPermsInstance != null && userManagerInstance != null));
    }

    /**
     * isAvailable Method
     * 
     * @return true if LuckPerms integration is available and enabled
     */
    public boolean isAvailable() {
        return enabled && luckPerms != null && userManager != null;
    }

    /**
     * loadUser Method
     * Loads a user by UUID, creating if necessary
     * 
     * @param playerUuid The UUID of the player to load
     * @return CompletableFuture containing the User object, or empty if failed
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<Optional<Object>> loadUser(UUID playerUuid) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return exceptionHandler.executeAsyncWithHandling("loading user " + playerUuid, () -> {
            try {
                CompletableFuture<Object> userFuture = (CompletableFuture<Object>) userManager.getClass()
                    .getMethod("loadUser", UUID.class).invoke(userManager, playerUuid);
                
                return userFuture.thenApply(Optional::of)
                    .exceptionally(throwable -> {
                        exceptionHandler.handleIntegrationException("LuckPerms", "loadUser", throwable);
                        return Optional.empty();
                    });
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                exceptionHandler.handleIntegrationException("LuckPerms", "loadUser reflection", e);
                return CompletableFuture.completedFuture(Optional.empty());
            }
        });
    }

    /**
     * addPlayerToGroup Method
     * Adds a player to a group using LuckPerms
     * 
     * @param playerUuid The UUID of the player to add to group
     * @param groupName The name of the group to add the player to
     * @param context The context for the permission (e.g., server, world)
     * @return CompletableFuture that resolves to true if successful
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> addPlayerToGroup(UUID playerUuid, String groupName, String context) {
        if (!isAvailable()) {
            LoggingUtils.debugLog(logger, debugEnabled, "LuckPerms not available for group assignment: " + playerUuid);
            return CompletableFuture.completedFuture(false);
        }

        return loadUser(playerUuid).thenCompose(userOpt -> {
            if (userOpt.isEmpty()) {
                exceptionHandler.handleIntegrationException("LuckPerms", "user loading for group assignment", 
                    new RuntimeException("Could not load user for group assignment: " + playerUuid));
                return CompletableFuture.completedFuture(false);
            }

            try {
                Object user = userOpt.get();
                
                // Create inheritance node using reflection
                Class<?> inheritanceNodeClass = Class.forName("net.luckperms.api.node.types.InheritanceNode");
                Object nodeBuilder = inheritanceNodeClass.getMethod("builder", String.class).invoke(null, groupName);
                
                // Add context if specified
                if (context != null && !context.isEmpty()) {
                    nodeBuilder = nodeBuilder.getClass()
                        .getMethod("withContext", String.class, String.class)
                        .invoke(nodeBuilder, "server", context);
                }
                
                Object node = nodeBuilder.getClass().getMethod("build").invoke(nodeBuilder);
                
                // Add the node to the user
                Object userData = user.getClass().getMethod("data").invoke(user);
                userData.getClass().getMethod("add", Class.forName("net.luckperms.api.node.Node")).invoke(userData, node);
                
                // Save the user
                CompletableFuture<Void> saveFuture = (CompletableFuture<Void>) userManager.getClass()
                    .getMethod("saveUser", Class.forName("net.luckperms.api.model.user.User")).invoke(userManager, user);
                
                return saveFuture.thenApply(v -> {
                    logger.info("Added player {} to group {} with context {}", playerUuid, groupName, context);
                    return true;
                }).exceptionally(throwable -> {
                    exceptionHandler.handleIntegrationException("LuckPerms", "save user after group assignment", throwable);
                    return false;
                });
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                exceptionHandler.handleIntegrationException("LuckPerms", "adding player to group: " + groupName, e);
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    /**
     * Removes a player from a group using LuckPerms
     * 
     * @param playerUuid The UUID of the player
     * @param groupName The name of the group to remove the player from
     * @param context The context for the permission (e.g., server, world)
     * @return CompletableFuture that resolves to true if successful
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> removePlayerFromGroup(UUID playerUuid, String groupName, String context) {
        if (!isAvailable()) {
            LoggingUtils.debugLog(logger, debugEnabled, "LuckPerms not available for group removal: " + playerUuid);
            return CompletableFuture.completedFuture(false);
        }

        return loadUser(playerUuid).thenCompose(userOpt -> {
            if (userOpt.isEmpty()) {
                exceptionHandler.handleIntegrationException("LuckPerms", "user loading for group removal",
                    new RuntimeException("Could not load user for group removal: " + playerUuid));
                return CompletableFuture.completedFuture(false);
            }

            try {
                Object user = userOpt.get();
                
                // Create inheritance node using reflection
                Class<?> inheritanceNodeClass = Class.forName("net.luckperms.api.node.types.InheritanceNode");
                Object nodeBuilder = inheritanceNodeClass.getMethod("builder", String.class).invoke(null, groupName);
                
                // Add context if specified
                if (context != null && !context.isEmpty()) {
                    nodeBuilder = nodeBuilder.getClass()
                        .getMethod("withContext", String.class, String.class)
                        .invoke(nodeBuilder, "server", context);
                }
                
                Object node = nodeBuilder.getClass().getMethod("build").invoke(nodeBuilder);
                
                // Remove the node from the user
                Object userData = user.getClass().getMethod("data").invoke(user);
                userData.getClass().getMethod("remove", Class.forName("net.luckperms.api.node.Node")).invoke(userData, node);
                
                // Save the user
                CompletableFuture<Void> saveFuture = (CompletableFuture<Void>) userManager.getClass()
                    .getMethod("saveUser", Class.forName("net.luckperms.api.model.user.User")).invoke(userManager, user);
                
                return saveFuture.thenApply(v -> {
                    logger.info("Removed player {} from group {} with context {}", playerUuid, groupName, context);
                    return true;
                }).exceptionally(throwable -> {
                    exceptionHandler.handleIntegrationException("LuckPerms", "save user after group removal", throwable);
                    return false;
                });
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                exceptionHandler.handleIntegrationException("LuckPerms", "removing player from group: " + groupName, e);
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    /**
     * Gets all groups a player belongs to
     * 
     * @param playerUuid The UUID of the player
     * @return CompletableFuture containing a set of group names
     */
    public CompletableFuture<Set<String>> getPlayerGroups(UUID playerUuid) {
        if (!isAvailable()) {
            LoggingUtils.debugLog(logger, debugEnabled, "LuckPerms not available for group lookup: " + playerUuid);
            return CompletableFuture.completedFuture(new HashSet<>());
        }

        return loadUser(playerUuid).thenApply(userOpt -> {
            if (userOpt.isEmpty()) {
                exceptionHandler.handleIntegrationException("LuckPerms", "user loading for group lookup",
                    new RuntimeException("Could not load user for group lookup: " + playerUuid));
                return new HashSet<String>();
            }

            try {
                Object user = userOpt.get();
                Collection<?> nodes = (Collection<?>) user.getClass().getMethod("getNodes").invoke(user);
                Set<String> groups = new HashSet<>();
                
                Class<?> inheritanceNodeClass = Class.forName("net.luckperms.api.node.types.InheritanceNode");
                
                for (Object node : nodes) {
                    if (inheritanceNodeClass.isInstance(node)) {
                        String groupName = (String) inheritanceNodeClass.getMethod("getGroupName").invoke(node);
                        groups.add(groupName);
                    }
                }
                
                return groups;
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                exceptionHandler.handleIntegrationException("LuckPerms", "getting player groups", e);
                return new HashSet<String>();
            }
        });
    }

    /**
     * Gets the primary group of a player
     * 
     * @param playerUuid The UUID of the player
     * @return CompletableFuture containing Optional with the primary group name
     */
    public CompletableFuture<Optional<String>> getPlayerPrimaryGroup(UUID playerUuid) {
        if (!isAvailable()) {
            LoggingUtils.debugLog(logger, debugEnabled, "LuckPerms not available for primary group lookup: " + playerUuid);
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return loadUser(playerUuid).thenApply(userOpt -> {
            if (userOpt.isEmpty()) {
                exceptionHandler.handleIntegrationException("LuckPerms", "user loading for primary group lookup",
                    new RuntimeException("Could not load user for primary group lookup: " + playerUuid));
                return Optional.<String>empty();
            }

            try {
                Object user = userOpt.get();
                String primaryGroup = (String) user.getClass().getMethod("getPrimaryGroup").invoke(user);
                return Optional.of(primaryGroup);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                exceptionHandler.handleIntegrationException("LuckPerms", "getting primary group", e);
                return Optional.<String>empty();
            }
        });
    }

    /**
     * Sets the primary group of a player
     * 
     * @param playerUuid The UUID of the player
     * @param groupName The name of the group to set as primary
     * @return CompletableFuture that resolves to true if successful
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> setPlayerPrimaryGroup(UUID playerUuid, String groupName) {
        if (!isAvailable()) {
            LoggingUtils.debugLog(logger, debugEnabled, "LuckPerms not available for primary group setting: " + playerUuid);
            return CompletableFuture.completedFuture(false);
        }

        return loadUser(playerUuid).thenCompose(userOpt -> {
            if (userOpt.isEmpty()) {
                exceptionHandler.handleIntegrationException("LuckPerms", "user loading for primary group setting",
                    new RuntimeException("Could not load user for primary group setting: " + playerUuid));
                return CompletableFuture.completedFuture(false);
            }

            try {
                Object user = userOpt.get();
                user.getClass().getMethod("setPrimaryGroup", String.class).invoke(user, groupName);
                
                CompletableFuture<Void> saveFuture = (CompletableFuture<Void>) userManager.getClass()
                    .getMethod("saveUser", Class.forName("net.luckperms.api.model.user.User")).invoke(userManager, user);
                
                return saveFuture.thenApply(v -> {
                    logger.info("Set primary group of player {} to {}", playerUuid, groupName);
                    return true;
                }).exceptionally(throwable -> {
                    exceptionHandler.handleIntegrationException("LuckPerms", "save user after primary group setting", throwable);
                    return false;
                });
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                exceptionHandler.handleIntegrationException("LuckPerms", "setting primary group: " + groupName, e);
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    /**
     * Syncs a player's groups based on their current rank
     * 
     * @param playerUuid The UUID of the player
     * @param mainRank The player's main rank level
     * @param subRank The player's sub rank level
     * @param discordRoles Set of Discord role names the player has
     * @return CompletableFuture that resolves to true if sync was successful
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> syncPlayerRankGroups(UUID playerUuid, int mainRank, int subRank, Set<String> discordRoles) {
        if (!isAvailable()) {
            LoggingUtils.debugLog(logger, debugEnabled, "LuckPerms not available for rank sync: " + playerUuid);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            return exceptionHandler.executeWithHandling("syncing rank groups for player " + playerUuid, () -> {
                Map<String, Object> luckPermsConfig = (Map<String, Object>) config.getOrDefault("luckperms", Map.of());
                
                // Get current groups
                Set<String> currentGroups = getPlayerGroups(playerUuid).join();
                
                // Calculate target groups based on rank and Discord roles
                Set<String> targetGroups = calculateTargetGroups(mainRank, subRank, discordRoles, luckPermsConfig);
                
                // Remove groups that shouldn't be there
                for (String group : currentGroups) {
                    if (isManagedGroup(group, luckPermsConfig) && !targetGroups.contains(group)) {
                        removePlayerFromGroup(playerUuid, group, null).join();
                    }
                }
                
                // Add new groups
                for (String group : targetGroups) {
                    if (!currentGroups.contains(group)) {
                        addPlayerToGroup(playerUuid, group, null).join();
                    }
                }
                
                // Set primary group if specified
                String primaryGroup = determinePrimaryGroup(targetGroups, luckPermsConfig);
                if (primaryGroup != null) {
                    setPlayerPrimaryGroup(playerUuid, primaryGroup).join();
                }
                
                return true;
            }, false);
        });
    }

    /**
     * Calculates the target groups for a player based on their rank and Discord roles
     * 
     * @param mainRank The main rank level
     * @param subRank The sub rank level
     * @param discordRoles Set of Discord role names
     * @param luckPermsConfig The LuckPerms configuration
     * @return Set of group names the player should have
     */
    @SuppressWarnings("unchecked")
    private Set<String> calculateTargetGroups(int mainRank, int subRank, Set<String> discordRoles, Map<String, Object> luckPermsConfig) {
        Set<String> targetGroups = new HashSet<>();
        
        Map<String, Object> rankMappings = (Map<String, Object>) luckPermsConfig.getOrDefault("rank_mappings", Map.of());
        Map<String, Object> discordMappings = (Map<String, Object>) luckPermsConfig.getOrDefault("discord_mappings", Map.of());
        
        // Add rank-based groups
        String rankKey = mainRank + "." + subRank;
        if (rankMappings.containsKey(rankKey)) {
            targetGroups.add(rankMappings.get(rankKey).toString());
        } else if (rankMappings.containsKey(String.valueOf(mainRank))) {
            targetGroups.add(rankMappings.get(String.valueOf(mainRank)).toString());
        }
        
        // Add Discord role-based groups
        for (String discordRole : discordRoles) {
            if (discordMappings.containsKey(discordRole)) {
                targetGroups.add(discordMappings.get(discordRole).toString());
            }
        }
        
        // Always include default verified group
        String defaultGroup = luckPermsConfig.getOrDefault("default_group", "verified").toString();
        targetGroups.add(defaultGroup);
        
        return targetGroups;
    }

    /**
     * Checks if a group is managed by this plugin
     * 
     * @param groupName The group name to check
     * @param luckPermsConfig The LuckPerms configuration
     * @return true if this group is managed by the plugin
     */
    @SuppressWarnings("unchecked")
    private boolean isManagedGroup(String groupName, Map<String, Object> luckPermsConfig) {
        Map<String, Object> rankMappings = (Map<String, Object>) luckPermsConfig.getOrDefault("rank_mappings", Map.of());
        Map<String, Object> discordMappings = (Map<String, Object>) luckPermsConfig.getOrDefault("discord_mappings", Map.of());
        
        return rankMappings.containsValue(groupName) || 
               discordMappings.containsValue(groupName) ||
               groupName.equals(luckPermsConfig.getOrDefault("default_group", "verified"));
    }

    /**
     * Determines the primary group from a set of target groups
     * 
     * @param targetGroups Set of groups the player should have
     * @param luckPermsConfig The LuckPerms configuration
     * @return The group name that should be primary, or null if no change needed
     */
    @SuppressWarnings("unchecked")
    private String determinePrimaryGroup(Set<String> targetGroups, Map<String, Object> luckPermsConfig) {
        Map<String, Object> groupPriorities = (Map<String, Object>) luckPermsConfig.getOrDefault("group_priorities", Map.of());
        
        return targetGroups.stream()
            .filter(groupPriorities::containsKey)
            .max((g1, g2) -> {
                int p1 = Integer.parseInt(groupPriorities.get(g1).toString());
                int p2 = Integer.parseInt(groupPriorities.get(g2).toString());
                return Integer.compare(p1, p2);
            })
            .orElse(null);
    }
}
