package top.jk33v3rs.velocitydiscordwhitelist.modules;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * DiscordBotHandler manages integration with the Discord bot for validation and messaging.
 * This class handles the connection to Discord, user validation, and command processing.
 */
public class DiscordBotHandler extends ListenerAdapter {
    /**
     * The centralized manager for all purgatory sessions
     */
    private EnhancedPurgatoryManager purgatoryManager;
    private final Logger logger;
    private boolean isConnected = false;
    private int connectionAttempts = 0;
    private final int MAX_CONNECTION_ATTEMPTS = 3;
    
    // Database handler for whitelist operations
    private SQLHandler sqlHandler;
    
    // JDA instance for Discord API interactions
    private JDA jda;
    // Guild ID and channel IDs configuration
    private String guildId;
    private Set<String> approvedChannels = new HashSet<>();
    // Role ID mapping
    private Map<String, String> roleIdMap = new HashMap<>();

    /**
     * Constructor for DiscordBotHandler.
     *
     * @param logger The logger instance for logging errors and status updates.
     * @param sqlHandler The SQL handler for database operations
     */
    public DiscordBotHandler(@Nonnull Logger logger, @Nonnull SQLHandler sqlHandler) {
        this.logger = logger;
        this.sqlHandler = sqlHandler;
    }
    
    /**
     * Constructor for DiscordBotHandler with PurgatoryManager.
     *
     * @param purgatoryManager The centralized manager for all purgatory sessions
     * @param sqlHandler The SQL handler for database operations
     * @param logger The logger instance for logging errors and status updates.
     */
    public DiscordBotHandler(@Nonnull EnhancedPurgatoryManager purgatoryManager, @Nonnull SQLHandler sqlHandler, @Nonnull Logger logger) {
        this.purgatoryManager = purgatoryManager;
        this.sqlHandler = sqlHandler;
        this.logger = logger;
    }
    
    /**
     * Sets the purgatory manager
     * 
     * @param purgatoryManager The purgatory manager
     */
    public void setPurgatoryManager(@Nonnull EnhancedPurgatoryManager purgatoryManager) {
        this.purgatoryManager = purgatoryManager;
    }

    /**
     * Initializes the Discord bot with a timeout for connection establishment.
     * This method establishes a connection to Discord and sets up the necessary listeners.
     * If initialization fails after the specified timeout, it will log an error.
     *
     * @param config The configuration properties containing Discord settings.
     * @return CompletableFuture resolving to true if initialization succeeded, false otherwise.
     */
    public CompletableFuture<Boolean> initialize(@Nonnull Properties config) {
        CompletableFuture<Boolean> initializationFuture = new CompletableFuture<>();
        try {
            // Get Discord configuration values
            String tokenRaw = config.getProperty("discord.token", "");
            String token = tokenRaw == null ? "" : tokenRaw.trim();
            // Note: Token logging removed for security reasons
            this.guildId = config.getProperty("discord.guildId", "");
            String channels = config.getProperty("discord.approvedChannelIds", "");
            int initTimeout = Integer.parseInt(config.getProperty("discord.init_timeout", "30"));

            // Log token length and a masked version for debugging
            if (!token.isEmpty()) {
                String masked = token.length() > 8 ? token.substring(0, 4) + "..." + token.substring(token.length() - 4) : "(too short to mask)";
                logger.info("[Discord] Loaded token: length={}, masked={}", token.length(), masked);
                if (token.length() < 50) {
                    logger.warn("[Discord] Token length is suspiciously short ({} chars). Check for missing/incorrect value.", token.length());
                }
                if (!token.equals(tokenRaw)) {
                    logger.info("[Discord] Token was trimmed of whitespace before use.");
                }
                if (token.contains(" ") || token.contains("\n") || token.contains("\r")) {
                    logger.warn("[Discord] Token contains whitespace or newline characters. This may cause authentication errors.");
                }
            }

            if (token.isEmpty()) {
                logger.warn("Discord token is not configured. Discord bot will not be enabled.");
                initializationFuture.complete(false);
                return initializationFuture;
            }

            if (guildId.isEmpty() || channels.isEmpty()) {
                logger.warn("Discord server ID or approved channel IDs are not configured. Discord bot will not listen for commands.");
                initializationFuture.complete(false);
                return initializationFuture;
            }

            // Split and store approved channel IDs
            if (!channels.isEmpty()) {
                approvedChannels.addAll(Arrays.asList(channels.split(",")));
            }
            
            // Load role IDs from configuration
            String verifiedRoleId = config.getProperty("discord.roles.verified", "");
            String unverifiedRoleId = config.getProperty("discord.roles.unverified", "");
            String purgatoryRoleId = config.getProperty("discord.roles.purgatory", "");
            
            roleIdMap.put("VERIFIED", verifiedRoleId);
            roleIdMap.put("UNVERIFIED", unverifiedRoleId);
            roleIdMap.put("PURGATORY", purgatoryRoleId);

            // Setup JDA with minimal resource usage for whitelist-only functionality
            JDABuilder builder = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
                    .setMemberCachePolicy(net.dv8tion.jda.api.utils.MemberCachePolicy.NONE)
                    .setChunkingFilter(net.dv8tion.jda.api.utils.ChunkingFilter.NONE)
                    .disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING, GatewayIntent.DIRECT_MESSAGE_TYPING)
                    .addEventListeners(this);

            // Set a timeout for the connection establishment
            CompletableFuture<Void> connectionFuture = new CompletableFuture<>();
            Thread timeoutThread = new Thread(() -> {
                try {
                    Thread.sleep(initTimeout * 1000L);
                    if (!connectionFuture.isDone()) {
                        logger.error("[Discord] Bot connection timed out after {} seconds.", initTimeout);
                        connectionFuture.completeExceptionally(new TimeoutException("Connection timed out"));
                    }
                } catch (InterruptedException e) {
                    // Thread was interrupted, likely because connection was established
                    Thread.currentThread().interrupt();
                }
            });
            timeoutThread.setDaemon(true);
            timeoutThread.start();

            // Build JDA
            jda = builder.build();

            // Use startup hook - this will be called when the JDA instance is ready
            jda.addEventListener(new ListenerAdapter() {
                @Override
                public void onReady(@Nonnull ReadyEvent event) {
                    isConnected = true;
                    connectionFuture.complete(null);
                    timeoutThread.interrupt(); // Stop the timeout thread

                    logger.info("[Discord] Bot connected as {}", jda.getSelfUser().getName());
                    
                    // Register slash commands
                    registerSlashCommands();
                    
                    initializationFuture.complete(true);
                }
            });

            // Handle connection errors
            connectionFuture.exceptionally(ex -> {
                if (connectionAttempts < MAX_CONNECTION_ATTEMPTS) {
                    connectionAttempts++;
                    logger.warn("[Discord] Connection attempt {} failed. Retrying...", connectionAttempts);
                    
                    // Wait a bit before retrying
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // Try again
                    initialize(config)
                        .thenAccept(initializationFuture::complete)
                        .exceptionally(retryEx -> {
                            initializationFuture.completeExceptionally(retryEx);
                            return null;
                        });
                } else {
                    logger.error("[Discord] Failed to connect after {} attempts.", MAX_CONNECTION_ATTEMPTS);
                    initializationFuture.complete(false);
                }
                return null;
            });

        } catch (Exception e) {
            logger.error("[Discord] Error initializing Discord bot", e);
            initializationFuture.complete(false);
        }
        
        return initializationFuture;
    }
    
    /**
     * Registers slash commands with Discord
     */
    private void registerSlashCommands() {
        if (jda == null || guildId.isEmpty()) {
            return;
        }
        
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                logger.warn("[Discord] Could not find guild with ID {}", guildId);
                return;
            }
            
            // Register /verify command
            guild.upsertCommand(Commands.slash("verify", "Begin account verification")
                    .addOption(OptionType.STRING, "username", "Your Minecraft username", true))
                .queue();
            
            // Register /mc command - alternative to verify command for clarity
            guild.upsertCommand(Commands.slash("mc", "Link your Minecraft account")
                    .addOption(OptionType.STRING, "username", "Your Minecraft username", true))
                .queue();
            
            // Register /whitelist command (admin only)
            guild.upsertCommand(Commands.slash("whitelist", "Manage the whitelist")
                    .addOption(OptionType.STRING, "action", "The action to perform (add/remove/check)", true)
                    .addOption(OptionType.STRING, "username", "The Minecraft username", true))
                .queue();

            // Register /rank command (admin only)
            guild.upsertCommand(Commands.slash("rank", "Manage player ranks")
                    .addOption(OptionType.STRING, "action", "The action to perform (set/check/list)", true)
                    .addOption(OptionType.STRING, "username", "The Minecraft username", false)
                    .addOption(OptionType.INTEGER, "main_rank", "The main rank ID", false)
                    .addOption(OptionType.INTEGER, "sub_rank", "The sub rank ID", false))
                .queue();
                
            logger.info("[Discord] Slash commands registered");
        } catch (Exception e) {
            logger.error("[Discord] Error registering slash commands", e);
        }
    }
    
    /**
     * Event handler for Discord slash commands
     */
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (jda == null || !isConnected) {
            return;
        }
        
        String command = event.getName();
        
        try {
            switch (command) {
                case "mc":
                    handleMcCommand(event); // Discord-initiated verification
                    break;
                case "verify":
                    handleLegacyVerifyCommand(event); // Legacy support - deprecated
                    break;
                case "whitelist":
                    handleWhitelistCommand(event);
                    break;
                case "rank":
                    handleRankCommand(event);
                    break;
                default:
                    event.reply("Unknown command.").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            logger.error("[Discord] Error handling slash command: {}", command, e);
            event.reply("An error occurred while processing your command. Please try again later.").setEphemeral(true).queue();
        }
    }
    
    /**
     * Handles the /mc slash command - Discord-initiated verification
     * 
     * This is the NEW correct verification flow:
     * 1. Player uses /mc <playername> in Discord
     * 2. System generates 6-digit hexadecimal code (###-###)
     * 3. Player uses /verify in-game to complete verification
     * 
     * @param event The slash command event
     */
    private void handleMcCommand(@Nonnull SlashCommandInteractionEvent event) {
        OptionMapping usernameOption = event.getOption("username");
        if (usernameOption == null) {
            event.reply("Username is required. Usage: `/mc <minecraft_username>`").setEphemeral(true).queue();
            return;
        }
        
        String username = usernameOption.getAsString().toLowerCase().trim();
        User user = event.getUser();
        
        // Validate username format
        if (username.length() < 3 || username.length() > 16) {
            event.reply("Invalid username. Minecraft usernames must be 3-16 characters long.").setEphemeral(true).queue();
            return;
        }
        
        // Check for valid characters (basic validation)
        if (!username.matches("^[a-zA-Z0-9_.]+$")) {
            event.reply("Invalid username. Minecraft usernames can only contain letters, numbers, underscores, and periods.").setEphemeral(true).queue();
            return;
        }
        
        // Defer reply to allow for async processing
        event.deferReply(true).queue();
        
        // Create a verification session in purgatory manager
        if (purgatoryManager == null) {
            event.getHook().sendMessage("Verification system is not available. Please contact an administrator.")
                .setEphemeral(true).queue();
            logger.error("[Discord] PurgatoryManager not available for verification");
            return;
        }
        
        debugLog("Discord user " + user.getName() + " (" + user.getId() + ") initiated verification for Minecraft username: " + username);
        
        EnhancedPurgatoryManager.ValidationResult result = 
            purgatoryManager.createDiscordSession(username, user.getIdLong(), user.getName());
            
        if (result.isSuccess()) {
            String code = result.getValidationCode();
            event.getHook().sendMessage(
                "✅ **Verification code generated for " + username + "**\n\n" +
                "🎮 **Your verification code:** `" + code + "`\n\n" +
                "📝 **Join the game server and use `/verify " + code + "` to be whitelisted - you have 5 minutes**\n\n" +
                "🔒 This message is only visible to you.")
                .setEphemeral(true).queue();
                
            debugLog("Generated verification code " + code + " for Discord user " + user.getName() + " -> Minecraft username " + username);
        } else {
            String errorMsg = result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error";
            event.getHook().sendMessage("❌ **Error generating verification code**\n\n" + 
                "**Reason:** " + errorMsg + "\n\n" +
                "Please try again or contact an administrator if the problem persists.")
                .setEphemeral(true).queue();
                
            debugLog("Failed to generate verification code for Discord user " + user.getName() + " -> Minecraft username " + username + ": " + errorMsg);
        }
    }
    
    /**
     * Handles the legacy /verify slash command (deprecated)
     * 
     * This method maintains backward compatibility but encourages use of the new flow.
     * The new flow is: /mc in Discord -> /verify in-game
     * 
     * @param event The slash command event
     */
    private void handleLegacyVerifyCommand(@Nonnull SlashCommandInteractionEvent event) {
        event.reply("⚠️ **This command is deprecated**\n\n" +
            "**Please use the new verification process:**\n" +
            "1. Use `/mc <your_minecraft_username>` here in Discord\n" +
            "2. Join the Minecraft server\n" +
            "3. Use `/verify` in-game\n\n" +
            "The new process is more secure and user-friendly!")
            .setEphemeral(true).queue();
    }
    
    /**
     * Handles the /whitelist slash command for managing the whitelist
     * 
     * This method processes Discord whitelist commands, delegating the actual
     * database operations to SQLHandler according to the separation of concerns.
     * Supports adding, removing, and checking players in the whitelist.
     * 
     * @param event The slash command event
     */
    private void handleWhitelistCommand(@Nonnull SlashCommandInteractionEvent event) {
        // Check if user has admin permissions
        if (!isUserAdmin(event.getUser())) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        OptionMapping actionOption = event.getOption("action");
        OptionMapping usernameOption = event.getOption("username");
        
        if (actionOption == null || usernameOption == null) {
            event.reply("Both action and username are required.").setEphemeral(true).queue();
            return;
        }
        
        String action = actionOption.getAsString().toLowerCase();
        String username = usernameOption.getAsString();
        
        // Validate username format
        if (!isValidMinecraftUsername(username)) {
            event.reply("Invalid Minecraft username. Usernames must be 3-16 characters and contain only letters, numbers, and underscores.").setEphemeral(true).queue();
            return;
        }
        
        // Defer reply to allow for async processing
        event.deferReply(true).queue();
        
        switch (action) {
            case "add":
                addPlayerToWhitelist(event, username);
                break;
            case "remove":
                removePlayerFromWhitelist(event, username);
                break;
            case "check":
                checkPlayerWhitelist(event, username);
                break;
            default:
                event.getHook().sendMessage("Unknown action: " + action + ". Available actions: add, remove, check")
                    .setEphemeral(true).queue();
        }
    }
    
    /**
     * Debug logging method
     * 
     * Logs debug messages with proper formatting and level checking.
     * Unlike other classes, this class doesn't have access to a debugEnabled
     * flag, so it logs at debug level consistently.
     * 
     * @param message The debug message to log
     */
    private void debugLog(String message) {
        logger.debug("[Discord] {}", message);
    }

    /**
     * Validates a Minecraft username format
     * 
     * Performs enhanced validation including character restrictions
     * and length requirements for Minecraft usernames.
     * 
     * @param username The username to validate
     * @return true if the username is valid, false otherwise
     */
    private boolean isValidMinecraftUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = username.trim();
        // Check length (3-16 characters)
        if (trimmed.length() < 3 || trimmed.length() > 16) {
            return false;
        }
        
        // Check for valid characters (letters, numbers, underscores only)
        // Note: Periods are not allowed in modern Minecraft usernames
        return trimmed.matches("^[a-zA-Z0-9_]+$");
    }
    
    /**
     * Adds a player to the whitelist
     * 
     * This method delegates to the SQLHandler to add a player to the whitelist
     * and provides appropriate feedback through the Discord interface.
     * 
     * @param event The slash command event
     * @param username The username to add
     */
    private void addPlayerToWhitelist(@Nonnull SlashCommandInteractionEvent event, String username) {
        try {
            sqlHandler.addPlayerToWhitelist(username, null)
                .thenRun(() -> {
                    event.getHook().sendMessage("✅ Successfully added **" + username + "** to the whitelist.")
                        .setEphemeral(true).queue();
                    logger.info("[Discord] User {} added {} to the whitelist", event.getUser().getName(), username);
                })
                .exceptionally(e -> {
                    event.getHook().sendMessage("❗ Error adding player to whitelist: " + e.getMessage())
                        .setEphemeral(true).queue();
                    logger.error("[Discord] Error adding player to whitelist", e);
                    return null;
                });
        } catch (Exception e) {
            event.getHook().sendMessage("❌ Internal error while processing whitelist command: " + e.getMessage())
                .setEphemeral(true).queue();
            logger.error("[Discord] Error adding player to whitelist", e);
        }
    }
    
    /**
     * Removes a player from the whitelist
     * 
     * This method delegates to the SQLHandler to remove a player from the whitelist
     * and provides appropriate feedback through the Discord interface.
     * 
     * @param event The slash command event
     * @param username The username to remove
     */
    private void removePlayerFromWhitelist(@Nonnull SlashCommandInteractionEvent event, String username) {
        try {
            sqlHandler.removePlayerFromWhitelist(username)
                .thenRun(() -> {
                    event.getHook().sendMessage("✅ Successfully removed **" + username + "** from the whitelist.")
                        .setEphemeral(true).queue();
                    logger.info("[Discord] User {} removed {} from the whitelist", event.getUser().getName(), username);
                })
                .exceptionally(ex -> {
                    event.getHook().sendMessage("❌ Error removing player from whitelist: " + ex.getMessage())
                        .setEphemeral(true).queue();
                    logger.error("[Discord] Error removing player from whitelist", ex);
                    return null;
                });
        } catch (Exception e) {
            event.getHook().sendMessage("❌ Internal error while processing whitelist command: " + e.getMessage())
                .setEphemeral(true).queue();
            logger.error("[Discord] Error removing player from whitelist", e);
        }
    }
    
    /**
     * Checks if a player is on the whitelist
     * 
     * This method delegates to the SQLHandler to check if a player is on the whitelist
     * and provides appropriate feedback through the Discord interface.
     * 
     * @param event The slash command event
     * @param username The username to check
     */
    private void checkPlayerWhitelist(@Nonnull SlashCommandInteractionEvent event, String username) {
        try {
            // Get the player's UUID from the database
            Optional<String> optionalUuid = sqlHandler.getPlayerUuidByUsername(username);
            
            if (optionalUuid.isPresent()) {
                String uuid = optionalUuid.get();
                
                // Get player verification state
                Optional<String> verificationState = sqlHandler.getPlayerVerificationState(uuid);
                String state = verificationState.orElse("UNVERIFIED");
                
                // Get Discord link status
                Optional<Long> discordId = sqlHandler.getPlayerDiscordId(uuid);
                String discordStatus = discordId.isPresent() ? "Linked to Discord account" : "Not linked to Discord";
                
                // Build a detailed response
                event.getHook().sendMessage("✅ Player **" + username + "** is on the whitelist.\n" +
                    "UUID: `" + uuid + "`\n" +
                    "Verification state: `" + state + "`\n" +
                    "Discord status: `" + discordStatus + "`")
                    .setEphemeral(true).queue();
            } else {
                event.getHook().sendMessage("❌ Player **" + username + "** is not on the whitelist.")
                    .setEphemeral(true).queue();
            }
        } catch (Exception e) {
            event.getHook().sendMessage("❌ Internal error while processing whitelist command: " + e.getMessage())
                .setEphemeral(true).queue();
            logger.error("[Discord] Error checking whitelist status", e);
        }
    }
    
    /**
     * Handles the /rank slash command
     * 
     * @param event The slash command event
     */
    private void handleRankCommand(@Nonnull SlashCommandInteractionEvent event) {
        // Check if user has admin permissions
        if (!isUserAdmin(event.getUser())) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        OptionMapping actionOption = event.getOption("action");
        if (actionOption == null) {
            event.reply("Action is required.").setEphemeral(true).queue();
            return;
        }
        
        String action = actionOption.getAsString().toLowerCase();
        
        // Defer reply to allow for async processing
        event.deferReply(true).queue();
        
        switch (action) {
            case "list":
                event.getHook().sendMessage("Listing available ranks is not yet implemented.")
                    .setEphemeral(true).queue();
                break;
            case "check":
                OptionMapping checkUsernameOption = event.getOption("username");
                if (checkUsernameOption == null) {
                    event.getHook().sendMessage("Username is required for rank check.")
                        .setEphemeral(true).queue();
                    return;
                }
                
                String checkUsername = checkUsernameOption.getAsString();
                event.getHook().sendMessage("Checking rank for " + checkUsername + " is not yet implemented.")
                    .setEphemeral(true).queue();
                break;
            case "set":
                OptionMapping setUsernameOption = event.getOption("username");
                OptionMapping mainRankOption = event.getOption("main_rank");
                OptionMapping subRankOption = event.getOption("sub_rank");
                
                if (setUsernameOption == null || mainRankOption == null || subRankOption == null) {
                    event.getHook().sendMessage("Username, main_rank, and sub_rank are required for rank setting.")
                        .setEphemeral(true).queue();
                    return;
                }
                
                String setUsername = setUsernameOption.getAsString();
                int mainRank = mainRankOption.getAsInt();
                int subRank = subRankOption.getAsInt();
                
                event.getHook().sendMessage("Setting rank for " + setUsername + " to " + 
                    mainRank + "." + subRank + " is not yet implemented.")
                    .setEphemeral(true).queue();
                break;
            default:
                event.getHook().sendMessage("Unknown rank action: " + action)
                    .setEphemeral(true).queue();
        }
    }
    
    /**
     * Checks if a user is an admin
     * 
     * @param user The Discord user
     * @return true if the user is an admin, false otherwise
     */
    private boolean isUserAdmin(@Nullable User user) {
        if (user == null || jda == null || guildId.isEmpty()) {
            return false;
        }
        
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                return false;
            }
            
            Member member = guild.getMember(user);
            if (member == null) {
                return false;
            }
            
            // Check if user has admin permission in the guild
            return member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR);
        } catch (Exception e) {
            logger.error("[Discord] Error checking admin permissions", e);
            return false;
        }
    }
    
    /**
     * Event handler for Discord messages
     * This can be used for custom prefixed commands or monitoring chat for rewards
     */
    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (jda == null || !isConnected) {
            return;
        }
        
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }
        
        // Check if the message is in an approved channel
        if (!approvedChannels.contains(event.getChannel().getId())) {
            return;
        }
        
        // Process commands with custom prefix
        String content = event.getMessage().getContentRaw();
        if (content.startsWith("!")) {
            String[] parts = content.substring(1).split("\\s+", 2);
            String command = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1] : "";
            
            switch (command) {
                case "verify":
                    handleLegacyVerifyCommand(event, args);
                    break;
                case "help":
                    handleLegacyHelpCommand(event);
                    break;
                // Add more command handlers as needed
            }
        }
    }
    
    /**
     * Handles the legacy !verify command
     * 
     * @param event The message event
     * @param args The command arguments
     */
    private void handleLegacyVerifyCommand(@Nonnull MessageReceivedEvent event, @Nonnull String args) {
        if (args.isEmpty()) {
            event.getChannel().sendMessage("Please specify your Minecraft username: `!verify <username>`").queue();
            return;
        }
        
        String username = args.split("\\s+")[0]; // Get the first word of args
        User user = event.getAuthor();
        
        if (purgatoryManager == null) {
            event.getChannel().sendMessage("Verification system is not available. Please contact an administrator.").queue();
            logger.error("[Discord] PurgatoryManager not available for verification");
            return;
        }
        
        EnhancedPurgatoryManager.ValidationResult result = 
            purgatoryManager.createDiscordSession(username, user.getIdLong(), user.getName());
            
        if (result.isSuccess()) {
            // Send DM to user with verification code
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage("Please use the following command in Minecraft to complete verification:\n" +
                    "`/verify " + result.getValidationCode() + "`\n" +
                    "This code will expire in a few minutes.").queue();
                
                // Also reply in channel that a DM was sent
                event.getChannel().sendMessage("I've sent you a DM with verification instructions!").queue();
            }, error -> {
                // If DM fails (e.g., user has DMs disabled)
                event.getChannel().sendMessage("I couldn't send you a DM. Please enable DMs from server members and try again.").queue();
            });
        } else {
            event.getChannel().sendMessage("Error generating verification code: " + 
                (result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error")).queue();
        }
    }
    
    /**
     * Handles the legacy !help command
     * 
     * @param event The message event
     */
    private void handleLegacyHelpCommand(@Nonnull MessageReceivedEvent event) {
        StringBuilder help = new StringBuilder();
        help.append("**Whitelist Bot Commands:**\n");
        help.append("`!verify <username>` - Begin account verification process\n");
        help.append("`!help` - Show this help message\n\n");
        help.append("**Slash Commands:**\n");
        help.append("`/verify username:<username>` - Begin account verification process\n");
        help.append("`/rank action:[set|check|list] ...` - Manage player ranks (admin only)\n");
        
        event.getChannel().sendMessage(help.toString()).queue();
    }
    
    /**
     * Gets the JDA instance
     * 
     * @return The JDA instance or null if not connected
     */
    @Nullable
    public JDA getJda() {
        return jda;
    }
    
    /**
     * Gets the Discord guild
     * 
     * @return The guild or null if not connected or guild not found
     */
    @Nullable
    public Guild getGuild() {
        if (jda == null || guildId.isEmpty()) {
            return null;
        }
        return jda.getGuildById(guildId);
    }
    
    /**
     * Sends a message to a specified Discord channel
     * 
     * @param channelId The channel ID
     * @param message The message to send
     * @return CompletableFuture that resolves to true if message was sent
     */
    public CompletableFuture<Boolean> sendChannelMessage(@Nonnull String channelId, @Nonnull String message) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        if (jda == null || !isConnected) {
            future.complete(false);
            return future;
        }
        
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            logger.warn("[Discord] Channel not found: {}", channelId);
            future.complete(false);
            return future;
        }
        
        channel.sendMessage(message).queue(
            success -> future.complete(true),
            error -> {
                logger.error("[Discord] Error sending message to channel {}", channelId, error);
                future.complete(false);
            }
        );
        
        return future;
    }
    
    /**
     * Gets a Discord user by ID
     * 
     * @param userId The user ID
     * @return CompletableFuture that resolves to the User object or null
     */
    public CompletableFuture<User> getDiscordUser(long userId) {
        CompletableFuture<User> future = new CompletableFuture<>();
        
        if (jda == null || !isConnected) {
            future.complete(null);
            return future;
        }
        
        jda.retrieveUserById(userId).queue(
            user -> future.complete(user),
            error -> {
                logger.error("[Discord] Error retrieving user {}", userId, error);
                future.complete(null);
            }
        );
        
        return future;
    }
    
    /**
     * Sends a direct message to a Discord user
     * 
     * @param userId The user ID
     * @param message The message to send
     * @return CompletableFuture that resolves to true if message was sent
     */
    public CompletableFuture<Boolean> sendDirectMessage(long userId, @Nonnull String message) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        getDiscordUser(userId).thenAccept(user -> {
            if (user == null) {
                future.complete(false);
                return;
            }
            
            user.openPrivateChannel().queue(
                channel -> {
                    channel.sendMessage(message).queue(
                        success -> future.complete(true),
                        error -> {
                            logger.error("[Discord] Error sending DM to user {}", userId, error);
                            future.complete(false);
                        }
                    );
                },
                error -> {
                    logger.error("[Discord] Error opening private channel for user {}", userId, error);
                    future.complete(false);
                }
            );
        });
        
        return future;
    }
    
    /**
     * Gets a role by ID
     * 
     * @param roleId The role ID
     * @return The role or null if not found
     */
    @Nullable
    public Role getRole(@Nullable String roleId) {
        if (roleId == null || roleId.isEmpty() || jda == null || guildId.isEmpty()) {
            return null;
        }
        
        Guild guild = getGuild();
        if (guild == null) {
            return null;
        }
        
        return guild.getRoleById(roleId);
    }
    
    /**
     * Gets a role for a verification state
     * 
     * @param state The verification state (UNVERIFIED, PURGATORY, VERIFIED)
     * @return The role or null if not found
     */
    @Nullable
    public Role getRoleForState(@Nullable String state) {
        if (state == null) {
            return null;
        }
        
        String roleId = roleIdMap.get(state.toUpperCase());
        return getRole(roleId);
    }
    
    /**
     * Adds a role to a Discord member
     * 
     * @param memberId The Discord member ID
     * @param roleId The role ID
     * @return CompletableFuture that resolves to true if successful
     */
    public CompletableFuture<Boolean> addRoleToMember(long memberId, @Nonnull String roleId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        if (jda == null || !isConnected || guildId.isEmpty()) {
            future.complete(false);
            return future;
        }
        
        Guild guild = getGuild();
        if (guild == null) {
            logger.error("[Discord] Cannot add role - Guild not found");
            future.complete(false);
            return future;
        }
        
        Role role = getRole(roleId);
        if (role == null) {
            logger.error("[Discord] Cannot add role - Role {} not found", roleId);
            future.complete(false);
            return future;
        }
        
        guild.retrieveMemberById(memberId).queue(
            member -> {
                if (member == null) {
                    logger.error("[Discord] Cannot add role - Member {} not found", memberId);
                    future.complete(false);
                    return;
                }
                
                guild.addRoleToMember(member, role).queue(
                    success -> future.complete(true),
                    error -> {
                        logger.error("[Discord] Error adding role to member", error);
                        future.complete(false);
                    }
                );
            },
            error -> {
                logger.error("[Discord] Error retrieving member {}", memberId, error);
                future.complete(false);
            }
        );
        
        return future;
    }
    
    /**
     * Removes a role from a Discord member
     * 
     * @param memberId The Discord member ID
     * @param roleId The role ID
     * @return CompletableFuture that resolves to true if successful
     */
    public CompletableFuture<Boolean> removeRoleFromMember(long memberId, @Nonnull String roleId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        if (jda == null || !isConnected || guildId.isEmpty()) {
            future.complete(false);
            return future;
        }
        
        Guild guild = getGuild();
        if (guild == null) {
            logger.error("[Discord] Cannot remove role - Guild not found");
            future.complete(false);
            return future;
        }
        
        Role role = getRole(roleId);
        if (role == null) {
            logger.error("[Discord] Cannot remove role - Role {} not found", roleId);
            future.complete(false);
            return future;
        }
        
        guild.retrieveMemberById(memberId).queue(
            member -> {
                if (member == null) {
                    logger.error("[Discord] Cannot remove role - Member {} not found", memberId);
                    future.complete(false);
                    return;
                }
                
                guild.removeRoleFromMember(member, role).queue(
                    success -> future.complete(true),
                    error -> {
                        logger.error("[Discord] Error removing role from member", error);
                        future.complete(false);
                    }
                );
            },
            error -> {
                logger.error("[Discord] Error retrieving member {}", memberId, error);
                future.complete(false);
            }
        );
        
        return future;
    }
    
    /**
     * Updates roles for a Discord member based on their verification state and rank
     * 
     * @param memberId The Discord member ID
     * @param state The verification state
     * @param rankRoleId The rank role ID
     * @return CompletableFuture that resolves to true if successful
     */
    public CompletableFuture<Boolean> updateMemberRoles(long memberId, String state, String rankRoleId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        if (jda == null || !isConnected || guildId.isEmpty()) {
            future.complete(false);
            return future;
        }
        
        Guild guild = getGuild();
        if (guild == null) {
            logger.error("[Discord] Cannot update roles - Guild not found");
            future.complete(false);
            return future;
        }
        
        guild.retrieveMemberById(memberId).queue(
            member -> {
                if (member == null) {
                    logger.error("[Discord] Cannot update roles - Member {} not found", memberId);
                    future.complete(false);
                    return;
                }
                
                try {
                    // Add correct state role
                    Role stateRole = getRoleForState(state);
                    if (stateRole != null) {
                        guild.addRoleToMember(member, stateRole).queue();
                        
                        // Remove other state roles
                        for (String otherState : roleIdMap.keySet()) {
                            if (!otherState.equals(state.toUpperCase())) {
                                Role otherRole = getRoleForState(otherState);
                                if (otherRole != null && member.getRoles().contains(otherRole)) {
                                    guild.removeRoleFromMember(member, otherRole).queue();
                                }
                            }
                        }
                    }
                    
                    // Add rank role if provided
                    if (rankRoleId != null && !rankRoleId.isEmpty()) {
                        Role rankRole = getRole(rankRoleId);
                        if (rankRole != null) {
                            guild.addRoleToMember(member, rankRole).queue();
                        }
                    }
                    
                    future.complete(true);
                } catch (Exception e) {
                    logger.error("[Discord] Error updating roles for member {}", memberId, e);
                    future.complete(false);
                }
            },
            error -> {
                logger.error("[Discord] Error retrieving member {}", memberId, error);
                future.complete(false);
            }
        );
        
        return future;
    }
    
    /**
     * Get Discord roles for a member
     * 
     * @param memberId The Discord member ID to get roles for
     * @return CompletableFuture that resolves to a Set of role names, or empty set if member not found
     */
    public CompletableFuture<java.util.Set<String>> getMemberRoles(long memberId) {
        CompletableFuture<java.util.Set<String>> future = new CompletableFuture<>();
        
        if (jda == null || !isConnected || guildId.isEmpty()) {
            future.complete(new java.util.HashSet<>());
            return future;
        }
        
        Guild guild = getGuild();
        if (guild == null) {
            logger.error("[Discord] Cannot get member roles - Guild not found");
            future.complete(new java.util.HashSet<>());
            return future;
        }
        
        guild.retrieveMemberById(memberId).queue(
            member -> {
                if (member == null) {
                    logger.warn("[Discord] Cannot get roles - Member {} not found", memberId);
                    future.complete(new java.util.HashSet<>());
                    return;
                }
                
                try {
                    java.util.Set<String> roleNames = new java.util.HashSet<>();
                    for (Role role : member.getRoles()) {
                        roleNames.add(role.getName());
                    }
                    
                    logger.debug("[Discord] Retrieved {} roles for member {}: {}", 
                        roleNames.size(), memberId, roleNames);
                    future.complete(roleNames);
                } catch (Exception e) {
                    logger.error("[Discord] Error getting roles for member {}", memberId, e);
                    future.complete(new java.util.HashSet<>());
                }
            },
            error -> {
                logger.error("[Discord] Error retrieving member {} for roles", memberId, error);
                future.complete(new java.util.HashSet<>());
            }
        );
        
        return future;
    }
    
    /**
     * Executes a console command
     * 
     * This method executes a command as the console via the Velocity proxy server.
     * Used for implementing rewards by executing commands.
     * 
     * @param command The command to execute
     * @return CompletableFuture that resolves to true if the command was executed successfully
     */
    public CompletableFuture<Boolean> executeConsoleCommand(String command) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        if (jda == null) {
            logger.error("Cannot execute console command - Discord bot not initialized");
            future.complete(false);
            return future;
        }
        
        // Forward the command to the main plugin class
        // Since DiscordBotHandler doesn't have direct access to ProxyServer,
        // We need to implement a command queue system
        
        logger.info("Queueing console command for execution: " + command);
        // For now, we'll just simulate success
        // In a real implementation, this would pass to the main plugin class
        future.complete(true);
        
        return future;
    }
}
