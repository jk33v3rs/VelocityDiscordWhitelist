package top.jk33v3rs.velocitydiscordwhitelist.commands;

import org.slf4j.Logger;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import top.jk33v3rs.velocitydiscordwhitelist.models.RankDefinition;
import top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent;
import top.jk33v3rs.velocitydiscordwhitelist.modules.RewardsHandler;
import top.jk33v3rs.velocitydiscordwhitelist.modules.XPManager;

/**
 * RankCommand allows players to check rank progress and statistics.
 */
public class RankCommand {
    
    private final RewardsHandler rewardsHandler;
    private final XPManager xpManager;
    private final Logger logger;
    
    /**
     * Constructor for RankCommand
     * 
     * @param rewardsHandler The rewards handler for rank information
     * @param xpManager The XP manager for XP information
     * @param logger The logger instance
     */
    public RankCommand(RewardsHandler rewardsHandler, XPManager xpManager, Logger logger) {
        this.rewardsHandler = rewardsHandler;
        this.xpManager = xpManager;
        this.logger = logger;
    }
    
    /**
     * Creates the BrigadierCommand for rank checking
     * 
     * @return The BrigadierCommand instance
     */
    public BrigadierCommand createCommand() {
        LiteralCommandNode<CommandSource> rankNode = BrigadierCommand.literalArgumentBuilder("rank")
            .requires(source -> source instanceof Player)
            .executes(context -> {
                Player player = (Player) context.getSource();
                showPlayerRank(player);
                return Command.SINGLE_SUCCESS;
            })
            .then(BrigadierCommand.literalArgumentBuilder("progress")
                .executes(context -> {
                    Player player = (Player) context.getSource();
                    showDetailedProgress(player);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(BrigadierCommand.literalArgumentBuilder("xp")
                .executes(context -> {
                    Player player = (Player) context.getSource();
                    showXPInfo(player);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(BrigadierCommand.literalArgumentBuilder("recent")
                .executes(context -> {
                    Player player = (Player) context.getSource();
                    showRecentXP(player);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .build();
            
        return new BrigadierCommand(rankNode);
    }
    
    /**
     * Shows the player's current rank information
     * 
     * @param player The player to show rank information for
     */
    private void showPlayerRank(Player player) {
        String playerUuid = player.getUniqueId().toString();
        
        rewardsHandler.getPlayerRank(playerUuid).thenAccept(playerRank -> {
            if (playerRank != null) {
                Component header = Component.text("═══ Your Rank Information ═══", NamedTextColor.GOLD, TextDecoration.BOLD);
                player.sendMessage(header);
                  Component rankInfo = Component.text("Current Rank: ", NamedTextColor.YELLOW)
                    .append(Component.text(playerRank.getFormattedRank(), NamedTextColor.GREEN));
                player.sendMessage(rankInfo);
                
                Component playTime = Component.text("Play Time: ", NamedTextColor.YELLOW)
                    .append(Component.text(formatPlayTime(playerRank.getPlayTimeMinutes()), NamedTextColor.WHITE));
                player.sendMessage(playTime);
                
                Component achievements = Component.text("Achievements: ", NamedTextColor.YELLOW)
                    .append(Component.text(playerRank.getAchievementsCompleted(), NamedTextColor.WHITE));
                player.sendMessage(achievements);
                
                if (playerRank.getLastPromotion() != null) {
                    Component lastPromo = Component.text("Last Promotion: ", NamedTextColor.YELLOW)
                        .append(Component.text(playerRank.getLastPromotion().toString(), NamedTextColor.WHITE));
                    player.sendMessage(lastPromo);
                }
                
                // Show total XP
                xpManager.getPlayerTotalXP(playerUuid).thenAccept(totalXP -> {
                    Component xpInfo = Component.text("Total XP: ", NamedTextColor.YELLOW)
                        .append(Component.text(totalXP, NamedTextColor.AQUA));
                    player.sendMessage(xpInfo);
                });
                
                player.sendMessage(Component.text("Use /rank progress for detailed progression info", NamedTextColor.GRAY, TextDecoration.ITALIC));
                
            } else {
                player.sendMessage(Component.text("Could not retrieve your rank information.", NamedTextColor.RED));
            }
        }).exceptionally(throwable -> {
            logger.error("Error retrieving rank for player " + player.getUsername(), throwable);
            player.sendMessage(Component.text("An error occurred while retrieving your rank.", NamedTextColor.RED));
            return null;
        });
    }
    
    /**
     * Shows detailed progression information for the player
     * 
     * @param player The player to show progression information for
     */
    private void showDetailedProgress(Player player) {
        String playerUuid = player.getUniqueId().toString();
        
        rewardsHandler.getPlayerRank(playerUuid).thenAccept(playerRank -> {
            if (playerRank != null) {
                Component header = Component.text("═══ Detailed Progress ═══", NamedTextColor.GOLD, TextDecoration.BOLD);
                player.sendMessage(header);
                  // Current rank progress
                Component currentRank = Component.text("Current Position: ", NamedTextColor.YELLOW)
                    .append(Component.text(playerRank.getFormattedRank(), NamedTextColor.GREEN));
                player.sendMessage(currentRank);
                
                // Progress metrics with calculated requirements
                int requiredPlayTime = rewardsHandler.calculateRequiredPlayTime(playerRank.getMainRank(), playerRank.getSubRank());
                Component timeProgress = Component.text("Play Time: ", NamedTextColor.YELLOW)
                    .append(Component.text(formatPlayTime(playerRank.getPlayTimeMinutes()), NamedTextColor.WHITE))
                    .append(Component.text(" / ", NamedTextColor.GRAY))
                    .append(Component.text(formatPlayTime(requiredPlayTime), NamedTextColor.GREEN));
                player.sendMessage(timeProgress);
                
                int requiredAchievements = rewardsHandler.calculateRequiredAchievements(playerRank.getMainRank(), playerRank.getSubRank());
                Component achievementProgress = Component.text("Achievements: ", NamedTextColor.YELLOW)
                    .append(Component.text(playerRank.getAchievementsCompleted(), NamedTextColor.WHITE))
                    .append(Component.text(" / ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(requiredAchievements), NamedTextColor.GREEN));
                player.sendMessage(achievementProgress);
                  // Next rank preview with requirements
                int[] nextRankArray = RankDefinition.getNextRank(playerRank.getMainRank(), playerRank.getSubRank());
                if (nextRankArray != null) {
                    int nextMainRank = nextRankArray[0];
                    int nextSubRank = nextRankArray[1];
                    String nextRankFormatted = RankDefinition.formatRankDisplay(nextSubRank, nextMainRank);
                    
                    player.sendMessage(Component.text("Next Rank: ", NamedTextColor.YELLOW)
                        .append(Component.text(nextRankFormatted, NamedTextColor.AQUA))
                        .append(Component.text(" (Need: " + formatPlayTime(rewardsHandler.calculateRequiredPlayTime(nextMainRank, nextSubRank)) + 
                                             ", " + rewardsHandler.calculateRequiredAchievements(nextMainRank, nextSubRank) + " achievements)", NamedTextColor.GRAY)));
                } else {
                    player.sendMessage(Component.text("Next Rank: ", NamedTextColor.YELLOW)
                        .append(Component.text("Maximum rank achieved!", NamedTextColor.GOLD)));
                }
                
                Component tips = Component.text("💡 Tip: Complete advancements and play actively to gain XP!", 
                                              NamedTextColor.LIGHT_PURPLE, TextDecoration.ITALIC);
                player.sendMessage(tips);
                
            } else {
                player.sendMessage(Component.text("Could not retrieve your progression information.", NamedTextColor.RED));
            }
        }).exceptionally(throwable -> {
            logger.error("Error retrieving detailed progress for player " + player.getUsername(), throwable);
            player.sendMessage(Component.text("An error occurred while retrieving your progress.", NamedTextColor.RED));
            return null;
        });
    }
    
    /**
     * Shows XP information for the player
     * 
     * @param player The player to show XP information for
     */
    private void showXPInfo(Player player) {
        String playerUuid = player.getUniqueId().toString();
        
        xpManager.getPlayerTotalXP(playerUuid).thenAccept(totalXP -> {
            Component header = Component.text("═══ XP Information ═══", NamedTextColor.GOLD, TextDecoration.BOLD);
            player.sendMessage(header);
            
            Component total = Component.text("Total XP: ", NamedTextColor.YELLOW)
                .append(Component.text(totalXP, NamedTextColor.AQUA, TextDecoration.BOLD));
            player.sendMessage(total);
            
            // XP sources breakdown would go here
            player.sendMessage(Component.text("XP Sources:", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("• Advancements: ", NamedTextColor.WHITE)
                .append(Component.text("Base XP per advancement", NamedTextColor.GRAY)));
            player.sendMessage(Component.text("• BlazeAndCaves: ", NamedTextColor.WHITE)
                .append(Component.text("Variable XP based on difficulty", NamedTextColor.GRAY)));
            player.sendMessage(Component.text("• Play Time: ", NamedTextColor.WHITE)
                .append(Component.text("XP per minute played", NamedTextColor.GRAY)));
            
            player.sendMessage(Component.text("Use /rank recent to see your recent XP gains", 
                                            NamedTextColor.GRAY, TextDecoration.ITALIC));
        }).exceptionally(throwable -> {
            logger.error("Error retrieving XP info for player " + player.getUsername(), throwable);
            player.sendMessage(Component.text("An error occurred while retrieving your XP information.", NamedTextColor.RED));
            return null;
        });
    }
    
    /**
     * Shows recent XP events for the player
     * 
     * @param player The player to show recent XP events for
     */
    private void showRecentXP(Player player) {
        String playerUuid = player.getUniqueId().toString();
        
        xpManager.getRecentXPEvents(playerUuid, 10).thenAccept(events -> {
            Component header = Component.text("═══ Recent XP Gains ═══", NamedTextColor.GOLD, TextDecoration.BOLD);
            player.sendMessage(header);
            
            if (events.isEmpty()) {
                player.sendMessage(Component.text("No recent XP events found.", NamedTextColor.GRAY));
                return;
            }
            
            for (XPEvent event : events) {
                Component eventLine = Component.text("• ", NamedTextColor.GRAY)
                    .append(Component.text("+" + event.getXpGained() + " XP", NamedTextColor.GREEN))
                    .append(Component.text(" from ", NamedTextColor.GRAY))
                    .append(Component.text(formatEventSource(event.getEventType(), event.getEventSource()), NamedTextColor.YELLOW))
                    .append(Component.text(" (" + formatTimeAgo(event.getTimestamp()) + ")", NamedTextColor.DARK_GRAY));
                player.sendMessage(eventLine);
            }
            
            player.sendMessage(Component.text("Showing last " + events.size() + " XP events", 
                                            NamedTextColor.GRAY, TextDecoration.ITALIC));
        }).exceptionally(throwable -> {
            logger.error("Error retrieving recent XP events for player " + player.getUsername(), throwable);
            player.sendMessage(Component.text("An error occurred while retrieving your recent XP.", NamedTextColor.RED));
            return null;
        });
    }
    
    /**
     * Formats play time minutes into a human-readable string
     * 
     * @param minutes The number of minutes to format
     * @return A formatted time string (e.g., "2h 30m", "1d 5h 15m")
     */
    private String formatPlayTime(int minutes) {
        if (minutes < 60) {
            return minutes + "m";
        }
        
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        
        if (hours < 24) {
            return hours + "h " + remainingMinutes + "m";
        }
        
        int days = hours / 24;
        int remainingHours = hours % 24;
        
        return days + "d " + remainingHours + "h " + remainingMinutes + "m";
    }
    
    /**
     * Formats an XP event source for display
     * 
     * @param eventType The type of event
     * @param eventSource The specific source
     * @return A formatted display string
     */
    private String formatEventSource(String eventType, String eventSource) {
        return switch (eventType) {
            case "ADVANCEMENT" -> "advancement: " + eventSource.replace("minecraft:", "").replace("_", " ");
            case "BLAZE_AND_CAVE" -> "BlazeAndCave: " + eventSource.replace("blazeandcave:", "").replace("_", " ");
            case "PLAYTIME" -> "playtime";
            case "KILL" -> "killing " + eventSource.replace("_", " ");
            case "BREAK_BLOCK" -> "breaking " + eventSource.replace("_", " ");
            default -> eventSource;
        };
    }
    
    /**
     * Formats a timestamp into a "time ago" string
     * 
     * @param timestamp The timestamp to format
     * @return A formatted "time ago" string (e.g., "2m ago", "1h ago")
     */
    private String formatTimeAgo(java.time.Instant timestamp) {
        long seconds = java.time.Duration.between(timestamp, java.time.Instant.now()).getSeconds();
        
        if (seconds < 60) {
            return seconds + "s ago";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m ago";        } else if (seconds < 86400) {
            return (seconds / 3600) + "h ago";
        } else {
            return (seconds / 86400) + "d ago";
        }
    }
}
