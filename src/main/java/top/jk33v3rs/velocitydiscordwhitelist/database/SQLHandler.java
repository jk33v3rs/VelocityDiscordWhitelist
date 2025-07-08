package top.jk33v3rs.velocitydiscordwhitelist.database;

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

import org.slf4j.Logger;

import com.velocitypowered.api.proxy.Player;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import top.jk33v3rs.velocitydiscordwhitelist.models.PlayerInfo;
import top.jk33v3rs.velocitydiscordwhitelist.models.PlayerRank;
import top.jk33v3rs.velocitydiscordwhitelist.models.RankDefinition;
import top.jk33v3rs.velocitydiscordwhitelist.utils.DatabaseConfigValidator;
import top.jk33v3rs.velocitydiscordwhitelist.utils.ExceptionHandler;

/**
 * SQLHandler manages database connections and whitelist operations using
 * HikariCP.
 */
public class SQLHandler implements AutoCloseable {
    private final Logger logger;
    private final HikariDataSource dataSource;
    private final String tableName;
    private final boolean debugEnabled;
    private final ExceptionHandler exceptionHandler;

    /**
     * Initializes the database connection pool and creates necessary tables
     * 
     * @param config           The configuration map containing database settings
     * @param logger           The logger instance for this class
     * @param debugEnabled     Whether debug logging is enabled
     * @param exceptionHandler The exception handler for centralized error handling
     * @throws RuntimeException if database initialization fails
     */
    @SuppressWarnings("unchecked")
    public SQLHandler(Map<String, Object> config, Logger logger, boolean debugEnabled, ExceptionHandler exceptionHandler) {
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        this.exceptionHandler = exceptionHandler;

        // Validate that config is not null
        if (config == null) {
            exceptionHandler.handleIntegrationException("SQLHandler", "configuration validation", 
                new IllegalArgumentException("Configuration cannot be null"));
            throw new RuntimeException("Configuration cannot be null");
        }

        Map<String, Object> dbConfig = (Map<String, Object>) config.get("database");

        // Validate that database config exists
        if (dbConfig == null) {
            exceptionHandler.handleIntegrationException("SQLHandler", "database configuration validation", 
                new IllegalArgumentException("Database configuration is missing. Please check your config.yml file."));
            throw new RuntimeException("Database configuration is missing. Please check your config.yml file.");
        }

        this.tableName = dbConfig.getOrDefault("table", "whitelist").toString();        // Validate configuration before attempting connection
        DatabaseConfigValidator validator = new DatabaseConfigValidator(exceptionHandler);
        DatabaseConfigValidator.ValidationResult configResult = validator.validateConfig(config);
        
        if (!configResult.isValid()) {
            exceptionHandler.handleIntegrationException("SQLHandler", "configuration validation", 
                new IllegalArgumentException(configResult.getMessage()));
            throw new RuntimeException("Database configuration validation failed: " + configResult.getMessage());
        }
        
        if (debugEnabled) {
            logger.info("Database configuration validated: {}", configResult.getMessage());
        }
        
        // Test connectivity before creating connection pool
        DatabaseConfigValidator.ValidationResult connectivityResult = validator.testConnectivity(config);
        
        if (!connectivityResult.isValid()) {
            String errorMessage = "Database connectivity test failed: " + connectivityResult.getMessage();
            exceptionHandler.handleIntegrationException("SQLHandler", "connectivity validation", 
                new RuntimeException(errorMessage));
            throw new RuntimeException(errorMessage);
        }
        
        if (debugEnabled) {
            logger.info("Database connectivity test passed: {}", connectivityResult.getMessage());
        }

        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            exceptionHandler.handleIntegrationException("SQLHandler", "JDBC driver loading", e);
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
            exceptionHandler.handleDatabaseException("connection pool initialization", e, "HikariCP configuration");
            throw new RuntimeException("Failed to initialize database connection", e);
        }
    }

    /**
     * Creates all required tables for the plugin.
     */
    public void createTable() {
        exceptionHandler.executeWithHandling("creating database tables", () -> {
            try (Connection conn = getConnection();
                    Statement stmt = conn.createStatement()) {

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
                        tableName);
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
                throw new RuntimeException("Failed to create database tables", e);
            }
        });
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
                    String dbUsername = rs.getString("user");
                    if (!dbUsername.equals(username)) {
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
        String checkUsernameSql = String.format("SELECT `UUID` FROM `%s` WHERE `user` = ? AND `UUID` IS NULL",
                tableName);
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
     * @return CompletableFuture<Boolean> true if player is whitelisted, false
     *         otherwise
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
     * @param uuid        The player's UUID
     * @param name        The player's username
     * @param discordId   The Discord ID (can be null)
     * @param discordName The Discord username (can be null)
     * @return CompletableFuture<Boolean> true if successful
     */
    public CompletableFuture<Boolean> addPlayerToWhitelist(UUID uuid, String name, String discordId,
            String discordName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format(
                    "INSERT INTO `%s` (`UUID`, `user`, `discord_id`, `discord_name`) VALUES (?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE `user` = VALUES(`user`), `discord_id` = VALUES(`discord_id`), `discord_name` = VALUES(`discord_name`)",
                    tableName);

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
            String sql = String.format(
                    "SELECT `UUID`, `user`, `discord_id`, `discord_name`, `verification_state` FROM `%s`", tableName);

            try (Connection conn = getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    PlayerInfo info = new PlayerInfo(
                            rs.getString("UUID"),
                            rs.getString("user"),
                            rs.getString("discord_id"),
                            rs.getString("discord_name"),
                            rs.getString("verification_state"));
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
            String sql = String.format(
                    "SELECT `UUID`, `user`, `discord_id`, `discord_name`, `verification_state` FROM `%s` WHERE `UUID` = ?",
                    tableName);

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
                                rs.getString("verification_state"));
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
                                rs.getTimestamp("first_join") != null ? rs.getTimestamp("first_join").toInstant()
                                        : Instant.now(),
                                rs.getInt("playtime_minutes"),
                                rs.getInt("achievements_completed"),
                                rs.getTimestamp("last_promotion") != null
                                        ? rs.getTimestamp("last_promotion").toInstant()
                                        : null);

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
                stmt.setTimestamp(6,
                        playerRank.getLastPromotion() != null ? Timestamp.from(playerRank.getLastPromotion()) : null);

                int result = stmt.executeUpdate();

                // Also update verified_at in whitelist table if set
                if (playerRank.getVerifiedAt() != null) {
                    String updateVerifiedSql = String.format(
                            "UPDATE `%s` SET `verified_at` = ? WHERE `UUID` = ?", tableName);
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
     * executeUpdate
     *
     * Executes a parameterized SQL update statement.
     *
     * @param sql    The SQL statement with parameter placeholders
     * @param params The parameters to set in the statement
     * @return The number of affected rows
     */    private int executeUpdate(String sql, Object[] params) {
        return exceptionHandler.executeWithHandling("executing SQL update: " + sql, () -> {
            try (Connection conn = getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        Object param = params[i];
                        if (param == null) {
                            stmt.setNull(i + 1, java.sql.Types.VARCHAR);
                        } else if (param instanceof String) {
                            stmt.setString(i + 1, (String) param);
                        } else if (param instanceof Integer) {
                            stmt.setInt(i + 1, (Integer) param);
                        } else if (param instanceof Long) {
                            stmt.setLong(i + 1, (Long) param);                        } else if (param instanceof java.sql.Timestamp) {
                            stmt.setTimestamp(i + 1, (java.sql.Timestamp) param);
                        } else if (param instanceof Boolean) {
                            stmt.setBoolean(i + 1, (Boolean) param);
                        } else if (param instanceof Double) {
                            stmt.setDouble(i + 1, (Double) param);
                        } else if (param instanceof Float) {
                            stmt.setFloat(i + 1, (Float) param);
                        } else if (param instanceof java.sql.Date) {
                            stmt.setDate(i + 1, (java.sql.Date) param);
                        } else {
                            // Convert unknown types to string as a last resort
                            stmt.setString(i + 1, param.toString());
                        }
                    }
                }
                return stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("SQL execution failed", e);
            }
        }, 0);
    }

    /**
     * executeQueryForSingleResult
     *
     * Executes a query and returns a single result using a ResultSet mapper
     * function.
     *
     * @param <T>    The type of result to return
     * @param sql    The SQL query to execute
     * @param params The parameters for the query
     * @param mapper The function to map ResultSet to result type
     * @return Optional containing the result if found
     */
    @SuppressWarnings("unused") // Helper method for future use    
    private <T> Optional<T> executeQueryForSingleResult(String sql, Object[] params, ResultSetMapper<T> mapper) {
        return exceptionHandler.executeWithHandling("executing SQL query: " + sql, () -> {
            try (Connection conn = getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        Object param = params[i];
                        if (param == null) {
                            stmt.setNull(i + 1, java.sql.Types.VARCHAR);
                        } else if (param instanceof String) {
                            stmt.setString(i + 1, (String) param);
                        } else if (param instanceof Integer) {
                            stmt.setInt(i + 1, (Integer) param);
                        } else if (param instanceof Long) {
                            stmt.setLong(i + 1, (Long) param);
                        } else if (param instanceof java.sql.Timestamp) {
                            stmt.setTimestamp(i + 1, (java.sql.Timestamp) param);
                        } else if (param instanceof Boolean) {
                            stmt.setBoolean(i + 1, (Boolean) param);
                        } else if (param instanceof Double) {
                            stmt.setDouble(i + 1, (Double) param);
                        } else if (param instanceof Float) {
                            stmt.setFloat(i + 1, (Float) param);
                        } else if (param instanceof java.sql.Date) {
                            stmt.setDate(i + 1, (java.sql.Date) param);
                        } else {
                            // Convert unknown types to string as a last resort
                            stmt.setString(i + 1, param.toString());
                        }
                    }
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(mapper.map(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("SQL query execution failed", e);
            }
            return Optional.empty();
        }, Optional.empty());
    }

    /**
     * executeQueryForMultipleResults
     *
     * Executes a query and returns multiple results using a ResultSet mapper
     * function.
     *
     * @param <T>    The type of results to return
     * @param sql    The SQL query to execute
     * @param params The parameters for the query
     * @param mapper The function to map ResultSet to result type
     * @return List of results
     */
    @SuppressWarnings("unused") // Helper method for future use
    private <T> List<T> executeQueryForMultipleResults(String sql, Object[] params, ResultSetMapper<T> mapper) {
        return exceptionHandler.executeWithHandling("executing SQL query for multiple results: " + sql, () -> {
            List<T> results = new ArrayList<>();
            try (Connection conn = getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        Object param = params[i];
                        if (param == null) {
                            stmt.setNull(i + 1, java.sql.Types.VARCHAR);
                        } else if (param instanceof String) {
                            stmt.setString(i + 1, (String) param);
                        } else if (param instanceof Integer) {
                            stmt.setInt(i + 1, (Integer) param);
                        } else if (param instanceof Long) {
                            stmt.setLong(i + 1, (Long) param);                        } else if (param instanceof java.sql.Timestamp) {
                            stmt.setTimestamp(i + 1, (java.sql.Timestamp) param);
                        } else if (param instanceof Boolean) {
                            stmt.setBoolean(i + 1, (Boolean) param);
                        } else if (param instanceof Double) {
                            stmt.setDouble(i + 1, (Double) param);
                        } else if (param instanceof Float) {
                            stmt.setFloat(i + 1, (Float) param);
                        } else if (param instanceof java.sql.Date) {
                            stmt.setDate(i + 1, (java.sql.Date) param);
                        } else {
                            // Convert unknown types to string as a last resort
                            stmt.setString(i + 1, param.toString());
                        }
                    }
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        T result = mapper.map(rs);
                        if (result != null) {
                            results.add(result);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("SQL query execution failed", e);
            }
            return results;
        }, new ArrayList<>());
    }

    /**
     * Functional interface for mapping ResultSet to objects
     */
    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * Gets all rank definitions from database
     * 
     * @return List<RankDefinition> all rank definitions
     */
    public List<RankDefinition> getAllRankDefinitions() {
        List<RankDefinition> definitions = new ArrayList<>();

        String createTableSql = """
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
            stmt.executeUpdate(createTableSql);

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
                            (rs.getInt("main_rank") - 1) * 7 + rs.getInt("sub_rank"),
                            rs.getInt("main_rank"),
                            rs.getInt("sub_rank"),
                            rs.getString("rank_name"),
                            rs.getLong("discord_role_id"),
                            rs.getInt("required_time_minutes"),
                            rs.getInt("required_achievements"),
                            rs.getString("main_rank_name"));

                    // Set rewards if available
                    int economyReward = rs.getInt("rewards_economy");
                    String commandsJson = rs.getString("rewards_commands");
                    if (economyReward > 0 || commandsJson != null) {
                        List<String> commands = new ArrayList<>();
                        if (commandsJson != null && !commandsJson.isEmpty()) {
                            commandsJson = commandsJson.replace("[", "").replace("]", "").replace("\"", "");
                            if (!commandsJson.trim().isEmpty()) {
                                String[] commandArray = commandsJson.split(",");
                                for (String cmd : commandArray) {
                                    commands.add(cmd.trim());
                                }
                            }
                        }
                        def.setRewards(
                                new top.jk33v3rs.velocitydiscordwhitelist.models.RankRewards(economyReward, commands));
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
                    "bystander", "wanderer", "adventurer", "explorer", "tracker", "pathfinder", "navigator",
                    "wayfinder",
                    "scout", "ranger", "guardian", "sentinel", "warden", "protector", "defender", "champion",
                    "hero", "legend", "mythic", "epic", "divine", "celestial", "transcendent", "eternal", "immortal"
            };

            String[] subRankNames = { "novice", "apprentice", "adept", "master", "heroic", "mythic", "immortal" };

            for (int mainRank = 1; mainRank <= 25; mainRank++) {
                String mainRankName = mainRankNames[mainRank - 1];

                for (int subRank = 1; subRank <= 7; subRank++) {
                    String subRankName = subRankNames[subRank - 1];
                    String fullRankName = subRankName + " " + mainRankName;

                    int requiredTime = (int) (Math.pow(1.5, mainRank + subRank - 2) * 60);
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
     * @param xpAmount   The amount of XP gained
     * @param source     The source of the XP
     */
    public void logXpGain(String playerUuid, int xpAmount, String source) {
        String sql = """
                    INSERT INTO xp_events (uuid, event_type, event_source, xp_gained)
                    VALUES (?, 'XP_GAIN', ?, ?)
                """;

        executeUpdate(sql, new Object[] { playerUuid, source, xpAmount });
    }

    /**
     * Logs achievement for a player
     * 
     * @param playerUuid      The player's UUID
     * @param achievementName The name of the achievement
     */
    public void logAchievement(String playerUuid, String achievementName) {
        String sql = """
                    INSERT INTO xp_events (uuid, event_type, event_source, xp_gained)
                    VALUES (?, 'ACHIEVEMENT', ?, 1)
                """;

        executeUpdate(sql, new Object[] { playerUuid, achievementName });
    }

    /**
     * Logs rank promotion for a player
     * 
     * @param playerUuid   The player's UUID
     * @param fromMainRank Previous main rank
     * @param fromSubRank  Previous sub rank
     * @param toMainRank   New main rank
     * @param toSubRank    New sub rank
     * @param reason       Reason for promotion
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
     * Records a BlazeAndCaves achievement completion for a player
     * 
     * @param playerUuid The player's UUID as string
     * @param achievementKey The namespaced key of the completed achievement
     * @param bonusXP The bonus XP awarded for this achievement
     */
    public void recordAchievementCompletion(String playerUuid, String achievementKey, int bonusXP) {
        String sql = "INSERT INTO " + tableName + "_achievements (player_uuid, achievement_key, bonus_xp, completed_at) " +
                     "VALUES (?, ?, ?, NOW()) ON DUPLICATE KEY UPDATE bonus_xp = VALUES(bonus_xp), completed_at = NOW()";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerUuid);
            stmt.setString(2, achievementKey);
            stmt.setInt(3, bonusXP);
            
            stmt.executeUpdate();
            
            if (debugEnabled) {
                debugLog("Recorded achievement completion: " + achievementKey + " for player " + playerUuid + 
                         " with " + bonusXP + " bonus XP");
            }
            
        } catch (SQLException e) {
            logger.error("Error recording achievement completion for player: {}", playerUuid, e);
            exceptionHandler.handleDatabaseException("Record achievement completion", e, 
                "Player: " + playerUuid + ", Achievement: " + achievementKey);
        }
    }
    
    /**
     * Gets the count of achievements completed by a player
     * 
     * @param playerUuid The player's UUID as string
     * @return The number of achievements completed by this player
     */
    public int getPlayerAchievementCount(String playerUuid) {
        String sql = "SELECT COUNT(*) FROM " + tableName + "_achievements WHERE player_uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    
                    if (debugEnabled) {
                        debugLog("Retrieved achievement count for player " + playerUuid + ": " + count);
                    }
                    
                    return count;
                } else {
                    return 0;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error getting achievement count for player: {}", playerUuid, e);
            exceptionHandler.handleDatabaseException("Get player achievement count", e, "Player: " + playerUuid);
            return 0;
        }
    }
    
    /**
     * Tests the database connection to ensure it's working
     * 
     * @return true if connection is working, false otherwise
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(5);
        } catch (SQLException e) {
            if (debugEnabled) {
                debugLog("Database connection test failed: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * isDatabaseAvailable
     *
     * Performs a lightweight health check to determine if the database is available.
     * This method is used by DatabaseMonitor to check connectivity status.
     * 
     * @return boolean true if the database is available and responsive, false otherwise
     */
    public boolean isDatabaseAvailable() {
        try (Connection conn = getConnection()) {
            if (conn == null || !conn.isValid(5)) {
                return false;
            }
            
            // Perform a lightweight query to verify database responsiveness
            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) == 1;
                }
            }
        } catch (SQLException e) {
            if (debugEnabled) {
                debugLog("Database availability check failed: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * retryConnection
     *
     * Attempts to restore the database connection by closing and reinitializing
     * the HikariCP data source. This method is used by DatabaseMonitor for
     * automatic connection recovery.
     * 
     * @return boolean true if the connection was successfully restored, false otherwise
     */
    public boolean retryConnection() {
        try {
            if (debugEnabled) {
                debugLog("Attempting to restore database connection...");
            }
            
            // Close existing connections in the pool
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                if (debugEnabled) {
                    debugLog("Closed existing connection pool");
                }
            }
            
            // Small delay to allow connection cleanup
            Thread.sleep(1000);
            
            // The dataSource is final, so we cannot reinitialize it here
            // Instead, we'll test if the existing pool can recover
            try (Connection conn = dataSource.getConnection()) {
                if (conn != null && conn.isValid(5)) {
                    // Test with a simple query
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
                        try (ResultSet rs = stmt.executeQuery()) {
                            boolean success = rs.next() && rs.getInt(1) == 1;
                            if (success && debugEnabled) {
                                debugLog("Database connection successfully restored");
                            }
                            return success;
                        }
                    }
                }
            }
            
            return false;
            
        } catch (SQLException e) {
            if (debugEnabled) {
                debugLog("Failed to restore database connection: " + e.getMessage());
            }
            exceptionHandler.handleDatabaseException("Database connection retry", e, "retryConnection method");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (debugEnabled) {
                debugLog("Database connection retry interrupted");
            }
            return false;
        }
    }

    /**
     * addPlayerToWhitelistByUsername
     * 
     * Adds a player to the whitelist using only their username (for admin commands).
     * This is used when UUID is not available, such as from Discord admin commands.
     * 
     * @param username The Minecraft username
     * @param adminDiscordId The Discord ID of the admin performing the action (optional)
     * @return CompletableFuture<Boolean> true if successful
     */
    public CompletableFuture<Boolean> addPlayerToWhitelistByUsername(String username, String adminDiscordId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format(
                    "INSERT INTO `%s` (`user`, `discord_id`, `added_by`) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE `user` = VALUES(`user`), `added_by` = VALUES(`added_by`)",
                    tableName);

            try (Connection conn = getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, null);
                stmt.setString(3, adminDiscordId);

                int result = stmt.executeUpdate();
                debugLog("Added player " + username + " to whitelist by admin " + adminDiscordId);
                return result > 0;
            } catch (SQLException e) {
                logger.error("Error adding player to whitelist by username", e);
                return false;
            }
        });
    }
    
    /**
     * removePlayerFromWhitelistByUsername
     * 
     * Removes a player from the whitelist using only their username (for admin commands).
     * This is used when UUID is not available, such as from Discord admin commands.
     * 
     * @param username The Minecraft username
     * @param adminDiscordId The Discord ID of the admin performing the action (optional)
     * @return CompletableFuture<Boolean> true if successful
     */
    public CompletableFuture<Boolean> removePlayerFromWhitelistByUsername(String username, String adminDiscordId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format("DELETE FROM `%s` WHERE `user` = ?", tableName);
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                
                int result = stmt.executeUpdate();
                if (result > 0) {
                    debugLog("Removed player " + username + " from whitelist (admin: " + adminDiscordId + ")");
                    return true;
                } else {
                    debugLog("Player " + username + " was not found in whitelist");
                    return false;
                }
            } catch (SQLException e) {
                logger.error("Error removing player {} from whitelist", username, e);
                exceptionHandler.handleDatabaseException("Remove player from whitelist", e, "Username: " + username);
                return false;
            }
        });
    }

    /**
     * getPlayerVerificationState
     * 
     * Gets the verification state of a player.
     * 
     * @param playerUuid The player's UUID
     * @return Optional containing verification state if found
     */
    public Optional<String> getPlayerVerificationState(String playerUuid) {
        try (Connection conn = getConnection()) {
            String sql = String.format("SELECT verification_state FROM `%s` WHERE `UUID` = ?", tableName);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("verification_state"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting verification state for player {}", playerUuid, e);
            exceptionHandler.handleDatabaseException("Get verification state", e, "UUID: " + playerUuid);
        }
        return Optional.empty();
    }
    
    /**
     * getPlayerNameFromUuid
     * 
     * Gets the player name from their UUID.
     * 
     * @param playerUuid The player's UUID
     * @return Optional containing player name if found
     */
    public Optional<String> getPlayerNameFromUuid(String playerUuid) {
        try (Connection conn = getConnection()) {
            String sql = String.format("SELECT `user` FROM `%s` WHERE `UUID` = ?", tableName);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("user"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player name for UUID {}", playerUuid, e);
            exceptionHandler.handleDatabaseException("Get player name", e, "UUID: " + playerUuid);
        }
        return Optional.empty();
    }
    
    /**
     * logRewardProcessing
     * 
     * Logs reward processing for a player rank promotion.
     * 
     * @param playerUuid The player's UUID
     * @param fromMainRank The previous main rank
     * @param fromSubRank The previous sub rank  
     * @param toMainRank The new main rank
     * @param toSubRank The new sub rank
     */
    public void logRewardProcessing(String playerUuid, int fromMainRank, int fromSubRank, int toMainRank, int toSubRank) {
        String sql = """
                INSERT INTO xp_events (uuid, event_type, event_source, xp_gained, metadata)
                VALUES (?, 'REWARD_PROCESSING', 'Rank Promotion', 0, ?)
                """;
        
        String metadata = String.format("Rank promotion from %d.%d to %d.%d", fromMainRank, fromSubRank, toMainRank, toSubRank);
        executeUpdate(sql, new Object[] { playerUuid, metadata });
        debugLog("Logged reward processing for " + playerUuid + ": " + metadata);
    }

    /**
     * getPlayerDiscordId
     * 
     * Gets the Discord ID associated with a player's UUID.
     * 
     * @param playerUuid The player's UUID
     * @return Optional containing Discord ID if found
     */
    public Optional<String> getPlayerDiscordId(String playerUuid) {
        try (Connection conn = getConnection()) {
            String sql = String.format("SELECT `discord_id` FROM `%s` WHERE `UUID` = ?", tableName);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("discord_id"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting Discord ID for player {}", playerUuid, e);
            exceptionHandler.handleDatabaseException("Get Discord ID", e, "UUID: " + playerUuid);
        }
        return Optional.empty();
    }

    /**
     * listWhitelistedPlayers
     * 
     * Gets a list of all whitelisted players, optionally filtered by search term.
     * 
     * @param searchTerm Optional search term to filter players (null for all)
     * @param limit Maximum number of results to return
     * @return List of whitelisted player usernames
     */
    public List<String> listWhitelistedPlayers(String searchTerm, int limit) {
        List<String> players = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql;
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                sql = String.format("SELECT `user` FROM `%s` WHERE `user` LIKE ? ORDER BY `user` LIMIT ?", tableName);
            } else {
                sql = String.format("SELECT `user` FROM `%s` ORDER BY `user` LIMIT ?", tableName);
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                    stmt.setString(1, "%" + searchTerm.trim() + "%");
                    stmt.setInt(2, limit);
                } else {
                    stmt.setInt(1, limit);
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String username = rs.getString("user");
                        if (username != null) {
                            players.add(username);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error listing whitelisted players with search term: {}", searchTerm, e);
            exceptionHandler.handleDatabaseException("List whitelisted players", e, "Search: " + searchTerm);
        }
        return players;
    }
}
