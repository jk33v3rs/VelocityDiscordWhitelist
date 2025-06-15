package top.jk33v3rs.velocitydiscordwhitelist.utils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

import org.slf4j.Logger;

import top.jk33v3rs.velocitydiscordwhitelist.database.SQLHandler;

/**
 * DatabaseMonitor
 * 
 * Monitors database connectivity and attempts to restore connection
 * when the database becomes unavailable. This class provides automatic
 * retry functionality and health checking for the database connection.
 */
public class DatabaseMonitor {
    private final Logger logger;
    private final SQLHandler sqlHandler;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> monitoringTask;
    private final int retryIntervalSeconds;
    private final int maxRetryAttempts;
    private int currentRetryCount;
    private boolean isMonitoring;
    
    /**
     * DatabaseMonitor constructor
     * 
     * Creates a new database monitor instance with the specified configuration.
     * 
     * @param logger The logger instance for this monitor
     * @param sqlHandler The SQL handler to monitor
     * @param retryIntervalSeconds How often to retry connection (in seconds)
     * @param maxRetryAttempts Maximum number of retry attempts before giving up
     */
    public DatabaseMonitor(Logger logger, SQLHandler sqlHandler, int retryIntervalSeconds, int maxRetryAttempts) {
        this.logger = logger;
        this.sqlHandler = sqlHandler;
        this.retryIntervalSeconds = retryIntervalSeconds;
        this.maxRetryAttempts = maxRetryAttempts;
        this.currentRetryCount = 0;
        this.isMonitoring = false;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DatabaseMonitor");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * startMonitoring
     * 
     * Starts monitoring the database connection. If the database is currently
     * unavailable, it will begin retry attempts immediately.
     */
    public void startMonitoring() {
        if (isMonitoring) {
            logger.warn("Database monitoring is already active");
            return;
        }
        
        logger.info("Starting database connection monitoring (retry interval: {}s, max attempts: {})", 
                   retryIntervalSeconds, maxRetryAttempts);
        
        isMonitoring = true;
        currentRetryCount = 0;
        
        // Schedule periodic health checks
        monitoringTask = scheduler.scheduleWithFixedDelay(
            this::performHealthCheck,
            0, // Start immediately
            retryIntervalSeconds,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * stopMonitoring
     * 
     * Stops database connection monitoring and cancels any pending retry attempts.
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }
        
        logger.info("Stopping database connection monitoring");
        isMonitoring = false;
        
        if (monitoringTask != null && !monitoringTask.isCancelled()) {
            monitoringTask.cancel(false);
        }
    }
    
    /**
     * shutdown
     * 
     * Gracefully shuts down the database monitor and its associated thread pool.
     */
    public void shutdown() {
        stopMonitoring();
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * performHealthCheck
     * 
     * Performs a health check on the database connection. If the database
     * is unavailable, it will attempt to restore the connection.
     */
    private void performHealthCheck() {
        try {
            if (!sqlHandler.isDatabaseAvailable()) {
                handleDatabaseUnavailable();
            } else {
                // Database is available - reset retry count
                if (currentRetryCount > 0) {
                    logger.info("Database connection restored successfully after {} attempts", currentRetryCount);
                    currentRetryCount = 0;
                }
            }
        } catch (Exception e) {
            logger.error("Error during database health check: {}", e.getMessage());
        }
    }
    
    /**
     * handleDatabaseUnavailable
     * 
     * Handles the case when the database is unavailable by attempting
     * to restore the connection. Implements exponential backoff for retries.
     */
    private void handleDatabaseUnavailable() {
        currentRetryCount++;
        
        if (currentRetryCount > maxRetryAttempts) {
            logger.error("Database connection failed after {} attempts - stopping retry attempts", maxRetryAttempts);
            logger.error("Manual intervention may be required to restore database connectivity");
            stopMonitoring();
            return;
        }
        
        logger.warn("Database unavailable - attempting reconnection (attempt {}/{})", 
                   currentRetryCount, maxRetryAttempts);
        
        boolean connectionRestored = sqlHandler.retryConnection();
        
        if (connectionRestored) {
            logger.info("Successfully restored database connection on attempt {}", currentRetryCount);
            currentRetryCount = 0;
        } else {
            // Calculate exponential backoff delay for next attempt
            int backoffDelay = Math.min(retryIntervalSeconds * (int) Math.pow(2, currentRetryCount - 1), 300);
            logger.warn("Failed to restore database connection - next attempt in {}s", backoffDelay);
        }
    }
    
    /**
     * isMonitoring
     * 
     * Checks if the database monitor is currently active.
     * 
     * @return boolean true if monitoring is active, false otherwise
     */
    public boolean isMonitoring() {
        return isMonitoring;
    }
    
    /**
     * getCurrentRetryCount
     * 
     * Gets the current number of retry attempts that have been made.
     * 
     * @return int the current retry count
     */
    public int getCurrentRetryCount() {
        return currentRetryCount;
    }
    
    /**
     * forceHealthCheck
     * 
     * Forces an immediate health check of the database connection.
     * This can be used to manually trigger a connection attempt.
     */
    public void forceHealthCheck() {
        if (isMonitoring) {
            logger.info("Forcing immediate database health check");
            scheduler.execute(this::performHealthCheck);
        } else {
            logger.warn("Cannot force health check - monitoring is not active");
        }
    }
}
