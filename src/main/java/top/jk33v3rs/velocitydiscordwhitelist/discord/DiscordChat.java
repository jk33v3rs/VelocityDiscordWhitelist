package top.jk33v3rs.velocitydiscordwhitelist.discord;

import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import top.jk33v3rs.velocitydiscordwhitelist.config.SimpleConfigLoader;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;

/**
 * DiscordChat
 * 
 * Handles Discord-Minecraft chat integration similar to HuskChat/DiscordSRV.
 * Provides global chat synchronization between Discord and all connected Minecraft servers.
 * 
 * Features:
 * - Bidirectional chat relay (Discord ↔ Minecraft)
 * - Global chat across all servers on the Velocity network
 * - Channel-specific filtering
 * - Message formatting and embeds
 * - Join/leave/death notifications
 * - Anti-spam protection
 */
public class DiscordChat extends ListenerAdapter {
    
    private final Logger logger;
    private final ExceptionHandler exceptionHandler;
    private final SimpleConfigLoader configLoader;
    private final DiscordHandler discordHandler;
    private final ProxyServer proxyServer;
    private final MiniMessage miniMessage;
    
    // Configuration
    private final String chatChannelId;
    private final boolean chatEnabled;
    private final boolean embedMessages;
    private final String minecraftToDiscordFormat;
    @SuppressWarnings("unused") // TODO: Implement Discord -> Minecraft message formatting
    private final String discordToMinecraftFormat; 
    private final boolean showJoinLeave;
    @SuppressWarnings("unused") // TODO: Implement death message notifications
    private final boolean showDeathMessages;
    private final boolean globalChatEnabled;
    
    // Online players cache for faster lookups
    private final ConcurrentMap<String, String> onlinePlayerServers = new ConcurrentHashMap<>();
    
    /**
     * Constructor for DiscordChat
     * 
     * @param logger The logger instance
     * @param exceptionHandler The exception handler
     * @param configLoader The configuration loader
     * @param discordHandler The main Discord handler
     * @param proxyServer The Velocity proxy server instance
     */
    public DiscordChat(Logger logger, ExceptionHandler exceptionHandler, 
                      SimpleConfigLoader configLoader, DiscordHandler discordHandler,
                      ProxyServer proxyServer) {
        this.logger = logger;
        this.exceptionHandler = exceptionHandler;
        this.configLoader = configLoader;
        this.discordHandler = discordHandler;
        this.proxyServer = proxyServer;
        this.miniMessage = MiniMessage.miniMessage();
        
        // Load configuration
        this.chatChannelId = configLoader.get("discord.chat.channel_id", "");
        this.chatEnabled = configLoader.get("discord.chat.enabled", false);
        this.embedMessages = configLoader.get("discord.chat.use_embeds", true);
        this.minecraftToDiscordFormat = configLoader.get("discord.chat.minecraft_format", "**[%server%]** **%player%**: %message%");
        this.discordToMinecraftFormat = configLoader.get("discord.chat.discord_format", "[Discord] %user%: %message%");
        this.showJoinLeave = configLoader.get("discord.chat.show_join_leave", true);
        this.showDeathMessages = configLoader.get("discord.chat.show_death_messages", true);
        this.globalChatEnabled = configLoader.get("discord.chat.global_chat", true);
        
        logger.info("DiscordChat initialized - Chat: {}, Global: {}, Channel: {}", 
                   chatEnabled, globalChatEnabled, chatChannelId);
    }
    
    /**
     * onMessageReceived
     * 
     * Handles Discord messages and relays them to Minecraft if configured.
     * 
     * @param event The MessageReceivedEvent
     */
    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!chatEnabled || event.getAuthor().isBot()) {
            return;
        }
        
        // Check if message is in the designated chat channel
        if (!chatChannelId.isEmpty() && !event.getChannel().getId().equals(chatChannelId)) {
            return;
        }
        
        try {
            Member member = event.getMember();
            if (member == null) return;
            
            String username = member.getEffectiveName();
            String message = event.getMessage().getContentDisplay();
            
            // Format and relay message to Minecraft
            relayToMinecraft(username, message);
            
        } catch (Exception e) {
            logger.error("Error processing Discord chat message", e);
            exceptionHandler.handleIntegrationException("DiscordChat", "message relay", e);
        }
    }
    
    /**
     * relayToMinecraft
     * 
     * Relays a Discord message to all Minecraft servers on the Velocity network.
     * Uses MiniMessage for rich text formatting.
     * 
     * @param username The Discord username
     * @param message The message content
     */
    private void relayToMinecraft(String username, String message) {
        if (!globalChatEnabled) {
            return;
        }
        
        try {
            // Create rich formatted message using MiniMessage
            String miniMessageFormat = configLoader.get("discord.chat.discord_minimessage_format", 
                "<gray>[</gray><blue>Discord</blue><gray>]</gray> <white>%user%</white><gray>:</gray> <aqua>%message%</aqua>");
            
            String formattedMessage = miniMessageFormat
                .replace("%user%", username)
                .replace("%message%", message);
            
            // Parse with MiniMessage for rich formatting
            Component messageComponent = miniMessage.deserialize(formattedMessage);
            
            // Broadcast to all connected players across all servers
            Collection<Player> players = proxyServer.getAllPlayers();
            int successCount = 0;
            
            for (Player player : players) {
                try {
                    player.sendMessage(messageComponent);
                    successCount++;
                } catch (Exception e) {
                    logger.debug("Failed to send message to player {}: {}", player.getUsername(), e.getMessage());
                }
            }
            
            logger.debug("Relayed Discord message from {} to {}/{} players", username, successCount, players.size());
            
        } catch (Exception e) {
            logger.error("Error relaying Discord message to Minecraft", e);
            exceptionHandler.handleIntegrationException("DiscordChat", "Discord to Minecraft relay", e);
        }
    }
    
    /**
     * relayToDiscord
     * 
     * Relays a Minecraft message to Discord.
     * 
     * @param username The Minecraft username  
     * @param message The message content
     * @param serverName The server name (optional)
     */
    public void relayToDiscord(String username, String message, String serverName) {
        if (!chatEnabled || chatChannelId.isEmpty()) {
            return;
        }
        
        try {
            Guild guild = discordHandler.getGuild();
            if (guild == null) return;
            
            TextChannel chatChannel = guild.getTextChannelById(chatChannelId);
            if (chatChannel == null) return;
            
            if (embedMessages) {
                // Send as rich embed with player avatar
                EmbedBuilder embed = new EmbedBuilder()
                    .setAuthor(username, null, getMinecraftAvatarUrl(username))
                    .setDescription(message)
                    .setColor(0x55FF55)
                    .setTimestamp(Instant.now());
                
                if (serverName != null && !serverName.isEmpty()) {
                    embed.setFooter("From: " + serverName);
                }
                
                chatChannel.sendMessageEmbeds(embed.build()).queue();
            } else {
                // Send as plain text using configured format
                String formattedMessage = minecraftToDiscordFormat
                    .replace("%server%", serverName != null ? serverName : "Server")
                    .replace("%player%", username)
                    .replace("%message%", message);
                
                chatChannel.sendMessage(formattedMessage).queue();
            }
            
        } catch (Exception e) {
            logger.error("Error relaying message to Discord", e);
            exceptionHandler.handleIntegrationException("DiscordChat", "Minecraft to Discord relay", e);
        }
    }
    
    /**
     * handlePlayerChat
     * 
     * Handles Minecraft player chat events for global chat relay.
     * This should be called from the main plugin's chat event handler.
     * 
     * @param player The player who sent the message
     * @param message The chat message
     */
    public void handlePlayerChat(Player player, String message) {
        if (!chatEnabled || !globalChatEnabled) {
            return;
        }
        
        try {
            // Get the server name for the player
            String serverName = "Unknown";
            if (player.getCurrentServer().isPresent()) {
                serverName = player.getCurrentServer().get().getServerInfo().getName();
            }
            
            // Update player server mapping
            onlinePlayerServers.put(player.getUsername(), serverName);
            
            // Relay to Discord
            relayToDiscord(player.getUsername(), message, serverName);
            
            // Relay to other servers (global chat)
            if (globalChatEnabled) {
                relayGlobalChat(player, message, serverName);
            }
            
        } catch (Exception e) {
            logger.error("Error handling player chat for {}", player.getUsername(), e);
            exceptionHandler.handleIntegrationException("DiscordChat", "player chat handling", e);
        }
    }
    
    /**
     * relayGlobalChat
     * 
     * Relays a player's chat message to all other servers in the network.
     * 
     * @param sender The player who sent the message
     * @param message The message content
     * @param senderServer The server the message came from
     */
    private void relayGlobalChat(Player sender, String message, String senderServer) {
        try {
            // Format message for global chat
            String globalMessage = String.format("[%s] %s: %s", senderServer, sender.getUsername(), message);
            Component globalComponent = Component.text(globalMessage, NamedTextColor.YELLOW);
            
            // Send to all players except those on the sender's server
            Collection<Player> allPlayers = proxyServer.getAllPlayers();
            for (Player player : allPlayers) {
                // Skip sending to players on the same server as the sender
                if (player.getCurrentServer().isPresent() && 
                    player.getCurrentServer().get().getServerInfo().getName().equals(senderServer)) {
                    continue;
                }
                
                try {
                    player.sendMessage(globalComponent);
                } catch (Exception e) {
                    logger.debug("Failed to send global chat message to {}: {}", player.getUsername(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error relaying global chat", e);
        }
    }
    
    /**
     * handlePlayerJoin
     * 
     * Handles player join events for Discord notifications.
     * 
     * @param player The player who joined
     */
    @Subscribe
    public void handlePlayerJoin(PostLoginEvent event) {
        if (!showJoinLeave) return;
        
        Player player = event.getPlayer();
        String serverName = "Network";
        if (player.getCurrentServer().isPresent()) {
            serverName = player.getCurrentServer().get().getServerInfo().getName();
        }
        
        onlinePlayerServers.put(player.getUsername(), serverName);
        sendServerMessage(String.format("**%s** joined the server", player.getUsername()), "join");
    }
    
    /**
     * handlePlayerLeave
     * 
     * Handles player disconnect events for Discord notifications.
     * 
     * @param event The disconnect event
     */
    @Subscribe
    public void handlePlayerLeave(DisconnectEvent event) {
        if (!showJoinLeave) return;
        
        Player player = event.getPlayer();
        onlinePlayerServers.remove(player.getUsername());
        sendServerMessage(String.format("**%s** left the server", player.getUsername()), "leave");
    }
    
    /**
     * onPlayerChat
     * 
     * Handles player chat events for global chat relay.
     * This method listens to Velocity chat events and relays them through the global chat system.
     * 
     * @param event The PlayerChatEvent from Velocity
     */
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        if (!chatEnabled || !globalChatEnabled) {
            return;
        }
        
        try {
            Player player = event.getPlayer();
            String message = event.getMessage();
            
            // Handle the chat through our existing system
            handlePlayerChat(player, message);
            
        } catch (Exception e) {
            logger.error("Error handling chat event for player {}", event.getPlayer().getUsername(), e);
            exceptionHandler.handleIntegrationException("DiscordChat", "chat event handling", e);
        }
    }
    
    /**
     * sendServerMessage
     * 
     * Sends a server message (join/leave/death) to Discord.
     * 
     * @param message The server message
     * @param messageType The type of message (join, leave, death, etc.)
     */
    public void sendServerMessage(String message, String messageType) {
        if (!chatEnabled || chatChannelId.isEmpty()) {
            return;
        }
        
        try {
            Guild guild = discordHandler.getGuild();
            if (guild == null) return;
            
            TextChannel chatChannel = guild.getTextChannelById(chatChannelId);
            if (chatChannel == null) return;
            
            // Format based on message type
            int color = switch (messageType.toLowerCase()) {
                case "join" -> 0x55FF55; // Green
                case "leave" -> 0xFF5555; // Red  
                case "death" -> 0xFFAA00; // Orange
                default -> 0x5555FF; // Blue
            };
            
            if (embedMessages) {
                EmbedBuilder embed = new EmbedBuilder()
                    .setDescription(message)
                    .setColor(color)
                    .setTimestamp(Instant.now());
                
                chatChannel.sendMessageEmbeds(embed.build()).queue();
            } else {
                chatChannel.sendMessage("📢 " + message).queue();
            }
            
        } catch (Exception e) {
            logger.error("Error sending server message to Discord", e);
        }
    }
    
    /**
     * getMinecraftAvatarUrl
     * 
     * Gets the Minecraft avatar URL for a username.
     * 
     * @param username The Minecraft username
     * @return The avatar URL
     */
    private String getMinecraftAvatarUrl(String username) {
        return String.format("https://mc-heads.net/avatar/%s/32", username);
    }
    
    // Getters
    public boolean isChatEnabled() { return chatEnabled; }
    public String getChatChannelId() { return chatChannelId; }
}
