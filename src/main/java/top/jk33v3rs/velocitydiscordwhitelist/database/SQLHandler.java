package top.jk33v3rs.velocitydiscordwhitelist.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import com.velocitypowered.api.proxy.Player;
import top.jk33v3rs.velocitydiscordwhitelist.models.PlayerRank;
import top.jk33v3rs.velocitydiscordwhitelist.models.RankDefinition;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQLHandler manages database connections and whitelist operations using HikariCP.
 */
public class SQLHandler implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final Logger logger;
    private final String tableName;
    private final boolean debugEnabled;

    /**
     * Constructs SQLHandler with HikariCP connection pooling.
     */
    @SuppressWarnings("unchecked")
    public SQLHandler(Map<String, Object> config, Logger logger, boolean debugEnabled) {
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        Map<String, Object> dbConfig = (Map<String, Object>) config.get("database");
        this.tableName = dbConfig.getOrDefault("table", "whitelist").toString();

        try {
            // Explicitly load the MariaDB JDBC driver
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.error("Failed to load MariaDB JDBC driver", e);
            throw new RuntimeException("Failed to load MariaDB JDBC driver", e);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
        
        // Build JDBC URL from configuration
        String host = dbConfig.getOrDefault("host", "localhost").toString();
        int port = Integer.parseInt(dbConfig.getOrDefault("port", 3306).toString());
        String database = dbConfig.getOrDefault("database", "minecraft").toString();
        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s", host, port, database);
        
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(dbConfig.getOrDefault("username", "root").toString());
        hikariConfig.setPassword(dbConfig.getOrDefault("password", "").toString());
        
        // Connection pool settings
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(5);
        hikariConfig.setIdleTimeout(300000);
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setMaxLifetime(600000);
        
        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            if (debugEnabled) {
                logger.info("Successfully initialized database connection pool");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize database connection pool", e);
            throw new RuntimeException("Failed to initialize database connection", e);
        }
    }

    /**
     * Creates all required tables for the plugin.
     */
    public void createTable() {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = getConnection();
            stmt = conn.createStatement();

            // Create the whitelist table if it doesn't exist
            String whitelist = String.format(
                "CREATE TABLE IF NOT EXISTS `%s` ("
                + "`UUID` varchar(100) NOT NULL, "
                + "`user` varchar(100) NOT NULL, "
                + "`discord_id` BIGINT NULL, "
                + "`discord_name` VARCHAR(255) NULL, "
                + "`linked_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP, "
                + "`verification_state` ENUM('UNVERIFIED', 'PURGATORY', 'VERIFIED') DEFAULT 'UNVERIFIED', "
                + "`is_bedrock` BOOLEAN DEFAULT FALSE, "
                + "`linked_java_uuid` VARCHAR(36) NULL, "
                + "`geyser_prefix` VARCHAR(16) NULL, "
                + "PRIMARY KEY(`UUID`)"
                + ")", 
                tableName
            );
            stmt.execute(whitelist);
            
            // Create rank definitions table
            String rankDefinitions = 
                "CREATE TABLE IF NOT EXISTS `rank_definitions` ("
                + "`rank_id` INT PRIMARY KEY AUTO_INCREMENT, "
                + "`main_rank` INT NOT NULL, "
                + "`sub_rank` INT NOT NULL, "
                + "`rank_name` VARCHAR(64) NOT NULL, "
                + "`discord_role_id` BIGINT NOT NULL, "
                + "`required_time_minutes` INT DEFAULT 0, "
                + "`required_achievements` INT DEFAULT 0, "
                + "`description` TEXT, "
                + "UNIQUE KEY (`main_rank`, `sub_rank`)"
                + ")";
            stmt.execute(rankDefinitions);
                
            // Create player ranks table
            String playerRanks = 
                "CREATE TABLE IF NOT EXISTS `player_ranks` ("
                + "`player_uuid` VARCHAR(36) NOT NULL, "
                + "`current_main_rank` INT DEFAULT 10, "
                + "`current_sub_rank` INT DEFAULT 1, "
                + "`join_date` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "`play_time_minutes` INT DEFAULT 0, "
                + "`achievements_completed` INT DEFAULT 0, "
                + "`last_promotion` TIMESTAMP NULL, "
                + "FOREIGN KEY (`player_uuid`) REFERENCES " + tableName + "(`UUID`), "
                + "PRIMARY KEY (`player_uuid`)"
                + ")";
            stmt.execute(playerRanks);
                
            // Create rank history table
            String rankHistory = 
                "CREATE TABLE IF NOT EXISTS `rank_history` ("
                + "`id` INT PRIMARY KEY AUTO_INCREMENT, "
                + "`player_uuid` VARCHAR(36) NOT NULL, "
                + "`old_main_rank` INT, "
                + "`old_sub_rank` INT, "
                + "`new_main_rank` INT, "
                + "`new_sub_rank` INT, "
                + "`promotion_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "`reason` VARCHAR(255), "
                + "`promoter_uuid` VARCHAR(36) NULL, "
                + "FOREIGN KEY (`player_uuid`) REFERENCES " + tableName + "(`UUID`)"
                + ")";
            stmt.execute(rankHistory);
                
            // Create XP history table
            String xpHistory = 
                "CREATE TABLE IF NOT EXISTS `xp_history` ("
                + "`id` INT PRIMARY KEY AUTO_INCREMENT, "
                + "`player_uuid` VARCHAR(36) NOT NULL, "
                + "`xp_amount` INT NOT NULL, "
                + "`source` VARCHAR(64) NOT NULL, "
                + "`gained_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (`player_uuid`) REFERENCES " + tableName + "(`UUID`)"
                + ")";
            stmt.execute(xpHistory);
            
            // Create comprehensive XP events table for rate limiting and tracking
            String xpEvents = 
                "CREATE TABLE IF NOT EXISTS `xp_events` ("
                + "`id` INT PRIMARY KEY AUTO_INCREMENT, "
                + "`player_uuid` VARCHAR(36) NOT NULL, "
                + "`event_type` VARCHAR(32) NOT NULL, "
                + "`event_source` VARCHAR(128) NOT NULL, "
                + "`xp_gained` INT NOT NULL, "
                + "`timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "`server_name` VARCHAR(64) NULL, "
                + "`metadata` TEXT NULL, "
                + "FOREIGN KEY (`player_uuid`) REFERENCES " + tableName + "(`UUID`), "
                + "INDEX `idx_rate_limiting` (`player_uuid`, `event_type`, `event_source`, `timestamp`), "
                + "INDEX `idx_player_recent` (`player_uuid`, `timestamp`)"
                + ")";
            stmt.execute(xpEvents);
                
            // Create achievement history table
            String achievementHistory = 
                "CREATE TABLE IF NOT EXISTS `achievement_history` ("
                + "`id` INT PRIMARY KEY AUTO_INCREMENT, "
                + "`player_uuid` VARCHAR(36) NOT NULL, "
                + "`achievement_name` VARCHAR(128) NOT NULL, "
                + "`achieved_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (`player_uuid`) REFERENCES " + tableName + "(`UUID`)"
                + ")";
            stmt.execute(achievementHistory);
                
            debugLog("Database tables created or verified");
        } catch (SQLException e) {
            logger.error("Error creating database tables", e);
            throw new RuntimeException("Failed to create database tables", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                logger.error("Error closing database resources", e);
            }
        }
    }

    /**
     * Checks if a player is whitelisted and updates their username if necessary.
     *
     * @param username The player's username
     * @param uuid     The player's UUID
     * @return true if the player is whitelisted, false otherwise
     */
    public boolean isWhitelisted(String username, UUID uuid) {
        debugLog("Checking if " + username + " is whitelisted");
        
        // First check by UUID
        String checkUuidSql = String.format("SELECT `user` FROM `%s` WHERE `UUID` = ?", tableName);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkUuidSql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // Update username if it has changed
                    if (!rs.getString("user").equals(username)) {
                        updateUsername(conn, username, uuid);
                    }
                    return true;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking whitelist by UUID", e);
            throw new RuntimeException("Failed to check whitelist", e);
        }

        // If not found by UUID, check by username and update UUID if found
        String checkUsernameSql = String.format("SELECT `UUID` FROM `%s` WHERE `user` = ? AND `UUID` IS NULL", tableName);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkUsernameSql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    updateUuid(conn, username, uuid);
                    return true;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking whitelist by username", e);
            throw new RuntimeException("Failed to check whitelist", e);
        }

        return false;
    }

    /**
     * Adds a player to the whitelist.
     *
     * @param username The player's username
     * @param uuid     The player's UUID (can be null)
     * @return true if the player was added, false if they were already whitelisted
     */
    public boolean addToWhitelist(String username, UUID uuid) {
        debugLog("Adding " + username + " to whitelist");
        String checkSql = String.format("SELECT 1 FROM `%s` WHERE `user` = ?", tableName);
        String insertSql = String.format("INSERT INTO `%s` (`UUID`, `user`) VALUES (?, ?)", tableName);

        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, username);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, uuid != null ? uuid.toString() : null);
                        insertStmt.setString(2, username);
                        insertStmt.execute();
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error adding player to whitelist", e);
            throw new RuntimeException("Failed to add player to whitelist", e);
        }
        return false;
    }

    /**
     * Removes a player from the whitelist.
     *
     * @param username The player's username
     * @return true if the player was removed, false if they weren't whitelisted
     */
    public boolean removeFromWhitelist(String username) {
        debugLog("Removing " + username + " from whitelist");
        String sql = String.format("DELETE FROM `%s` WHERE `user` = ?", tableName);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error removing player from whitelist", e);
            throw new RuntimeException("Failed to remove player from whitelist", e);
        }
    }

    /**
     * Lists whitelisted players matching a search pattern.
     *
     * @param search The search pattern (can be null for all players)
     * @param limit  Maximum number of results to return
     * @return A ResultSet containing the matching players
     */
    public ResultSet listWhitelisted(String search, int limit) {
        String sql = String.format("SELECT `user` FROM `%s` WHERE `user` LIKE ? LIMIT ?", tableName);
        try {
            Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, search != null ? "%" + search + "%" : "%");
            stmt.setInt(2, limit);
            return stmt.executeQuery();
        } catch (SQLException e) {
            logger.error("Error listing whitelisted players", e);
            throw new RuntimeException("Failed to list whitelisted players", e);
        }
    }

    /**
     * Updates a player's username in the whitelist.
     *
     * @param conn     The database connection to use
     * @param username The player's new username
     * @param uuid     The player's UUID
     */
    private void updateUsername(Connection conn, String username, UUID uuid) throws SQLException {
        debugLog("Updating username for " + uuid + " to " + username);
        String sql = String.format("UPDATE `%s` SET `user` = ? WHERE `UUID` = ?", tableName);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        }
    }

    /**
     * Updates a player's UUID in the whitelist.
     *
     * @param conn     The database connection to use
     * @param username The player's username
     * @param uuid     The player's UUID
     */
    private void updateUuid(Connection conn, String username, UUID uuid) throws SQLException {
        debugLog("Updating UUID for " + username + " to " + uuid);
        String sql = String.format("UPDATE `%s` SET `UUID` = ? WHERE `user` = ?", tableName);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
    }

    /**
     * Gets a connection from the connection pool.
     *
     * @return A database connection from the pool
     * @throws SQLException if a connection cannot be obtained
     */
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Logs a debug message if debug mode is enabled.
     *
     * @param message The message to log
     */
    private void debugLog(String message) {
        if (debugEnabled) {
            logger.info("[DEBUG] SQLHandler: " + message);
        }
    }

    /**
     * Closes the connection pool when the handler is closed.
     */
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            debugLog("Closing connection pool");
            dataSource.close();
        }
    }

    /**
     * Checks if a player is whitelisted using their Player object.
     *
     * @param player The Player object
     * @return CompletableFuture that resolves to true if whitelisted, false otherwise
     */
    public CompletableFuture<Boolean> isPlayerWhitelisted(Player player) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            String username = player.getUsername();
            UUID uuid = player.getUniqueId();

            // Check if player is bedrock player by looking for GeyserMC prefix
            String originalUsername = username;
            boolean isBedrock = false;
            String geyserPrefix = null;
            
            // Check common GeyserMC prefixes like ".", "*", etc.
            String[] possiblePrefixes = {".","*","+","-","~","#"};
            for (String prefix : possiblePrefixes) {
                if (username.startsWith(prefix) && username.length() > 1) {
                    geyserPrefix = prefix;
                    username = username.substring(1); // Remove the prefix
                    isBedrock = true;
                    debugLog("Detected Bedrock player: " + originalUsername + " (prefix: " + geyserPrefix + ")");
                    break;
                }
            }
            
            // If player is a Bedrock player, check whitelist by username without prefix
            if (isBedrock) {
                checkBedrockWhitelist(originalUsername, username, uuid.toString())
                    .whenComplete((isWhitelisted, error) -> {
                        if (error != null) {
                            logger.error("Error checking Bedrock whitelist", error);
                            future.complete(false);
                        } else {
                            future.complete(isWhitelisted);
                        }
                    });
                return future;
            }
            
            // For Java players, check as normal
            boolean isWhitelisted = isWhitelisted(username, uuid);
            future.complete(isWhitelisted);
        } catch (Exception e) {
            logger.error("Error checking whitelist", e);
            future.complete(false);
        }
        return future;
    }

    /**
     * Checks if a Bedrock player is whitelisted
     * 
     * @param originalUsername The original username with prefix
     * @param username The username without prefix
     * @param uuid The player's UUID
     * @return CompletableFuture that resolves to true if whitelisted, false otherwise
     */
    private CompletableFuture<Boolean> checkBedrockWhitelist(String originalUsername, String username, String uuid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try (Connection conn = getConnection()) {
            // First try to find the player by UUID
            String checkSql = String.format("SELECT `user`, `is_bedrock` FROM `%s` WHERE `UUID` = ?", tableName);
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setString(1, uuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Update username if it has changed
                        String storedUsername = rs.getString("user");
                        boolean isBedrock = rs.getBoolean("is_bedrock");
                        
                        if (!storedUsername.equals(originalUsername)) {
                            updateUsername(conn, originalUsername, UUID.fromString(uuid));
                            debugLog("Updated Bedrock username from " + storedUsername + " to " + originalUsername);
                        }
                        
                        // Update Bedrock flag if not already set
                        if (!isBedrock) {
                            updateBedrockStatus(conn, uuid, true, null);
                            debugLog("Updated Bedrock status for player: " + originalUsername);
                        }
                        
                        future.complete(true);
                        return future;
                    }
                }
            }
            
            // Then try to find by username without prefix
            String checkByUsernameSql = String.format("SELECT `UUID`, `is_bedrock` FROM `%s` WHERE `user` LIKE ? OR `user` = ?", tableName);
            try (PreparedStatement stmt = conn.prepareStatement(checkByUsernameSql)) {
                stmt.setString(1, "%" + username);
                stmt.setString(2, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedUuid = rs.getString("UUID");
                        boolean isBedrock = rs.getBoolean("is_bedrock");
                        
                        // Update UUID if this is a returning Bedrock player
                        if (!storedUuid.equals(uuid)) {
                            updateBedrockPlayerUuid(conn, storedUuid, uuid, originalUsername);
                            debugLog("Updated UUID for returning Bedrock player: " + originalUsername);
                        }
                        
                        // Update Bedrock flag if not already set
                        if (!isBedrock) {
                            updateBedrockStatus(conn, uuid, true, null);
                            debugLog("Updated Bedrock status for player: " + originalUsername);
                        }
                        
                        future.complete(true);
                        return future;
                    }
                }
            }
            
            // Not found in whitelist
            future.complete(false);
        } catch (SQLException e) {
            logger.error("Error checking Bedrock whitelist", e);
            future.complete(false);
        }
        
        return future;
    }

    /**
     * Updates a Bedrock player's UUID and username
     * 
     * @param conn The database connection
     * @param oldUuid The old UUID
     * @param newUuid The new UUID
     * @param username The player's username
     * @throws SQLException If an error occurs during the update
     */
    private void updateBedrockPlayerUuid(Connection conn, String oldUuid, String newUuid, String username) throws SQLException {
        String sql = String.format("UPDATE `%s` SET `UUID` = ?, `user` = ?, `is_bedrock` = TRUE WHERE `UUID` = ?", tableName);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newUuid);
            stmt.setString(2, username);
            stmt.setString(3, oldUuid);
            stmt.executeUpdate();
        }
    }

    /**
     * Updates a player's Bedrock status
     * 
     * @param conn The database connection
     * @param uuid The player's UUID
     * @param isBedrock Whether the player is a Bedrock player
     * @param geyserPrefix The GeyserMC prefix used by the player
     * @throws SQLException If an error occurs during the update
     */
    private void updateBedrockStatus(Connection conn, String uuid, boolean isBedrock, String geyserPrefix) throws SQLException {
        String sql = String.format("UPDATE `%s` SET `is_bedrock` = ?, `geyser_prefix` = ? WHERE `UUID` = ?", tableName);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, isBedrock);
            stmt.setString(2, geyserPrefix);
            stmt.setString(3, uuid);
            stmt.executeUpdate();
        }
    }

    /**
     * Gets a player's verification state
     * 
     * @param playerUuid The player's UUID
     * @return Optional containing the verification state or empty if not found
     */
    public Optional<String> getPlayerVerificationState(String playerUuid) {
        String sql = String.format("SELECT `verification_state` FROM `%s` WHERE `UUID` = ?", tableName);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("verification_state"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player verification state", e);
        }
        return Optional.empty();
    }

    /**
     * Updates a player's verification state
     * 
     * @param playerUuid The player's UUID
     * @param state The verification state to set
     * @return true if the update was successful, false otherwise
     */
    public boolean updateVerificationState(String playerUuid, String state) {
        String sql = String.format("UPDATE `%s` SET `verification_state` = ? WHERE `UUID` = ?", tableName);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, state);
            stmt.setString(2, playerUuid);
            int updated = stmt.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            logger.error("Error updating verification state", e);
            return false;
        }
    }

    /**
     * Links a Discord account to a Minecraft account
     * 
     * @param playerUuid The Minecraft player's UUID
     * @param discordId The Discord user ID
     * @param discordName The Discord username
     * @return true if the linking was successful, false otherwise
     */
    public boolean linkDiscordAccount(String playerUuid, long discordId, String discordName) {
        String sql = String.format(
            "UPDATE `%s` SET `discord_id` = ?, `discord_name` = ?, `linked_at` = CURRENT_TIMESTAMP WHERE `UUID` = ?",
            tableName
        );
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, discordId);
            stmt.setString(2, discordName);
            stmt.setString(3, playerUuid);
            int updated = stmt.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            logger.error("Error linking Discord account", e);
            return false;
        }
    }

    /**
     * Gets a player's Discord ID
     * 
     * @param playerUuid The Minecraft player's UUID
     * @return Optional containing the Discord ID or empty if not found
     */
    public Optional<Long> getPlayerDiscordId(String playerUuid) {
        String sql = String.format("SELECT `discord_id` FROM `%s` WHERE `UUID` = ?", tableName);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long discordId = rs.getLong("discord_id");
                    return rs.wasNull() ? Optional.empty() : Optional.of(discordId);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player Discord ID", e);
        }
        return Optional.empty();
    }

    /**
     * Gets a player's UUID by their Discord ID
     * 
     * @param discordId The Discord user ID
     * @return Optional containing the player UUID or empty if not found
     */
    public Optional<String> getPlayerUuidByDiscordId(long discordId) {
        String sql = String.format("SELECT `UUID` FROM `%s` WHERE `discord_id` = ?", tableName);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, discordId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("UUID"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player UUID by Discord ID", e);
        }
        return Optional.empty();
    }

    /**
     * Gets a player's UUID by their username
     * 
     * @param username The player's username
     * @return Optional containing the player's UUID or empty if not found
     */
    public Optional<String> getPlayerUuidByUsername(String username) {
        String sql = String.format("SELECT `UUID` FROM `%s` WHERE `user` = ?", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String uuid = rs.getString("UUID");
                    return Optional.ofNullable(uuid);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player UUID by username", e);
        }
        
        return Optional.empty();
    }

    /**
     * Gets a player's rank information from the database.
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture<Optional<PlayerRank>> containing the player's rank if found
     */
    public CompletableFuture<Optional<PlayerRank>> getPlayerRank(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_ranks WHERE player_uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new PlayerRank(
                            playerUuid,
                            rs.getInt("current_main_rank"),
                            rs.getInt("current_sub_rank"),
                            rs.getTimestamp("join_date").toInstant(),
                            rs.getInt("play_time_minutes"),
                            rs.getInt("achievements_completed"),
                            rs.getTimestamp("last_promotion") != null ? 
                                rs.getTimestamp("last_promotion").toInstant() : null
                        ));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                logger.error("Error getting player rank for " + playerUuid, e);
                throw new RuntimeException("Failed to get player rank", e);
            }
        });
    }

    /**
         * Saves a player's rank information to the database
         *
         * @param playerRank The player rank to save
         * @return CompletableFuture that resolves to true if the save was successful, false otherwise
         */
        public CompletableFuture<Boolean> savePlayerRank(PlayerRank playerRank) {
            /**
             * savePlayerRank
             * Saves or updates a player's rank information in the database.
             * @param playerRank The PlayerRank object containing rank data to save.
             * @return CompletableFuture<Boolean> that resolves to true if the save was successful, false otherwise.
             */
            return CompletableFuture.supplyAsync(() -> {
                String checkSql = "SELECT 1 FROM `player_ranks` WHERE `player_uuid` = ?";
                String insertSql = "INSERT INTO `player_ranks` (`player_uuid`, `current_main_rank`, `current_sub_rank`, " +
                        "`join_date`, `play_time_minutes`, `achievements_completed`, `last_promotion`) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";
                String updateSql = "UPDATE `player_ranks` SET `current_main_rank` = ?, `current_sub_rank` = ?, " +
                        "`play_time_minutes` = ?, `achievements_completed` = ?, `last_promotion` = ? " +
                        "WHERE `player_uuid` = ?";
                try (Connection conn = getConnection()) {
                    boolean exists = false;
                    try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                        checkStmt.setString(1, playerRank.getPlayerUuid());
                        try (ResultSet rs = checkStmt.executeQuery()) {
                            exists = rs.next();
                        }
                    }
                    if (exists) {
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setInt(1, playerRank.getMainRank());
                            updateStmt.setInt(2, playerRank.getSubRank());
                            updateStmt.setInt(3, playerRank.getPlayTimeMinutes());
                            updateStmt.setInt(4, playerRank.getAchievementsCompleted());
                            if (playerRank.getLastPromotion() != null) {
                                updateStmt.setTimestamp(5, Timestamp.from(playerRank.getLastPromotion()));
                            } else {
                                updateStmt.setTimestamp(5, null);
                            }
                            updateStmt.setString(6, playerRank.getPlayerUuid());
                            int updated = updateStmt.executeUpdate();
                            return updated > 0;
                        }
                    } else {
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                            insertStmt.setString(1, playerRank.getPlayerUuid());
                            insertStmt.setInt(2, playerRank.getMainRank());
                            insertStmt.setInt(3, playerRank.getSubRank());
                            insertStmt.setTimestamp(4, Timestamp.from(playerRank.getJoinDate()));
                            insertStmt.setInt(5, playerRank.getPlayTimeMinutes());
                            insertStmt.setInt(6, playerRank.getAchievementsCompleted());
                            if (playerRank.getLastPromotion() != null) {
                                insertStmt.setTimestamp(7, Timestamp.from(playerRank.getLastPromotion()));
                            } else {
                                insertStmt.setTimestamp(7, null);
                            }
                            int inserted = insertStmt.executeUpdate();
                            return inserted > 0;
                        }
                    }
                } catch (SQLException e) {
                    logger.error("Error saving player rank for " + playerRank.getPlayerUuid(), e);
                    return false;
                }
            });
        }
        
    /**
     * Logs a rank promotion
     * 
     * @param playerUuid The player's UUID
     * @param oldMainRank The old main rank
     * @param oldSubRank The old sub-rank
     * @param newMainRank The new main rank
     * @param newSubRank The new sub-rank
     * @param reason The reason for the promotion
     * @return true if the log was successful, false otherwise
     * @throws SQLException If an error occurs during the database operation
     */
    public boolean logRankPromotion(String playerUuid, int oldMainRank, int oldSubRank, 
                                  int newMainRank, int newSubRank, String reason) throws SQLException {
        String sql = "INSERT INTO `rank_history` (`player_uuid`, `old_main_rank`, `old_sub_rank`, " +
                    "`new_main_rank`, `new_sub_rank`, `reason`, `promoter_uuid`) " +
                    "VALUES (?, ?, ?, ?, ?, ?, NULL)";
                    
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setInt(2, oldMainRank);
            stmt.setInt(3, oldSubRank);
            stmt.setInt(4, newMainRank);
            stmt.setInt(5, newSubRank);
            stmt.setString(6, reason);
            
            int inserted = stmt.executeUpdate();
            return inserted > 0;
        }
    }
    
    /**
     * Logs XP gain for a player
     * 
     * @param playerUuid The player's UUID
     * @param xpAmount The amount of XP gained
     * @param source The source of the XP (chat, voice, achievement, etc)
     * @return true if the log was successful, false otherwise
     * @throws SQLException If an error occurs during the database operation
     */
    public boolean logXpGain(String playerUuid, int xpAmount, String source) throws SQLException {
        String sql = "INSERT INTO `xp_history` (`player_uuid`, `xp_amount`, `source`) VALUES (?, ?, ?)";
                    
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setInt(2, xpAmount);
            stmt.setString(3, source);
            
            int inserted = stmt.executeUpdate();
            return inserted > 0;
        }
    }
    
    /**
     * Logs an achievement for a player
     * 
     * @param playerUuid The player's UUID
     * @param achievementName The name of the achievement
     * @return true if the log was successful, false otherwise
     * @throws SQLException If an error occurs during the database operation
     */
    public boolean logAchievement(String playerUuid, String achievementName) throws SQLException {
        String sql = "INSERT INTO `achievement_history` (`player_uuid`, `achievement_name`) VALUES (?, ?)";
                    
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setString(2, achievementName);
            
            int inserted = stmt.executeUpdate();
            return inserted > 0;
        }
    }
    
    /**
     * Gets all rank definitions from the database
     * 
     * @return A list of all rank definitions
     * @throws SQLException If an error occurs during the database operation
     */
    public List<RankDefinition> getAllRankDefinitions() throws SQLException {
        String sql = "SELECT * FROM `rank_definitions` ORDER BY `main_rank`, `sub_rank`";
        List<RankDefinition> definitions = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                RankDefinition def = new RankDefinition(
                    rs.getInt("rank_id"),
                    rs.getInt("main_rank"),
                    rs.getInt("sub_rank"),
                    rs.getString("rank_name"),
                    rs.getLong("discord_role_id"),
                    rs.getInt("required_time_minutes"),
                    rs.getInt("required_achievements"),
                    rs.getString("description")
                );
                definitions.add(def);
            }
        }
        
        return definitions;
    }
    
    /**
     * Saves a rank definition to the database
     * 
     * @param rankDef The rank definition to save
     * @return The saved rank definition with updated ID if it was a new definition
     * @throws SQLException If an error occurs during the database operation
     */
    public RankDefinition saveRankDefinition(RankDefinition rankDef) throws SQLException {
        String checkSql = "SELECT `rank_id` FROM `rank_definitions` WHERE `main_rank` = ? AND `sub_rank` = ?";
        String insertSql = "INSERT INTO `rank_definitions` (`main_rank`, `sub_rank`, `rank_name`, `discord_role_id`, " +
                          "`required_time_minutes`, `required_achievements`, `description`) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?)";
        String updateSql = "UPDATE `rank_definitions` SET `rank_name` = ?, `discord_role_id` = ?, " +
                          "`required_time_minutes` = ?, `required_achievements` = ?, `description` = ? " +
                          "WHERE `rank_id` = ?";
                          
        try (Connection conn = getConnection()) {
            // Check if the rank definition already exists
            int existingId = -1;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, rankDef.getMainRank());
                checkStmt.setInt(2, rankDef.getSubRank());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getInt("rank_id");
                    }
                }
            }
            
            if (existingId > 0) {
                // Update existing definition
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setString(1, rankDef.getRankName());
                    stmt.setLong(2, rankDef.getDiscordRoleId());
                    stmt.setInt(3, rankDef.getRequiredTimeMinutes());
                    stmt.setInt(4, rankDef.getRequiredAchievements());
                    stmt.setString(5, rankDef.getDescription());
                    stmt.setInt(6, existingId);
                    
                    stmt.executeUpdate();
                    rankDef.setRankId(existingId);
                }
            } else {
                // Insert new definition
                try (PreparedStatement stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, rankDef.getMainRank());
                    stmt.setInt(2, rankDef.getSubRank());
                    stmt.setString(3, rankDef.getRankName());
                    stmt.setLong(4, rankDef.getDiscordRoleId());
                    stmt.setInt(5, rankDef.getRequiredTimeMinutes());
                    stmt.setInt(6, rankDef.getRequiredAchievements());
                    stmt.setString(7, rankDef.getDescription());
                    
                    stmt.executeUpdate();
                    
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            rankDef.setRankId(generatedKeys.getInt(1));
                        }
                    }
                }
            }
        }
        
        return rankDef;
    }

    /**
     * Adds a player to the whitelist asynchronously.
     *
     * @param player The player's username
     * @param uuid The player's UUID (can be null)
     * @return CompletableFuture that completes when the operation is done
     */
    public CompletableFuture<Void> addPlayerToWhitelist(String player, UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                boolean success = addToWhitelist(player, uuid);
                if (!success) {
                    logger.debug("[Whitelist] Player {} is already whitelisted", player);
                }
            } catch (SQLException e) {
                logger.error("[Whitelist] Error adding player to whitelist", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Removes a player from the whitelist asynchronously.
     *
     * @param player The player's username
     * @return CompletableFuture that completes when the operation is done
     */
    public CompletableFuture<Void> removePlayerFromWhitelist(String player) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                boolean success = removeFromWhitelist(player);
                if (!success) {
                    logger.debug("[Whitelist] Player {} was not on the whitelist", player);
                }
            } catch (SQLException e) {
                logger.error("[Whitelist] Error removing player from whitelist", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Lists whitelisted players matching a search pattern asynchronously.
     *
     * @param search The search pattern (can be null for all players)
     * @return CompletableFuture that resolves to a List of player names
     */
    public CompletableFuture<List<String>> listWhitelistedPlayers(String search) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> players = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                ResultSet rs = listWhitelisted(search, 100)) {
                while (rs.next()) {
                    players.add(rs.getString("username"));
                }
                return players;
            } catch (SQLException e) {
                logger.error("[Whitelist] Error listing whitelisted players", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Gets a player's Minecraft username from their UUID
     *
     * @param playerUuid The UUID of the player
     * @return Optional containing the player name if found, otherwise empty
     */
    /**
     * Gets a player's Minecraft username from their UUID
     *
     * @param playerUuid The UUID of the player
     * @return Optional containing the player name if found, otherwise empty
     */
    public Optional<String> getPlayerNameFromUuid(String playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                 "SELECT minecraft_name FROM " + tableName + " WHERE minecraft_uuid = ?")) {
            
            ps.setString(1, playerUuid);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("minecraft_name");
                    return Optional.ofNullable(name);
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving player name from UUID: " + playerUuid, e);
        }
        
        return Optional.empty();
    }

    /**
     * Logs reward processing for a player
     *
     * @param playerUuid The UUID of the player
     * @param mainRank The main rank ID
     * @param subRank The sub rank ID
     * @param economyAmount The amount of economy reward given
     * @param commandsExecuted The number of commands executed
     * @return CompletableFuture that resolves to true if log was successful
     */
    public CompletableFuture<Boolean> logRewardProcessing(String playerUuid, int mainRank, int subRank, 
                                                        int economyAmount, int commandsExecuted) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                 "INSERT INTO whitelist_reward_logs (player_uuid, main_rank, sub_rank, " +
                 "economy_amount, commands_executed, timestamp) VALUES (?, ?, ?, ?, ?, ?)")) {
            
            ps.setString(1, playerUuid);
            ps.setInt(2, mainRank);
            ps.setInt(3, subRank);
            ps.setInt(4, economyAmount);
            ps.setInt(5, commandsExecuted);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            
            int updated = ps.executeUpdate();
            future.complete(updated > 0);
        } catch (SQLException e) {
            logger.error("Error logging reward processing for player: " + playerUuid, e);
            future.complete(false);
        }
        
        return future;
    }
    
    /**
     * Saves an XP event to the database
     * 
     * @param xpEvent The XP event to save
     * @throws SQLException If a database error occurs
     */
    public void saveXPEvent(top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent xpEvent) throws SQLException {
        String sql = "INSERT INTO `xp_events` (`player_uuid`, `event_type`, `event_source`, `xp_gained`, `timestamp`, `server_name`, `metadata`) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, xpEvent.getPlayerUuid());
            ps.setString(2, xpEvent.getEventType());
            ps.setString(3, xpEvent.getEventSource());
            ps.setInt(4, xpEvent.getXpGained());
            ps.setTimestamp(5, Timestamp.from(xpEvent.getTimestamp()));
            ps.setString(6, xpEvent.getServerName());
            ps.setString(7, xpEvent.getMetadata());
            
            ps.executeUpdate();
            debugLog("XP event saved to database: " + xpEvent.toString());
        }
    }
    
    /**
     * Gets the count of XP events for a player within a time range
     * 
     * @param playerUuid The UUID of the player
     * @param eventType The type of event to count
     * @param eventSource The specific source to count
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return The count of matching XP events
     * @throws SQLException If a database error occurs
     */
    public int getXPEventCount(String playerUuid, String eventType, String eventSource, 
                              Instant startTime, Instant endTime) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `xp_events` WHERE `player_uuid` = ? AND `event_type` = ? AND `event_source` = ? AND `timestamp` BETWEEN ? AND ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, playerUuid);
            ps.setString(2, eventType);
            ps.setString(3, eventSource);
            ps.setTimestamp(4, Timestamp.from(startTime));
            ps.setTimestamp(5, Timestamp.from(endTime));
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }
    
    /**
     * Gets the total XP for a player
     * 
     * @param playerUuid The UUID of the player
     * @return The total XP gained by the player
     * @throws SQLException If a database error occurs
     */
    public int getPlayerTotalXP(String playerUuid) throws SQLException {
        String sql = "SELECT COALESCE(SUM(`xp_gained`), 0) FROM `xp_events` WHERE `player_uuid` = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, playerUuid);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }
    
    /**
     * Gets recent XP events for a player
     * 
     * @param playerUuid The UUID of the player
     * @param limit The maximum number of events to return
     * @return A list of recent XP events
     * @throws SQLException If a database error occurs
     */
    public List<top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent> getRecentXPEvents(String playerUuid, int limit) throws SQLException {
        String sql = "SELECT * FROM `xp_events` WHERE `player_uuid` = ? ORDER BY `timestamp` DESC LIMIT ?";
        List<top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent> events = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, playerUuid);
            ps.setInt(2, limit);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent event = new top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent(
                        rs.getString("player_uuid"),
                        rs.getString("event_type"),
                        rs.getString("event_source"),
                        rs.getInt("xp_gained"),
                        rs.getTimestamp("timestamp").toInstant(),
                        rs.getString("server_name"),
                        rs.getString("metadata")
                    );
                    events.add(event);
                }
            }
        }
        
        return events;
    }
    
    /**
     * Gets a player's platform (Java or Bedrock)
     * 
     * @param playerUuid The player's UUID
     * @return Optional containing a boolean indicating if the player is on Bedrock (true) or Java (false), or empty if not found
     */
    public Optional<Boolean> isPlayerBedrock(String playerUuid) {
        String sql = String.format("SELECT `is_bedrock` FROM `%s` WHERE `UUID` = ?", tableName);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getBoolean("is_bedrock"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player platform information", e);
        }
        return Optional.empty();
    }
}
