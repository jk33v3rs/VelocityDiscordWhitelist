package top.jk33v3rs.velocitydiscordwhitelist.discord;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.modules.PurgatoryManager;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;

/**
 * DiscordListener
 * 
 * Handles Discord slash commands for verification using DiscordNotify-style simple patterns
 * while maintaining the robust purgatory system for controlled access.
 */
public class DiscordListener extends ListenerAdapter {
    
    private final Logger logger;
    private final ExceptionHandler exceptionHandler;
    private Guild guild;
    
    // Dependencies injected by main plugin
    private SQLHandler sqlHandler;
    private PurgatoryManager purgatoryManager;
    
    // Active verification sessions (Discord ID -> Code)
    private final Map<String, String> activeVerificationCodes = new ConcurrentHashMap<>();
    
    // Configuration
    private final String verificationChannelId;
    
    /**
     * Constructor for DiscordListener
     * 
     * @param logger The logger instance
     * @param exceptionHandler The exception handler
     * @param verificationChannelId The Discord verification channel ID
     */
    public DiscordListener(Logger logger, ExceptionHandler exceptionHandler, 
                          String verificationChannelId) {
        this.logger = logger;
        this.exceptionHandler = exceptionHandler;
        this.verificationChannelId = verificationChannelId;
    }
    
    /**
     * setDependencies
     * 
     * Injects dependencies from the main plugin. Called after plugin initialization.
     * 
     * @param sqlHandler The SQL handler
     * @param purgatoryManager The purgatory manager
     */
    public void setDependencies(SQLHandler sqlHandler, PurgatoryManager purgatoryManager) {
        this.sqlHandler = sqlHandler;
        this.purgatoryManager = purgatoryManager;
    }
    
    /**
     * setGuild
     * 
     * Sets the guild reference for the listener.
     * 
     * @param guild The Discord guild
     */
    public void setGuild(Guild guild) {
        this.guild = guild;
    }
    
    /**
     * onReady
     * 
     * Registers guild-specific commands when bot is ready.
     * 
     * @param event The ReadyEvent
     */
    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        if (guild != null) {
            registerCommands(guild);
        }
    }
    
    /**
     * registerCommands
     * 
     * Registers Discord slash commands for the guild.
     * 
     * @param guild The Discord guild to register commands for
     */
    private void registerCommands(Guild guild) {
        guild.updateCommands().addCommands(
            // Main verification command
            Commands.slash("mc", "Link your Minecraft account for server access")
                .addOption(OptionType.STRING, "playername", "Your Minecraft username", true),
            
            // Admin whitelist management command
            Commands.slash("whitelist", "Manage the server whitelist (Admin only)")
                .addOption(OptionType.STRING, "action", "Action to perform: add, remove, check, list", true)
                .addOption(OptionType.STRING, "username", "Minecraft username (not required for list)", false)
        ).queue(
            commands -> logger.info("Successfully registered {} Discord commands", commands.size()),
            throwable -> {
                logger.error("Failed to register Discord commands", throwable);
                exceptionHandler.handleDiscordException(null, "Discord command registration", throwable);
            }
        );
    }
    
    /**
     * onSlashCommandInteraction
     * 
     * Handles all slash command interactions.
     * 
     * @param event The slash command interaction event
     */
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        // Ensure we're in the right guild and channel
        Guild eventGuild = event.getGuild();
        if (guild == null || eventGuild == null || !eventGuild.equals(guild)) {
            return; // Ignore commands from other guilds
        }
        
        // Check if command is in verification channel (if specified)
        if (!verificationChannelId.isEmpty() && !event.getChannel().getId().equals(verificationChannelId)) {
            event.reply("❌ This command can only be used in the verification channel.").setEphemeral(true).queue();
            return;
        }
        
        // Defer reply to give us more time to process
        event.deferReply(true).queue();
        
        String commandName = event.getName();
        if ("mc".equals(commandName)) {
            handleMcCommand(event);
        } else if ("whitelist".equals(commandName)) {
            handleWhitelistCommand(event);
        } else {
            event.getHook().sendMessage("❌ Unknown command.").setEphemeral(true).queue();
        }
    }
    
    /**
     * handleMcCommand
     * 
     * Handles the /mc <playername> command for verification.
     * 
     * @param event The slash command interaction event
     */
    private void handleMcCommand(SlashCommandInteractionEvent event) {
        OptionMapping usernameOption = event.getOption("playername");
        if (usernameOption == null) {
            event.getHook().sendMessage("❌ Please provide your Minecraft username.").setEphemeral(true).queue();
            return;
        }
        
        String username = usernameOption.getAsString().trim();
        User user = event.getUser();
        
        // Basic username validation
        if (username.length() < 3 || username.length() > 16) {
            event.getHook().sendMessage("❌ Invalid username. Minecraft usernames must be 3-16 characters long.").setEphemeral(true).queue();
            return;
        }
        
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            event.getHook().sendMessage("❌ Invalid username. Minecraft usernames can only contain letters, numbers, and underscores.").setEphemeral(true).queue();
            return;
        }
        
        if (sqlHandler == null) {
            event.getHook().sendMessage("❌ Database is not available. Please try again later.").setEphemeral(true).queue();
            return;
        }
        
        try {
            // Check if player is already whitelisted
            boolean isWhitelisted = sqlHandler.isPlayerWhitelistedByUsername(username);
            if (isWhitelisted) {
                event.getHook().sendMessage("✅ You are already whitelisted! You can join the server directly.").setEphemeral(true).queue();
                return;
            }
            
            // Check if user already has an active session
            if (activeVerificationCodes.containsKey(user.getId())) {
                event.getHook().sendMessage("⏳ You already have an active verification session. Please complete it first or wait for it to expire.").setEphemeral(true).queue();
                return;
            }
            
            // Create purgatory session
            createPurgatorySession(event, username, user);
            
        } catch (Exception e) {
            logger.error("Error processing /mc command for user " + user.getId() + " with username " + username, e);
            exceptionHandler.handleDiscordException(event, "Error processing /mc command", e);
            event.getHook().sendMessage("❌ An error occurred while processing your request. Please try again later.").setEphemeral(true).queue();
        }
    }
    
    /**
     * createPurgatorySession
     * 
     * Creates a purgatory session for the user and provides instructions.
     * 
     * @param event The slash command interaction event
     * @param username The Minecraft username
     * @param user The Discord user
     */
    private void createPurgatorySession(SlashCommandInteractionEvent event, String username, User user) {
        if (purgatoryManager == null) {
            event.getHook().sendMessage("❌ Purgatory system is not available. Please try again later.").setEphemeral(true).queue();
            return;
        }
        
        try {
            // Generate verification code
            String verificationCode = generateVerificationCode();
            activeVerificationCodes.put(user.getId(), verificationCode);
            
            // Parse Discord user ID
            long discordUserId = Long.parseLong(user.getId());
            
            // Create purgatory session using the manager's method
            PurgatoryManager.ValidationResult result = purgatoryManager.createDiscordSession(
                username, discordUserId, user.getName()
            );
            
            if (!result.isSuccess()) {
                activeVerificationCodes.remove(user.getId());
                event.getHook().sendMessage("❌ Failed to create verification session: " + result.getErrorMessage()).setEphemeral(true).queue();
                return;
            }
            
            // Success message with instructions
            StringBuilder message = new StringBuilder();
            message.append("🎮 **Verification Started!**\n\n");
            message.append("Your Minecraft account `").append(username).append("` has been temporarily whitelisted.\n\n");
            message.append("**Next Steps:**\n");
            message.append("1. Join the Minecraft server\n");
            message.append("2. You'll be automatically verified on join!\n");
            message.append("3. Complete any onboarding tasks as instructed\n\n");
            message.append("⏰ This session will expire in 10 minutes if unused.\n");
            message.append("🔒 You'll have limited access until verification is complete.");
            
            event.getHook().sendMessage(message.toString()).setEphemeral(true).queue();
            
            logger.info("Created purgatory session for Discord user {} with Minecraft username {}", user.getId(), username);
            
        } catch (NumberFormatException e) {
            activeVerificationCodes.remove(user.getId());
            logger.error("Invalid Discord user ID format: " + user.getId(), e);
            event.getHook().sendMessage("❌ Invalid Discord user ID format. Please try again later.").setEphemeral(true).queue();
        } catch (Exception e) {
            activeVerificationCodes.remove(user.getId());
            logger.error("Error creating purgatory session for user " + user.getId() + " with username " + username, e);
            exceptionHandler.handleDiscordException(event, "Error creating purgatory session", e);
            event.getHook().sendMessage("❌ An error occurred while creating your verification session. Please try again later.").setEphemeral(true).queue();
        }
    }
    
    /**
     * generateVerificationCode
     * 
     * Generates a random verification code.
     * 
     * @return A 6-digit verification code in XXX-XXX format
     */
    private String generateVerificationCode() {
        // Generate 6 random digits
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append((int) (Math.random() * 10));
        }
        // Format as XXX-XXX
        return code.substring(0, 3) + "-" + code.substring(3);
    }
    
    /**
     * completeVerification
     * 
     * Completes the verification process for a user.
     * 
     * @param discordUserId The Discord user ID
     * @param username The Minecraft username
     * @return true if verification was successful
     */
    public boolean completeVerification(String discordUserId, String username) {
        try {
            // Remove from active codes
            activeVerificationCodes.remove(discordUserId);
            
            // Remove purgatory session
            if (purgatoryManager != null) {
                purgatoryManager.removeSession(username);
            }
            
            logger.info("Completed verification for Discord user {} with Minecraft username {}", discordUserId, username);
            return true;
            
        } catch (Exception e) {
            logger.error("Error completing verification for Discord user {} with username {}", discordUserId, username, e);
            exceptionHandler.handleDiscordException(null, "Error completing verification", e);
            return false;
        }
    }
    
    /**
     * handleWhitelistCommand
     * 
     * Handles the /whitelist command for admin whitelist management.
     * 
     * @param event The slash command interaction event
     */
    private void handleWhitelistCommand(SlashCommandInteractionEvent event) {
        // Check if user has admin permissions
        if (!isUserAdmin(event.getUser())) {
            event.getHook().sendMessage("❌ You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        OptionMapping actionOption = event.getOption("action");
        OptionMapping usernameOption = event.getOption("username");
        
        if (actionOption == null || usernameOption == null) {
            event.getHook().sendMessage("❌ Please provide both action and username.").setEphemeral(true).queue();
            return;
        }
        
        String action = actionOption.getAsString().toLowerCase();
        String username = usernameOption.getAsString().trim();
        
        // Basic username validation
        if (username.length() < 3 || username.length() > 16 || !username.matches("^[a-zA-Z0-9_]+$")) {
            event.getHook().sendMessage("❌ Invalid username format.").setEphemeral(true).queue();
            return;
        }
        
        if (sqlHandler == null) {
            event.getHook().sendMessage("❌ Database is not available.").setEphemeral(true).queue();
            return;
        }
        
        try {
            switch (action) {
                case "add" -> handleWhitelistAdd(event, username);
                case "remove" -> handleWhitelistRemove(event, username);
                case "check" -> handleWhitelistCheck(event, username);
                case "list" -> handleWhitelistList(event, username);
                default -> event.getHook().sendMessage("❌ Invalid action. Use: add, remove, check, or list").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            logger.error("Error processing whitelist command for user " + event.getUser().getId() + " with action " + action + " and username " + username, e);
            exceptionHandler.handleDiscordException(event, "Error processing whitelist command", e);
            event.getHook().sendMessage("❌ An error occurred while processing the command.").setEphemeral(true).queue();
        }
    }
    
    /**
     * isUserAdmin
     * 
     * Checks if a Discord user has admin permissions.
     * 
     * @param user The Discord user
     * @return true if user is admin, false otherwise
     */
    private boolean isUserAdmin(User user) {
        if (guild == null || user == null) {
            return false;
        }
        
        try {
            var member = guild.getMember(user);
            if (member == null) {
                return false;
            }
            
            // Check if user has admin permission or manage server permission
            return member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR) ||
                   member.hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER);
        } catch (Exception e) {
            logger.error("Error checking admin permissions for user " + user.getId(), e);
            return false;
        }
    }
    
    /**
     * handleWhitelistAdd
     * 
     * Handles adding a player to the whitelist.
     * 
     * @param event The slash command interaction event
     * @param username The username to add
     */
    private void handleWhitelistAdd(SlashCommandInteractionEvent event, String username) {
        try {
            // Check if already whitelisted
            boolean isAlreadyWhitelisted = sqlHandler.isPlayerWhitelistedByUsername(username);
            if (isAlreadyWhitelisted) {
                event.getHook().sendMessage("⚠️ Player **" + username + "** is already whitelisted.").setEphemeral(true).queue();
                return;
            }
            
            // Add to whitelist using admin command
            sqlHandler.addPlayerToWhitelistByUsername(username, event.getUser().getId())
                .thenAccept(success -> {
                    if (success) {
                        event.getHook().sendMessage("✅ Player **" + username + "** has been added to the whitelist.").setEphemeral(true).queue();
                        logger.info("Admin {} added {} to whitelist", event.getUser().getName(), username);
                    } else {
                        event.getHook().sendMessage("❌ Failed to add player to whitelist.").setEphemeral(true).queue();
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error adding player to whitelist: " + username, throwable);
                    event.getHook().sendMessage("❌ Failed to add player to whitelist.").setEphemeral(true).queue();
                    return null;
                });
            
        } catch (Exception e) {
            logger.error("Error adding player to whitelist: " + username, e);
            event.getHook().sendMessage("❌ Failed to add player to whitelist.").setEphemeral(true).queue();
        }
    }
    
    /**
     * handleWhitelistRemove
     * 
     * Handles removing a player from the whitelist.
     * 
     * @param event The slash command interaction event
     * @param username The username to remove
     */
    private void handleWhitelistRemove(SlashCommandInteractionEvent event, String username) {
        try {
            // Check if currently whitelisted
            boolean isWhitelisted = sqlHandler.isPlayerWhitelistedByUsername(username);
            if (!isWhitelisted) {
                event.getHook().sendMessage("⚠️ Player **" + username + "** is not on the whitelist.").setEphemeral(true).queue();
                return;
            }
            
            // Remove from whitelist using admin command
            sqlHandler.removePlayerFromWhitelistByUsername(username, event.getUser().getId())
                .thenAccept(success -> {
                    if (success) {
                        event.getHook().sendMessage("✅ Player **" + username + "** has been removed from the whitelist.").setEphemeral(true).queue();
                        logger.info("Admin {} removed {} from whitelist", event.getUser().getName(), username);
                    } else {
                        event.getHook().sendMessage("❌ Failed to remove player from whitelist.").setEphemeral(true).queue();
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error removing player from whitelist: " + username, throwable);
                    event.getHook().sendMessage("❌ Failed to remove player from whitelist.").setEphemeral(true).queue();
                    return null;
                });
            
        } catch (Exception e) {
            logger.error("Error removing player from whitelist: " + username, e);
            event.getHook().sendMessage("❌ Failed to remove player from whitelist.").setEphemeral(true).queue();
        }
    }
    
    /**
     * handleWhitelistCheck
     * 
     * Handles checking if a player is on the whitelist.
     * 
     * @param event The slash command interaction event
     * @param username The username to check
     */
    private void handleWhitelistCheck(SlashCommandInteractionEvent event, String username) {
        try {
            boolean isWhitelisted = sqlHandler.isPlayerWhitelistedByUsername(username);
            
            if (isWhitelisted) {
                event.getHook().sendMessage("✅ Player **" + username + "** is whitelisted.").setEphemeral(true).queue();
            } else {
                event.getHook().sendMessage("❌ Player **" + username + "** is not whitelisted.").setEphemeral(true).queue();
            }
            
        } catch (Exception e) {
            logger.error("Error checking whitelist status for: " + username, e);
            event.getHook().sendMessage("❌ Failed to check whitelist status.").setEphemeral(true).queue();
        }
    }
    
    /**
     * handleWhitelistList
     * 
     * Handles listing whitelisted players.
     * 
     * @param event The SlashCommandInteractionEvent
     * @param searchTerm Optional search term to filter results
     */
    private void handleWhitelistList(SlashCommandInteractionEvent event, String searchTerm) {
        if (sqlHandler == null) {
            event.getHook().sendMessage("❌ Database is not available.").setEphemeral(true).queue();
            return;
        }
        
        CompletableFuture.supplyAsync(() -> {
            try {
                return sqlHandler.listWhitelistedPlayers(searchTerm, 50); // Limit to 50 results
            } catch (Exception e) {
                logger.error("Error listing whitelisted players", e);
                throw new RuntimeException(e);
            }
        }).thenAccept(players -> {
            if (players.isEmpty()) {
                String message = searchTerm != null && !searchTerm.trim().isEmpty() 
                    ? "❌ No whitelisted players found matching: `" + searchTerm + "`"
                    : "❌ No players are currently whitelisted.";
                event.getHook().sendMessage(message).setEphemeral(true).queue();
            } else {
                StringBuilder response = new StringBuilder();
                String title = searchTerm != null && !searchTerm.trim().isEmpty()
                    ? "🔍 Whitelisted players matching `" + searchTerm + "` (" + players.size() + " found):"
                    : "📋 Whitelisted players (" + players.size() + " total):";
                
                response.append(title).append("\n```\n");
                for (String player : players) {
                    response.append("• ").append(player).append("\n");
                }
                response.append("```");
                
                if (players.size() >= 50) {
                    response.append("\n*Note: Results limited to 50 players. Use search to filter.*");
                }
                
                event.getHook().sendMessage(response.toString()).setEphemeral(true).queue();
            }
        }).exceptionally(ex -> {
            event.getHook().sendMessage("❌ Error listing whitelisted players: " + ex.getMessage()).setEphemeral(true).queue();
            exceptionHandler.handleDatabaseException("whitelist list", ex, "Search: " + searchTerm);
            return null;
        });
    }
    
    /**
     * handlePlayerJoin
     * 
     * Handles when a player joins the server for verification purposes.
     * Called by the main plugin when a player attempts to join.
     * 
     * @param player The player attempting to join
     * @return true if player should be allowed to join, false otherwise
     */
    public boolean handlePlayerJoin(com.velocitypowered.api.proxy.Player player) {
        String username = player.getUsername();
        
        try {
            // Check if player is already fully whitelisted
            if (sqlHandler != null && sqlHandler.isPlayerWhitelistedByUsername(username)) {
                logger.debug("Player {} is whitelisted, allowing join", username);
                return true;
            }
            
            // Check if player has an active purgatory session
            if (purgatoryManager != null && purgatoryManager.isInPurgatory(username)) {
                logger.debug("Player {} is in purgatory, allowing limited join", username);
                return true;
            }
            
            // Player is not whitelisted and not in purgatory
            logger.debug("Player {} is not whitelisted or in purgatory, denying join", username);
            return false;
            
        } catch (Exception e) {
            logger.error("Error handling player join for {}", username, e);
            // On error, deny join for safety
            return false;
        }
    }
}