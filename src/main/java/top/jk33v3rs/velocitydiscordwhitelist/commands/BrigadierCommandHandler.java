package top.jk33v3rs.velocitydiscordwhitelist.commands;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import top.jk33v3rs.velocitydiscordwhitelist.modules.EnhancedPurgatoryManager;
import com.velocitypowered.api.command.CommandManager;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Handles Minecraft verification commands (/verify only). */
public class BrigadierCommandHandler {
    private final ProxyServer server;
    private final Logger logger;
    private final EnhancedPurgatoryManager purgatoryManager;
    private final boolean debugEnabled;

    @Inject
    public BrigadierCommandHandler(ProxyServer server, Logger logger, EnhancedPurgatoryManager purgatoryManager, boolean debugEnabled) {
        this.server = server;
        this.logger = logger;
        this.purgatoryManager = purgatoryManager;
        this.debugEnabled = debugEnabled;
        registerCommands();
    }

    private void debugLog(String message) {
        if (debugEnabled) {
            logger.info("[BrigadierCommandHandler] " + message);
        }
    }

    public void registerCommands() {
        CommandManager commandManager = server.getCommandManager();

        // Register /verify command
        CommandMeta verifyMeta = commandManager.metaBuilder("verify")
            .build();
        BrigadierCommand verifyCommand = createVerifyCommand();
        commandManager.register(verifyMeta, verifyCommand);
        debugLog("Registered /verify command");
    }

    private BrigadierCommand createVerifyCommand() {
        LiteralCommandNode<CommandSource> verifyNode = BrigadierCommand.literalArgumentBuilder("verify")
            .then(BrigadierCommand.requiredArgumentBuilder("code", StringArgumentType.word())
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String code = context.getArgument("code", String.class);

                    if (!(source instanceof Player)) {
                        source.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    Player player = (Player) source;
                    String username = player.getUsername();

                    debugLog("Player " + username + " attempting verification with code " + code);

                    // Check for active session
                    Optional<EnhancedPurgatoryManager.PurgatorySession> existingSession = purgatoryManager.getSession(username);
                    if (existingSession.isEmpty()) {
                        debugLog("No active session found for player " + username);
                        source.sendMessage(Component.text("You don't have an active verification session. Please use /mc command in our Discord server to start verification.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    EnhancedPurgatoryManager.PurgatorySession session = existingSession.get();
                    debugLog("Found session for " + username + " (attempts: " + session.getVerificationAttempts() + ")");

                    // Check if session is expired
                    if (session.isExpired()) {
                        debugLog("Session expired for player " + username);
                        source.sendMessage(Component.text("Your verification session has expired. Please use /mc in Discord to get a new code.", NamedTextColor.RED));
                        purgatoryManager.removeSession(username);
                        return Command.SINGLE_SUCCESS;
                    }

                    // Check if code was already used
                    if (session.isCodeUsed()) {
                        debugLog("Attempt to use already-used code for player " + username);
                        source.sendMessage(Component.text("This code has already been used. Please use /mc in Discord to get a new code if needed.", NamedTextColor.RED));
                        purgatoryManager.removeSession(username);
                        return Command.SINGLE_SUCCESS;
                    }

                    // Validate the code
                    boolean validCode = purgatoryManager.validateCode(username, code);
                    
                    if (validCode) {
                        debugLog("Code validation successful for player " + username);
                        
                        // Complete verification
                        CompletableFuture<Boolean> completionFuture = purgatoryManager.completeVerification(username, player.getUniqueId());
                        completionFuture.thenAccept(success -> {
                            if (success) {
                                source.sendMessage(Component.text("Successfully verified! You can now join the server.", NamedTextColor.GREEN));
                                debugLog("Player " + username + " successfully verified");
                                
                                // Check if Discord was linked
                                if (session.getDiscordUserId() != null) {
                                    source.sendMessage(Component.text("Your Minecraft account has been linked to Discord account " + session.getDiscordUsername(), NamedTextColor.GREEN));
                                    debugLog("Discord account linked: " + session.getDiscordUsername() + " (" + session.getDiscordUserId() + ")");
                                }
                            } else {
                                source.sendMessage(Component.text("An error occurred during verification. Please try again or contact an admin.", NamedTextColor.RED));
                                debugLog("Failed to complete verification for player " + username);
                            }
                        });
                    } else {
                        // Handle failed validation
                        int attemptsLeft = EnhancedPurgatoryManager.PurgatorySession.MAX_VERIFICATION_ATTEMPTS - session.getVerificationAttempts();
                        debugLog("Validation failed for player " + username + " with code: " + code + " (" + attemptsLeft + " attempts left)");

                        // Check for max attempts
                        if (session.getVerificationAttempts() >= EnhancedPurgatoryManager.PurgatorySession.MAX_VERIFICATION_ATTEMPTS) {
                            debugLog("Player " + username + " reached max verification attempts, removing session");
                            source.sendMessage(Component.text("Too many failed attempts. Please use /mc to start over.", NamedTextColor.RED));
                            purgatoryManager.removeSession(username);
                        } else {
                            source.sendMessage(Component.text("Invalid verification code. Please check the code and try again. (" + attemptsLeft + " attempts remaining)", NamedTextColor.RED));
                            if (attemptsLeft <= 3) {
                                source.sendMessage(Component.text("Warning: After " + attemptsLeft + " more failed attempts, you'll need to start over with /mc in Discord", NamedTextColor.GOLD));
                            }
                        }
                    }

                    return Command.SINGLE_SUCCESS;
                }))
            .executes(context -> {
                context.getSource().sendMessage(Component.text("Usage: /verify <code>", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            })
            .build();

        return new BrigadierCommand(verifyNode);
    }
}
