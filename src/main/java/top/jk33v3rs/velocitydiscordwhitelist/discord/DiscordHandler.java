package top.jk33v3rs.velocitydiscordwhitelist.discord;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.velocitypowered.api.proxy.ProxyServer;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import top.jk33v3rs.velocitydiscordwhitelist.config.BotStatus;
import top.jk33v3rs.velocitydiscordwhitelist.config.SimpleConfigLoader;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.modules.PurgatoryManager;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;

/**
 * DiscordHandler
 * 
 * Main Discord bot connection handler inspired by DiscordNotify and Spicord patterns.
 * Manages bot initialization, connection status, and coordinates Discord modules.
 * 
 * Key Design Principle: This is a CONNECTION MANAGER, not a command handler.
 * It manages the JDA instance and coordinates modules, but doesn't handle commands directly.
 */
public class DiscordHandler extends ListenerAdapter {
    
    private final Logger logger;
    private final ExceptionHandler exceptionHandler;
    private final SimpleConfigLoader configLoader;
    
    // Dependencies that need to be injected into modules
    private final SQLHandler sqlHandler;
    private final PurgatoryManager purgatoryManager;
    private final ProxyServer proxyServer;
    
    // Bot configuration
    private final String token;
    private final String guildId;
    private final boolean enabled;
    
    // Bot state
    private JDA jda;
    private BotStatus status = BotStatus.OFFLINE;
    private Guild guild;
    
    // Discord modules - these get initialized after bot connects
    private DiscordListener discordListener;
    private DiscordChat discordChat;
    
    /**
     * Constructor for DiscordHandler
     * 
     * @param logger The logger instance
     * @param exceptionHandler The exception handler
     * @param configLoader The configuration loader
     * @param sqlHandler The SQL handler for database operations
     * @param purgatoryManager The purgatory manager for verification sessions
     * @param proxyServer The Velocity proxy server instance
     */
    public DiscordHandler(Logger logger, ExceptionHandler exceptionHandler, SimpleConfigLoader configLoader,
                         SQLHandler sqlHandler, PurgatoryManager purgatoryManager, ProxyServer proxyServer) {
        this.logger = logger;
        this.exceptionHandler = exceptionHandler;
        this.configLoader = configLoader;
        this.sqlHandler = sqlHandler;
        this.purgatoryManager = purgatoryManager;
        this.proxyServer = proxyServer;
        
        // Load Discord configuration
        this.token = configLoader.get("discord.token", "");
        this.guildId = configLoader.get("discord.guild_id", "");
        this.enabled = configLoader.get("discord.enabled", false);
    }
    
    /**
     * initializeBot
     * 
     * Initializes the Discord bot connection using minimal resources.
     * 
     * @return CompletableFuture that completes when bot is ready or fails
     */
    public CompletableFuture<Boolean> initializeBot() {
        if (!enabled) {
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
                
                // Build JDA with minimal intents
                jda = JDABuilder.createLight(token)
                    .enableIntents(
                        GatewayIntent.GUILD_MESSAGES, 
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS
                    )
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOJI, CacheFlag.STICKER)
                    .addEventListeners(this)
                    .build();
                
                // Wait for ready event with timeout
                jda.awaitReady();
                
                // Validate guild access
                guild = jda.getGuildById(guildId);
                if (guild == null) {
                    throw new IllegalStateException("Bot cannot access guild: " + guildId);
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
     * Called when the Discord bot is ready. Initializes all Discord modules.
     * This is where we inject dependencies into modules AFTER the bot is connected.
     * 
     * @param event The ReadyEvent
     */
    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        logger.info("Discord bot ready. Initializing modules...");
        
        try {
            // Initialize Discord modules with proper dependency injection
            initializeModules();
            
            status = BotStatus.READY;
            logger.info("All Discord modules initialized successfully");
            
        } catch (Exception e) {
            logger.error("Error initializing Discord modules", e);
            exceptionHandler.handleIntegrationException("Discord", "module initialization", e);
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
     * initializeModules
     * 
     * Initializes Discord modules with proper dependency injection.
     * NO CIRCULAR DEPENDENCIES - modules get what they need, not a reference to this handler.
     */
    private void initializeModules() {
        // Initialize command listener (verification commands)
        String verificationChannelId = configLoader.get("discord.verification_channel", "");
        discordListener = new DiscordListener(logger, exceptionHandler, verificationChannelId);
        discordListener.setDependencies(sqlHandler, purgatoryManager);
        discordListener.setGuild(guild);  // Set guild AFTER bot connects
        jda.addEventListener(discordListener);
        
        // Initialize chat handler (global chat integration)
        if (configLoader.get("discord.chat.enabled", false)) {
            discordChat = new DiscordChat(logger, exceptionHandler, configLoader, this, proxyServer);
            jda.addEventListener(discordChat);
        }
        
        logger.info("Discord modules initialized: Listener={}, Chat={}", 
                   discordListener != null, discordChat != null);
    }
    
    /**
     * shutdown
     * 
     * Gracefully shuts down the Discord bot and all modules.
     */
    public void shutdown() {
        if (jda != null) {
            status = BotStatus.STOPPING;
            logger.info("Shutting down Discord bot and modules...");
            
            // Remove all event listeners
            if (discordListener != null) jda.removeEventListener(discordListener);
            if (discordChat != null) jda.removeEventListener(discordChat);
            
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
     * updateMemberRoles
     * 
     * Updates Discord member roles based on verification state and rank.
     * Compatibility method for RewardsHandler integration.
     * 
     * @param memberId The Discord member ID
     * @param state The verification state (VERIFIED, UNVERIFIED, etc.)
     * @param rankRoleId The rank role ID to assign (optional)
     * @return CompletableFuture that resolves to true if successful
     */
    public CompletableFuture<Boolean> updateMemberRoles(long memberId, String state, String rankRoleId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (jda == null || !isConnected() || guild == null) {
            logger.warn("Cannot update Discord roles - bot not connected or guild unavailable");
            future.complete(false);
            return future;
        }

        try {
            guild.retrieveMemberById(memberId).queue(
                member -> {
                    if (member == null) {
                        logger.error("Cannot update roles - Discord member {} not found in guild", memberId);
                        future.complete(false);
                        return;
                    }

                    try {
                        boolean success = true;
                        
                        // Handle verified role based on state
                        String verifiedRoleId = configLoader.get("discord.roles.verified", "");
                        if (!verifiedRoleId.isEmpty()) {
                            Role verifiedRole = guild.getRoleById(verifiedRoleId);
                            if (verifiedRole != null) {
                                boolean shouldHaveVerifiedRole = "VERIFIED".equalsIgnoreCase(state);
                                boolean hasVerifiedRole = member.getRoles().contains(verifiedRole);

                                if (shouldHaveVerifiedRole && !hasVerifiedRole) {
                                    guild.addRoleToMember(member, verifiedRole).queue();
                                    logger.debug("Added verified role to Discord member {}", memberId);
                                } else if (!shouldHaveVerifiedRole && hasVerifiedRole) {
                                    guild.removeRoleFromMember(member, verifiedRole).queue();
                                    logger.debug("Removed verified role from Discord member {}", memberId);
                                }
                            }
                        }

                        // Handle rank role if provided
                        if (rankRoleId != null && !rankRoleId.isEmpty()) {
                            Role rankRole = guild.getRoleById(rankRoleId);
                            if (rankRole != null && !member.getRoles().contains(rankRole)) {
                                guild.addRoleToMember(member, rankRole).queue();
                                logger.debug("Added rank role {} to Discord member {}", rankRoleId, memberId);
                            }
                        }

                        future.complete(success);
                        
                    } catch (Exception e) {
                        logger.error("Error updating Discord roles for member {}", memberId, e);
                        exceptionHandler.handleIntegrationException("Discord", "role update", e);
                        future.complete(false);
                    }
                },
                error -> {
                    logger.error("Failed to retrieve Discord member {}: {}", memberId, error.getMessage());
                    future.complete(false);
                }
            );
            
        } catch (Exception e) {
            logger.error("Error initiating Discord role update for member {}", memberId, e);
            exceptionHandler.handleIntegrationException("Discord", "role update initiation", e);
            future.complete(false);
        }

        return future;
    }

    // Getters for modules and status
    public BotStatus getStatus() { return status; }
    public boolean isReady() { return status.canProcessCommands(); }
    public boolean isConnected() { return jda != null && jda.getStatus() == JDA.Status.CONNECTED && status.isOnline(); }
    public JDA getJDA() { return jda; }
    public Guild getGuild() { return guild; }
    public DiscordListener getListener() { return discordListener; }
    public DiscordChat getChat() { return discordChat; }
    
    // Legacy compatibility methods for DiscordBotHandler interface
    public boolean getConnectionStatus() {
        return isConnected();
    }
    
    public boolean isConnectionFailed() {
        return status == BotStatus.ERROR;
    }
}
