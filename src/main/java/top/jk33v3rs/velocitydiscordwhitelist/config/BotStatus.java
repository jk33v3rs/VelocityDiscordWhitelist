package top.jk33v3rs.velocitydiscordwhitelist.config;

/**
 * BotStatus
 * 
 * Represents the current status of the Discord bot connection.
 * Inspired by Spicord's BotStatus enum for clean status tracking.
 */
public enum BotStatus {
    /**
     * Bot is offline and not connected to Discord
     */
    OFFLINE,
    
    /**
     * Bot is currently starting up and attempting to connect
     */
    STARTING,
    
    /**
     * Bot is connected and ready to process commands
     */
    READY,
    
    /**
     * Bot is shutting down
     */
    STOPPING,
    
    /**
     * Bot encountered an error and is disabled
     */
    ERROR;
    
    /**
     * isOnline
     * 
     * Checks if the bot is in an online state (READY).
     * 
     * @return true if bot is ready, false otherwise
     */
    public boolean isOnline() {
        return this == READY;
    }
    
    /**
     * canProcessCommands
     * 
     * Checks if the bot can process Discord commands.
     * 
     * @return true if bot can process commands, false otherwise
     */
    public boolean canProcessCommands() {
        return this == READY;
    }
}
