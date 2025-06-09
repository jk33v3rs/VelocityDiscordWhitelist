package top.jk33v3rs.velocitydiscordwhitelist.models;

/**
 * PlayerInfo represents basic player information from the whitelist database.
 * This class holds data about a player's UUID, username, Discord information,
 * and verification status.
 */
public class PlayerInfo {
    private final String uuid;
    private final String username;
    private final String discordId;
    private final String discordName;
    private final String verificationState;

    /**
     * Creates a new PlayerInfo instance with the specified data.
     *
     * @param uuid The player's UUID as a string
     * @param username The player's Minecraft username
     * @param discordId The player's Discord ID (can be null)
     * @param discordName The player's Discord username (can be null)
     * @param verificationState The player's verification state (UNVERIFIED, PURGATORY, VERIFIED)
     */
    public PlayerInfo(String uuid, String username, String discordId, String discordName, String verificationState) {
        this.uuid = uuid;
        this.username = username;
        this.discordId = discordId;
        this.discordName = discordName;
        this.verificationState = verificationState != null ? verificationState : "UNVERIFIED";
    }

    /**
     * Gets the player's UUID.
     *
     * @return The player's UUID as a string
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Gets the player's Minecraft username.
     *
     * @return The player's username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the player's Discord ID.
     *
     * @return The player's Discord ID, or null if not linked
     */
    public String getDiscordId() {
        return discordId;
    }

    /**
     * Gets the player's Discord username.
     *
     * @return The player's Discord username, or null if not linked
     */
    public String getDiscordName() {
        return discordName;
    }

    /**
     * Gets the player's verification state.
     *
     * @return The verification state (UNVERIFIED, PURGATORY, or VERIFIED)
     */
    public String getVerificationState() {
        return verificationState;
    }

    /**
     * Checks if the player has Discord linked.
     *
     * @return true if the player has a Discord ID, false otherwise
     */
    public boolean hasDiscordLinked() {
        return discordId != null && !discordId.isEmpty();
    }

    /**
     * Checks if the player is verified.
     *
     * @return true if the player's verification state is VERIFIED, false otherwise
     */
    public boolean isVerified() {
        return "VERIFIED".equalsIgnoreCase(verificationState);
    }

    /**
     * Checks if the player is in purgatory.
     *
     * @return true if the player's verification state is PURGATORY, false otherwise
     */
    public boolean isInPurgatory() {
        return "PURGATORY".equalsIgnoreCase(verificationState);
    }

    /**
     * Returns a string representation of the player info.
     *
     * @return A formatted string containing the player's basic information
     */
    @Override
    public String toString() {
        return String.format("PlayerInfo{uuid='%s', username='%s', discordId='%s', discordName='%s', verificationState='%s'}", 
            uuid, username, discordId, discordName, verificationState);
    }

    /**
     * Checks if this PlayerInfo is equal to another object.
     *
     * @param obj The object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        PlayerInfo that = (PlayerInfo) obj;
        return uuid != null ? uuid.equals(that.uuid) : that.uuid == null;
    }

    /**
     * Returns the hash code for this PlayerInfo.
     *
     * @return The hash code based on the UUID
     */
    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }
}