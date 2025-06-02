package top.jk33v3rs.velocitydiscordwhitelist.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.co        // Build the status message
        Component statusMessage = Component.text("═══════════════════════════════════════")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .appendNewline()
                .append(Component.text("        VERIFICATION STATUS")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("═══════════════════════════════════════")
                        .color(NamedTextColor.GOLD)dSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import top.jk33v3rs.velocitydiscordwhitelist.modules.VelocityDiscordWhitelist;
import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;
import top.jk33v3rs.velocitydiscordwhitelist.utils.MessageLoader;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * VerifyStatusCommand handles the /verifystatus command for checking player verification status.
 * This command allows players to check their verification status including platform and Discord linking.
 */
public class VerifyStatusCommand {

    private final VelocityDiscordWhitelist plugin;
    private final SQLHandler sqlHandler;
    private final MessageLoader messageLoader;

    /**
     * Constructor for VerifyStatusCommand
     * 
     * @param plugin The main plugin instance
     * @param messageLoader The message loader for localized messages
     */
    public VerifyStatusCommand(VelocityDiscordWhitelist plugin, MessageLoader messageLoader) {
        this.plugin = plugin;
        this.sqlHandler = plugin.getSqlHandler();
        this.messageLoader = messageLoader;
    }

    /**
     * Execute the verify status command
     * 
     * @param context The command context
     * @return Command result
     */
    public int execute(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        
        if (!(source instanceof Player)) {
            source.sendMessage(messageLoader.getMessage(
                "command.players_only", 
                "This command can only be used by players."));
            return Command.SINGLE_SUCCESS;
        }
        
        Player player = (Player) source;
        String playerUuid = player.getUniqueId().toString();
        
        // Send initial message
        player.sendMessage(messageLoader.getMessage(
            "verify.status.checking", 
            "Checking your verification status..."));
        
        // Async database query
        CompletableFuture.supplyAsync(() -> {
            return sqlHandler.getPlayerVerificationStatus(playerUuid);
        }).thenAccept(status -> {
            sendVerificationStatus(player, status);
        }).exceptionally(throwable -> {
            player.sendMessage(Component.text("Error checking verification status: " + throwable.getMessage())
                    .color(NamedTextColor.RED));
            return null;
        });
        
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Send verification status information to the player
     * 
     * @param player The player to send the message to
     * @param status Map containing verification status information
     */
    private void sendVerificationStatus(Player player, Map<String, Object> status) {
        if (status.containsKey("error")) {
            player.sendMessage(messageLoader.getMessage(
                "verify.status.error.database", 
                "Error retrieving verification status from database."));
            return;
        }
        
        if (!(Boolean) status.getOrDefault("exists", false)) {
            player.sendMessage(Component.text()
                    .append(Component.text("═══════════════════════════════════════")
                            .color(NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD))
                    .appendNewline()
                    .append(Component.text("        VERIFICATION STATUS")
                            .color(NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD))
                    .appendNewline()
                    .append(Component.text("═══════════════════════════════════════")
                            .color(NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD))
                    .appendNewline()
                    .append(Component.text("Status: ")
                            .color(NamedTextColor.WHITE))
                    .append(Component.text("NOT VERIFIED")
                            .color(NamedTextColor.RED)
                            .decorate(TextDecoration.BOLD))
                    .appendNewline()
                    .append(Component.text("Platform: ")
                            .color(NamedTextColor.WHITE))
                    .append(Component.text("Unknown")
                            .color(NamedTextColor.GRAY))
                    .appendNewline()
                    .append(Component.text("Discord: ")
                            .color(NamedTextColor.WHITE))
                    .append(Component.text("Not linked")
                            .color(NamedTextColor.GRAY))
                    .appendNewline()
                    .appendNewline()
                    .append(Component.text("You need to verify your account to access the server.")
                            .color(NamedTextColor.YELLOW))
                    .appendNewline()
                    .append(Component.text("Use ")
                            .color(NamedTextColor.WHITE))
                    .append(Component.text("/verify")
                            .color(NamedTextColor.GREEN)
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text(" to start the verification process.")
                            .color(NamedTextColor.WHITE))
                    .build());
            return;
        }
        
        String verificationState = (String) status.get("verification_state");
        String discordId = (String) status.get("discord_id");
        Boolean isBedrock = (Boolean) status.get("is_bedrock");
        String linkedJavaUuid = (String) status.get("linked_java_uuid");
        
        // Determine platform information
        String platform;
        String platformDetails = "";
        NamedTextColor platformColor;
        
        if (isBedrock != null && isBedrock) {
            platform = "BEDROCK";
            platformColor = NamedTextColor.AQUA;
            if (linkedJavaUuid != null && !linkedJavaUuid.isEmpty()) {
                platformDetails = " (Linked to Java account)";
            }
        } else {
            platform = "JAVA";
            platformColor = NamedTextColor.GREEN;
        }
        
        // Determine verification status color and display
        NamedTextColor statusColor;
        String statusText;
        switch (verificationState != null ? verificationState.toUpperCase() : "UNKNOWN") {
            case "VERIFIED":
                statusColor = NamedTextColor.GREEN;
                statusText = "VERIFIED ✓";
                break;
            case "PURGATORY":
                statusColor = NamedTextColor.YELLOW;
                statusText = "PENDING APPROVAL";
                break;
            case "UNVERIFIED":
                statusColor = NamedTextColor.RED;
                statusText = "UNVERIFIED";
                break;
            default:
                statusColor = NamedTextColor.GRAY;
                statusText = "UNKNOWN";
                break;
        }
        
        // Build the status message
        Component.TextComponent.Builder messageBuilder = Component.text()
                .append(Component.text("═══════════════════════════════════════")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("        VERIFICATION STATUS")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("═══════════════════════════════════════")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("Status: ")
                        .color(NamedTextColor.WHITE))
                .append(Component.text(statusText)
                        .color(statusColor)
                        .decorate(TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("Platform: ")
                        .color(NamedTextColor.WHITE))
                .append(Component.text(platform + platformDetails)
                        .color(platformColor)
                        .decorate(TextDecoration.BOLD))
                .appendNewline();
        
        if (discordId != null && !discordId.isEmpty()) {
            messageBuilder.append(Component.text("Discord: ")
                    .color(NamedTextColor.WHITE))
                    .append(Component.text("Linked (ID: " + discordId + ")")
                            .color(NamedTextColor.BLUE)
                            .decorate(TextDecoration.BOLD))
                    .appendNewline();
        } else {
            messageBuilder.append(Component.text("Discord: ")
                    .color(NamedTextColor.WHITE))
                    .append(Component.text("Not linked")
                            .color(NamedTextColor.GRAY))
                    .appendNewline();
        }
        
        // Add status-specific messages
        messageBuilder.appendNewline();
        switch (verificationState != null ? verificationState.toUpperCase() : "UNKNOWN") {
            case "VERIFIED":
                messageBuilder.append(Component.text("You are fully verified and have access to the server!")
                        .color(NamedTextColor.GREEN));
                break;
            case "PURGATORY":
                messageBuilder.append(Component.text("Your verification is pending approval by staff.")
                        .color(NamedTextColor.YELLOW))
                        .appendNewline()
                        .append(Component.text("Please wait for a staff member to review your application.")
                                .color(NamedTextColor.WHITE));
                break;
            case "UNVERIFIED":
                messageBuilder.append(Component.text("You need to complete the verification process.")
                        .color(NamedTextColor.YELLOW))
                        .appendNewline()
                        .append(Component.text("Use ")
                                .color(NamedTextColor.WHITE))
                        .append(Component.text("/verify")
                                .color(NamedTextColor.GREEN)
                                .decorate(TextDecoration.BOLD))
                        .append(Component.text(" to start verification.")
                                .color(NamedTextColor.WHITE));
                break;
            default:
                messageBuilder.append(Component.text("Unknown verification status. Please contact staff.")
                        .color(NamedTextColor.RED));
                break;
        }
        
        player.sendMessage(messageBuilder.build());
    }
}
