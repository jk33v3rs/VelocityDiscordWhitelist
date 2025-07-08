package top.jk33v3rs.velocitydiscordwhitelist.modules;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import top.jk33v3rs.velocitydiscordwhitelist.config.BotStatus;
import top.jk33v3rs.velocitydiscordwhitelist.config.SimpleConfigLoader;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;

/**
 * SimpleDiscordBotHandler
 * 
 * A simplified Discord bot handler inspired by Spicord's design patterns.
 * This class manages Discord bot connection and command handling with clean status tracking.
 * 
 * Key Features:
 * - Simple bot initialization with minimal intents
 * - Guild-specific command registration (not global)
 * - Proper status tracking with BotStatus enum
 * - Clean connection timeout handling
 * - Graceful degradation when Discord is unavailable
 */
public class SimpleDiscordBotHandler extends ListenerAdapter {
    
    private final Logger logger;
    private final ExceptionHandler exceptionHandler;
    private final SimpleConfigLoader configLoader;
    private final SQLHandler sqlHandler;
    private final EnhancedPurgatoryManager purgatoryManager;
    
    // Bot configuration
    private final String token;
    private final String guildId;
    private final String verificationChannelId;
    private final String adminRoleId;
    private final String verifiedRoleId;
    
    // Bot state
    private JDA jda;
    private BotStatus status = BotStatus.OFFLINE;
    private Guild guild;
    private TextChannel verificationChannel;
    private final Map<String, String> activeVerificationCodes = new HashMap<>();
    
    /**
     * Constructor for SimpleDiscordBotHandler
     * 
     * @param logger The logger instance
     * @param exceptionHandler The exception handler
     * @param configLoader The configuration loader
     * @param sqlHandler The SQL handler for database operations
     * @param purgatoryManager The purgatory manager
     */
    public SimpleDiscordBotHandler(Logger logger, ExceptionHandler exceptionHandler, 
                                 SimpleConfigLoader configLoader, SQLHandler sqlHandler,
                                 EnhancedPurgatoryManager purgatoryManager) {
        this.logger = logger;
        this.exceptionHandler = exceptionHandler;
        this.configLoader = configLoader;
        this.sqlHandler = sqlHandler;
        this.purgatoryManager = purgatoryManager;
        
        // Load Discord configuration
        this.token = configLoader.get("discord.token", "");
        this.guildId = configLoader.get("discord.guild_id", "");
        this.verificationChannelId = configLoader.get("discord.verification_channel", "");
        this.adminRoleId = configLoader.get("discord.roles.admin", "");
        this.verifiedRoleId = configLoader.get("discord.roles.verified", "");
    }
    
    /**
     * initializeBot
     * 
     * Initializes the Discord bot connection using JDABuilder.createLight()
     * for minimal resource usage, similar to Spicord's approach.
     * 
     * @return CompletableFuture that completes when bot is ready or fails
     */
    public CompletableFuture<Boolean> initializeBot() {
        if (!configLoader.isDiscordEnabled()) {
            logger.info("Discord integration is disabled");
            status = BotStatus.OFFLINE;
            return CompletableFuture.completedFuture(false);
        }
        
        if (token.isEmpty() || token.contains("YOUR_")) {
            logger.error("Discord bot token is not configured");
            status = BotStatus.ERROR;
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                status = BotStatus.STARTING;
                logger.info("Starting Discord bot...");
                
                // Build JDA with minimal intents (Spicord pattern)
                jda = JDABuilder.createLight(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER)
                    .addEventListeners(this)
                    .build();
                
                // Wait for ready event with timeout
                jda.awaitReady();
                
                // Validate guild access
                guild = jda.getGuildById(guildId);
                if (guild == null) {
                    throw new IllegalStateException("Bot cannot access guild: " + guildId);
                }
                
                // Validate verification channel
                verificationChannel = guild.getTextChannelById(verificationChannelId);
                if (verificationChannel == null) {
                    throw new IllegalStateException("Bot cannot access verification channel: " + verificationChannelId);
                }
                
                status = BotStatus.READY;
                logger.info("Discord bot connected successfully to guild: {}", guild.getName());
                return true;
                
            } catch (Exception e) {
                status = BotStatus.ERROR;
                logger.error("Failed to initialize Discord bot", e);
                exceptionHandler.handleIntegrationException("Discord", "bot initialization", e);
                return false;
            }
        }).orTimeout(30, TimeUnit.SECONDS)
          .exceptionally(throwable -> {
              status = BotStatus.ERROR;
              logger.error("Discord bot initialization timed out or failed", throwable);
              return false;
          });
    }
    
    /**
     * onReady
     * 
     * Called when the Discord bot is ready. Registers guild-specific commands.
     * 
     * @param event The ReadyEvent
     */
    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        logger.info("Discord bot ready. Registering guild commands...");
        
        try {
            // Register guild-specific commands (not global)
            if (guild != null) {
                guild.updateCommands()
                    .addCommands(
                        Commands.slash("mc", "Link your Minecraft account for whitelist verification")
                            .addOption(OptionType.STRING, "username", "Your Minecraft username", true),
                        Commands.slash("verify", "Verify your account with the provided code")
                            .addOption(OptionType.STRING, "code", "Your verification code", true)
                    )
                    .queue(
                        success -> logger.info("Guild commands registered successfully"),
                        error -> logger.error("Failed to register guild commands", error)
                    );
            }
            
            status = BotStatus.READY;
            
        } catch (Exception e) {
            logger.error("Error in onReady event", e);
            exceptionHandler.handleIntegrationException("Discord", "ready event", e);
        }
    }
    
    /**
     * onSessionDisconnect
     * 
     * Called when the Discord session disconnects.
     * 
     * @param event The SessionDisconnectEvent
     */
    @Override
    public void onSessionDisconnect(@Nonnull SessionDisconnectEvent event) {
        status = BotStatus.OFFLINE;
        logger.warn("Discord bot disconnected. Code: {}", event.getCloseCode());
    }
    
    /**
     * onSlashCommandInteraction
     * 
     * Handles Discord slash command interactions.
     * 
     * @param event The SlashCommandInteractionEvent
     */
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!status.canProcessCommands()) {
            event.reply("Bot is not ready to process commands.").setEphemeral(true).queue();
            return;
        }
        
        try {
            String commandName = event.getName();
            
            switch (commandName) {
                case "mc" -> handleMcCommand(event);
                case "verify" -> handleVerifyCommand(event);
                default -> event.reply("Unknown command.").setEphemeral(true).queue();
            }
            
        } catch (Exception e) {
            logger.error("Error handling slash command: {}", event.getName(), e);
            event.reply("An error occurred while processing your command.").setEphemeral(true).queue();
            exceptionHandler.handleIntegrationException("Discord", "slash command: " + event.getName(), e);
        }
    }
    
    /**
     * handleMcCommand
     * 
     * Handles the /mc command for starting the verification process.
     * 
     * @param event The SlashCommandInteractionEvent
     */
    private void handleMcCommand(SlashCommandInteractionEvent event) {
        // Only allow in verification channel
        if (!event.getChannel().getId().equals(verificationChannelId)) {
            event.reply("This command can only be used in the verification channel.").setEphemeral(true).queue();
            return;
        }
        
        OptionMapping usernameOption = event.getOption("username");
        if (usernameOption == null) {
            event.reply("Please provide your Minecraft username.").setEphemeral(true).queue();
            return;
        }
        
        String username = usernameOption.getAsString().trim();
        String discordUserId = event.getUser().getId();
        
        // Check if already verified
        try {
            if (sqlHandler.isPlayerWhitelisted(username)) {
                event.reply("This Minecraft account is already whitelisted.").setEphemeral(true).queue();
                return;
            }
            
            // Generate verification code
            String code = generateVerificationCode();
            activeVerificationCodes.put(discordUserId, code);
            
            // Start purgatory session
            purgatoryManager.startPurgatorySession(username, discordUserId, code);
            
            // Send verification code
            String message = configLoader.getMessage("verification_code", "Your verification code is: %code%")
                                        .replace("%code%", code);
            
            event.reply(message + "\n\nJoin the server and use `/verify " + code + "` in-game to complete verification.")
                 .setEphemeral(true)
                 .queue();
            
            logger.info("Started verification process for {} (Discord: {})", username, discordUserId);
            
        } catch (Exception e) {
            logger.error("Error in /mc command for user: {}", username, e);
            event.reply("An error occurred. Please try again later.").setEphemeral(true).queue();
            exceptionHandler.handleIntegrationException("Discord", "/mc command", e);
        }
    }
    
    /**
     * handleVerifyCommand
     * 
     * Handles the /verify command for code verification.
     * 
     * @param event The SlashCommandInteractionEvent
     */
    private void handleVerifyCommand(SlashCommandInteractionEvent event) {
        OptionMapping codeOption = event.getOption("code");
        if (codeOption == null) {
            event.reply("Please provide your verification code.").setEphemeral(true).queue();
            return;
        }
        
        String providedCode = codeOption.getAsString().trim();
        String discordUserId = event.getUser().getId();
        String expectedCode = activeVerificationCodes.get(discordUserId);
        
        if (expectedCode == null || !expectedCode.equals(providedCode)) {
            event.reply(configLoader.getMessage("invalid_code", "Invalid verification code.")).setEphemeral(true).queue();
            return;
        }
        
        try {
            // Complete verification process
            if (purgatoryManager.completeVerification(discordUserId)) {
                // Add verified role if configured
                if (!verifiedRoleId.isEmpty()) {
                    Member member = guild.getMemberById(discordUserId);
                    Role verifiedRole = guild.getRoleById(verifiedRoleId);
                    if (member != null && verifiedRole != null) {
                        guild.addRoleToMember(member, verifiedRole).queue();
                    }
                }
                
                activeVerificationCodes.remove(discordUserId);
                
                String successMessage = configLoader.getMessage("verification_success", "Verification successful! Welcome to the server!");
                event.reply(successMessage).setEphemeral(true).queue();
                
                logger.info("Completed verification for Discord user: {}", discordUserId);
            } else {
                event.reply("Verification failed. Please try again.").setEphemeral(true).queue();
            }
            
        } catch (Exception e) {
            logger.error("Error in /verify command for user: {}", discordUserId, e);
            event.reply("An error occurred during verification.").setEphemeral(true).queue();
            exceptionHandler.handleIntegrationException("Discord", "/verify command", e);
        }
    }
    
    /**
     * generateVerificationCode
     * 
     * Generates a 6-character verification code in XXX-XXX format.
     * 
     * @return A verification code string
     */
    private String generateVerificationCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        
        for (int i = 0; i < 6; i++) {
            if (i == 3) {
                code.append("-");
            }
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        
        return code.toString();
    }
    
    /**
     * shutdown
     * 
     * Gracefully shuts down the Discord bot.
     */
    public void shutdown() {
        if (jda != null) {
            status = BotStatus.STOPPING;
            logger.info("Shutting down Discord bot...");
            jda.shutdown();
            
            try {
                if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                    jda.shutdownNow();
                }
                status = BotStatus.OFFLINE;
                logger.info("Discord bot shutdown complete");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Discord bot shutdown interrupted");
            }
        }
    }
    
    /**
     * getStatus
     * 
     * Gets the current bot status.
     * 
     * @return The current BotStatus
     */
    public BotStatus getStatus() {
        return status;
    }
    
    /**
     * isReady
     * 
     * Checks if the bot is ready to process commands.
     * 
     * @return true if bot is ready, false otherwise
     */
    public boolean isReady() {
        return status.canProcessCommands();
    }
    
    /**
     * isConnected
     * 
     * Checks if the bot is connected to Discord.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED && status.isOnline();
    }
}
