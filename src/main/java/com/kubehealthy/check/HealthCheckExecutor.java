package com.kuberhealthy.check;

import com.kuberhealthy.model.CheckResult;
import com.kuberhealthy.model.HealthCheck;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for executing health checks
 */
public interface HealthCheckExecutor {
    
    /**
     * Execute a health check asynchronously
     * 
     * @param healthCheck The health check to execute
     * @return A CompletableFuture containing the check result
     */
    CompletableFuture<CheckResult> execute(HealthCheck healthCheck);
    
    /**
     * Cancel a running health check
     * 
     * @param checkUUID The UUID of the check to cancel
     * @return true if the check was cancelled, false otherwise
     */
    boolean cancel(String checkUUID);
    
    /**
     * Check if a health check is currently running
     * 
     * @param checkName The name of the check
     * @return true if running, false otherwise
     */
    boolean isRunning(String checkName);
}