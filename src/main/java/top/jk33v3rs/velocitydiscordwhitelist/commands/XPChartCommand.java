package top.jk33v3rs.velocitydiscordwhitelist.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Player command for viewing XP progression charts.
 */
public class XPChartCommand {
    
    public XPChartCommand() {
        // No fields needed for this command
    }
    
    public BrigadierCommand createCommand() {
        LiteralCommandNode<CommandSource> xpChartNode = BrigadierCommand.literalArgumentBuilder("xpchart")
            .requires(source -> source instanceof Player)
            .executes(context -> {
                Player player = (Player) context.getSource();
                showXPChart(player);
                return Command.SINGLE_SUCCESS;
            })
            .then(BrigadierCommand.literalArgumentBuilder("blazeandcave")
                .executes(context -> {
                    Player player = (Player) context.getSource();
                    showBlazeAndCaveChart(player);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(BrigadierCommand.literalArgumentBuilder("ranks")
                .executes(context -> {
                    Player player = (Player) context.getSource();
                    showRankRequirements(player);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(BrigadierCommand.literalArgumentBuilder("rates")
                .executes(context -> {
                    Player player = (Player) context.getSource();
                    showRateLimiting(player);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .build();
            
        return new BrigadierCommand(xpChartNode);
    }
    
    /**
     * Shows the main XP chart with all sources and their values
     * 
     * @param player The player to show the XP chart to
     */
    private void showXPChart(Player player) {
        Component header = Component.text("═══ XP Chart - What Grants XP ═══", NamedTextColor.GOLD, TextDecoration.BOLD);
        player.sendMessage(header);
        
        // Advancement XP
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("🏆 Regular Advancements:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Recipe unlocks: ", NamedTextColor.WHITE)
            .append(Component.text("5-10 XP", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("• Story advancements: ", NamedTextColor.WHITE)
            .append(Component.text("15-25 XP", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("• Challenge advancements: ", NamedTextColor.WHITE)
            .append(Component.text("25-50 XP", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("• Goal advancements: ", NamedTextColor.WHITE)
            .append(Component.text("50-100 XP", NamedTextColor.GREEN)));
        
        // BlazeAndCave XP
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("🔥 BlazeAndCave Advancements:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Easy: ", NamedTextColor.WHITE)
            .append(Component.text("10-25 XP", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("• Medium: ", NamedTextColor.WHITE)
            .append(Component.text("25-50 XP (+25% bonus)", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("• Hard: ", NamedTextColor.WHITE)
            .append(Component.text("50-100 XP (+50% bonus)", NamedTextColor.GOLD)));
        player.sendMessage(Component.text("• Insane: ", NamedTextColor.WHITE)
            .append(Component.text("100+ XP (+100% bonus)", NamedTextColor.RED)));
        player.sendMessage(Component.text("• Terralith bonus: ", NamedTextColor.WHITE)
            .append(Component.text("+10% XP", NamedTextColor.AQUA)));
        player.sendMessage(Component.text("• Hardcore bonus: ", NamedTextColor.WHITE)
            .append(Component.text("+50% XP", NamedTextColor.DARK_RED)));
        
        // Other sources
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("⏰ Other XP Sources:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Play time: ", NamedTextColor.WHITE)
            .append(Component.text("1-2 XP per minute (reduced rate)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("• Mob kills: ", NamedTextColor.WHITE)
            .append(Component.text("1-5 XP (reduced rate)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("• Block breaking: ", NamedTextColor.WHITE)
            .append(Component.text("0.1-1 XP (heavily limited)", NamedTextColor.DARK_GRAY)));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Use /xpchart blazeandcave for detailed BlazeAndCave info", 
                                        NamedTextColor.GRAY, TextDecoration.ITALIC));
        player.sendMessage(Component.text("Use /xpchart ranks for rank requirements", 
                                        NamedTextColor.GRAY, TextDecoration.ITALIC));
        player.sendMessage(Component.text("Use /xpchart rates for rate limiting info", 
                                        NamedTextColor.GRAY, TextDecoration.ITALIC));
    }
    
    /**
     * Shows detailed BlazeAndCave advancement information
     * 
     * @param player The player to show BlazeAndCave chart to
     */
    private void showBlazeAndCaveChart(Player player) {
        Component header = Component.text("═══ BlazeAndCave 1.21 XP Chart ═══", NamedTextColor.GOLD, TextDecoration.BOLD);
        player.sendMessage(header);
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("📊 XP Calculation Formula:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("Base XP × Difficulty Multiplier × Variant Bonuses", NamedTextColor.WHITE));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("🎯 Difficulty Multipliers:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Easy: ", NamedTextColor.GREEN)
            .append(Component.text("×1.0 (no bonus)", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("• Medium: ", NamedTextColor.YELLOW)
            .append(Component.text("×1.25 (+25%)", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("• Hard: ", NamedTextColor.GOLD)
            .append(Component.text("×1.5 (+50%)", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("• Insane: ", NamedTextColor.RED)
            .append(Component.text("×2.0 (+100%)", NamedTextColor.WHITE)));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("🌍 Variant Bonuses:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Terralith Datapack: ", NamedTextColor.AQUA)
            .append(Component.text("×1.1 (+10%)", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("• Hardcore Mode: ", NamedTextColor.DARK_RED)
            .append(Component.text("×1.5 (+50%)", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("• Combined: ", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text("×1.65 (+65%)", NamedTextColor.WHITE)));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("💎 Example Calculations:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Easy advancement (10 base): ", NamedTextColor.WHITE)
            .append(Component.text("10 XP", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("• Hard Terralith advancement (50 base): ", NamedTextColor.WHITE)
            .append(Component.text("50 × 1.5 × 1.1 = 82 XP", NamedTextColor.GOLD)));
        player.sendMessage(Component.text("• Insane Hardcore advancement (100 base): ", NamedTextColor.WHITE)
            .append(Component.text("100 × 2.0 × 1.5 = 300 XP", NamedTextColor.RED)));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("📝 Categories include: Overworld, Nether, End, Terralith, Hardcore", 
                                        NamedTextColor.GRAY, TextDecoration.ITALIC));
    }
    
    /**
     * Shows rank requirements and progression thresholds
     * 
     * @param player The player to show rank requirements to
     */
    private void showRankRequirements(Player player) {
        Component header = Component.text("═══ Rank Requirements Chart ═══", NamedTextColor.GOLD, TextDecoration.BOLD);
        player.sendMessage(header);
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("🏅 Rank Progression System:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("Ranks have main levels (1-10) and sub-ranks (1-5)", NamedTextColor.WHITE));
        player.sendMessage(Component.text("Progress through sub-ranks before advancing to next main rank", NamedTextColor.GRAY));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("📈 Sample Rank Structure:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        
        // Main Rank 1 - Bystander
        player.sendMessage(Component.text("Rank 1 - Bystander:", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.sendMessage(Component.text("  1.1 Novice: ", NamedTextColor.WHITE)
            .append(Component.text("0 XP, 0 minutes", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  1.2 Learner: ", NamedTextColor.WHITE)
            .append(Component.text("50 XP, 60 minutes", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  1.3 Explorer: ", NamedTextColor.WHITE)
            .append(Component.text("150 XP, 180 minutes", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  1.4 Adventurer: ", NamedTextColor.WHITE)
            .append(Component.text("300 XP, 360 minutes", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  1.5 Veteran: ", NamedTextColor.WHITE)
            .append(Component.text("500 XP, 600 minutes", NamedTextColor.GRAY)));
        
        // Main Rank 2 - Member
        player.sendMessage(Component.text("Rank 2 - Member:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("  2.1 Junior: ", NamedTextColor.WHITE)
            .append(Component.text("750 XP, 900 minutes", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  2.2 Regular: ", NamedTextColor.WHITE)
            .append(Component.text("1000 XP, 1200 minutes", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  2.3 Senior: ", NamedTextColor.WHITE)
            .append(Component.text("1500 XP, 1800 minutes", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  2.4 Expert: ", NamedTextColor.WHITE)
            .append(Component.text("2000 XP, 2400 minutes", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  2.5 Master: ", NamedTextColor.WHITE)
            .append(Component.text("2750 XP, 3000 minutes", NamedTextColor.GRAY)));
        
        // Higher ranks preview
        player.sendMessage(Component.text("Ranks 3-5: ", NamedTextColor.GOLD)
            .append(Component.text("Trusted, Elite, Legend", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Ranks 6-10: ", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text("Hero, Champion, Mythic, Divine, Transcendent", NamedTextColor.WHITE)));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("📊 Requirements combine:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Total XP earned", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• Play time (minutes)", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• Achievements completed", NamedTextColor.WHITE));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Use /rank progress to see your current progression", 
                                        NamedTextColor.GRAY, TextDecoration.ITALIC));
    }
    
    /**
     * Shows rate limiting information to explain XP farming prevention
     * 
     * @param player The player to show rate limiting info to
     */
    private void showRateLimiting(Player player) {
        Component header = Component.text("═══ XP Rate Limiting ═══", NamedTextColor.GOLD, TextDecoration.BOLD);
        player.sendMessage(header);
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("🛡️ Anti-Farming Protection:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("Rate limiting prevents XP farming and ensures fair progression", NamedTextColor.WHITE));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("⏱️ Cooldown Periods:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Same advancement: ", NamedTextColor.WHITE)
            .append(Component.text("5 second cooldown", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("• Same source type: ", NamedTextColor.WHITE)
            .append(Component.text("Varies by activity", NamedTextColor.GRAY)));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("📊 Rate Limits (per player):", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Per minute: ", NamedTextColor.WHITE)
            .append(Component.text("10 XP events maximum", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("• Per hour: ", NamedTextColor.WHITE)
            .append(Component.text("100 XP events maximum", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("• Per day: ", NamedTextColor.WHITE)
            .append(Component.text("500 XP events maximum", NamedTextColor.GRAY)));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("🎯 XP Source Modifiers:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Advancements: ", NamedTextColor.WHITE)
            .append(Component.text("100% XP (no reduction)", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("• BlazeAndCave: ", NamedTextColor.WHITE)
            .append(Component.text("100% XP + bonuses", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("• Play time: ", NamedTextColor.WHITE)
            .append(Component.text("50% XP (prevents AFK farming)", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("• Mob kills: ", NamedTextColor.WHITE)
            .append(Component.text("80% XP (prevents kill farming)", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("• Block breaking: ", NamedTextColor.WHITE)
            .append(Component.text("30% XP (prevents mining farming)", NamedTextColor.RED)));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("💡 Tips for Efficient Progression:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• Focus on unique advancements rather than repetitive actions", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• BlazeAndCave advancements give the best XP rates", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• Spread out your gameplay across different activities", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• Higher difficulty advancements give exponentially more XP", NamedTextColor.WHITE));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("⚡ If you hit rate limits:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("• You'll see reduced or no XP from repeated actions", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• Try different activities or take a short break", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• Limits reset over time (minutes to hours)", NamedTextColor.WHITE));
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("This system ensures everyone has a fair chance to progress!", 
                                        NamedTextColor.LIGHT_PURPLE, TextDecoration.ITALIC));
    }
}
