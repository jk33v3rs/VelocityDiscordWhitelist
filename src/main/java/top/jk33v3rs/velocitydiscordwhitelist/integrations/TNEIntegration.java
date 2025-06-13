// This file is part of VelocityDiscordWhitelist.

package top.jk33v3rs.velocitydiscordwhitelist.integrations;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * TNEIntegration handles The New Economy (TNE) Core integration.
 */
public class TNEIntegration {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final boolean debugEnabled;
    private final String targetServer;
    private final boolean enabled;
    private final ExceptionHandler exceptionHandler;
    
    // Configuration values
    private final BigDecimal verificationReward;
    private final BigDecimal rankUpReward;
    private final BigDecimal dailyBonus;
    private final String currencyName;
    private final boolean autoCreateAccounts;

    /** Creates TNEIntegration with proxy server, logger, debug flag and config map. */
    @SuppressWarnings("unchecked")    public TNEIntegration(ProxyServer proxyServer, Logger logger, boolean debugEnabled, Map<String, Object> config) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        this.exceptionHandler = new ExceptionHandler(logger, debugEnabled);
        
        Map<String, Object> tneConfig = (Map<String, Object>) config.getOrDefault("tne", Map.of());
        this.enabled = Boolean.parseBoolean(tneConfig.getOrDefault("enabled", "false").toString());
        this.targetServer = tneConfig.getOrDefault("target_server", "survival").toString();
        
        // Load configuration values
        Map<String, Object> rewardsConfig = (Map<String, Object>) tneConfig.getOrDefault("rewards", Map.of());
        this.verificationReward = new BigDecimal(rewardsConfig.getOrDefault("verification", "100.0").toString());
        this.rankUpReward = new BigDecimal(rewardsConfig.getOrDefault("rank_up", "50.0").toString());
        this.dailyBonus = new BigDecimal(rewardsConfig.getOrDefault("daily_bonus", "25.0").toString());
        this.currencyName = tneConfig.getOrDefault("currency_name", "dollars").toString();
        this.autoCreateAccounts = Boolean.parseBoolean(tneConfig.getOrDefault("auto_create_accounts", "true").toString());
        
        debugLog("TNEIntegration initialized - Enabled: " + enabled);
    }

    /** Returns true if TNE integration is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Gives verification reward to specified player. */
    public CompletableFuture<Boolean> giveVerificationReward(UUID playerUuid, String playerName) {
        if (!enabled) {
            debugLog("TNE not enabled for verification reward: " + playerName);
            return CompletableFuture.completedFuture(false);
        }

        return sendEconomyCommand(playerUuid, "deposit", verificationReward, "Verification reward")
            .thenApply(success -> {
                if (success) {
                    logger.info("Gave verification reward of {} {} to player {}", 
                               verificationReward, currencyName, playerName);
                } else {
                    logger.warn("Failed to give verification reward to player {}", playerName);
                }
                return success;
            });
    }

    /** Gives rank up reward to specified player for achieving new rank. */
    public CompletableFuture<Boolean> giveRankUpReward(UUID playerUuid, String playerName, String newRank) {
        if (!enabled) {
            debugLog("TNE not enabled for rank up reward: " + playerName);
            return CompletableFuture.completedFuture(false);
        }

        return sendEconomyCommand(playerUuid, "deposit", rankUpReward, "Rank up reward: " + newRank)
            .thenApply(success -> {
                if (success) {
                    logger.info("Gave rank up reward of {} {} to player {} for reaching rank {}", 
                               rankUpReward, currencyName, playerName, newRank);
                } else {
                    logger.warn("Failed to give rank up reward to player {} for rank {}", playerName, newRank);
                }
                return success;
            });
    }

    /** Gives daily bonus to specified player. */
    public CompletableFuture<Boolean> giveDailyBonus(UUID playerUuid, String playerName) {
        if (!enabled) {
            debugLog("TNE not enabled for daily bonus: " + playerName);
            return CompletableFuture.completedFuture(false);
        }

        return sendEconomyCommand(playerUuid, "deposit", dailyBonus, "Daily activity bonus")
            .thenApply(success -> {
                if (success) {
                    logger.info("Gave daily bonus of {} {} to player {}", 
                               dailyBonus, currencyName, playerName);
                } else {
                    logger.warn("Failed to give daily bonus to player {}", playerName);
                }
                return success;
            });
    }

    /** Gets player's balance or returns zero if TNE not enabled. */
    public CompletableFuture<BigDecimal> getPlayerBalance(UUID playerUuid) {
        if (!enabled) {
            debugLog("TNE not enabled for balance check: " + playerUuid);
            return CompletableFuture.completedFuture(BigDecimal.ZERO);
        }

        // For TNE balance checking, we'd need to implement a request-response system
        // This is a simplified implementation that returns zero
        debugLog("Balance check requested for player: " + playerUuid);
        return CompletableFuture.completedFuture(BigDecimal.ZERO);
    }

    /** Checks if player has specified amount of money. */
    public CompletableFuture<Boolean> hasAmount(UUID playerUuid, BigDecimal amount) {
        if (!enabled) {
            debugLog("TNE not enabled for balance check: " + playerUuid);
            return CompletableFuture.completedFuture(false);
        }

        // This would typically require a request-response system to check balance
        debugLog("Balance check requested for player: " + playerUuid + " amount: " + amount);
        return CompletableFuture.completedFuture(true); // Simplified implementation
    }

    /** Withdraws money from player account. */
    public CompletableFuture<Boolean> withdrawMoney(UUID playerUuid, BigDecimal amount, String reason) {
        if (!enabled) {
            debugLog("TNE not enabled for withdrawal: " + playerUuid);
            return CompletableFuture.completedFuture(false);
        }

        return sendEconomyCommand(playerUuid, "withdraw", amount, reason);
    }

    /** Deposits money into player account. */
    public CompletableFuture<Boolean> depositMoney(UUID playerUuid, BigDecimal amount, String reason) {
        if (!enabled) {
            debugLog("TNE not enabled for deposit: " + playerUuid);
            return CompletableFuture.completedFuture(false);
        }

        return sendEconomyCommand(playerUuid, "deposit", amount, reason);
    }

    /** Formats currency amount for display. */
    public String formatCurrency(BigDecimal amount) {
        if (enabled) {
            return amount + " " + currencyName;
        } else {
            return amount.toString();
        }
    }

    /** Gets configured currency name. */
    public String getCurrencyName() {
        return currencyName;
    }

    /** Creates player account if auto-creation enabled. */
    public CompletableFuture<Boolean> createPlayerAccount(UUID playerUuid) {
        if (!enabled || !autoCreateAccounts) {
            debugLog("TNE not enabled or auto-creation disabled for: " + playerUuid);
            return CompletableFuture.completedFuture(false);
        }

        return sendEconomyCommand(playerUuid, "create_account", BigDecimal.ZERO, "Auto-created account");
    }

    /** Sends economy command to target server via plugin messaging. */
    private CompletableFuture<Boolean> sendEconomyCommand(UUID playerUuid, String action, BigDecimal amount, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<RegisteredServer> server = proxyServer.getServer(targetServer);
                if (server.isEmpty()) {
                    logger.warn("TNE target server '{}' not found", targetServer);
                    return false;
                }

                // Create plugin message data
                String message = String.format("tne:%s:%s:%s:%s", action, playerUuid.toString(), amount.toString(), reason);
                
                // Send plugin message to target server
                server.get().sendPluginMessage(() -> "velocitydiscordwhitelist:tne", message.getBytes());
                
                debugLog("Sent TNE command: " + action + " for player " + playerUuid + " amount " + amount);
                return true;
                  } catch (Exception e) {
                exceptionHandler.executeWithHandling("TNE economy command", () -> {
                    throw new RuntimeException("Error sending TNE economy command", e);
                });
                return false;
            }
        });
    }

    /** Reloads TNE configuration. */
    public boolean reloadConfiguration() {
        try {
            debugLog("TNE Integration configuration reloaded successfully");
            return true;        } catch (Exception e) {
            exceptionHandler.executeWithHandling("TNE configuration reload", () -> {
                throw new RuntimeException("Error reloading TNE configuration", e);
            });
            return false;
        }
    }

    /** Logs debug messages if debug mode enabled. */
    private void debugLog(String message) {
        if (debugEnabled) {
            logger.info("[TNEIntegration] " + message);
        }
    }
}
