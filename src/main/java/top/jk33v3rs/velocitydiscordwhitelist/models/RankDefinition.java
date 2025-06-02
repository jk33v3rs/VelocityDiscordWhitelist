package top.jk33v3rs.velocitydiscordwhitelist.models;

/**
 * RankDefinition represents a rank in the "tick-tock" ranking system.
 */
public class RankDefinition {
    private int rankId;
    private int mainRank;
    private int subRank;
    private String rankName;
    private long discordRoleId;
    private int requiredTimeMinutes;
    private int requiredAchievements;
    private String description;
    private RankRewards rewards;
    
    /**
     * Constructor for RankDefinition
     */
    public RankDefinition(int rankId, int mainRank, int subRank, String rankName, 
                         long discordRoleId, int requiredTimeMinutes, 
                         int requiredAchievements, String description,
                         RankRewards rewards) {
        this.rankId = rankId;
        this.mainRank = mainRank;
        this.subRank = subRank;
        this.rankName = rankName;
        this.discordRoleId = discordRoleId;
        this.requiredTimeMinutes = requiredTimeMinutes;
        this.requiredAchievements = requiredAchievements;
        this.description = description;
        this.rewards = rewards != null ? rewards : RankRewards.createEmpty();
    }
    
    /**
     * Constructor for RankDefinition without rewards
     * 
     * @param rankId The unique identifier for this rank
     * @param mainRank The main rank ID (e.g., 1 for Newcomer)
     * @param subRank The sub-rank ID (e.g., 0 for Fresh)
     * @param rankName The display name of this rank (combined sub-rank + main rank name)
     * @param discordRoleId The Discord role ID associated with this rank
     * @param requiredTimeMinutes The required playtime in minutes to achieve this rank
     * @param requiredAchievements The required number of achievements to achieve this rank
     * @param description A text description of this rank
     */
    public RankDefinition(int rankId, int mainRank, int subRank, String rankName, 
                         long discordRoleId, int requiredTimeMinutes, 
                         int requiredAchievements, String description) {
        this(rankId, mainRank, subRank, rankName, discordRoleId, requiredTimeMinutes, 
             requiredAchievements, description, RankRewards.createEmpty());
    }
    
    // Standard getters and setters
    public int getRankId() { return rankId; }
    public void setRankId(int rankId) { this.rankId = rankId; }
    
    public int getMainRank() { return mainRank; }
    public void setMainRank(int mainRank) { this.mainRank = mainRank; }
    
    public int getSubRank() { return subRank; }
    public void setSubRank(int subRank) { this.subRank = subRank; }
    
    public String getRankName() { return rankName; }
    public void setRankName(String rankName) { this.rankName = rankName; }
    
    public long getDiscordRoleId() { return discordRoleId; }
    public void setDiscordRoleId(long discordRoleId) { this.discordRoleId = discordRoleId; }
    
    public int getRequiredTimeMinutes() { return requiredTimeMinutes; }
    public void setRequiredTimeMinutes(int requiredTimeMinutes) { this.requiredTimeMinutes = requiredTimeMinutes; }
    
    public int getRequiredAchievements() { return requiredAchievements; }
    public void setRequiredAchievements(int requiredAchievements) { this.requiredAchievements = requiredAchievements; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public RankRewards getRewards() { return rewards; }
    public void setRewards(RankRewards rewards) { this.rewards = rewards != null ? rewards : RankRewards.createEmpty(); }
    
    /**
     * getFormattedRankName
     * Gets the formatted full rank name (sub-rank + main rank)
     * @return The formatted rank name
     */
    public String getFormattedRankName() {
        String[] parts = rankName.split(" ", 2);
        return parts.length == 2 ? parts[0] + " " + parts[1] : rankName;
    }
    
    /**
     * getMainRankName
     * Gets just the main rank name portion
     * @return The main rank name
     */
    public String getMainRankName() {
        String[] parts = rankName.split(" ", 2);
        return parts.length == 2 ? parts[1] : rankName;
    }
    
    /**
     * getSubRankName
     * Gets just the sub-rank name portion
     * @return The sub-rank name
     */
    public String getSubRankName() {
        String[] parts = rankName.split(" ", 2);
        return parts.length == 2 ? parts[0] : "";
    }
    
    /**
     * getSubRankNameById
     * Gets the subrank name by ID (1-7 progression)
     * @param subRankId The subrank ID (1-7)
     * @return The subrank name
     */
    public static String getSubRankNameById(int subRankId) {
        switch (subRankId) {
            case 1: return "novice";
            case 2: return "apprentice";
            case 3: return "adept";
            case 4: return "master";
            case 5: return "heroic";
            case 6: return "mythic";
            case 7: return "immortal";
            default: return "unknown";
        }
    }
    
    /**
     * getSubRankIdByName
     * Gets the subrank ID by name
     * @param subRankName The subrank name (case insensitive)
     * @return The subrank ID (1-7), or 0 if not found
     */
    public static int getSubRankIdByName(String subRankName) {
        if (subRankName == null) return 0;
        
        switch (subRankName.toLowerCase()) {
            case "novice": return 1;
            case "apprentice": return 2;
            case "adept": return 3;
            case "master": return 4;
            case "heroic": return 5;
            case "mythic": return 6;
            case "immortal": return 7;
            default: return 0;
        }
    }
    
    /**
     * formatRankDisplay
     * Creates a formatted rank display using subrank and main rank
     * @param subRankId The subrank ID (1-7)
     * @param mainRankName The main rank name
     * @return The formatted rank string
     */
    public static String formatRankDisplay(int subRankId, String mainRankName) {
        String subRankName = getSubRankNameById(subRankId);
        if ("unknown".equals(subRankName) || mainRankName == null || mainRankName.isEmpty()) {
            return mainRankName != null ? mainRankName : "unknown";
        }
        return subRankName + " " + mainRankName;
    }
    
    public static boolean isValidSubRankId(int subRankId) { return subRankId >= 1 && subRankId <= 7; }
    
    /**
     * getMainRankNameById
     * Gets the main rank name by ID (1-25 progression)
     * @param mainRankId The main rank ID (1-25)
     * @return The main rank name
     */
    public static String getMainRankNameById(int mainRankId) {
        switch (mainRankId) {
            case 1: return "bystander";
            case 2: return "onlooker";
            case 3: return "wanderer";
            case 4: return "traveller";
            case 5: return "explorer";
            case 6: return "adventurer";
            case 7: return "surveyor";
            case 8: return "navigator";
            case 9: return "journeyman";
            case 10: return "pathfinder";
            case 11: return "trailblazer";
            case 12: return "pioneer";
            case 13: return "craftsman";
            case 14: return "specialist";
            case 15: return "artisan";
            case 16: return "veteran";
            case 17: return "sage";
            case 18: return "luminary";
            case 19: return "titan";
            case 20: return "legend";
            case 21: return "eternal";
            case 22: return "ascendant";
            case 23: return "celestial";
            case 24: return "divine";
            case 25: return "deity";
            default: return "unknown";
        }
    }
    
    /**
     * getMainRankIdByName
     * Gets the main rank ID by name
     * @param mainRankName The main rank name (case insensitive)
     * @return The main rank ID (1-25), or 0 if not found
     */
    public static int getMainRankIdByName(String mainRankName) {
        if (mainRankName == null) return 0;
        
        switch (mainRankName.toLowerCase()) {
            case "bystander": return 1;
            case "onlooker": return 2;
            case "wanderer": return 3;
            case "traveller": return 4;
            case "explorer": return 5;
            case "adventurer": return 6;
            case "surveyor": return 7;
            case "navigator": return 8;
            case "journeyman": return 9;
            case "pathfinder": return 10;
            case "trailblazer": return 11;
            case "pioneer": return 12;
            case "craftsman": return 13;
            case "specialist": return 14;
            case "artisan": return 15;
            case "veteran": return 16;
            case "sage": return 17;
            case "luminary": return 18;
            case "titan": return 19;
            case "legend": return 20;
            case "eternal": return 21;
            case "ascendant": return 22;
            case "celestial": return 23;
            case "divine": return 24;
            case "deity": return 25;
            default: return 0;
        }
    }
    
    /**
     * formatRankDisplay
     * Creates a formatted rank display using main and subrank IDs
     * @param subRankId The subrank ID (1-7)
     * @param mainRankId The main rank ID (1-25)
     * @return The formatted rank string
     */
    public static String formatRankDisplay(int subRankId, int mainRankId) {
        String subRankName = getSubRankNameById(subRankId);
        String mainRankName = getMainRankNameById(mainRankId);
        
        if ("unknown".equals(subRankName) || "unknown".equals(mainRankName)) {
            return "unknown rank";
        }
        
        return subRankName + " " + mainRankName;
    }
    
    public static boolean isValidMainRankId(int mainRankId) { return mainRankId >= 1 && mainRankId <= 25; }
    public static int getTotalMainRanks() { return 25; }
    public static int getTotalSubRanks() { return 7; }
    
    /**
     * getNextRank
     * Calculates the next rank progression in the ranking system
     * @param currentMainRank The current main rank ID (1-25)
     * @param currentSubRank The current sub-rank ID (1-7)
     * @return Array with [nextMainRank, nextSubRank], or null if at maximum rank
     */
    public static int[] getNextRank(int currentMainRank, int currentSubRank) {
        if (!isValidMainRankId(currentMainRank) || !isValidSubRankId(currentSubRank)) {
            return null;
        }
        
        // At maximum rank, no progression possible
        if (currentMainRank == 25 && currentSubRank == 7) {
            return null;
        }
        
        // Increment subrank if not at max
        if (currentSubRank < 7) {
            return new int[]{currentMainRank, currentSubRank + 1};
        }
        
        // Advance main rank and reset subrank
        if (currentMainRank < 25) {
            return new int[]{currentMainRank + 1, 1};
        }
        
        return null;
    }
    
    /**
     * formatOfficialRankDisplay
     * Creates a formatted rank display using both main and subrank names
     * @param mainRankId The main rank ID (1-25)
     * @param subRankId The subrank ID (1-7)
     * @return The formatted rank string
     */
    public static String formatOfficialRankDisplay(int mainRankId, int subRankId) {
        String mainRankName = getMainRankNameById(mainRankId);
        String subRankName = getSubRankNameById(subRankId);
        
        if ("unknown".equals(mainRankName) || "unknown".equals(subRankName)) {
            return "unknown rank";
        }
        
        return subRankName + " " + mainRankName;
    }
}
