package top.jk33v3rs.velocitydiscordwhitelist.database;

import com.velocitypowered.api.proxy.Player;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import top.jk33v3rs.velocitydiscordwhitelist.models.PlayerInfo;
import top.jk33v3rs.velocitydiscordwhitelist.models.PlayerRank;
import top.jk33v3rs.velocitydiscordwhitelist.models.RankDefinition;

/**
 * SQLHandler manages database connections and whitelist operations using HikariCP.
 */
public class SQLHandler implements AutoCloseable {
    private final Logger logger;
    private final HikariDataSource dataSource;
    private final String tableName;
    private final boolean debugEnabled;

    /**
     * Initializes the database connection pool and creates necessary tables
     * 
     * @param config The configuration map containing database settings
     * @param logger The logger instance for this class
     * @param debugEnabled Whether debug logging is enabled
     * @throws RuntimeException if database initialization fails
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
                + "`rank_id` INT DEFAULT 0, "
                + "`verified_at` TIMESTAMP NULL, "
                + "`linked_java_uuid` VARCHAR(36) NULL, "
                + "`geyser_prefix` VARCHAR(16) NULL, "
                + "PRIMARY KEY(`UUID`)"
                + ")", 
                tableName
            );
            stmt.execute(whitelist);
            
            // Create user_data table
            String userData = """
                CREATE TABLE IF NOT EXISTS user_data (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL UNIQUE,
                    username VARCHAR(16) NOT NULL,
                    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    playtime_minutes INT DEFAULT 0,
                    xp_points INT DEFAULT 0,
                    achievements_completed INT DEFAULT 0,
                    last_promotion TIMESTAMP NULL,
                    progression_data TEXT NULL,
                    INDEX idx_uuid (uuid),
                    INDEX idx_username (username)
                )
            """;
            stmt.execute(userData);
            
            // Create rank_data table
            String rankData = """
                CREATE TABLE IF NOT EXISTS rank_data (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    main_rank INT NOT NULL DEFAULT 1,
                    sub_rank INT NOT NULL DEFAULT 1,
                    rank_assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    progression_data TEXT,
                    playtime_minutes INT DEFAULT 0,
                    achievements_completed INT DEFAULT 0,
                    last_promotion TIMESTAMP NULL,
                    INDEX idx_uuid (uuid),
                    INDEX idx_rank (main_rank, sub_rank)
                )
            """;
            stmt.execute(rankData);
            
            // Create discord_data table
            String discordData = """
                CREATE TABLE IF NOT EXISTS discord_data (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    discord_id VARCHAR(20) NOT NULL,
                    discord_username VARCHAR(32),
                    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    verification_code VARCHAR(10),
                    INDEX idx_uuid (uuid),
                    INDEX idx_discord_id (discord_id)
                )
            """;
            stmt.execute(discordData);
            
            // Create verification_data table
            String verificationData = """
                CREATE TABLE IF NOT EXISTS verification_data (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    verification_type VARCHAR(20) NOT NULL,
                    verified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    verification_method VARCHAR(20),
                    metadata TEXT,
                    INDEX idx_uuid (uuid),
                    INDEX idx_type (verification_type)
                )
            """;
            stmt.execute(verificationData);
            
            // Create xp_events table for tracking XP history
            String xpEvents = """
                CREATE TABLE IF NOT EXISTS xp_events (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    event_type VARCHAR(50) NOT NULL,
                    event_source VARCHAR(100),
                    xp_gained INT NOT NULL,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    server_name VARCHAR(50),
                    metadata TEXT,
                    INDEX idx_uuid (uuid),
                    INDEX idx_timestamp (timestamp),
                    INDEX idx_event_type (event_type)
                )
            """;
            stmt.execute(xpEvents);
            
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
        String checkSql = String.format("SELECT COUNT(*) FROM `%s` WHERE `user` = ?", tableName);
        String insertSql = String.format("INSERT INTO `%s` (`UUID`, `user`) VALUES (?, ?)", tableName);

        try (Connection conn = getConnection()) {
            // Check if player already exists
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, username);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        debugLog("Player " + username + " is already whitelisted");
                        return false;
                    }
                    
                    // Add player to whitelist
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, uuid != null ? uuid.toString() : null);
                        insertStmt.setString(2, username);
                        insertStmt.execute();
                        return true;
                    }
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            logger.error("Error adding player to whitelist", e);
            throw new RuntimeException("Failed to add player to whitelist", e);
        }
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
        } catch (SQLException | IllegalArgumentException e) {
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
        } catch (SQLException | IllegalArgumentException e) {
            logger.error("Error listing whitelisted players", e);
            throw new RuntimeException("Failed to list whitelisted players", e);
        }
    }

    /**
     * Lists whitelisted players asynchronously
     * 
     * @param search The search pattern (can be null for all players)
     * @return CompletableFuture containing list of usernames
     */
    public CompletableFuture<java.util.List<String>> listWhitelistedPlayers(String search) {
        return CompletableFuture.supplyAsync(() -> {
            java.util.List<String> players = new ArrayList<>();
            try (ResultSet rs = listWhitelisted(search, 100)) {
                while (rs.next()) {
                    players.add(rs.getString("user"));
                }
            } catch (SQLException e) {
                logger.error("Failed to list whitelisted players", e);
                throw new RuntimeException("Failed to list whitelisted players", e);
            }
            return players;
        });
    }

    /**
     * Checks if a player is whitelisted
     * 
     * @param player The player to check
     * @return CompletableFuture<Boolean> true if player is whitelisted, false otherwise
     */
    public CompletableFuture<Boolean> isPlayerWhitelisted(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            return isWhitelisted(player.getUsername(), player.getUniqueId());
        });
    }

    /**
     * Updates a player's username in the whitelist.
     *
     * @param conn     The database connection to use
     * @param username The player's new username
     * @param uuid     The player's UUID
     */
    private void updateUsername(Connection conn, String username, UUID uuid) throws SQLException {
        String sql = String.format("UPDATE `%s` SET `user` = ? WHERE `UUID` = ?", tableName);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            debugLog("Updated username for UUID " + uuid + " to " + username);
        } catch (SQLException | IllegalArgumentException e) {
            logger.error("Error updating username for UUID " + uuid, e);
            throw e;
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
        String sql = String.format("UPDATE `%s` SET `UUID` = ? WHERE `user` = ?", tableName);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.executeUpdate();
            debugLog("Updated UUID for " + username + " to " + uuid);
        } catch (SQLException | IllegalArgumentException e) {
            logger.error("Error updating UUID for username " + username, e);
            throw e;
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
            dataSource.close();
            debugLog("Database connection pool closed");
        }
    }

    /**
     * Adds a player to the whitelist with Discord information
     * 
     * @param uuid The player's UUID
     * @param name The player's username
     * @param discordId The Discord ID (can be null)
     * @param discordName The Discord username (can be null)
     * @return CompletableFuture<Boolean> true if successful
     */
    public CompletableFuture<Boolean> addPlayerToWhitelist(UUID uuid, String name, String discordId, String discordName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format(
                "INSERT INTO `%s` (`UUID`, `user`, `discord_id`, `discord_name`) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE `user` = VALUES(`user`), `discord_id` = VALUES(`discord_id`), `discord_name` = VALUES(`discord_name`)",
                tableName
            );
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.setString(3, discordId);
                stmt.setString(4, discordName);
                
                int result = stmt.executeUpdate();
                debugLog("Added player " + name + " to whitelist with Discord info");
                return result > 0;
            } catch (SQLException e) {
                logger.error("Error adding player to whitelist with Discord info", e);
                return false;
            }
        });
    }

    /**
     * Removes a player from the whitelist by UUID
     * 
     * @param playerUuid The player's UUID
     * @return CompletableFuture<Boolean> true if successful
     */
    public CompletableFuture<Boolean> removePlayerFromWhitelist(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format("DELETE FROM `%s` WHERE `UUID` = ?", tableName);
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                
                int result = stmt.executeUpdate();
                debugLog("Removed player " + playerUuid + " from whitelist");
                return result > 0;
            } catch (SQLException e) {
                logger.error("Error removing player from whitelist", e);
                return false;
            }
        });
    }

    /**
     * Gets all whitelisted players
     * 
     * @return CompletableFuture<List<PlayerInfo>> list of whitelisted players
     */
    public CompletableFuture<List<PlayerInfo>> getWhitelistedPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerInfo> players = new ArrayList<>();
            String sql = String.format("SELECT `UUID`, `user`, `discord_id`, `discord_name`, `verification_state` FROM `%s`", tableName);
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    PlayerInfo info = new PlayerInfo(
                        rs.getString("UUID"),
                        rs.getString("user"),
                        rs.getString("discord_id"),
                        rs.getString("discord_name"),
                        rs.getString("verification_state")
                    );
                    players.add(info);
                }
            } catch (SQLException e) {
                logger.error("Error getting whitelisted players", e);
                throw new RuntimeException("Failed to get whitelisted players", e);
            }
            
            return players;
        });
    }

    /**
     * Gets player information by UUID
     * 
     * @param playerUuid The player's UUID
     * @return CompletableFuture<Optional<PlayerInfo>> player information if found
     */
    public CompletableFuture<Optional<PlayerInfo>> getPlayerInfo(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format("SELECT `UUID`, `user`, `discord_id`, `discord_name`, `verification_state` FROM `%s` WHERE `UUID` = ?", tableName);
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        PlayerInfo info = new PlayerInfo(
                            rs.getString("UUID"),
                            rs.getString("user"),
                            rs.getString("discord_id"),
                            rs.getString("discord_name"),
                            rs.getString("verification_state")
                        );
                        return Optional.of(info);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting player info", e);
            }
            
            return Optional.empty();
        });
    }

    /**
     * Gets player rank information
     * 
     * @param playerUuid The player's UUID as string
     * @return CompletableFuture<Optional<PlayerRank>> player rank if found
     */
    public CompletableFuture<Optional<PlayerRank>> getPlayerRank(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT r.uuid, r.main_rank, r.sub_rank, r.playtime_minutes, r.achievements_completed, 
                       r.last_promotion, u.first_join, w.verified_at
                FROM rank_data r
                LEFT JOIN user_data u ON r.uuid = u.uuid
                LEFT JOIN %s w ON r.uuid = w.UUID
                WHERE r.uuid = ?
            """.formatted(tableName);
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        PlayerRank rank = new PlayerRank(
                            rs.getString("uuid"),
                            rs.getInt("main_rank"),
                            rs.getInt("sub_rank"),
                            rs.getTimestamp("first_join") != null ? rs.getTimestamp("first_join").toInstant() : Instant.now(),
                            rs.getInt("playtime_minutes"),
                            rs.getInt("achievements_completed"),
                            rs.getTimestamp("last_promotion") != null ? rs.getTimestamp("last_promotion").toInstant() : null
                        );
                        
                        Timestamp verifiedAt = rs.getTimestamp("verified_at");
                        if (verifiedAt != null) {
                            rank.setVerifiedAt(verifiedAt.toInstant());
                        }
                        
                        return Optional.of(rank);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting player rank", e);
            }
            
            return Optional.empty();
        });
    }

    /**
     * Saves player rank information
     * 
     * @param playerRank The player rank to save
     * @return CompletableFuture<Boolean> true if successful
     */
    public CompletableFuture<Boolean> savePlayerRank(PlayerRank playerRank) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO rank_data (uuid, main_rank, sub_rank, playtime_minutes, achievements_completed, last_promotion)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE 
                    main_rank = VALUES(main_rank),
                    sub_rank = VALUES(sub_rank),
                    playtime_minutes = VALUES(playtime_minutes),
                    achievements_completed = VALUES(achievements_completed),
                    last_promotion = VALUES(last_promotion)
            """;
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerRank.getPlayerUuid());
                stmt.setInt(2, playerRank.getMainRank());
                stmt.setInt(3, playerRank.getSubRank());
                stmt.setInt(4, playerRank.getPlayTimeMinutes());
                stmt.setInt(5, playerRank.getAchievementsCompleted());
                stmt.setTimestamp(6, playerRank.getLastPromotion() != null ? 
                    Timestamp.from(playerRank.getLastPromotion()) : null);
                
                int result = stmt.executeUpdate();
                
                // Also update verified_at in whitelist table if set
                if (playerRank.getVerifiedAt() != null) {
                    String updateVerifiedSql = String.format(
                        "UPDATE `%s` SET `verified_at` = ? WHERE `UUID` = ?", tableName
                    );
                    try (PreparedStatement verifiedStmt = conn.prepareStatement(updateVerifiedSql)) {
                        verifiedStmt.setTimestamp(1, Timestamp.from(playerRank.getVerifiedAt()));
                        verifiedStmt.setString(2, playerRank.getPlayerUuid());
                        verifiedStmt.executeUpdate();
                    }
                }
                
                return result > 0;
            } catch (SQLException e) {
                logger.error("Error saving player rank", e);
                return false;
            }
        });
    }

    /**
     * Gets all rank definitions from database
     * 
     * @return List<RankDefinition> all rank definitions
     */
    public List<RankDefinition> getAllRankDefinitions() {
        List<RankDefinition> definitions = new ArrayList<>();
        String sql = """
            CREATE TABLE IF NOT EXISTS rank_definitions (
                id INT AUTO_INCREMENT PRIMARY KEY,
                main_rank INT NOT NULL,
                sub_rank INT NOT NULL,
                rank_name VARCHAR(100) NOT NULL,
                main_rank_name VARCHAR(50) NOT NULL,
                required_time_minutes INT DEFAULT 0,
                required_achievements INT DEFAULT 0,
                discord_role_id BIGINT NULL,
                rewards_economy INT DEFAULT 0,
                rewards_commands TEXT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY unique_rank (main_rank, sub_rank)
            )
        """;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Create table if it doesn't exist
            stmt.execute(sql);
            
            // Insert default rank definitions if table is empty
            String countSql = "SELECT COUNT(*) FROM rank_definitions";
            try (ResultSet rs = stmt.executeQuery(countSql)) {
                if (rs.next() && rs.getInt(1) == 0) {
                    insertDefaultRankDefinitions(conn);
                }
            }
            
            // Get all rank definitions
            String selectSql = """
                SELECT main_rank, sub_rank, rank_name, main_rank_name, 
                       required_time_minutes, required_achievements, discord_role_id,
                       rewards_economy, rewards_commands
                FROM rank_definitions 
                ORDER BY main_rank, sub_rank
            """;
            
            try (ResultSet rs = stmt.executeQuery(selectSql)) {
                while (rs.next()) {
                    RankDefinition def = new RankDefinition(
                        (rs.getInt("main_rank") - 1) * 7 + rs.getInt("sub_rank"), // rankId
                        rs.getInt("main_rank"),
                        rs.getInt("sub_rank"),
                        rs.getString("rank_name"),
                        rs.getLong("discord_role_id"),
                        rs.getInt("required_time_minutes"),
                        rs.getInt("required_achievements"),
                        rs.getString("main_rank_name") // description
                    );
                    
                    // Set rewards if available
                    int economyReward = rs.getInt("rewards_economy");
                    String commandsJson = rs.getString("rewards_commands");
                    if (economyReward > 0 || commandsJson != null) {
                        List<String> commands = new ArrayList<>();
                        if (commandsJson != null && !commandsJson.isEmpty()) {
                            // Simple JSON parsing for commands array
                            commandsJson = commandsJson.replace("[", "").replace("]", "").replace("\"", "");
                            if (!commandsJson.trim().isEmpty()) {
                                String[] commandArray = commandsJson.split(",");
                                for (String cmd : commandArray) {
                                    commands.add(cmd.trim());
                                }
                            }
                        }
                        def.setRewards(new top.jk33v3rs.velocitydiscordwhitelist.models.RankRewards(economyReward, commands));
                    }
                    
                    definitions.add(def);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error getting rank definitions", e);
        }
        
        return definitions;
    }

    /**
     * Inserts default rank definitions into the database
     * 
     * @param conn Database connection
     */
    private void insertDefaultRankDefinitions(Connection conn) throws SQLException {
        String insertSql = """
            INSERT INTO rank_definitions (main_rank, sub_rank, rank_name, main_rank_name, required_time_minutes, required_achievements)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            // Insert basic rank structure (1-25 main ranks, 1-7 sub ranks each)
            String[] mainRankNames = {
                "bystander", "wanderer", "adventurer", "explorer", "tracker", "pathfinder", "navigator", "wayfinder",
                "scout", "ranger", "guardian", "sentinel", "warden", "protector", "defender", "champion",
                "hero", "legend", "mythic", "epic", "divine", "celestial", "transcendent", "eternal", "immortal"
            };
            
            String[] subRankNames = {"novice", "apprentice", "adept", "master", "heroic", "mythic", "immortal"};
            
            for (int mainRank = 1; mainRank <= 25; mainRank++) {
                String mainRankName = mainRankNames[mainRank - 1];
                
                for (int subRank = 1; subRank <= 7; subRank++) {
                    String subRankName = subRankNames[subRank - 1];
                    String fullRankName = subRankName + " " + mainRankName;
                    
                    // Calculate requirements (exponential growth)
                    int requiredTime = (int) (Math.pow(1.5, mainRank + subRank - 2) * 60); // Base 60 minutes
                    int requiredAchievements = (mainRank - 1) * 7 + (subRank - 1);
                    
                    stmt.setInt(1, mainRank);
                    stmt.setInt(2, subRank);
                    stmt.setString(3, fullRankName);
                    stmt.setString(4, mainRankName);
                    stmt.setInt(5, requiredTime);
                    stmt.setInt(6, requiredAchievements);
                    stmt.addBatch();
                }
            }
            
            stmt.executeBatch();
            debugLog("Inserted default rank definitions");
        }
    }

    /**
     * Logs XP gain for a player
     * 
     * @param playerUuid The player's UUID
     * @param xpAmount The amount of XP gained
     * @param source The source of the XP
     */
    public void logXpGain(String playerUuid, int xpAmount, String source) {
        String sql = """
            INSERT INTO xp_events (uuid, event_type, event_source, xp_gained)
            VALUES (?, 'XP_GAIN', ?, ?)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setString(2, source);
            stmt.setInt(3, xpAmount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error logging XP gain", e);
        }
    }

    /**
     * Logs achievement for a player
     * 
     * @param playerUuid The player's UUID
     * @param achievementName The name of the achievement
     */
    public void logAchievement(String playerUuid, String achievementName) {
        String sql = """
            INSERT INTO xp_events (uuid, event_type, event_source, xp_gained)
            VALUES (?, 'ACHIEVEMENT', ?, 1)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setString(2, achievementName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error logging achievement", e);
        }
    }

    /**
     * Logs rank promotion for a player
     * 
     * @param playerUuid The player's UUID
     * @param fromMainRank Previous main rank
     * @param fromSubRank Previous sub rank
     * @param toMainRank New main rank
     * @param toSubRank New sub rank
     * @param reason Reason for promotion
     */
    public void logRankPromotion(String playerUuid, int fromMainRank, int fromSubRank, 
                                int toMainRank, int toSubRank, String reason) {
        String sql = """
            INSERT INTO xp_events (uuid, event_type, event_source, xp_gained, metadata)
            VALUES (?, 'RANK_PROMOTION', ?, 0, ?)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setString(2, reason);
            stmt.setString(3, String.format("From %d.%d to %d.%d", fromMainRank, fromSubRank, toMainRank, toSubRank));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error logging rank promotion", e);
        }
    }

    /**
     * Gets player verification state
     * 
     * @param playerUuid The player's UUID
     * @return Optional<String> The verification state if found
     */
    public Optional<String> getPlayerVerificationState(String playerUuid) {
        String sql = String.format("SELECT `verification_state` FROM `%s` WHERE `UUID` = ?", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("verification_state"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player verification state", e);
        }
        
        return Optional.empty();
    }

    /**
     * Gets player Discord ID
     * 
     * @param playerUuid The player's UUID
     * @return Optional<Long> The Discord ID if found
     */
    public Optional<Long> getPlayerDiscordId(String playerUuid) {
        String sql = String.format("SELECT `discord_id` FROM `%s` WHERE `UUID` = ?", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long discordId = rs.getLong("discord_id");
                    if (!rs.wasNull()) {
                        return Optional.of(discordId);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player Discord ID", e);
        }
        
        return Optional.empty();
    }

    /**
     * Gets player name from UUID
     * 
     * @param playerUuid The player's UUID
     * @return Optional<String> The player name if found
     */
    public Optional<String> getPlayerNameFromUuid(String playerUuid) {
        String sql = String.format("SELECT `user` FROM `%s` WHERE `UUID` = ?", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("user"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player name from UUID", e);
        }
        
        return Optional.empty();
    }

    /**
     * Logs reward processing for a player
     * 
     * @param playerUuid The player's UUID
     * @param mainRank The main rank
     * @param subRank The sub rank
     * @param economyAmount The economy reward amount
     * @param commandCount The number of commands executed
     */
    public void logRewardProcessing(String playerUuid, int mainRank, int subRank, 
                                   int economyAmount, int commandCount) {
        String sql = """
            INSERT INTO xp_events (uuid, event_type, event_source, xp_gained, metadata)
            VALUES (?, 'REWARD_PROCESSING', ?, ?, ?)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setString(2, String.format("Rank %d.%d", mainRank, subRank));
            stmt.setInt(3, economyAmount);
            stmt.setString(4, String.format("Commands executed: %d", commandCount));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error logging reward processing", e);
        }
    }

    /**
     * Gets player total XP from all sources
     * 
     * @param playerUuid The player's UUID
     * @return The total XP points for the player
     */
    public int getPlayerTotalXP(String playerUuid) {
        String sql = """
            SELECT COALESCE(SUM(xp_gained), 0) as total_xp
            FROM xp_events 
            WHERE uuid = ? AND event_type = 'XP_GAIN'
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_xp");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player total XP", e);
        }
        
        return 0;
    }

    /**
     * Gets player XP within a date range
     * 
     * @param playerUuid The player's UUID
     * @param startDate Start of the date range
     * @param endDate End of the date range
     * @return The total XP gained in the date range
     */
    public int getPlayerXPInRange(String playerUuid, Instant startDate, Instant endDate) {
        String sql = """
            SELECT COALESCE(SUM(xp_gained), 0) as total_xp
            FROM xp_events 
            WHERE uuid = ? AND event_type = 'XP_GAIN' 
            AND timestamp BETWEEN ? AND ?
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setTimestamp(2, Timestamp.from(startDate));
            stmt.setTimestamp(3, Timestamp.from(endDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_xp");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player XP in range", e);
        }
        
        return 0;
    }

    /**
     * Updates player playtime
     * 
     * @param playerUuid The player's UUID
     * @param additionalMinutes Additional playtime minutes to add
     */
    public void updatePlayerPlaytime(String playerUuid, int additionalMinutes) {
        String sql = """
            INSERT INTO user_data (uuid, username, playtime_minutes)
            VALUES (?, 'Unknown', ?)
            ON DUPLICATE KEY UPDATE 
                playtime_minutes = playtime_minutes + VALUES(playtime_minutes),
                last_seen = CURRENT_TIMESTAMP
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setInt(2, additionalMinutes);
            stmt.executeUpdate();
            
            // Also update rank_data table
            String rankSql = """
                INSERT INTO rank_data (uuid, playtime_minutes)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE 
                    playtime_minutes = playtime_minutes + VALUES(playtime_minutes)
            """;
            
            try (PreparedStatement rankStmt = conn.prepareStatement(rankSql)) {
                rankStmt.setString(1, playerUuid);
                rankStmt.setInt(2, additionalMinutes);
                rankStmt.executeUpdate();
            }
            
        } catch (SQLException e) {
            logger.error("Error updating player playtime", e);
        }
    }

    /**
     * Updates player achievement count
     * 
     * @param playerUuid The player's UUID
     * @param newAchievementCount The new total achievement count
     */
    public void updatePlayerAchievements(String playerUuid, int newAchievementCount) {
        String sql = """
            INSERT INTO user_data (uuid, username, achievements_completed)
            VALUES (?, 'Unknown', ?)
            ON DUPLICATE KEY UPDATE 
                achievements_completed = VALUES(achievements_completed),
                last_seen = CURRENT_TIMESTAMP
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setInt(2, newAchievementCount);
            stmt.executeUpdate();
            
            // Also update rank_data table
            String rankSql = """
                INSERT INTO rank_data (uuid, achievements_completed)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE 
                    achievements_completed = VALUES(achievements_completed)
            """;
            
            try (PreparedStatement rankStmt = conn.prepareStatement(rankSql)) {
                rankStmt.setString(1, playerUuid);
                rankStmt.setInt(2, newAchievementCount);
                rankStmt.executeUpdate();
            }
            
        } catch (SQLException e) {
            logger.error("Error updating player achievements", e);
        }
    }

    /**
     * Gets player's current playtime in minutes
     * 
     * @param playerUuid The player's UUID
     * @return The player's playtime in minutes
     */
    public int getPlayerPlaytime(String playerUuid) {
        String sql = """
            SELECT COALESCE(playtime_minutes, 0) as playtime
            FROM user_data 
            WHERE uuid = ?
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("playtime");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player playtime", e);
        }
        
        return 0;
    }

    /**
     * Gets player's current achievement count
     * 
     * @param playerUuid The player's UUID
     * @return The player's achievement count
     */
    public int getPlayerAchievements(String playerUuid) {
        String sql = """
            SELECT COALESCE(achievements_completed, 0) as achievements
            FROM user_data 
            WHERE uuid = ?
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("achievements");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player achievements", e);
        }
        
        return 0;
    }

    /**
     * Updates player XP total
     * 
     * @param playerUuid The player's UUID
     * @param newXPTotal The new total XP amount
     */
    public void updatePlayerXP(String playerUuid, int newXPTotal) {
        String sql = """
            INSERT INTO user_data (uuid, username, xp_points)
            VALUES (?, 'Unknown', ?)
            ON DUPLICATE KEY UPDATE 
                xp_points = VALUES(xp_points),
                last_seen = CURRENT_TIMESTAMP
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setInt(2, newXPTotal);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating player XP", e);
        }
    }

    /**
     * Gets daily XP breakdown for a player over the specified number of days
     * 
     * @param playerUuid The player's UUID
     * @param days Number of days to look back
     * @return Map of date strings to XP amounts
     */
    public java.util.Map<String, Integer> getDailyXPBreakdown(String playerUuid, int days) {
        java.util.Map<String, Integer> breakdown = new java.util.LinkedHashMap<>();
        String sql = """
            SELECT DATE(timestamp) as date, COALESCE(SUM(xp_gained), 0) as daily_xp
            FROM xp_events 
            WHERE uuid = ? AND event_type = 'XP_GAIN' 
            AND timestamp >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
            GROUP BY DATE(timestamp)
            ORDER BY date DESC
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setInt(2, days);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    breakdown.put(rs.getString("date"), rs.getInt("daily_xp"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting daily XP breakdown", e);
        }
        
        return breakdown;
    }

    /**
     * Gets XP statistics for a player
     * 
     * @param playerUuid The player's UUID
     * @return Map containing various XP statistics
     */
    public java.util.Map<String, Object> getXPStatistics(String playerUuid) {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        // Get total XP
        stats.put("total_xp", getPlayerTotalXP(playerUuid));
        
        // Get XP from last 7 days
        Instant sevenDaysAgo = Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);
        stats.put("weekly_xp", getPlayerXPInRange(playerUuid, sevenDaysAgo, Instant.now()));
        
        // Get XP from last 24 hours
        Instant oneDayAgo = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
        stats.put("daily_xp", getPlayerXPInRange(playerUuid, oneDayAgo, Instant.now()));
        
        // Get most active XP source
        String mostActiveSource = getMostActiveXPSource(playerUuid);
        stats.put("most_active_source", mostActiveSource);
        
        return stats;
    }

    /**
     * Gets the most active XP source for a player
     * 
     * @param playerUuid The player's UUID
     * @return The name of the most active XP source
     */
    private String getMostActiveXPSource(String playerUuid) {
        String sql = """
            SELECT event_source, SUM(xp_gained) as total_xp
            FROM xp_events 
            WHERE uuid = ? AND event_type = 'XP_GAIN'
            GROUP BY event_source
            ORDER BY total_xp DESC
            LIMIT 1
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("event_source");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting most active XP source", e);
        }
        
        return "Unknown";
    }

    /**
     * Gets XP events for a player within a date range
     * 
     * @param playerUuid The player's UUID
     * @param startDate Start of the date range
     * @param endDate End of the date range
     * @return List of XP events
     */
    public java.util.List<java.util.Map<String, Object>> getXPEvents(String playerUuid, Instant startDate, Instant endDate) {
        java.util.List<java.util.Map<String, Object>> events = new ArrayList<>();
        String sql = """
            SELECT event_source, xp_gained, timestamp, server_name, metadata
            FROM xp_events 
            WHERE uuid = ? AND event_type = 'XP_GAIN' 
            AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp DESC
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setTimestamp(2, Timestamp.from(startDate));
            stmt.setTimestamp(3, Timestamp.from(endDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> event = new java.util.HashMap<>();
                    event.put("source", rs.getString("event_source"));
                    event.put("xp_gained", rs.getInt("xp_gained"));
                    event.put("timestamp", rs.getTimestamp("timestamp").toInstant());
                    event.put("server_name", rs.getString("server_name"));
                    event.put("metadata", rs.getString("metadata"));
                    events.add(event);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting XP events", e);
        }
        
        return events;
    }

    /**
     * Records playtime session for a player
     * 
     * @param playerUuid The player's UUID
     * @param sessionStart When the session started
     * @param sessionEnd When the session ended
     * @param serverName The server name where the session occurred
     */
    public void recordPlaytimeSession(String playerUuid, Instant sessionStart, Instant sessionEnd, String serverName) {
        long sessionMinutes = java.time.Duration.between(sessionStart, sessionEnd).toMinutes();
        
        if (sessionMinutes > 0) {
            // Update playtime
            updatePlayerPlaytime(playerUuid, (int) sessionMinutes);
            
            // Log the session
            String sql = """
                INSERT INTO xp_events (uuid, event_type, event_source, xp_gained, timestamp, server_name, metadata)
                VALUES (?, 'PLAYTIME_SESSION', ?, 0, ?, ?, ?)
            """;
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid);
                stmt.setString(2, "Session");
                stmt.setTimestamp(3, Timestamp.from(sessionEnd));
                stmt.setString(4, serverName);
                stmt.setString(5, String.format("Session duration: %d minutes", sessionMinutes));
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error recording playtime session", e);
            }
        }
    }

    /**
     * Gets player XP breakdown by source since a specific date
     * 
     * @param playerUuid The player's UUID
     * @param since The date to start from
     * @return Map of source names to XP amounts
     */
    public java.util.Map<String, Integer> getPlayerXPBySource(String playerUuid, Instant since) {
        java.util.Map<String, Integer> breakdown = new java.util.LinkedHashMap<>();
        String sql = """
            SELECT event_source, COALESCE(SUM(xp_gained), 0) as source_xp
            FROM xp_events 
            WHERE uuid = ? AND event_type = 'XP_GAIN' 
            AND timestamp >= ?
            GROUP BY event_source
            ORDER BY source_xp DESC
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setTimestamp(2, Timestamp.from(since));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    breakdown.put(rs.getString("event_source"), rs.getInt("source_xp"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player XP by source", e);
        }
        
        return breakdown;
    }

    /**
     * Gets all players who need rank updates based on progression
     * 
     * @return List of player UUIDs that may need rank updates
     */
    public java.util.List<String> getPlayersNeedingRankUpdate() {
        java.util.List<String> players = new ArrayList<>();
        String sql = """
            SELECT DISTINCT r.uuid
            FROM rank_data r
            JOIN user_data u ON r.uuid = u.uuid
            WHERE r.last_promotion IS NULL 
               OR r.last_promotion < DATE_SUB(NOW(), INTERVAL 1 DAY)
            ORDER BY r.last_promotion ASC
            LIMIT 100
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                players.add(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            logger.error("Error getting players needing rank update", e);
        }
        
        return players;
    }

    /**
     * Gets top players by XP for leaderboard
     * 
     * @param limit Number of top players to return
     * @param days Number of days to look back (0 for all time)
     * @return List of maps containing player info and XP
     */
    public java.util.List<java.util.Map<String, Object>> getTopPlayersByXP(int limit, int days) {
        java.util.List<java.util.Map<String, Object>> topPlayers = new ArrayList<>();
        String sql;
        
        if (days > 0) {
            sql = """
                SELECT e.uuid, w.user, COALESCE(SUM(e.xp_gained), 0) as total_xp
                FROM xp_events e
                JOIN %s w ON e.uuid = w.UUID
                WHERE e.event_type = 'XP_GAIN' 
                AND e.timestamp >= DATE_SUB(NOW(), INTERVAL ? DAY)
                GROUP BY e.uuid, w.user
                ORDER BY total_xp DESC
                LIMIT ?
            """.formatted(tableName);
        } else {
            sql = """
                SELECT e.uuid, w.user, COALESCE(SUM(e.xp_gained), 0) as total_xp
                FROM xp_events e
                JOIN %s w ON e.uuid = w.UUID
                WHERE e.event_type = 'XP_GAIN'
                GROUP BY e.uuid, w.user
                ORDER BY total_xp DESC
                LIMIT ?
            """.formatted(tableName);
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            if (days > 0) {
                stmt.setInt(1, days);
                stmt.setInt(2, limit);
            } else {
                stmt.setInt(1, limit);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    java.util.Map<String, Object> player = new java.util.HashMap<>();
                    player.put("rank", rank++);
                    player.put("uuid", rs.getString("uuid"));
                    player.put("username", rs.getString("user"));
                    player.put("total_xp", rs.getInt("total_xp"));
                    topPlayers.add(player);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting top players by XP", e);
        }
        
        return topPlayers;
    }

    /**
     * Gets top players by playtime for leaderboard
     * 
     * @param limit Number of top players to return
     * @return List of maps containing player info and playtime
     */
    public java.util.List<java.util.Map<String, Object>> getTopPlayersByPlaytime(int limit) {
        java.util.List<java.util.Map<String, Object>> topPlayers = new ArrayList<>();
        String sql = """
            SELECT u.uuid, w.user, u.playtime_minutes
            FROM user_data u
            JOIN %s w ON u.uuid = w.UUID
            ORDER BY u.playtime_minutes DESC
            LIMIT ?
        """.formatted(tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    java.util.Map<String, Object> player = new java.util.HashMap<>();
                    player.put("rank", rank++);
                    player.put("uuid", rs.getString("uuid"));
                    player.put("username", rs.getString("user"));
                    player.put("playtime_minutes", rs.getInt("playtime_minutes"));
                    topPlayers.add(player);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting top players by playtime", e);
        }
        
        return topPlayers;
    }

    /**
     * Gets player rank position in leaderboard by XP
     * 
     * @param playerUuid The player's UUID
     * @return The player's rank position (1-based), or 0 if not found
     */
    public int getPlayerXPRank(String playerUuid) {
        String sql = """
            SELECT rank_position FROM (
                SELECT uuid, 
                       ROW_NUMBER() OVER (ORDER BY total_xp DESC) as rank_position
                FROM (
                    SELECT uuid, COALESCE(SUM(xp_gained), 0) as total_xp
                    FROM xp_events 
                    WHERE event_type = 'XP_GAIN'
                    GROUP BY uuid
                ) xp_totals
            ) ranked_players
            WHERE uuid = ?
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("rank_position");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player XP rank", e);
        }
        
        return 0;
    }

    /**
     * Gets player rank position in leaderboard by playtime
     * 
     * @param playerUuid The player's UUID
     * @return The player's rank position (1-based), or 0 if not found
     */
    public int getPlayerPlaytimeRank(String playerUuid) {
        String sql = """
            SELECT rank_position FROM (
                SELECT uuid, 
                       ROW_NUMBER() OVER (ORDER BY playtime_minutes DESC) as rank_position
                FROM user_data
            ) ranked_players
            WHERE uuid = ?
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("rank_position");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player playtime rank", e);
        }
        
        return 0;
    }

    /**
     * Gets player XP since a specific date
     * 
     * @param playerUuid The player's UUID
     * @param since The date to start from
     * @return The total XP gained since the date
     */
    public int getPlayerXPSince(String playerUuid, Instant since) {
        return getPlayerXPInRange(playerUuid, since, Instant.now());
    }

    /**
     * Gets recent XP events for a player
     * 
     * @param playerUuid The player's UUID
     * @param limit Maximum number of events to return
     * @return List of recent XP events
     */
    public java.util.List<java.util.Map<String, Object>> getRecentXPEvents(String playerUuid, int limit) {
        java.util.List<java.util.Map<String, Object>> events = new ArrayList<>();
        String sql = """
            SELECT event_source, xp_gained, timestamp, server_name, metadata
            FROM xp_events 
            WHERE uuid = ? AND event_type = 'XP_GAIN' 
            ORDER BY timestamp DESC
            LIMIT ?
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> event = new java.util.HashMap<>();
                    event.put("source", rs.getString("event_source"));
                    event.put("xp_gained", rs.getInt("xp_gained"));
                    event.put("timestamp", rs.getTimestamp("timestamp").toInstant());
                    event.put("server_name", rs.getString("server_name"));
                    event.put("metadata", rs.getString("metadata"));
                    events.add(event);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting recent XP events", e);
        }
        
        return events;
    }

    /**
     * Gets server-wide statistics
     * 
     * @return Map containing server statistics
     */
    public java.util.Map<String, Object> getServerStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        try (Connection conn = getConnection()) {
            // Total players
            String totalPlayersSql = String.format("SELECT COUNT(*) as total FROM `%s`", tableName);
            try (PreparedStatement stmt = conn.prepareStatement(totalPlayersSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("total_players", rs.getInt("total"));
                }
            }
            
            // Total XP awarded
            String totalXpSql = "SELECT COALESCE(SUM(xp_gained), 0) as total_xp FROM xp_events WHERE event_type = 'XP_GAIN'";
            try (PreparedStatement stmt = conn.prepareStatement(totalXpSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("total_xp_awarded", rs.getInt("total_xp"));
                }
            }
            
            // Total playtime
            String totalPlaytimeSql = "SELECT COALESCE(SUM(playtime_minutes), 0) as total_playtime FROM user_data";
            try (PreparedStatement stmt = conn.prepareStatement(totalPlaytimeSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("total_playtime_minutes", rs.getInt("total_playtime"));
                }
            }
            
            // Active players (last 7 days)
            String activePlayersSql = """
                SELECT COUNT(DISTINCT uuid) as active_players 
                FROM xp_events 
                WHERE timestamp >= DATE_SUB(NOW(), INTERVAL 7 DAY)
            """;
            try (PreparedStatement stmt = conn.prepareStatement(activePlayersSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("active_players_7d", rs.getInt("active_players"));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error getting server statistics", e);
        }
        
        return stats;
    }

    /**
     * Checks if a player exists in any of our tables
     * 
     * @param playerUuid The player's UUID
     * @return true if player exists, false otherwise
     */
    public boolean playerExists(String playerUuid) {
        String sql = String.format("SELECT 1 FROM `%s` WHERE `UUID` = ? LIMIT 1", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking if player exists", e);
        }
        
        return false;
    }

    /**
     * Gets XP chart data for a player over a period
     * 
     * @param playerUuid The player's UUID
     * @param days Number of days to look back
     * @return Chart data with dates and cumulative XP
     */
    public java.util.Map<String, Object> getXPChartData(String playerUuid, int days) {
        java.util.Map<String, Object> chartData = new java.util.HashMap<>();
        java.util.List<String> dates = new ArrayList<>();
        java.util.List<Integer> cumulativeXP = new ArrayList<>();
        
        String sql = """
            SELECT DATE(timestamp) as date, COALESCE(SUM(xp_gained), 0) as daily_xp
            FROM xp_events 
            WHERE uuid = ? AND event_type = 'XP_GAIN' 
            AND timestamp >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
            GROUP BY DATE(timestamp)
            ORDER BY date ASC
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setInt(2, days);
            
            int runningTotal = 0;
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    dates.add(rs.getString("date"));
                    runningTotal += rs.getInt("daily_xp");
                    cumulativeXP.add(runningTotal);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting XP chart data", e);
        }
        
        chartData.put("dates", dates);
        chartData.put("cumulative_xp", cumulativeXP);
        chartData.put("player_uuid", playerUuid);
        
        return chartData;
    }

    /**
     * Bulk update player data for performance
     * 
     * @param playerUpdates List of player data updates
     */
    public void bulkUpdatePlayerData(java.util.List<java.util.Map<String, Object>> playerUpdates) {
        String sql = """
            INSERT INTO user_data (uuid, username, playtime_minutes, xp_points, achievements_completed)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
                username = VALUES(username),
                playtime_minutes = VALUES(playtime_minutes),
                xp_points = VALUES(xp_points),
                achievements_completed = VALUES(achievements_completed),
                last_seen = CURRENT_TIMESTAMP
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (java.util.Map<String, Object> update : playerUpdates) {
                stmt.setString(1, (String) update.get("uuid"));
                stmt.setString(2, (String) update.get("username"));
                stmt.setInt(3, (Integer) update.getOrDefault("playtime_minutes", 0));
                stmt.setInt(4, (Integer) update.getOrDefault("xp_points", 0));
                stmt.setInt(5, (Integer) update.getOrDefault("achievements_completed", 0));
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            debugLog("Bulk updated " + playerUpdates.size() + " player records");
            
        } catch (SQLException e) {
            logger.error("Error bulk updating player data", e);
        }
    }
}
