package com.kuberhealthy.controller;

import com.kuberhealthy.check.HealthCheckExecutor;
import com.kuberhealthy.model.CheckResult;
import com.kuberhealthy.model.HealthCheck;
import com.kuberhealthy.model.HealthCheckStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Controller that manages the lifecycle of health checks
 */
public class HealthCheckController {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);
    
    private final HealthCheckExecutor executor;
    private final Map<String, HealthCheck> healthChecks;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledChecks;
    
    public HealthCheckController(HealthCheckExecutor executor) {
        this.executor = executor;
        this.healthChecks = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(10);
        this.scheduledChecks = new ConcurrentHashMap<>();
    }
    
    /**
     * Register a new health check
     */
    public void registerHealthCheck(HealthCheck healthCheck) {
        logger.info("Registering health check: {}", healthCheck.getName());
        
        healthChecks.put(healthCheck.getName(), healthCheck);
        
        // Cancel existing schedule if present
        ScheduledFuture<?> existingSchedule = scheduledChecks.get(healthCheck.getName());
        if (existingSchedule != null) {
            existingSchedule.cancel(false);
        }
        
        // Schedule the health check
        ScheduledFuture<?> scheduledFuture = scheduler.scheduleAtFixedRate(
            () -> runHealthCheck(healthCheck.getName()),
            0,
            healthCheck.getRunIntervalSeconds(),
            TimeUnit.SECONDS
        );
        
        scheduledChecks.put(healthCheck.getName(), scheduledFuture);
    }
    
    /**
     * Unregister a health check
     */
    public void unregisterHealthCheck(String checkName) {
        logger.info("Unregistering health check: {}", checkName);
        
        ScheduledFuture<?> scheduledFuture = scheduledChecks.remove(checkName);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        
        healthChecks.remove(checkName);
    }
    
    /**
     * Run a health check immediately
     */
    public CompletableFuture<CheckResult> runHealthCheck(String checkName) {
        HealthCheck healthCheck = healthChecks.get(checkName);
        if (healthCheck == null) {
            logger.warn("Health check not found: {}", checkName);
            return CompletableFuture.completedFuture(null);
        }
        
        // Check if already running
        if (executor.isRunning(checkName)) {
            logger.info("Health check already running: {}", checkName);
            return CompletableFuture.completedFuture(null);
        }
        
        logger.info("Running health check: {}", checkName);
        
        // Update status to running
        HealthCheckStatus status = healthCheck.getStatus();
        status.setState(HealthCheckStatus.CheckState.RUNNING);
        status.setLastRun(Instant.now());
        
        // Execute the check
        CompletableFuture<CheckResult> future = executor.execute(healthCheck);
        
        // Handle the result
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("Error executing health check: " + checkName, throwable);
                status.setState(HealthCheckStatus.CheckState.FAILED);
                status.setOk(false);
                status.addError("Execution error: " + throwable.getMessage());
                status.setConsecutiveFailures(status.getConsecutiveFailures() + 1);
            } else if (result != null) {
                handleCheckResult(healthCheck, result);
            }
        });
        
        return future;
    }
    
    private void handleCheckResult(HealthCheck healthCheck, CheckResult result) {
        HealthCheckStatus status = healthCheck.getStatus();
        
        if (result.isOk()) {
            logger.info("Health check passed: {}", healthCheck.getName());
            status.setState(HealthCheckStatus.CheckState.COMPLETED);
            status.setOk(true);
            status.setLastSuccess(Instant.now());
            status.setConsecutiveFailures(0);
            status.getErrors().clear();
        } else {
            logger.warn("Health check failed: {} - Errors: {}", 
                healthCheck.getName(), result.getErrors());
            status.setState(HealthCheckStatus.CheckState.FAILED);
            status.setOk(false);
            status.setErrors(result.getErrors());
            status.setConsecutiveFailures(status.getConsecutiveFailures() + 1);
        }
    }
    
    /**
     * Get a health check by name
     */
    public HealthCheck getHealthCheck(String checkName) {
        return healthChecks.get(checkName);
    }
    
    /**
     * Get all registered health checks
     */
    public List<HealthCheck> getAllHealthChecks() {
        return List.copyOf(healthChecks.values());
    }
    
    /**
     * Get the overall health status
     */
    public boolean isHealthy() {
        if (healthChecks.isEmpty()) {
            return true; // No checks means healthy
        }
        
        return healthChecks.values().stream()
            .allMatch(check -> check.getStatus().isOk());
    }
    
    /**
     * Get count of failing checks
     */
    public long getFailingChecksCount() {
        return healthChecks.values().stream()
            .filter(check -> !check.getStatus().isOk())
            .count();
    }
    
    /**
     * Shutdown the controller
     */
    public void shutdown() {
        logger.info("Shutting down health check controller");
        
        // Cancel all scheduled checks
        scheduledChecks.values().forEach(future -> future.cancel(false));
        scheduledChecks.clear();
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}