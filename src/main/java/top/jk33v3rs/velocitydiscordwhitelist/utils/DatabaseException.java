package top.jk33v3rs.velocitydiscordwhitelist.utils;

/**
 * Database-specific exception for the VelocityDiscordWhitelist plugin.
 * 
 * This exception is thrown when database operations fail, providing specific
 * error handling for SQL-related issues including connection failures,
 * query errors, and transaction problems.
 */
public class DatabaseException extends VelocityDiscordWhitelistException {
    
    private final String sqlState;
    private final int errorCode;
    private final String query;
    
    /**
     * Constructor for DatabaseException with SQL state and error code.
     * 
     * Creates a database exception with detailed SQL error information
     * for proper debugging and error tracking.
     * 
     * @param errorCode The plugin error code for this exception
     * @param message The detailed technical message for logging
     * @param sqlState The SQL state code from the database
     * @param dbErrorCode The database-specific error code
     */
    public DatabaseException(ErrorCode errorCode, String message, String sqlState, int dbErrorCode) {
        super(errorCode, message);
        this.sqlState = sqlState;
        this.errorCode = dbErrorCode;
        this.query = null;
    }
    
    /**
     * Constructor for DatabaseException with SQL query context.
     * 
     * Creates a database exception that includes the SQL query that failed,
     * which is useful for debugging query-specific issues.
     * 
     * @param errorCode The plugin error code for this exception
     * @param message The detailed technical message for logging
     * @param sqlState The SQL state code from the database
     * @param dbErrorCode The database-specific error code
     * @param query The SQL query that caused the error
     */
    public DatabaseException(ErrorCode errorCode, String message, String sqlState, int dbErrorCode, String query) {
        super(errorCode, message);
        this.sqlState = sqlState;
        this.errorCode = dbErrorCode;
        this.query = query;
    }
    
    /**
     * Constructor for DatabaseException with cause.
     * 
     * Creates a database exception wrapping an underlying SQLException
     * while preserving the original exception context.
     * 
     * @param errorCode The plugin error code for this exception
     * @param message The detailed technical message for logging
     * @param cause The underlying SQLException that caused this error
     */
    public DatabaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.sqlState = null;
        this.errorCode = -1;
        this.query = null;
    }
    
    /**
     * Constructor for DatabaseException with full context.
     * 
     * Creates a database exception with complete error context including
     * the underlying cause, SQL state, error code, and query.
     * 
     * @param errorCode The plugin error code for this exception
     * @param message The detailed technical message for logging
     * @param sqlState The SQL state code from the database
     * @param dbErrorCode The database-specific error code
     * @param query The SQL query that caused the error
     * @param cause The underlying SQLException that caused this error
     */
    public DatabaseException(ErrorCode errorCode, String message, String sqlState, int dbErrorCode, String query, Throwable cause) {
        super(errorCode, message, cause);
        this.sqlState = sqlState;
        this.errorCode = dbErrorCode;
        this.query = query;
    }
    
    /**
     * Gets the SQL state code for this database error.
     * 
     * @return The SQL state code, or null if not available
     */
    public String getSqlState() {
        return sqlState;
    }
    
    /**
     * Gets the database-specific error code.
     * 
     * @return The database error code, or -1 if not available
     */
    public int getDatabaseErrorCode() {
        return errorCode;
    }
    
    /**
     * Gets the SQL query that caused this error.
     * 
     * @return The SQL query string, or null if not available
     */
    public String getQuery() {
        return query;
    }
    
    /**
     * Checks if this is a connection-related error.
     * 
     * @return true if this is a connection error, false otherwise
     */
    public boolean isConnectionError() {
        return getErrorCode() == ErrorCode.DATABASE_CONNECTION_FAILED ||
               (sqlState != null && sqlState.startsWith("08"));
    }
    
    /**
     * Checks if this is a timeout-related error.
     * 
     * @return true if this is a timeout error, false otherwise
     */
    public boolean isTimeoutError() {
        return getErrorCode() == ErrorCode.DATABASE_TIMEOUT ||
               (sqlState != null && sqlState.equals("HYT00"));
    }
    
    /**
     * Checks if this is a constraint violation error.
     * 
     * @return true if this is a constraint violation, false otherwise
     */
    public boolean isConstraintViolation() {
        return getErrorCode() == ErrorCode.DATABASE_CONSTRAINT_VIOLATION ||
               (sqlState != null && sqlState.startsWith("23"));
    }
}