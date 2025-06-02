package top.jk33v3rs.velocitydiscordwhitelist.models;

import java.time.Instant;

/**
 * XPEvent represents an individual XP gain event with rate limiting data.
 */
public class XPEvent {
    private String playerUuid;
    private String eventType;
    private String eventSource;
    private int xpGained;
    private Instant timestamp;
    private String serverName;
    private String metadata;
    
    /**
     * Constructor for XPEvent
     * 
     * @param playerUuid The UUID of the player who gained XP
     * @param eventType The type of event (e.g., "ADVANCEMENT", "BLAZE_AND_CAVE", "PLAYTIME", "KILL", "BREAK_BLOCK")
     * @param eventSource The specific source of the XP (e.g., advancement name, mob type, block type)
     * @param xpGained The amount of XP gained from this event
     * @param timestamp The timestamp when the XP was gained
     * @param serverName The name of the server where the event occurred
     * @param metadata Additional JSON metadata about the event
     */
    public XPEvent(String playerUuid, String eventType, String eventSource, int xpGained, 
                   Instant timestamp, String serverName, String metadata) {
        this.playerUuid = playerUuid;
        this.eventType = eventType;
        this.eventSource = eventSource;
        this.xpGained = xpGained;
        this.timestamp = timestamp;
        this.serverName = serverName;
        this.metadata = metadata;
    }
    
    /**
     * Gets the player UUID
     * 
     * @return The player's UUID
     */
    public String getPlayerUuid() {
        return playerUuid;
    }
    
    /**
     * Sets the player UUID
     * 
     * @param playerUuid The player's UUID
     */
    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }
    
    /**
     * Gets the event type
     * 
     * @return The type of XP event
     */
    public String getEventType() {
        return eventType;
    }
    
    /**
     * Sets the event type
     * 
     * @param eventType The type of XP event
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    /**
     * Gets the event source
     * 
     * @return The specific source of the XP gain
     */
    public String getEventSource() {
        return eventSource;
    }
    
    /**
     * Sets the event source
     * 
     * @param eventSource The specific source of the XP gain
     */
    public void setEventSource(String eventSource) {
        this.eventSource = eventSource;
    }
    
    /**
     * Gets the XP gained
     * 
     * @return The amount of XP gained
     */
    public int getXpGained() {
        return xpGained;
    }
    
    /**
     * Sets the XP gained
     * 
     * @param xpGained The amount of XP gained
     */
    public void setXpGained(int xpGained) {
        this.xpGained = xpGained;
    }
    
    /**
     * Gets the timestamp
     * 
     * @return The timestamp when XP was gained
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Sets the timestamp
     * 
     * @param timestamp The timestamp when XP was gained
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Gets the server name
     * 
     * @return The server where the event occurred
     */
    public String getServerName() {
        return serverName;
    }
    
    /**
     * Sets the server name
     * 
     * @param serverName The server where the event occurred
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
    
    /**
     * Gets the metadata
     * 
     * @return Additional JSON metadata about the event
     */
    public String getMetadata() {
        return metadata;
    }
    
    /**
     * Sets the metadata
     * 
     * @param metadata Additional JSON metadata about the event
     */
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    /**
     * Generates a unique key for rate limiting based on player, event type, and source
     * 
     * @return A unique string key for rate limiting purposes
     */
    public String getRateLimitKey() {
        return playerUuid + ":" + eventType + ":" + eventSource;
    }
    
    @Override
    public String toString() {
        return "XPEvent{" +
                "playerUuid='" + playerUuid + '\'' +
                ", eventType='" + eventType + '\'' +
                ", eventSource='" + eventSource + '\'' +
                ", xpGained=" + xpGained +
                ", timestamp=" + timestamp +
                ", serverName='" + serverName + '\'' +
                ", metadata='" + metadata + '\'' +
                '}';
    }
}
