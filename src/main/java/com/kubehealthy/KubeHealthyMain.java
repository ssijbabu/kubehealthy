package com.kuberhealthy;

import com.kuberhealthy.check.HealthCheckExecutor;
import com.kuberhealthy.check.KubernetesCheckExecutor;
import com.kuberhealthy.controller.HealthCheckController;
import com.kuberhealthy.http.HealthCheckHttpServer;
import com.kuberhealthy.model.HealthCheck;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Main entry point for KuberHealthy Java application
 */
public class KuberHealthyMain {
    
    private static final Logger logger = LoggerFactory.getLogger(KuberHealthyMain.class);
    
    public static void main(String[] args) {
        logger.info("Starting KuberHealthy Java...");
        
        try {
            // Initialize Kubernetes client
            ApiClient apiClient = Config.defaultClient();
            logger.info("Kubernetes client initialized");
            
            // Create executor and controller
            HealthCheckExecutor executor = new KubernetesCheckExecutor(apiClient);
            HealthCheckController controller = new HealthCheckController(executor);
            
            // Register example health checks
            registerExampleChecks(controller);
            
            // Start HTTP server
            int port = getPortFromEnv();
            HealthCheckHttpServer httpServer = new HealthCheckHttpServer(controller, port);
            httpServer.start();
            
            logger.info("KuberHealthy Java started successfully");
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down KuberHealthy Java...");
                httpServer.stop();
                controller.shutdown();
                if (executor instanceof KubernetesCheckExecutor) {
                    ((KubernetesCheckExecutor) executor).shutdown();
                }
                logger.info("Shutdown complete");
            }));
            
            // Keep the application running
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.error("Fatal error starting KuberHealthy", e);
            System.exit(1);
        }
    }
    
    private static void registerExampleChecks(HealthCheckController controller) {
        logger.info("Registering example health checks...");
        
        // DNS check
        HealthCheck dnsCheck = createDnsCheck();
        controller.registerHealthCheck(dnsCheck);
        
        // Pod deployment check
        HealthCheck podCheck = createPodCheck();
        controller.registerHealthCheck(podCheck);
        
        logger.info("Registered {} example checks", 2);
    }
    
    private static HealthCheck createDnsCheck() {
        HealthCheck check = new HealthCheck(
            "dns-check",
            "default",
            60,  // Run every 60 seconds
            30   // 30 second timeout
        );
        
        HealthCheck.PodSpec podSpec = new HealthCheck.PodSpec();
        podSpec.setImage("busybox:latest");
        podSpec.setCommand(Arrays.asList("sh", "-c"));
        podSpec.setArgs(Arrays.asList(
            "nslookup kubernetes.default.svc.cluster.local && echo 'DNS check passed'"
        ));
        
        check.setPodSpec(podSpec);
        return check;
    }
    
    private static HealthCheck createPodCheck() {
        HealthCheck check = new HealthCheck(
            "pod-deployment-check",
            "default",
            120, // Run every 120 seconds
            60   // 60 second timeout
        );
        
        HealthCheck.PodSpec podSpec = new HealthCheck.PodSpec();
        podSpec.setImage("busybox:latest");
        podSpec.setCommand(Arrays.asList("sh", "-c"));
        podSpec.setArgs(Arrays.asList(
            "echo 'Testing pod creation and execution' && sleep 5 && echo 'Pod check passed'"
        ));
        
        check.setPodSpec(podSpec);
        return check;
    }
    
    private static int getPortFromEnv() {
        String portStr = System.getenv("PORT");
        if (portStr != null && !portStr.isEmpty()) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT environment variable: {}, using default 8080", portStr);
            }
        }
        return 8080;
    }
}