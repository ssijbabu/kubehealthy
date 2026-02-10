package com.kuberhealthy.check;

import com.kuberhealthy.model.CheckResult;
import com.kuberhealthy.model.HealthCheck;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Executes health checks by running pods in Kubernetes
 */
public class KubernetesCheckExecutor implements HealthCheckExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(KubernetesCheckExecutor.class);
    private final CoreV1Api coreApi;
    private final ExecutorService executorService;
    private final Map<String, CompletableFuture<CheckResult>> runningChecks;
    
    public KubernetesCheckExecutor(ApiClient apiClient) {
        this.coreApi = new CoreV1Api(apiClient);
        this.executorService = Executors.newCachedThreadPool();
        this.runningChecks = new ConcurrentHashMap<>();
    }
    
    @Override
    public CompletableFuture<CheckResult> execute(HealthCheck healthCheck) {
        String checkUUID = UUID.randomUUID().toString();
        logger.info("Starting health check: {} with UUID: {}", healthCheck.getName(), checkUUID);
        
        CompletableFuture<CheckResult> future = CompletableFuture.supplyAsync(() -> {
            CheckResult result = new CheckResult(healthCheck.getName(), false);
            result.setUuid(checkUUID);
            long startTime = System.currentTimeMillis();
            
            try {
                // Create and run the check pod
                V1Pod pod = createCheckPod(healthCheck, checkUUID);
                V1Pod createdPod = coreApi.createNamespacedPod(
                    healthCheck.getNamespace(),
                    pod,
                    null, null, null, null
                );
                
                logger.info("Created check pod: {}", createdPod.getMetadata().getName());
                
                // Wait for pod completion with timeout
                boolean completed = waitForPodCompletion(
                    healthCheck.getNamespace(),
                    createdPod.getMetadata().getName(),
                    healthCheck.getTimeoutSeconds()
                );
                
                if (completed) {
                    // Get pod status and logs
                    V1Pod finalPod = coreApi.readNamespacedPodStatus(
                        createdPod.getMetadata().getName(),
                        healthCheck.getNamespace(),
                        null
                    );
                    
                    boolean success = isPodSuccessful(finalPod);
                    result.setOk(success);
                    
                    if (!success) {
                        result.addError("Check pod failed with status: " + getPodPhase(finalPod));
                        String logs = getPodLogs(healthCheck.getNamespace(), createdPod.getMetadata().getName());
                        if (logs != null && !logs.isEmpty()) {
                            result.addError("Pod logs: " + logs);
                        }
                    }
                } else {
                    result.addError("Check timed out after " + healthCheck.getTimeoutSeconds() + " seconds");
                }
                
                // Cleanup pod
                deletePod(healthCheck.getNamespace(), createdPod.getMetadata().getName());
                
            } catch (ApiException e) {
                logger.error("Kubernetes API error during check execution", e);
                result.addError("Kubernetes API error: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Error executing health check", e);
                result.addError("Execution error: " + e.getMessage());
            } finally {
                long endTime = System.currentTimeMillis();
                result.setRunDurationMillis(endTime - startTime);
                runningChecks.remove(healthCheck.getName());
            }
            
            return result;
        }, executorService);
        
        runningChecks.put(healthCheck.getName(), future);
        return future;
    }
    
    @Override
    public boolean cancel(String checkUUID) {
        // Find and cancel the check with the given UUID
        for (Map.Entry<String, CompletableFuture<CheckResult>> entry : runningChecks.entrySet()) {
            CompletableFuture<CheckResult> future = entry.getValue();
            if (!future.isDone()) {
                future.cancel(true);
                runningChecks.remove(entry.getKey());
                logger.info("Cancelled check: {}", entry.getKey());
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
    
    private V1Pod createCheckPod(HealthCheck healthCheck, String checkUUID) {
        String podName = "khcheck-" + healthCheck.getName() + "-" + checkUUID.substring(0, 8);
        
        V1Pod pod = new V1Pod();
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(podName);
        metadata.setNamespace(healthCheck.getNamespace());
        
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "kuberhealthy");
        labels.put("check-name", healthCheck.getName());
        labels.put("check-uuid", checkUUID);
        metadata.setLabels(labels);
        
        pod.setMetadata(metadata);
        
        V1PodSpec spec = new V1PodSpec();
        spec.setRestartPolicy("Never");
        
        V1Container container = new V1Container();
        container.setName("check");
        
        if (healthCheck.getPodSpec() != null) {
            container.setImage(healthCheck.getPodSpec().getImage());
            container.setCommand(healthCheck.getPodSpec().getCommand());
            container.setArgs(healthCheck.getPodSpec().getArgs());
        } else {
            // Default check container
            container.setImage("busybox:latest");
            container.setCommand(java.util.Arrays.asList("sh", "-c", "echo 'Health check passed'; exit 0"));
        }
        
        spec.addContainersItem(container);
        pod.setSpec(spec);
        
        return pod;
    }
    
    private boolean waitForPodCompletion(String namespace, String podName, long timeoutSeconds) {
        long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000);
        
        while (System.currentTimeMillis() < endTime) {
            try {
                V1Pod pod = coreApi.readNamespacedPodStatus(podName, namespace, null);
                String phase = getPodPhase(pod);
                
                if ("Succeeded".equals(phase) || "Failed".equals(phase)) {
                    return true;
                }
                
                Thread.sleep(1000);
            } catch (ApiException e) {
                logger.error("Error checking pod status", e);
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }
    
    private boolean isPodSuccessful(V1Pod pod) {
        return "Succeeded".equals(getPodPhase(pod));
    }
    
    private String getPodPhase(V1Pod pod) {
        if (pod.getStatus() != null && pod.getStatus().getPhase() != null) {
            return pod.getStatus().getPhase();
        }
        return "Unknown";
    }
    
    private String getPodLogs(String namespace, String podName) {
        try {
            return coreApi.readNamespacedPodLog(
                podName, namespace, null, null, null, null,
                null, null, null, null, null
            );
        } catch (ApiException e) {
            logger.error("Error reading pod logs", e);
            return null;
        }
    }
    
    private void deletePod(String namespace, String podName) {
        try {
            coreApi.deleteNamespacedPod(
                podName, namespace, null, null, null,
                null, null, null
            );
            logger.info("Deleted check pod: {}", podName);
        } catch (ApiException e) {
            logger.error("Error deleting pod", e);
        }
    }
    
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
