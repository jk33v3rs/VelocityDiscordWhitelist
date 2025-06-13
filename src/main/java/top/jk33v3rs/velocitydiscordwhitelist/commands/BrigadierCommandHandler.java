package top.jk33v3rs.velocitydiscordwhitelist.commands;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.modules.EnhancedPurgatoryManager;
import top.jk33v3rs.velocitydiscordwhitelist.modules.RewardsHandler;
import top.jk33v3rs.velocitydiscordwhitelist.modules.XPManager;

/** Handles Minecraft commands including /verify, /rank, /xpchart, and VWL admin commands. */
public class BrigadierCommandHandler {
    private final ProxyServer server;
    private final Logger logger;
    private final EnhancedPurgatoryManager purgatoryManager;
    private final RewardsHandler rewardsHandler;
    private final XPManager xpManager;
    private final SQLHandler sqlHandler;
    private final boolean debugEnabled;

    /**
     * Constructor for BrigadierCommandHandler
     *
     * @param server The Velocity proxy server instance
     * @param logger The logger for logging messages
     * @param purgatoryManager The purgatory manager for verification
     * @param rewardsHandler The rewards handler for rank management
     * @param xpManager The XP manager for experience tracking
     * @param sqlHandler The SQL handler for database operations
     * @param debugEnabled Whether debug logging is enabled
     */
    public BrigadierCommandHandler(ProxyServer server, Logger logger, EnhancedPurgatoryManager purgatoryManager,
                                 RewardsHandler rewardsHandler, XPManager xpManager, SQLHandler sqlHandler, boolean debugEnabled) {
        this.server = server;
        this.logger = logger;
        this.purgatoryManager = purgatoryManager;
        this.rewardsHandler = rewardsHandler;
        this.xpManager = xpManager;
        this.sqlHandler = sqlHandler;
        this.debugEnabled = debugEnabled;
        
        // Initialize commands after all fields are set
        initializeCommands();
    }
    
    /**
     * initializeCommands
     * Initializes command registration in a way that's safe to call from constructor.
     * This method is private and final to prevent override issues.
     */
    private void initializeCommands() {
        try {
            this.registerCommands();
        } catch (RuntimeException e) {
            logger.error("Failed to register commands during initialization", e);
        }
    }

    /**
     * debugLog method
     * Logs debug messages when debug mode is enabled.
     * 
     * @param message The debug message to log
     */
    private void debugLog(String message) {
        if (debugEnabled) {
            logger.info("[BrigadierCommandHandler] " + message);
        }
    }

    /**
     * registerCommands method
     * Registers all VWL commands with the Velocity command manager.
     * Includes /verify, /rank, /xpchart, and VWL admin commands.
     */
    public void registerCommands() {
        CommandManager commandManager = server.getCommandManager();

        // Register /verify command
        CommandMeta verifyMeta = commandManager.metaBuilder("verify")
            .build();
        BrigadierCommand verifyCommand = createVerifyCommand();
        commandManager.register(verifyMeta, verifyCommand);
        debugLog("Registered /verify command");

        // Register /rank command
        CommandMeta rankMeta = commandManager.metaBuilder("rank")
            .build();
        BrigadierCommand rankCommand = createRankCommand();
        commandManager.register(rankMeta, rankCommand);
        debugLog("Registered /rank command");

        // Register /xpchart command
        CommandMeta xpChartMeta = commandManager.metaBuilder("xpchart")
            .build();
        BrigadierCommand xpChartCommand = createXPChartCommand();
        commandManager.register(xpChartMeta, xpChartCommand);
        debugLog("Registered /xpchart command");

        // Register /vwl admin command
        CommandMeta vwlMeta = commandManager.metaBuilder("vwl")
            .aliases("velocitywhitelist")
            .build();
        BrigadierCommand vwlCommand = createVWLCommand();
        commandManager.register(vwlMeta, vwlCommand);
        debugLog("Registered /vwl admin command");
    }

    /**
     * createVerifyCommand method
     * Creates the BrigadierCommand for the /verify command.
     * Allows players to verify their Discord account using a code.
     *
     * @return The BrigadierCommand instance for /verify
     */
    private BrigadierCommand createVerifyCommand() {
        LiteralCommandNode<CommandSource> verifyNode = BrigadierCommand.literalArgumentBuilder("verify")
            .then(BrigadierCommand.requiredArgumentBuilder("code", StringArgumentType.word())
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String code = context.getArgument("code", String.class);

                    if (source instanceof Player player) {
                        String username = player.getUsername();

                        debugLog("Player " + username + " attempting verification with code " + code);

                        // Check for active session
                        Optional<EnhancedPurgatoryManager.PurgatorySession> existingSession = purgatoryManager.getSession(username);
                        if (existingSession.isEmpty()) {
                            debugLog("No active session found for player " + username);
                            source.sendMessage(Component.text("You don't have an active verification session. Please use /mc command in our Discord server to start verification.", NamedTextColor.RED));
                            return Command.SINGLE_SUCCESS;
                        }

                        EnhancedPurgatoryManager.PurgatorySession session = existingSession.get();
                        debugLog("Found session for " + username + " (attempts: " + session.getVerificationAttempts() + ")");

                        // Check if session is expired
                        if (session.isExpired()) {
                            debugLog("Session expired for player " + username);
                            source.sendMessage(Component.text("Your verification session has expired. Please use /mc in Discord to get a new code.", NamedTextColor.RED));
                            purgatoryManager.removeSession(username);
                            return Command.SINGLE_SUCCESS;
                        }

                        // Check if code was already used
                        if (session.isCodeUsed()) {
                            debugLog("Attempt to use already-used code for player " + username);
                            source.sendMessage(Component.text("This code has already been used. Please use /mc in Discord to get a new code if needed.", NamedTextColor.RED));
                            purgatoryManager.removeSession(username);
                            return Command.SINGLE_SUCCESS;
                        }

                        // Validate the code
                        boolean validCode = purgatoryManager.validateCode(username, code);
                        
                        if (validCode) {
                            debugLog("Code validation successful for player " + username);
                            
                            // Complete verification
                            CompletableFuture<Boolean> completionFuture = purgatoryManager.completeVerification(username, player.getUniqueId());
                            completionFuture.thenAccept(success -> {
                                if (success) {
                                    source.sendMessage(Component.text("Successfully verified! You can now join the server.", NamedTextColor.GREEN));
                                    debugLog("Player " + username + " successfully verified");
                                    
                                    // Check if Discord was linked
                                    if (session.getDiscordUserId() != null) {
                                        source.sendMessage(Component.text("Your Minecraft account has been linked to Discord account " + session.getDiscordUsername(), NamedTextColor.GREEN));
                                        debugLog("Discord account linked: " + session.getDiscordUsername() + " (" + session.getDiscordUserId() + ")");
                                    }
                                } else {
                                    source.sendMessage(Component.text("An error occurred during verification. Please try again or contact an admin.", NamedTextColor.RED));
                                    debugLog("Failed to complete verification for player " + username);
                                }
                            });
                        } else {
                            // Handle failed validation
                            int attemptsLeft = EnhancedPurgatoryManager.PurgatorySession.MAX_VERIFICATION_ATTEMPTS - session.getVerificationAttempts();
                            debugLog("Validation failed for player " + username + " with code: " + code + " (" + attemptsLeft + " attempts left)");

                            // Check for max attempts
                            if (session.getVerificationAttempts() >= EnhancedPurgatoryManager.PurgatorySession.MAX_VERIFICATION_ATTEMPTS) {
                                debugLog("Player " + username + " reached max verification attempts, removing session");
                                source.sendMessage(Component.text("Too many failed attempts. Please use /mc to start over.", NamedTextColor.RED));
                                purgatoryManager.removeSession(username);
                            } else {
                                source.sendMessage(Component.text("Invalid verification code. Please check the code and try again. (" + attemptsLeft + " attempts remaining)", NamedTextColor.RED));
                                if (attemptsLeft <= 3) {
                                    source.sendMessage(Component.text("Warning: After " + attemptsLeft + " more failed attempts, you'll need to start over with /mc in Discord", NamedTextColor.GOLD));
                                }
                            }
                        }
                    } else {
                        if (source != null) {
                            source.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                        }
                        return 0;
                    }

                    return Command.SINGLE_SUCCESS;
                }))
            .executes(context -> {
                context.getSource().sendMessage(Component.text("Usage: /verify <code>", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            })
            .build();

        return new BrigadierCommand(verifyNode);
    }

    /**
     * createRankCommand method
     * Creates the BrigadierCommand for the /rank command.
     * Allows players to check their current rank and progress.
     *
     * @return The BrigadierCommand instance for /rank
     */
    private BrigadierCommand createRankCommand() {
        LiteralCommandNode<CommandSource> rankNode = BrigadierCommand.literalArgumentBuilder("rank")
            .executes(context -> {
                CommandSource source = context.getSource();
                if (source instanceof Player player) {
                    String username = player.getUsername();
                    try {
                        // Fetch rank and XP info from RewardsHandler
                        rewardsHandler.getPlayerRank(username).thenAccept(playerRank -> {
                            if (playerRank != null) {
                                source.sendMessage(Component.text("Your current rank: " + playerRank.getFormattedRank(), NamedTextColor.AQUA));
                                source.sendMessage(Component.text("Main Rank: " + playerRank.getMainRank() + ", Sub Rank: " + playerRank.getSubRank(), NamedTextColor.GREEN));
                                source.sendMessage(Component.text("Play Time: " + playerRank.getPlayTimeMinutes() + " minutes", NamedTextColor.YELLOW));
                                source.sendMessage(Component.text("Achievements: " + playerRank.getAchievementsCompleted(), NamedTextColor.YELLOW));
                            } else {
                                source.sendMessage(Component.text("Could not retrieve your rank information. Please try again later.", NamedTextColor.RED));
                            }
                        }).exceptionally(ex -> {
                            source.sendMessage(Component.text("Could not retrieve your rank information. Please try again later.", NamedTextColor.RED));
                            logger.error("Error fetching rank for player " + username, ex);
                            return null;
                        });
                    } catch (Exception e) {
                        source.sendMessage(Component.text("Could not retrieve your rank information. Please try again later.", NamedTextColor.RED));
                        logger.error("Error fetching rank for player " + username, e);
                    }
                } else {
                    if (source != null) {
                        source.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                    }
                }
                return Command.SINGLE_SUCCESS;
            })
            .build();

        return new BrigadierCommand(rankNode);
    }

    /**
     * createXPChartCommand method
     * Creates the BrigadierCommand for the /xpchart command.
     * Allows players to view a chart of XP required for each rank.
     *
     * @return The BrigadierCommand instance for /xpchart
     */
    private BrigadierCommand createXPChartCommand() {
        LiteralCommandNode<CommandSource> xpChartNode = BrigadierCommand.literalArgumentBuilder("xpchart")
            .executes(context -> {
                CommandSource source = context.getSource();
                try {
                    if (xpManager == null) {
                        source.sendMessage(Component.text("XP Chart feature is currently disabled.", NamedTextColor.YELLOW));
                        source.sendMessage(Component.text("Use /rank to check your current rank and progress.", NamedTextColor.GRAY));
                    } else {
                        source.sendMessage(Component.text("XP Chart feature is being implemented.", NamedTextColor.YELLOW));
                        source.sendMessage(Component.text("Use /rank to check your current rank and progress.", NamedTextColor.GRAY));
                    }
                } catch (Exception e) {
                    source.sendMessage(Component.text("Could not retrieve XP chart. Please try again later.", NamedTextColor.RED));
                    logger.error("Error fetching XP chart", e);
                }
                return Command.SINGLE_SUCCESS;
            })
            .build();

        return new BrigadierCommand(xpChartNode);
    }

    /**
     * createVWLCommand method
     * Creates the BrigadierCommand for the /vwl admin command.
     * Provides admin functionality for managing the whitelist.
     * 
     * @return The BrigadierCommand instance for /vwl
     */
    private BrigadierCommand createVWLCommand() {
        LiteralCommandNode<CommandSource> vwlNode = BrigadierCommand.literalArgumentBuilder("vwl")
            .requires(source -> source.hasPermission("velocitywhitelist.admin"))
            .then(BrigadierCommand.literalArgumentBuilder("add")
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .executes(context -> {
                        CommandSource source = context.getSource();
                        String playerName = context.getArgument("player", String.class);
                        
                        debugLog("Admin " + source + " adding player " + playerName + " to whitelist");
                        
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return sqlHandler.addToWhitelist(playerName, null);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }).thenAccept(success -> {
                            if (success) {
                                source.sendMessage(Component.text("Successfully added " + playerName + " to the whitelist.", NamedTextColor.GREEN));
                                debugLog("Successfully added " + playerName + " to whitelist");
                            } else {
                                source.sendMessage(Component.text("Player " + playerName + " is already whitelisted.", NamedTextColor.YELLOW));
                            }
                        }).exceptionally(ex -> {
                            source.sendMessage(Component.text("Error adding player to whitelist: " + ex.getMessage(), NamedTextColor.RED));
                            logger.error("Error adding player to whitelist", ex);
                            return null;
                        });
                        
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(BrigadierCommand.literalArgumentBuilder("del")
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .executes(context -> {
                        CommandSource source = context.getSource();
                        String playerName = context.getArgument("player", String.class);
                        
                        debugLog("Admin " + source + " removing player " + playerName + " from whitelist");
                        
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return sqlHandler.removeFromWhitelist(playerName);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }).thenAccept(success -> {
                            if (success) {
                                source.sendMessage(Component.text("Successfully removed " + playerName + " from the whitelist.", NamedTextColor.GREEN));
                                debugLog("Successfully removed " + playerName + " from whitelist");
                            } else {
                                source.sendMessage(Component.text("Player " + playerName + " was not on the whitelist.", NamedTextColor.YELLOW));
                            }
                        }).exceptionally(ex -> {
                            source.sendMessage(Component.text("Error removing player from whitelist: " + ex.getMessage(), NamedTextColor.RED));
                            logger.error("Error removing player from whitelist", ex);
                            return null;
                        });
                        
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(BrigadierCommand.literalArgumentBuilder("list")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    
                    debugLog("Admin " + source + " requesting whitelist");
                    
                    sqlHandler.listWhitelistedPlayers(null)
                        .thenAccept(players -> {
                            if (players.isEmpty()) {
                                source.sendMessage(Component.text("The whitelist is empty.", NamedTextColor.YELLOW));
                            } else {
                                source.sendMessage(Component.text("Whitelisted players (" + players.size() + "):", NamedTextColor.GREEN));
                                for (String player : players) {
                                    source.sendMessage(Component.text("- " + player, NamedTextColor.WHITE));
                                }
                            }
                        })
                        .exceptionally(ex -> {
                            source.sendMessage(Component.text("Error listing whitelisted players: " + ex.getMessage(), NamedTextColor.RED));
                            logger.error("Error listing whitelisted players", ex);
                            return null;
                        });
                    
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("search", StringArgumentType.word())
                    .executes(context -> {
                        CommandSource source = context.getSource();
                        String search = context.getArgument("search", String.class);
                        
                        debugLog("Admin " + source + " searching whitelist for: " + search);
                        
                        sqlHandler.listWhitelistedPlayers(search)
                            .thenAccept(players -> {
                                if (players.isEmpty()) {
                                    source.sendMessage(Component.text("No whitelisted players found matching '" + search + "'.", NamedTextColor.YELLOW));
                                } else {
                                    source.sendMessage(Component.text("Whitelisted players matching '" + search + "' (" + players.size() + "):", NamedTextColor.GREEN));
                                    for (String player : players) {
                                        source.sendMessage(Component.text("- " + player, NamedTextColor.WHITE));
                                    }
                                }
                            })
                            .exceptionally(ex -> {
                                source.sendMessage(Component.text("Error searching whitelisted players: " + ex.getMessage(), NamedTextColor.RED));
                                logger.error("Error searching whitelisted players", ex);
                                return null;
                            });
                        
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(BrigadierCommand.literalArgumentBuilder("reload")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    
                    debugLog("Admin " + source + " attempting configuration reload");
                    
                    // Note: Configuration reload requires restarting the plugin or proxy
                    // A full implementation would need access to the main plugin instance
                    source.sendMessage(Component.text("Configuration reload is not implemented yet.", NamedTextColor.YELLOW));
                    source.sendMessage(Component.text("Please restart the proxy to reload configuration changes.", NamedTextColor.GRAY));
                    
                    return Command.SINGLE_SUCCESS;
                }))
            .executes(context -> {
                CommandSource source = context.getSource();
                source.sendMessage(Component.text("VWL Commands:", NamedTextColor.GOLD));
                source.sendMessage(Component.text("/vwl add <player> - Add player to whitelist", NamedTextColor.WHITE));
                source.sendMessage(Component.text("/vwl del <player> - Remove player from whitelist", NamedTextColor.WHITE));
                source.sendMessage(Component.text("/vwl list [search] - List whitelisted players", NamedTextColor.WHITE));
                source.sendMessage(Component.text("/vwl reload - Reload configuration", NamedTextColor.WHITE));
                return Command.SINGLE_SUCCESS;
            })
            .build();

        return new BrigadierCommand(vwlNode);
    }
}
