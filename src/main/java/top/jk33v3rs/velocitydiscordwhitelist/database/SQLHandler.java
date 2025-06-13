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

        this.tableName = dbConfig.getOrDefault("table", "whitelist").toString();

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
     */
    private int executeUpdate(String sql, Object[] params) {
        return exceptionHandler.executeWithHandling("executing SQL update: " + sql, () -> {
            try (Connection conn = getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
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
    private <T> Optional<T> executeQueryForSingleResult(String sql, Object[] params, ResultSetMapper<T> mapper) {
        return exceptionHandler.executeWithHandling("executing SQL query: " + sql, () -> {
            try (Connection conn = getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
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
    private <T> List<T> executeQueryForMultipleResults(String sql, Object[] params, ResultSetMapper<T> mapper) {
        return exceptionHandler.executeWithHandling("executing SQL query for multiple results: " + sql, () -> {
            List<T> results = new ArrayList<>();
            try (Connection conn = getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
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
     * Gets player verification state
     * 
     * @param playerUuid The player's UUID
     * @return Optional<String> The verification state if found
     */
    public Optional<String> getPlayerVerificationState(String playerUuid) {
        String sql = String.format("SELECT `verification_state` FROM `%s` WHERE `UUID` = ?", tableName);
        return executeQueryForSingleResult(sql, new Object[] { playerUuid }, rs -> rs.getString("verification_state"));
    }

    /**
     * updateVerificationState
     *
     * Updates a player's verification state in the whitelist table.
     * This method is used by the purgatory system to track player verification
     * progress.
     *
     * @param playerUuid        The player's UUID as string
     * @param verificationState The new verification state ('UNVERIFIED',
     *                          'PURGATORY', 'VERIFIED')
     * @return boolean true if the update was successful, false otherwise
     */
    public boolean updateVerificationState(String playerUuid, String verificationState) {
        String sql = String.format("UPDATE `%s` SET `verification_state` = ? WHERE `UUID` = ?", tableName);

        return exceptionHandler.executeWithHandling("updating verification state for " + playerUuid, () -> {
            int rowsAffected = executeUpdate(sql, new Object[] { verificationState, playerUuid });
            debugLog("Updated verification state for UUID " + playerUuid + " to " + verificationState);
            return rowsAffected > 0;
        }, false);
    }

    /**
     * linkDiscordAccount
     *
     * Links a Discord account to a player's whitelist entry and creates an entry in
     * the discord_data table.
     * This method is used by the purgatory system to associate Discord accounts
     * with Minecraft accounts.
     *
     * @param playerUuid      The player's UUID as string
     * @param discordUserId   The Discord user ID
     * @param discordUsername The Discord username
     * @return boolean true if the linking was successful, false otherwise
     */
    public boolean linkDiscordAccount(String playerUuid, Long discordUserId, String discordUsername) {
        try (Connection conn = getConnection()) {
            // Start transaction
            conn.setAutoCommit(false);

            try {
                // Update main whitelist table with Discord info
                String updateWhitelistSql = String.format(
                        "UPDATE `%s` SET `discord_id` = ?, `discord_name` = ? WHERE `UUID` = ?",
                        tableName);
                try (PreparedStatement stmt = conn.prepareStatement(updateWhitelistSql)) {
                    stmt.setLong(1, discordUserId);
                    stmt.setString(2, discordUsername);
                    stmt.setString(3, playerUuid);
                    stmt.executeUpdate();
                }

                // Insert or update discord_data table
                String upsertDiscordDataSql = """
                            INSERT INTO discord_data (uuid, discord_id, discord_username, linked_at)
                            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                            ON DUPLICATE KEY UPDATE
                                discord_username = VALUES(discord_username),
                                linked_at = CURRENT_TIMESTAMP
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(upsertDiscordDataSql)) {
                    stmt.setString(1, playerUuid);
                    stmt.setString(2, discordUserId.toString());
                    stmt.setString(3, discordUsername);
                    stmt.executeUpdate();
                }

                // Commit transaction
                conn.commit();
                debugLog("Successfully linked Discord account " + discordUserId + " to UUID " + playerUuid);
                return true;

            } catch (SQLException e) {
                // Rollback transaction on error
                conn.rollback();
                logger.error("Error linking Discord account for UUID " + playerUuid, e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Error getting database connection for Discord linking", e);
            return false;
        }
    }

    /**
     * Gets player Discord ID
     * 
     * @param playerUuid The player's UUID
     * @return Optional<Long> The Discord ID if found
     */
    public Optional<Long> getPlayerDiscordId(String playerUuid) {
        String sql = String.format("SELECT `discord_id` FROM `%s` WHERE `UUID` = ?", tableName);

        return executeQueryForSingleResult(sql, new Object[] { playerUuid }, rs -> {
            long discordId = rs.getLong("discord_id");
            return rs.wasNull() ? null : discordId;
        }).filter(id -> id != null);
    }

    /**
     * Gets player name from UUID
     * 
     * @param playerUuid The player's UUID
     * @return Optional<String> The player name if found
     */
    public Optional<String> getPlayerNameFromUuid(String playerUuid) {
        String sql = String.format("SELECT `user` FROM `%s` WHERE `UUID` = ?", tableName);
        return executeQueryForSingleResult(sql, new Object[] { playerUuid }, rs -> rs.getString("user"));
    }

    /**
     * Logs reward processing for a player
     * 
     * @param playerUuid    The player's UUID
     * @param mainRank      The main rank
     * @param subRank       The sub rank
     * @param economyAmount The economy reward amount
     * @param commandCount  The number of commands executed
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
     * getXPEventCount
     * 
     * Gets the count of XP events for a player within a time range.
     * Used for rate limiting to prevent XP farming.
     * 
     * @param playerUuid  The player's UUID
     * @param eventType   The type of XP event
     * @param eventSource The source of the XP event
     * @param startTime   The start of the time range
     * @param endTime     The end of the time range
     * @return The number of XP events in the specified time range
     * @throws SQLException if database operation fails
     */
    public int getXPEventCount(String playerUuid, String eventType, String eventSource,
            Instant startTime, Instant endTime) throws SQLException {
        String sql = """
                    SELECT COUNT(*) as event_count
                    FROM xp_events
                    WHERE uuid = ? AND event_type = ? AND event_source = ?
                    AND timestamp BETWEEN ? AND ?
                """;

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setString(2, eventType);
            stmt.setString(3, eventSource);
            stmt.setTimestamp(4, Timestamp.from(startTime));
            stmt.setTimestamp(5, Timestamp.from(endTime));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("event_count");
                }
            }
        }

        return 0;
    }

    /**
     * recordXPEvent
     * 
     * Records an XP event to the database for tracking and analytics.
     * This method stores all XP gains with metadata for debugging and analysis.
     * 
     * @param playerUuid  The player's UUID
     * @param eventType   The type of XP event (e.g., "ADVANCEMENT", "PLAYTIME")
     * @param eventSource The specific source (e.g., advancement name,
     *                    "playtime_tick")
     * @param xpGained    The amount of XP gained
     * @param timestamp   The timestamp when the event occurred
     * @param serverName  The name of the server where the event occurred
     * @param metadata    Additional metadata about the event
     * @throws SQLException if database operation fails
     */
    public void recordXPEvent(String playerUuid, String eventType, String eventSource,
            int xpGained, Instant timestamp, String serverName, String metadata) throws SQLException {
        String sql = """
                    INSERT INTO xp_events (uuid, event_type, event_source, xp_gained, timestamp, server_name, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setString(2, eventType);
            stmt.setString(3, eventSource);
            stmt.setInt(4, xpGained);
            stmt.setTimestamp(5, Timestamp.from(timestamp));
            stmt.setString(6, serverName);
            stmt.setString(7, metadata);
            stmt.executeUpdate();
        }
    }

    /**
     * getRecentXPEvents
     * 
     * Gets recent XP events for a player, sorted by most recent first.
     * 
     * @param playerUuid The player's UUID
     * @param limit      Maximum number of events to return
     * @return List of XPEvent objects
     * @throws SQLException if database operation fails
     */
    public java.util.List<top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent> getRecentXPEvents(String playerUuid,
            int limit) throws SQLException {
        String sql = """
                    SELECT uuid, event_type, event_source, xp_gained, timestamp, server_name, metadata
                    FROM xp_events
                    WHERE uuid = ?
                    ORDER BY timestamp DESC
                    LIMIT ?
                """;

        return executeQueryForMultipleResults(sql, new Object[] { playerUuid, limit }, rs -> {
            return new top.jk33v3rs.velocitydiscordwhitelist.models.XPEvent(
                    rs.getString("uuid"),
                    rs.getString("event_type"),
                    rs.getString("event_source"),
                    rs.getInt("xp_gained"),
                    rs.getTimestamp("timestamp").toInstant(),
                    rs.getString("server_name"),
                    rs.getString("metadata"));
        });
    }

    /**
     * getPlayerXPSince
     * 
     * Gets total XP gained by a player since a specific timestamp.
     * 
     * @param playerUuid The player's UUID
     * @param since      The timestamp to calculate XP from
     * @return Total XP gained since the specified time
     * @throws SQLException if database operation fails
     */
    public int getPlayerXPSince(String playerUuid, Instant since) throws SQLException {
        String sql = """
                    SELECT COALESCE(SUM(xp_gained), 0) as total_xp
                    FROM xp_events
                    WHERE uuid = ? AND timestamp >= ?
                """;

        return executeQueryForSingleResult(sql, new Object[] { playerUuid, Timestamp.from(since) },
                rs -> rs.getInt("total_xp")).orElse(0);
    }

    /**
     * Gets player XP breakdown by source since a specific date
     * 
     * @param playerUuid The player's UUID
     * @param since      The date to start from
     * @return Map of source names to XP amounts
     */
    public java.util.Map<String, Integer> getPlayerXPBySource(String playerUuid, Instant since) {
        String sql = """
                    SELECT event_source, COALESCE(SUM(xp_gained), 0) as source_xp
                    FROM xp_events
                    WHERE uuid = ? AND event_type = 'XP_GAIN'
                    AND timestamp >= ?
                    GROUP BY event_source
                    ORDER BY source_xp DESC
                """;

        java.util.Map<String, Integer> breakdown = new java.util.LinkedHashMap<>();
        List<java.util.Map<String, Object>> results = executeQueryForMultipleResults(sql,
                new Object[] { playerUuid, Timestamp.from(since) },
                rs -> {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("source", rs.getString("event_source"));
                    row.put("xp", rs.getInt("source_xp"));
                    return row;
                });

        for (java.util.Map<String, Object> result : results) {
            breakdown.put((String) result.get("source"), (Integer) result.get("xp"));
        }

        return breakdown;
    }

    /**
     * Gets daily XP breakdown for a player over the specified number of days
     * 
     * @param playerUuid The player's UUID
     * @param days       Number of days to look back
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
     * Tests the database connection to ensure it's working
     * 
     * @return true if connection is working, false otherwise
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(5);
        } catch (SQLException e) {
            exceptionHandler.handleDatabaseException("connection test", e, "Failed to test database connection");
            return false;
        }
    }
}
