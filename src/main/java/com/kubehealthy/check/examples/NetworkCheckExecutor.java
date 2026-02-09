package com.kuberhealthy.check.examples;

import com.kuberhealthy.model.CheckResult;
import com.kuberhealthy.model.HealthCheck;
import com.kuberhealthy.check.HealthCheckExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Example: Simple network connectivity check executor
 * This demonstrates how to implement custom checks without Kubernetes pods
 */
public class NetworkCheckExecutor implements HealthCheckExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(NetworkCheckExecutor.class);
    private final Map<String, CompletableFuture<CheckResult>> runningChecks;
    
    public NetworkCheckExecutor() {
        this.runningChecks = new ConcurrentHashMap<>();
    }
    
    @Override
    public CompletableFuture<CheckResult> execute(HealthCheck healthCheck) {
        logger.info("Starting network check: {}", healthCheck.getName());
        
        CompletableFuture<CheckResult> future = CompletableFuture.supplyAsync(() -> {
            CheckResult result = new CheckResult(healthCheck.getName(), false);
            long startTime = System.currentTimeMillis();
            
            try {
                // Example: Check DNS resolution
                String hostname = "kubernetes.default.svc.cluster.local";
                
                logger.debug("Resolving hostname: {}", hostname);
                InetAddress address = InetAddress.getByName(hostname);
                
                logger.info("Successfully resolved {} to {}", hostname, address.getHostAddress());
                result.setOk(true);
                
            } catch (Exception e) {
                logger.error("Network check failed", e);
                result.addError("Network check failed: " + e.getMessage());
                result.setOk(false);
            } finally {
                long endTime = System.currentTimeMillis();
                result.setRunDurationMillis(endTime - startTime);
                runningChecks.remove(healthCheck.getName());
            }
            
            return result;
        });
        
        runningChecks.put(healthCheck.getName(), future);
        return future;
    }
    
    @Override
    public boolean cancel(String checkUUID) {
        for (Map.Entry<String, CompletableFuture<CheckResult>> entry : runningChecks.entrySet()) {
            CompletableFuture<CheckResult> future = entry.getValue();
            if (!future.isDone()) {
                future.cancel(true);
                runningChecks.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean isRunning(String checkName) {
        CompletableFuture<CheckResult> future = runningChecks.get(checkName);
        return future != null && !future.isDone();
    }
}