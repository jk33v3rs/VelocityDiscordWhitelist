// This file is part of VelocityDiscordWhitelist.

package top.jk33v3rs.velocitydiscordwhitelist.modules;

import org.slf4j.Logger;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Handler for database backup and restoration operations.
 */
public class DataBackupHandler {
    
    private final Logger logger;
    private final Connection connection;
    private final Path backupDirectory;
    private final Gson gson;
    
    // Configuration settings
    private boolean compressionEnabled;
    private int maxBackupFiles;
    private String dateTimeFormat;
    
    /**
     * Constructor for DataBackupHandler.
     */
    public DataBackupHandler(Logger logger, Connection connection, Path dataDirectory) {
        this.logger = logger;
        this.connection = connection;
        this.backupDirectory = dataDirectory.resolve("backups");
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        
        // Default configuration
        this.compressionEnabled = true;
        this.maxBackupFiles = 10;
        this.dateTimeFormat = "yyyy-MM-dd_HH-mm-ss";
        
        // Create backup directory if it doesn't exist
        try {
            Files.createDirectories(backupDirectory);
        } catch (IOException e) {
            logger.error("Failed to create backup directory", e);
        }
        
        logger.info("DataBackupHandler initialized successfully");
    }
    
    /**
     * Creates a complete backup of all whitelist data.
     * Exports all tables and metadata to a structured backup file.
     *
     * @param backupName Optional custom name for the backup (null for auto-generated).
     * @return CompletableFuture containing the backup file path on success, null on failure.
     */
    public CompletableFuture<Path> createBackup(String backupName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(dateTimeFormat));
                String fileName = (backupName != null) ? 
                    String.format("%s_%s", backupName, timestamp) : 
                    String.format("whitelist_backup_%s", timestamp);
                
                if (compressionEnabled) {
                    fileName += ".json.gz";
                } else {
                    fileName += ".json";
                }
                
                Path backupFile = backupDirectory.resolve(fileName);
                
                logger.info("Creating backup: {}", fileName);
                
                // Create backup data structure
                BackupData backupData = new BackupData();
                backupData.metadata = createBackupMetadata();
                backupData.tables = new HashMap<>();
                
                // Export all tables
                exportWhitelistTable(backupData);
                exportUserDataTable(backupData);
                exportRankDataTable(backupData);
                exportDiscordDataTable(backupData);
                exportVerificationDataTable(backupData);
                
                // Write backup file
                writeBackupFile(backupFile, backupData);
                
                // Clean up old backups if needed
                cleanupOldBackups();
                
                logger.info("Backup created successfully: {}", backupFile.getFileName());
                return backupFile;
                
            } catch (Exception e) {
                logger.error("Failed to create backup", e);
                return null;
            }
        });
    }
    
    /**
     * Restores data from a backup file.
     * Imports all data and recreates tables as necessary.
     *
     * @param backupFile The path to the backup file to restore.
     * @param overwriteExisting Whether to overwrite existing data or merge.
     * @return CompletableFuture containing true on success, false on failure.
     */
    public CompletableFuture<Boolean> restoreBackup(Path backupFile, boolean overwriteExisting) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting backup restoration from: {}", backupFile.getFileName());
                
                if (!Files.exists(backupFile)) {
                    logger.error("Backup file does not exist: {}", backupFile);
                    return false;
                }
                
                // Read backup data
                BackupData backupData = readBackupFile(backupFile);
                if (backupData == null) {
                    logger.error("Failed to read backup data");
                    return false;
                }
                
                // Validate backup compatibility
                if (!validateBackupCompatibility(backupData.metadata)) {
                    logger.error("Backup is not compatible with current version");
                    return false;
                }
                
                // Create backup of current data before restoration
                if (overwriteExisting) {
                    logger.info("Creating safety backup before restoration");
                    createBackup("pre_restore_safety_backup").join();
                }
                
                // Begin transaction for atomic restoration
                connection.setAutoCommit(false);
                
                try {
                    // Restore tables
                    restoreWhitelistTable(backupData, overwriteExisting);
                    restoreUserDataTable(backupData, overwriteExisting);
                    restoreRankDataTable(backupData, overwriteExisting);
                    restoreDiscordDataTable(backupData, overwriteExisting);
                    restoreVerificationDataTable(backupData, overwriteExisting);
                    
                    // Commit transaction
                    connection.commit();
                    connection.setAutoCommit(true);
                    
                    logger.info("Backup restoration completed successfully");
                    return true;
                    
                } catch (Exception e) {
                    // Rollback on error
                    connection.rollback();
                    connection.setAutoCommit(true);
                    throw e;
                }
                
            } catch (Exception e) {
                logger.error("Failed to restore backup", e);
                return false;
            }
        });
    }
    
    /**
     * Exports whitelist data to a portable format.
     * Creates a formatted export suitable for migration or analysis.
     *
     * @param format The export format ("json", "csv", "sql").
     * @param includeTimestamps Whether to include timestamp data.
     * @return CompletableFuture containing the export file path on success, null on failure.
     */
    public CompletableFuture<Path> exportData(String format, boolean includeTimestamps) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(dateTimeFormat));
                String fileName = String.format("whitelist_export_%s.%s", timestamp, format.toLowerCase());
                Path exportFile = backupDirectory.resolve(fileName);
                
                logger.info("Exporting data to format: {}", format);
                
                switch (format.toLowerCase()) {
                    case "json":
                        return exportToJson(exportFile, includeTimestamps);
                    case "csv":
                        return exportToCsv(exportFile, includeTimestamps);
                    case "sql":
                        return exportToSql(exportFile, includeTimestamps);
                    default:
                        logger.error("Unsupported export format: {}", format);
                        return null;
                }
                
            } catch (Exception e) {
                logger.error("Failed to export data", e);
                return null;
            }
        });
    }
    
    /**
     * Lists all available backup files.
     * Returns information about existing backups including metadata.
     *
     * @return List of backup file information.
     */
    public List<BackupInfo> listBackups() {
        List<BackupInfo> backups = new ArrayList<>();
        
        try {
            if (!Files.exists(backupDirectory)) {
                return backups;
            }
            
            Files.list(backupDirectory)
                    .filter(path -> path.getFileName().toString().endsWith(".json") || 
                                  path.getFileName().toString().endsWith(".json.gz"))
                    .forEach(path -> {
                        try {
                            BackupInfo info = getBackupInfo(path);
                            if (info != null) {
                                backups.add(info);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to read backup info for: {}", path.getFileName(), e);
                        }
                    });
            
            // Sort by creation date (newest first)
            backups.sort((a, b) -> b.createdAt.compareTo(a.createdAt));
            
        } catch (IOException e) {
            logger.error("Failed to list backup files", e);
        }
        
        return backups;
    }
    
    /**
     * Validates the integrity of a backup file.
     * Checks file structure and data consistency.
     *
     * @param backupFile The backup file to validate.
     * @return True if backup is valid, false otherwise.
     */
    public boolean validateBackup(Path backupFile) {
        try {
            logger.info("Validating backup file: {}", backupFile.getFileName());
            
            if (!Files.exists(backupFile)) {
                logger.error("Backup file does not exist");
                return false;
            }
            
            BackupData backupData = readBackupFile(backupFile);
            if (backupData == null) {
                logger.error("Failed to read backup data");
                return false;
            }
            
            // Validate metadata
            if (backupData.metadata == null) {
                logger.error("Backup metadata is missing");
                return false;
            }
            
            // Validate required fields
            if (backupData.metadata.version == null || 
                backupData.metadata.createdAt == null ||
                backupData.metadata.pluginVersion == null) {
                logger.error("Backup metadata is incomplete");
                return false;
            }
            
            // Validate table structure
            if (backupData.tables == null) {
                logger.error("Backup table data is missing");
                return false;
            }
            
            // Check for required tables
            String[] requiredTables = {"whitelist", "user_data"};
            for (String table : requiredTables) {
                if (!backupData.tables.containsKey(table)) {
                    logger.warn("Backup missing table: {}", table);
                }
            }
            
            logger.info("Backup validation completed successfully");
            return true;
            
        } catch (Exception e) {
            logger.error("Backup validation failed", e);
            return false;
        }
    }
    
    /**
     * Configures backup handler settings.
     * Updates operational parameters for backup operations.
     *
     * @param compressionEnabled Whether to enable backup compression.
     * @param includeMetadata Whether to include metadata in backups.
     * @param maxBackupFiles Maximum number of backup files to retain.
     */
    /**
     * Configures backup handler settings.
     * Updates operational parameters for backup operations.
     *
     * @param compressionEnabled Whether to enable backup compression.
     * @param includeMetadata (Unused) Whether to include metadata in backups.
     * @param maxBackupFiles Maximum number of backup files to retain.
     */
    public void configure(boolean compressionEnabled, boolean includeMetadata, int maxBackupFiles) {
        this.compressionEnabled = compressionEnabled;
        this.maxBackupFiles = maxBackupFiles;

        logger.info("Backup handler configured: compression={}, metadata={}, maxFiles={}",
                   compressionEnabled, includeMetadata, maxBackupFiles);
    }
    
    /**
     * Creates backup metadata with current system information.
     * Includes version, timestamp, and configuration details.
     *
     * @return BackupMetadata object with current system information.
     */
    private BackupMetadata createBackupMetadata() {
        BackupMetadata metadata = new BackupMetadata();
        metadata.version = "1.0";
        metadata.createdAt = LocalDateTime.now();
        metadata.pluginVersion = "1.3.2"; // Should be retrieved from plugin info
        metadata.databaseType = getDatabaseType();
        metadata.recordCounts = getTableRecordCounts();
        
        return metadata;
    }
    
    /**
     * Exports the main whitelist table data.
     * Retrieves all whitelist entries and associated data.
     *
     * @param backupData The backup data structure to populate.
     */
    private void exportWhitelistTable(BackupData backupData) throws SQLException {
        List<Map<String, Object>> records = new ArrayList<>();
        
        String query = "SELECT * FROM whitelist ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> record = new HashMap<>();
                ResultSetMetaData metaData = rs.getMetaData();
                
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    record.put(columnName, value);
                }
                
                records.add(record);
            }
        }
        
        backupData.tables.put("whitelist", records);
        logger.debug("Exported {} whitelist records", records.size());
    }
    
    /**
     * Exports user data table.
     * Retrieves user profiles and verification information.
     *
     * @param backupData The backup data structure to populate.
     */
    private void exportUserDataTable(BackupData backupData) throws SQLException {
        List<Map<String, Object>> records = new ArrayList<>();
        
        // Check if table exists first
        if (!tableExists("user_data")) {
            logger.debug("User data table does not exist, skipping export");
            return;
        }
        
        String query = "SELECT * FROM user_data ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> record = new HashMap<>();
                ResultSetMetaData metaData = rs.getMetaData();
                
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    record.put(columnName, value);
                }
                
                records.add(record);
            }
        }
        
        backupData.tables.put("user_data", records);
        logger.debug("Exported {} user data records", records.size());
    }
    
    /**
     * Exports rank data table.
     * Retrieves rank assignments and progression data.
     *
     * @param backupData The backup data structure to populate.
     */
    private void exportRankDataTable(BackupData backupData) throws SQLException {
        List<Map<String, Object>> records = new ArrayList<>();
        
        if (!tableExists("rank_data")) {
            logger.debug("Rank data table does not exist, skipping export");
            return;
        }
        
        String query = "SELECT * FROM rank_data ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> record = new HashMap<>();
                ResultSetMetaData metaData = rs.getMetaData();
                
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    record.put(columnName, value);
                }
                
                records.add(record);
            }
        }
        
        backupData.tables.put("rank_data", records);
        logger.debug("Exported {} rank data records", records.size());
    }
    
    /**
     * Exports Discord integration data.
     * Retrieves Discord user linkages and server data.
     *
     * @param backupData The backup data structure to populate.
     */
    private void exportDiscordDataTable(BackupData backupData) throws SQLException {
        List<Map<String, Object>> records = new ArrayList<>();
        
        if (!tableExists("discord_data")) {
            logger.debug("Discord data table does not exist, skipping export");
            return;
        }
        
        String query = "SELECT * FROM discord_data ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> record = new HashMap<>();
                ResultSetMetaData metaData = rs.getMetaData();
                
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    record.put(columnName, value);
                }
                
                records.add(record);
            }
        }
        
        backupData.tables.put("discord_data", records);
        logger.debug("Exported {} Discord data records", records.size());
    }
    
    /**
     * Exports verification data table.
     * Retrieves verification history and status information.
     *
     * @param backupData The backup data structure to populate.
     */
    private void exportVerificationDataTable(BackupData backupData) throws SQLException {
        List<Map<String, Object>> records = new ArrayList<>();
        
        if (!tableExists("verification_data")) {
            logger.debug("Verification data table does not exist, skipping export");
            return;
        }
        
        String query = "SELECT * FROM verification_data ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> record = new HashMap<>();
                ResultSetMetaData metaData = rs.getMetaData();
                
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    record.put(columnName, value);
                }
                
                records.add(record);
            }
        }
        
        backupData.tables.put("verification_data", records);
        logger.debug("Exported {} verification data records", records.size());
    }
    
    /**
     * Writes backup data to file with optional compression.
     * Handles JSON serialization and optional GZIP compression.
     *
     * @param backupFile The destination file path.
     * @param backupData The backup data to write.
     */
    private void writeBackupFile(Path backupFile, BackupData backupData) throws IOException {
        String jsonData = gson.toJson(backupData);
        
        if (compressionEnabled) {
            try (FileOutputStream fos = new FileOutputStream(backupFile.toFile());
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 OutputStreamWriter writer = new OutputStreamWriter(gzos)) {
                
                writer.write(jsonData);
            }
        } else {
            Files.write(backupFile, jsonData.getBytes());
        }
    }
    
    /**
     * Reads backup data from file with automatic decompression.
     * Handles JSON deserialization and optional GZIP decompression.
     *
     * @param backupFile The backup file to read.
     * @return BackupData object or null if reading failed.
     */
    private BackupData readBackupFile(Path backupFile) {
        try {
            String jsonData;
            
            if (backupFile.toString().endsWith(".gz")) {
                try (FileInputStream fis = new FileInputStream(backupFile.toFile());
                     GZIPInputStream gzis = new GZIPInputStream(fis);
                     InputStreamReader reader = new InputStreamReader(gzis)) {
                    
                    StringBuilder sb = new StringBuilder();
                    char[] buffer = new char[1024];
                    int length;
                    while ((length = reader.read(buffer)) > 0) {
                        sb.append(buffer, 0, length);
                    }
                    jsonData = sb.toString();
                }
            } else {
                jsonData = Files.readString(backupFile);
            }
            
            return gson.fromJson(jsonData, BackupData.class);
            
        } catch (Exception e) {
            logger.error("Failed to read backup file: {}", backupFile, e);
            return null;
        }
    }
    
    /**
     * Validates backup compatibility with current system.
     * Checks version compatibility and data structure.
     *
     * @param metadata The backup metadata to validate.
     * @return True if backup is compatible, false otherwise.
     */
    private boolean validateBackupCompatibility(BackupMetadata metadata) {
        // Version compatibility check
        if (!isVersionCompatible(metadata.version)) {
            logger.error("Backup version {} is not compatible with current version", metadata.version);
            return false;
        }
        
        // Plugin version check (warning only)
        if (!metadata.pluginVersion.equals("1.3.2")) {
            logger.warn("Backup was created with plugin version {}, current version is 1.3.2", 
                       metadata.pluginVersion);
        }
        
        return true;
    }
    
    /**
     * Safely casts table data from backup to typed list.
     * Handles type safety for table data extraction.
     *
     * @param backupData The backup data containing table information.
     * @param tableName The name of the table to extract.
     * @return List of records or null if not found/invalid.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getTableRecords(BackupData backupData, String tableName) {
        Object tableData = backupData.tables.get(tableName);
        if (tableData instanceof List<?>) {
            try {
                return (List<Map<String, Object>>) tableData;
            } catch (ClassCastException e) {
                logger.warn("Invalid table data format for table: {}", tableName);
                return null;
            }
        }
        return null;
    }
    
    /**
     * Restores whitelist table from backup data.
     * Recreates table structure and imports data.
     *
     * @param backupData The backup data containing table information.
     * @param overwriteExisting Whether to overwrite existing data.
     */
    private void restoreWhitelistTable(BackupData backupData, boolean overwriteExisting) throws SQLException {
        List<Map<String, Object>> records = getTableRecords(backupData, "whitelist");
        if (records == null || records.isEmpty()) {
            logger.warn("No whitelist data found in backup");
            return;
        }
        
        if (overwriteExisting) {
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM whitelist")) {
                stmt.executeUpdate();
                logger.info("Cleared existing whitelist data");
            }
        }
        
        // Insert records
        String insertQuery = "INSERT INTO whitelist (uuid, username, discord_id, verified_at, rank_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
            
            for (Map<String, Object> record : records) {
                stmt.setString(1, (String) record.get("uuid"));
                stmt.setString(2, (String) record.get("username"));
                stmt.setString(3, (String) record.get("discord_id"));
                stmt.setTimestamp(4, (Timestamp) record.get("verified_at"));
                stmt.setInt(5, (Integer) record.getOrDefault("rank_id", 0));
                
                stmt.addBatch();
            }
            
            stmt.executeBatch();
        }
        
        logger.info("Restored {} whitelist records", records.size());
    }
    
    /**
     * Restores user data table from backup data.
     * Recreates user profiles and verification information.
     *
     * @param backupData The backup data containing table information.
     * @param overwriteExisting Whether to overwrite existing data.
     */
    private void restoreUserDataTable(BackupData backupData, boolean overwriteExisting) throws SQLException {
        List<Map<String, Object>> records = getTableRecords(backupData, "user_data");
        if (records == null || records.isEmpty()) {
            logger.debug("No user data found in backup, skipping restoration");
            return;
        }
        
        if (overwriteExisting && tableExists("user_data")) {
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM user_data")) {
                stmt.executeUpdate();
                logger.info("Cleared existing user data");
            }
        }
        
        // Create table if it doesn't exist
        createUserDataTableIfNotExists();
        
        // Insert records
        String insertQuery = "INSERT INTO user_data (uuid, username, first_join, last_seen, playtime_minutes, xp_points) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
            
            for (Map<String, Object> record : records) {
                stmt.setString(1, (String) record.get("uuid"));
                stmt.setString(2, (String) record.get("username"));
                stmt.setTimestamp(3, (Timestamp) record.get("first_join"));
                stmt.setTimestamp(4, (Timestamp) record.get("last_seen"));
                stmt.setInt(5, (Integer) record.getOrDefault("playtime_minutes", 0));
                stmt.setInt(6, (Integer) record.getOrDefault("xp_points", 0));
                
                stmt.addBatch();
            }
            
            stmt.executeBatch();
        }
        
        logger.info("Restored {} user data records", records.size());
    }
    
    /**
     * Restores rank data table from backup data.
     * Recreates rank assignments and progression information.
     *
     * @param backupData The backup data containing table information.
     * @param overwriteExisting Whether to overwrite existing data.
     */
    private void restoreRankDataTable(BackupData backupData, boolean overwriteExisting) throws SQLException {
        List<Map<String, Object>> records = getTableRecords(backupData, "rank_data");
        if (records == null || records.isEmpty()) {
            logger.debug("No rank data found in backup, skipping restoration");
            return;
        }
        
        if (overwriteExisting && tableExists("rank_data")) {
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM rank_data")) {
                stmt.executeUpdate();
                logger.info("Cleared existing rank data");
            }
        }
        
        // Create table if it doesn't exist
        createRankDataTableIfNotExists();
        
        // Insert records
        String insertQuery = "INSERT INTO rank_data (uuid, rank_id, subrank_id, rank_assigned_at, progression_data) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
            
            for (Map<String, Object> record : records) {
                stmt.setString(1, (String) record.get("uuid"));
                stmt.setInt(2, (Integer) record.get("rank_id"));
                stmt.setInt(3, (Integer) record.getOrDefault("subrank_id", 0));
                stmt.setTimestamp(4, (Timestamp) record.get("rank_assigned_at"));
                stmt.setString(5, (String) record.get("progression_data"));
                
                stmt.addBatch();
            }
            
            stmt.executeBatch();
        }
        
        logger.info("Restored {} rank data records", records.size());
    }
    
    /**
     * Restores Discord data table from backup data.
     * Recreates Discord user linkages and server information.
     *
     * @param backupData The backup data containing table information.
     * @param overwriteExisting Whether to overwrite existing data.
     */
    private void restoreDiscordDataTable(BackupData backupData, boolean overwriteExisting) throws SQLException {
        List<Map<String, Object>> records = getTableRecords(backupData, "discord_data");
        if (records == null || records.isEmpty()) {
            logger.debug("No Discord data found in backup, skipping restoration");
            return;
        }
        
        if (overwriteExisting && tableExists("discord_data")) {
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM discord_data")) {
                stmt.executeUpdate();
                logger.info("Cleared existing Discord data");
            }
        }
        
        // Create table if it doesn't exist
        createDiscordDataTableIfNotExists();
        
        // Insert records
        String insertQuery = "INSERT INTO discord_data (uuid, discord_id, discord_username, linked_at, verification_code) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
            
            for (Map<String, Object> record : records) {
                stmt.setString(1, (String) record.get("uuid"));
                stmt.setString(2, (String) record.get("discord_id"));
                stmt.setString(3, (String) record.get("discord_username"));
                stmt.setTimestamp(4, (Timestamp) record.get("linked_at"));
                stmt.setString(5, (String) record.get("verification_code"));
                
                stmt.addBatch();
            }
            
            stmt.executeBatch();
        }
        
        logger.info("Restored {} Discord data records", records.size());
    }
    
    /**
     * Restores verification data table from backup data.
     * Recreates verification history and status information.
     *
     * @param backupData The backup data containing table information.
     * @param overwriteExisting Whether to overwrite existing data.
     */
    private void restoreVerificationDataTable(BackupData backupData, boolean overwriteExisting) throws SQLException {
        List<Map<String, Object>> records = getTableRecords(backupData, "verification_data");
        if (records == null || records.isEmpty()) {
            logger.debug("No verification data found in backup, skipping restoration");
            return;
        }
        
        if (overwriteExisting && tableExists("verification_data")) {
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM verification_data")) {
                stmt.executeUpdate();
                logger.info("Cleared existing verification data");
            }
        }
        
        // Create table if it doesn't exist
        createVerificationDataTableIfNotExists();
        
        // Insert records
        String insertQuery = "INSERT INTO verification_data (uuid, verification_type, verified_at, verification_method, metadata) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
            
            for (Map<String, Object> record : records) {
                stmt.setString(1, (String) record.get("uuid"));
                stmt.setString(2, (String) record.get("verification_type"));
                stmt.setTimestamp(3, (Timestamp) record.get("verified_at"));
                stmt.setString(4, (String) record.get("verification_method"));
                stmt.setString(5, (String) record.get("metadata"));
                
                stmt.addBatch();
            }
            
            stmt.executeBatch();
        }
        
        logger.info("Restored {} verification data records", records.size());
    }
    
    /**
     * Exports data to JSON format.
     * Creates a human-readable JSON export.
     *
     * @param exportFile The destination file path.
     * @param includeTimestamps Whether to include timestamp data.
     * @return The export file path on success, null on failure.
     */
    private Path exportToJson(Path exportFile, boolean includeTimestamps) throws Exception {
        // Create simplified export structure
        Map<String, Object> exportData = new HashMap<>();
        
        // Export whitelist data
        List<Map<String, Object>> whitelistData = new ArrayList<>();
        String query = "SELECT uuid, username, discord_id" + 
                      (includeTimestamps ? ", verified_at" : "") + 
                      " FROM whitelist ORDER BY username";
        
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> record = new HashMap<>();
                record.put("uuid", rs.getString("uuid"));
                record.put("username", rs.getString("username"));
                record.put("discord_id", rs.getString("discord_id"));
                
                if (includeTimestamps) {
                    record.put("verified_at", rs.getTimestamp("verified_at"));
                }
                
                whitelistData.add(record);
            }
        }
        
        exportData.put("whitelist", whitelistData);
        exportData.put("export_format", "json");
        exportData.put("export_timestamp", LocalDateTime.now());
        exportData.put("total_records", whitelistData.size());
        
        String jsonData = gson.toJson(exportData);
        Files.write(exportFile, jsonData.getBytes());
        
        logger.info("Exported {} records to JSON format", whitelistData.size());
        return exportFile;
    }
    
    /**
     * Exports data to CSV format.
     * Creates a spreadsheet-compatible CSV export.
     *
     * @param exportFile The destination file path.
     * @param includeTimestamps Whether to include timestamp data.
     * @return The export file path on success, null on failure.
     */
    private Path exportToCsv(Path exportFile, boolean includeTimestamps) throws Exception {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(exportFile))) {
            
            // Write CSV header
            if (includeTimestamps) {
                writer.println("UUID,Username,Discord ID,Verified At");
            } else {
                writer.println("UUID,Username,Discord ID");
            }
            
            // Export data
            String query = "SELECT uuid, username, discord_id" + 
                          (includeTimestamps ? ", verified_at" : "") + 
                          " FROM whitelist ORDER BY username";
            
            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                
                int recordCount = 0;
                while (rs.next()) {
                    StringBuilder line = new StringBuilder();
                    line.append(escapeCsvValue(rs.getString("uuid"))).append(",");
                    line.append(escapeCsvValue(rs.getString("username"))).append(",");
                    line.append(escapeCsvValue(rs.getString("discord_id")));
                    
                    if (includeTimestamps) {
                        Timestamp timestamp = rs.getTimestamp("verified_at");
                        line.append(",").append(timestamp != null ? timestamp.toString() : "");
                    }
                    
                    writer.println(line.toString());
                    recordCount++;
                }
                
                logger.info("Exported {} records to CSV format", recordCount);
            }
        }
        
        return exportFile;
    }
    
    /**
     * Exports data to SQL format.
     * Creates a SQL script for database reconstruction.
     *
     * @param exportFile The destination file path.
     * @param includeTimestamps Whether to include timestamp data.
     * @return The export file path on success, null on failure.
     */
    private Path exportToSql(Path exportFile, boolean includeTimestamps) throws Exception {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(exportFile))) {
            
            // Write SQL header
            writer.println("-- VelocityDiscordWhitelist Data Export");
            writer.println("-- Generated on: " + LocalDateTime.now());
            writer.println("-- Format: SQL");
            writer.println();
            
            // Create table statement
            writer.println("CREATE TABLE IF NOT EXISTS whitelist (");
            writer.println("    id INT AUTO_INCREMENT PRIMARY KEY,");
            writer.println("    uuid VARCHAR(36) NOT NULL UNIQUE,");
            writer.println("    username VARCHAR(16) NOT NULL,");
            writer.println("    discord_id VARCHAR(20),");
            if (includeTimestamps) {
                writer.println("    verified_at TIMESTAMP,");
            }
            writer.println("    INDEX idx_uuid (uuid),");
            writer.println("    INDEX idx_username (username)");
            writer.println(");");
            writer.println();
            
            // Export data
            String query = "SELECT uuid, username, discord_id" + 
                          (includeTimestamps ? ", verified_at" : "") + 
                          " FROM whitelist ORDER BY username";
            
            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                
                writer.println("INSERT INTO whitelist (uuid, username, discord_id" + 
                              (includeTimestamps ? ", verified_at" : "") + ") VALUES");
                
                List<String> values = new ArrayList<>();
                while (rs.next()) {
                    StringBuilder value = new StringBuilder("(");
                    value.append("'").append(escapeSqlValue(rs.getString("uuid"))).append("', ");
                    value.append("'").append(escapeSqlValue(rs.getString("username"))).append("', ");
                    value.append("'").append(escapeSqlValue(rs.getString("discord_id"))).append("'");
                    
                    if (includeTimestamps) {
                        Timestamp timestamp = rs.getTimestamp("verified_at");
                        value.append(", ").append(timestamp != null ? "'" + timestamp.toString() + "'" : "NULL");
                    }
                    
                    value.append(")");
                    values.add(value.toString());
                }
                
                for (int i = 0; i < values.size(); i++) {
                    writer.print(values.get(i));
                    if (i < values.size() - 1) {
                        writer.println(",");
                    } else {
                        writer.println(";");
                    }
                }
                
                logger.info("Exported {} records to SQL format", values.size());
            }
        }
        
        return exportFile;
    }
    
    /**
     * Gets backup information from a backup file.
     * Reads metadata without loading the entire backup.
     *
     * @param backupFile The backup file to analyze.
     * @return BackupInfo object or null if reading failed.
     */
    private BackupInfo getBackupInfo(Path backupFile) {
        try {
            BackupData backupData = readBackupFile(backupFile);
            if (backupData == null || backupData.metadata == null) {
                return null;
            }
            
            BackupInfo info = new BackupInfo();
            info.fileName = backupFile.getFileName().toString();
            info.filePath = backupFile;
            info.fileSize = Files.size(backupFile);
            info.createdAt = backupData.metadata.createdAt;
            info.pluginVersion = backupData.metadata.pluginVersion;
            info.databaseType = backupData.metadata.databaseType;
            info.recordCounts = backupData.metadata.recordCounts;
            info.compressed = backupFile.toString().endsWith(".gz");
            
            return info;
            
        } catch (Exception e) {
            logger.warn("Failed to read backup info for: {}", backupFile.getFileName(), e);
            return null;
        }
    }
    
    /**
     * Cleans up old backup files based on retention policy.
     * Removes oldest backups when exceeding the maximum count.
     */
    private void cleanupOldBackups() {
        try {
            List<BackupInfo> backups = listBackups();
            if (backups.size() <= maxBackupFiles) {
                return;
            }
            
            // Sort by creation date (oldest first for deletion)
            backups.sort(Comparator.comparing(b -> b.createdAt));
            
            int filesToDelete = backups.size() - maxBackupFiles;
            for (int i = 0; i < filesToDelete; i++) {
                BackupInfo backupToDelete = backups.get(i);
                try {
                    Files.delete(backupToDelete.filePath);
                    logger.info("Deleted old backup: {}", backupToDelete.fileName);
                } catch (IOException e) {
                    logger.warn("Failed to delete old backup: {}", backupToDelete.fileName, e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to cleanup old backups", e);
        }
    }
    
    /**
     * Gets the current database type from connection metadata.
     *
     * @return Database type string.
     */
    private String getDatabaseType() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            logger.warn("Failed to get database type", e);
            return "Unknown";
        }
    }
    
    /**
     * Gets record counts for all tables.
     *
     * @return Map of table names to record counts.
     */
    private Map<String, Integer> getTableRecordCounts() {
        Map<String, Integer> counts = new HashMap<>();
        String[] tables = {"whitelist", "user_data", "rank_data", "discord_data", "verification_data"};
        
        for (String table : tables) {
            if (tableExists(table)) {
                try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    if (rs.next()) {
                        counts.put(table, rs.getInt(1));
                    }
                } catch (SQLException e) {
                    logger.warn("Failed to get record count for table: {}", table, e);
                    counts.put(table, 0);
                }
            }
        }
        
        return counts;
    }
    
    /**
     * Checks if a table exists in the database.
     *
     * @param tableName The name of the table to check.
     * @return True if table exists, false otherwise.
     */
    private boolean tableExists(String tableName) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, tableName, null)) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warn("Failed to check if table exists: {}", tableName, e);
            return false;
        }
    }
    
    /**
     * Creates user data table if it doesn't exist.
     */
    private void createUserDataTableIfNotExists() throws SQLException {
        String createTable = """
            CREATE TABLE IF NOT EXISTS user_data (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL UNIQUE,
                username VARCHAR(16) NOT NULL,
                first_join TIMESTAMP,
                last_seen TIMESTAMP,
                playtime_minutes INT DEFAULT 0,
                xp_points INT DEFAULT 0,
                INDEX idx_uuid (uuid),
                INDEX idx_username (username)
            )
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(createTable)) {
            stmt.execute();
        }
    }
    
    /**
     * Creates rank data table if it doesn't exist.
     */
    private void createRankDataTableIfNotExists() throws SQLException {
        String createTable = """
            CREATE TABLE IF NOT EXISTS rank_data (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                rank_id INT NOT NULL,
                subrank_id INT DEFAULT 0,
                rank_assigned_at TIMESTAMP,
                progression_data TEXT,
                INDEX idx_uuid (uuid),
                INDEX idx_rank (rank_id)
            )
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(createTable)) {
            stmt.execute();
        }
    }
    
    /**
     * Creates Discord data table if it doesn't exist.
     */
    private void createDiscordDataTableIfNotExists() throws SQLException {
        String createTable = """
            CREATE TABLE IF NOT EXISTS discord_data (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                discord_id VARCHAR(20) NOT NULL,
                discord_username VARCHAR(32),
                linked_at TIMESTAMP,
                verification_code VARCHAR(10),
                INDEX idx_uuid (uuid),
                INDEX idx_discord_id (discord_id)
            )
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(createTable)) {
            stmt.execute();
        }
    }
    
    /**
     * Creates verification data table if it doesn't exist.
     */
    private void createVerificationDataTableIfNotExists() throws SQLException {
        String createTable = """
            CREATE TABLE IF NOT EXISTS verification_data (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                verification_type VARCHAR(20) NOT NULL,
                verified_at TIMESTAMP,
                verification_method VARCHAR(20),
                metadata TEXT,
                INDEX idx_uuid (uuid),
                INDEX idx_type (verification_type)
            )
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(createTable)) {
            stmt.execute();
        }
    }
    
    /**
     * Checks if a backup version is compatible with current system.
     *
     * @param backupVersion The backup version to check.
     * @return True if compatible, false otherwise.
     */
    private boolean isVersionCompatible(String backupVersion) {
        // Simple version compatibility check
        // In production, this would be more sophisticated
        return "1.0".equals(backupVersion);
    }
    
    /**
     * Escapes CSV values to handle special characters.
     *
     * @param value The value to escape.
     * @return Escaped CSV value.
     */
    private String escapeCsvValue(String value) {
        if (value == null) return "";
        
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }
    
    /**
     * Escapes SQL values to prevent injection.
     *
     * @param value The value to escape.
     * @return Escaped SQL value.
     */
    private String escapeSqlValue(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }
    
    // Data classes for backup structure
    
    /**
     * Main backup data structure.
     */
    private static class BackupData {
        public BackupMetadata metadata;
        public Map<String, Object> tables;
    }
    
    /**
     * Backup metadata information.
     */
    private static class BackupMetadata {
        public String version;
        public LocalDateTime createdAt;
        public String pluginVersion;
        public String databaseType;
        public Map<String, Integer> recordCounts;
    }
    
    /**
     * Backup file information for listing.
     */
    public static class BackupInfo {
        public String fileName;
        public Path filePath;
        public long fileSize;
        public LocalDateTime createdAt;
        public String pluginVersion;
        public String databaseType;
        public Map<String, Integer> recordCounts;
        public boolean compressed;
        
        @Override
        public String toString() {
            return String.format("Backup[%s, %s, %d bytes, %s]", 
                               fileName, createdAt, fileSize, pluginVersion);
        }
    }
}
