package top.jk33v3rs.velocitydiscordwhitelist.models;

import java.time.Instant;

/**
 * Player rank data and progression tracking.
 */
public class PlayerRank {
    private String playerUuid;
    private int mainRank;
    private int subRank;
    private Instant joinDate;
    private int playTimeMinutes;
    private int achievementsCompleted;
    private Instant lastPromotion;
    private Instant verifiedAt;
    public PlayerRank(String playerUuid, int mainRank, int subRank, Instant joinDate, 
                     int playTimeMinutes, int achievementsCompleted, Instant lastPromotion) {
        this.playerUuid = playerUuid;
        this.mainRank = mainRank;
        this.subRank = subRank;
        this.joinDate = joinDate;
        this.playTimeMinutes = playTimeMinutes;
        this.achievementsCompleted = achievementsCompleted;
        this.lastPromotion = lastPromotion;
        this.verifiedAt = null; // Default to not verified
    }
    
    /**
     * Constructor for PlayerRank with verification timestamp
     */
    public PlayerRank(String playerUuid, int mainRank, int subRank, Instant joinDate, 
                     int playTimeMinutes, int achievementsCompleted, Instant lastPromotion,
                     Instant verifiedAt) {
        this.playerUuid = playerUuid;
        this.mainRank = mainRank;
        this.subRank = subRank;
        this.joinDate = joinDate;
        this.playTimeMinutes = playTimeMinutes;
        this.achievementsCompleted = achievementsCompleted;
        this.lastPromotion = lastPromotion;
        this.verifiedAt = verifiedAt;
    }
    
    /**
     * Gets the player's UUID
     * 
     * @return The player's UUID as a string
     */
    public String getPlayerUuid() {
        return playerUuid;
    }
    
    /**
     * Sets the player's UUID
     * 
     * @param playerUuid The player's UUID as a string
     */
    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }
    
    /**
     * Gets the player's current main rank ID
     * 
     * @return The main rank ID
     */
    public int getMainRank() {
        return mainRank;
    }
    
    /**
     * Sets the player's main rank ID
     * 
     * @param mainRank The main rank ID
     */
    public void setMainRank(int mainRank) {
        this.mainRank = mainRank;
    }
    
    /**
     * Gets the player's current sub-rank ID
     * 
     * @return The sub-rank ID
     */
    public int getSubRank() {
        return subRank;
    }
    
    /**
     * Sets the player's sub-rank ID
     * 
     * @param subRank The sub-rank ID
     */
    public void setSubRank(int subRank) {
        this.subRank = subRank;
    }
    
    /**
     * Gets the player's join date
     * 
     * @return The join date as an Instant
     */
    public Instant getJoinDate() {
        return joinDate;
    }
    
    /**
     * Sets the player's join date
     * 
     * @param joinDate The join date as an Instant
     */
    public void setJoinDate(Instant joinDate) {
        this.joinDate = joinDate;
    }
    
    /**
     * Gets the player's total play time in minutes
     * 
     * @return The play time in minutes
     */
    public int getPlayTimeMinutes() {
        return playTimeMinutes;
    }
    
    /**
     * Sets the player's total play time in minutes
     * 
     * @param playTimeMinutes The play time in minutes
     */
    public void setPlayTimeMinutes(int playTimeMinutes) {
        this.playTimeMinutes = playTimeMinutes;
    }
    
    /**
     * Gets the number of achievements the player has completed
     * 
     * @return The achievement count
     */
    public int getAchievementsCompleted() {
        return achievementsCompleted;
    }
    
    /**
     * Sets the number of achievements the player has completed
     * 
     * @param achievementsCompleted The achievement count
     */
    public void setAchievementsCompleted(int achievementsCompleted) {
        this.achievementsCompleted = achievementsCompleted;
    }
    
    /**
     * Gets the timestamp of the player's last promotion
     * 
     * @return The last promotion timestamp, or null if never promoted
     */
    public Instant getLastPromotion() {
        return lastPromotion;
    }
    
    /**
     * Sets the timestamp of the player's last promotion
     * 
     * @param lastPromotion The last promotion timestamp
     */
    public void setLastPromotion(Instant lastPromotion) {
        this.lastPromotion = lastPromotion;
    }
    
    /**
     * Gets the timestamp when the player was verified
     * 
     * @return The verification timestamp, or null if not verified
     */
    public Instant getVerifiedAt() {
        return verifiedAt;
    }
    
    /**
     * Sets the timestamp when the player was verified
     * 
     * @param verifiedAt The verification timestamp
     */
    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
    
    /**
     * Validates that the player's rank data follows the official subrank and main rank progression systems
     * Ensures subrank is within the valid range (1-7) and main rank is within valid range (1-25)
     * and corrects invalid values
     * 
     * @return True if validation passed or was corrected, false if data is completely invalid
     */
    public boolean validateAndCorrectRankData() {
        // Validate and correct subrank
        if (subRank < 1) {
            subRank = 1; // Default to novice
        } else if (subRank > 7) {
            subRank = 7; // Cap at immortal
        }
        
        // Validate and correct main rank
        if (mainRank < 1) {
            mainRank = 1; // Default to bystander
        } else if (mainRank > 25) {
            mainRank = 25; // Cap at deity
        }
        
        return true; // All data is valid or was corrected
    }
    
    /**
     * Gets a formatted display string for this player's rank
     * Uses the official subrank and main rank progression systems
     * 
     * @return The formatted rank string (e.g., "novice bystander")
     */
    public String getFormattedRank() {
        return RankDefinition.formatRankDisplay(subRank, mainRank);
    }
}
