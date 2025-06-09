package top.jk33v3rs.velocitydiscordwhitelist.models;

/**
 * BlazeAndCavesAdvancement represents a BlazeAndCaves 1.21 advancement with XP mapping.
 */
public class BlazeAndCavesAdvancement {
    private String namespacedKey;
    private String displayName;
    private String category;
    private int baseXP;
    private boolean isTerralithVariant;
    private boolean isHardcoreVariant;
    private String difficulty;
    private String description;
    
    /**
     * Constructor for BlazeAndCavesAdvancement
     * 
     * @param namespacedKey The full namespaced key of the advancement (e.g., "blazeandcave:end/elytra_superhero")
     * @param displayName The display name of the advancement
     * @param category The category (e.g., "nether", "end", "overworld", "terralith")
     * @param baseXP The base XP value for this advancement
     * @param isTerralithVariant Whether this is a Terralith-specific advancement
     * @param isHardcoreVariant Whether this is a Hardcore-specific advancement
     * @param difficulty The difficulty rating ("easy", "medium", "hard", "insane")
     * @param description A brief description of the advancement
     */
    public BlazeAndCavesAdvancement(String namespacedKey, String displayName, String category, 
                                   int baseXP, boolean isTerralithVariant, boolean isHardcoreVariant,
                                   String difficulty, String description) {
        this.namespacedKey = namespacedKey;
        this.displayName = displayName;
        this.category = category;
        this.baseXP = baseXP;
        this.isTerralithVariant = isTerralithVariant;
        this.isHardcoreVariant = isHardcoreVariant;
        this.difficulty = difficulty;
        this.description = description;
    }
    
    /**
     * Gets the namespaced key
     * 
     * @return The full namespaced key of the advancement
     */
    public String getNamespacedKey() {
        return namespacedKey;
    }
    
    /**
     * Sets the namespaced key
     * 
     * @param namespacedKey The full namespaced key of the advancement
     */
    public void setNamespacedKey(String namespacedKey) {
        this.namespacedKey = namespacedKey;
    }
    
    /**
     * Gets the display name
     * 
     * @return The display name of the advancement
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Sets the display name
     * 
     * @param displayName The display name of the advancement
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Gets the category
     * 
     * @return The category of the advancement
     */
    public String getCategory() {
        return category;
    }
    
    /**
     * Sets the category
     * 
     * @param category The category of the advancement
     */
    public void setCategory(String category) {
        this.category = category;
    }
    
    /**
     * Gets the base XP value
     * 
     * @return The base XP value for this advancement
     */
    public int getBaseXP() {
        return baseXP;
    }
    
    /**
     * Sets the base XP value
     * 
     * @param baseXP The base XP value for this advancement
     */
    public void setBaseXP(int baseXP) {
        this.baseXP = baseXP;
    }
    
    /**
     * Checks if this is a Terralith variant
     * 
     * @return True if this is a Terralith-specific advancement
     */
    public boolean isTerralithVariant() {
        return isTerralithVariant;
    }
    
    /**
     * Sets the Terralith variant flag
     * 
     * @param terralithVariant True if this is a Terralith-specific advancement
     */
    public void setTerralithVariant(boolean terralithVariant) {
        isTerralithVariant = terralithVariant;
    }
    
    /**
     * Checks if this is a Hardcore variant
     * 
     * @return True if this is a Hardcore-specific advancement
     */
    public boolean isHardcoreVariant() {
        return isHardcoreVariant;
    }
    
    /**
     * Sets the Hardcore variant flag
     * 
     * @param hardcoreVariant True if this is a Hardcore-specific advancement
     */
    public void setHardcoreVariant(boolean hardcoreVariant) {
        isHardcoreVariant = hardcoreVariant;
    }
    
    /**
     * Gets the difficulty rating
     * 
     * @return The difficulty rating of the advancement
     */
    public String getDifficulty() {
        return difficulty;
    }
    
    /**
     * Sets the difficulty rating
     * 
     * @param difficulty The difficulty rating of the advancement
     */
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
    
    /**
     * Gets the description
     * 
     * @return The description of the advancement
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Sets the description
     * 
     * @param description The description of the advancement
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * Calculates the final XP value with difficulty and variant modifiers
     * 
     * @return The final XP value including all modifiers
     */
    public int getFinalXP() {
        return getFinalXP(1.0, 1.25, 1.5, 2.0, 0.1, 0.5);
    }
    
    /**
     * Calculates the final XP value with configurable difficulty and variant modifiers
     * 
     * @param easyMultiplier Multiplier for easy difficulty
     * @param mediumMultiplier Multiplier for medium difficulty  
     * @param hardMultiplier Multiplier for hard difficulty
     * @param insaneMultiplier Multiplier for insane difficulty
     * @param terralithBonus Bonus multiplier for Terralith variants
     * @param hardcoreBonus Bonus multiplier for Hardcore variants
     * @return The final XP value including all modifiers
     */
    public int getFinalXP(double easyMultiplier, double mediumMultiplier, double hardMultiplier, 
                         double insaneMultiplier, double terralithBonus, double hardcoreBonus) {
        // Apply difficulty multipliers using modern switch expression
        double finalXP = baseXP * switch (difficulty.toLowerCase()) {
            case "easy" -> easyMultiplier;
            case "medium" -> mediumMultiplier;
            case "hard" -> hardMultiplier;
            case "insane" -> insaneMultiplier;
            default -> 1.0;
        };
        
        // Apply variant bonuses
        if (isTerralithVariant) {
            finalXP = finalXP * (1.0 + terralithBonus);
        }
        
        if (isHardcoreVariant) {
            finalXP = finalXP * (1.0 + hardcoreBonus);
        }
        
        return (int) finalXP;
    }
    
    /**
     * Checks if this advancement matches a given namespaced key
     * 
     * @param key The namespaced key to check against
     * @return True if the keys match
     */
    public boolean matches(String key) {
        return namespacedKey.equals(key);
    }
    
    @Override
    public String toString() {
        return "BlazeAndCavesAdvancement{" +
                "namespacedKey='" + namespacedKey + '\'' +
                ", displayName='" + displayName + '\'' +
                ", category='" + category + '\'' +
                ", baseXP=" + baseXP +
                ", finalXP=" + getFinalXP() +
                ", isTerralithVariant=" + isTerralithVariant +
                ", isHardcoreVariant=" + isHardcoreVariant +
                ", difficulty='" + difficulty + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
