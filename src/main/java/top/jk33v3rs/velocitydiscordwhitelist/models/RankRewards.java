package top.jk33v3rs.velocitydiscordwhitelist.models;

import java.util.List;
import java.util.ArrayList;

/**
 * RankRewards represents rewards given when a player reaches a specific rank.
 */
public class RankRewards {
    private int economyAmount;
    private List<String> commands;
    
    /**
     * Constructor for RankRewards
     */
    public RankRewards(int economyAmount, List<String> commands) {
        this.economyAmount = economyAmount;
        this.commands = commands != null ? commands : new ArrayList<>();
    }
    
    /**
     * Gets the economy reward amount
     * 
     * @return The amount of currency
     */
    public int getEconomyAmount() {
        return economyAmount;
    }
    
    /**
     * Sets the economy reward amount
     * 
     * @param economyAmount The amount of currency
     */
    public void setEconomyAmount(int economyAmount) {
        this.economyAmount = economyAmount;
    }
    
    /**
     * Gets the list of commands to execute
     * 
     * @return The list of commands
     */
    public List<String> getCommands() {
        return commands;
    }
    
    /**
     * Sets the list of commands to execute
     * 
     * @param commands The list of commands
     */
    public void setCommands(List<String> commands) {
        this.commands = commands != null ? commands : new ArrayList<>();
    }
    
    /**
     * Adds a command to the list
     * 
     * @param command The command to add
     */
    public void addCommand(String command) {
        if (command != null && !command.isEmpty()) {
            this.commands.add(command);
        }
    }
    
    /**
     * Creates a deep copy of this RankRewards instance
     * 
     * @return A new RankRewards instance with the same values
     */
    public RankRewards copy() {
        return new RankRewards(
            this.economyAmount,
            new ArrayList<>(this.commands)
        );
    }
    
    /**
     * Creates a default empty rewards instance
     * 
     * @return A new RankRewards instance with default values
     */
    public static RankRewards createEmpty() {
        return new RankRewards(0, new ArrayList<>());
    }
}
