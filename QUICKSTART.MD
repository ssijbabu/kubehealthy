# KuberHealthy Java - Quick Start Guide

This guide will help you get KuberHealthy Java up and running in 5 minutes.

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker
- Kubernetes cluster (or kind/minikube for local testing)
- kubectl configured

## Option 1: Quick Deploy to Existing Cluster

### Step 1: Build and Deploy

```bash
# Clone or navigate to the project directory
cd kuberhealthy-java

# Build the Docker image
make docker-build

# Deploy to Kubernetes
make deploy
```

### Step 2: Verify Installation

```bash
# Check if pods are running
kubectl get pods -n kuberhealthy

# Check logs
kubectl logs -n kuberhealthy deployment/kuberhealthy -f
```

### Step 3: Access the API

```bash
# Port forward to access locally
kubectl port-forward -n kuberhealthy svc/kuberhealthy 8080:8080

# In another terminal, check health status
curl http://localhost:8080/healthz
curl http://localhost:8080/status
curl http://localhost:8080/checks
curl http://localhost:8080/metrics
```

## Option 2: Local Development with kind

### Step 1: Create Local Cluster

```bash
# Create a kind cluster
make create-kind-cluster

# Verify cluster is running
kubectl cluster-info
```

### Step 2: Build and Load Image

```bash
# Build the application and Docker image
make docker-build

# Load image into kind cluster
make load-image-kind
```

### Step 3: Deploy

```bash
# Deploy to the kind cluster
make deploy

# Wait for deployment
kubectl wait --for=condition=available --timeout=300s deployment/kuberhealthy -n kuberhealthy
```

### Step 4: Test

```bash
# Check status
make status

# View logs
make logs
```

## Option 3: Run Locally (Development Mode)

### Step 1: Set up kubeconfig

Ensure you have a valid `~/.kube/config` pointing to your cluster.

### Step 2: Build and Run

```bash
# Build the application
mvn clean package

# Run locally
java -jar target/kuberhealthy-java-1.0.0.jar
```

### Step 3: Test

Open another terminal:

```bash
curl http://localhost:8080/healthz
curl http://localhost:8080/status
```

## Understanding the Output

### Health Check Response

```json
{
  "status": "healthy",
  "timestamp": 1707516789000
}
```

- **status**: "healthy" if all checks pass, "unhealthy" if any fail
- **timestamp**: Unix timestamp in milliseconds

### Status Response

```json
{
  "healthy": true,
  "totalChecks": 2,
  "failingChecks": 0,
  "timestamp": 1707516789000
}
```

### Checks Response

```json
{
  "checks": [
    {
      "name": "dns-check",
      "namespace": "default",
      "runIntervalSeconds": 60,
      "timeoutSeconds": 30,
      "status": {
        "state": "COMPLETED",
        "ok": true,
        "errors": [],
        "lastRun": "2024-02-09T12:34:56Z",
        "lastSuccess": "2024-02-09T12:34:56Z",
        "consecutiveFailures": 0
      }
    }
  ],
  "count": 1
}
```

## Common Issues

### Pods Not Starting

**Problem**: Pods stuck in Pending state

**Solution**:
```bash
# Check pod status
kubectl describe pod -n kuberhealthy <pod-name>

# Common issues:
# 1. Image pull errors - verify image exists
# 2. Resource constraints - check cluster capacity
# 3. RBAC issues - verify ServiceAccount permissions
```

### Health Checks Failing

**Problem**: Checks show as failed in status

**Solution**:
```bash
# View check pod logs
kubectl logs -n default <khcheck-pod-name>

# Common issues:
# 1. Network policies blocking pod creation
# 2. Insufficient RBAC permissions
# 3. Timeout too short for check execution
```

### Cannot Access API

**Problem**: Connection refused on port 8080

**Solution**:
```bash
# Verify service is running
kubectl get svc -n kuberhealthy

# Verify pod is ready
kubectl get pods -n kuberhealthy

# Check pod logs
kubectl logs -n kuberhealthy deployment/kuberhealthy
```

## Next Steps

1. **Add Custom Checks**: Edit `KuberHealthyMain.java` to register your own checks
2. **Configure Prometheus**: Set up scraping from `/metrics` endpoint
3. **Create Alerts**: Use check metrics to trigger alerts
4. **Build Dashboard**: Visualize check status in Grafana

## Example Custom Check

Add this to `KuberHealthyMain.java`:

```java
private static HealthCheck createCustomCheck() {
    HealthCheck check = new HealthCheck(
        "my-api-check",
        "default",
        300,  // Run every 5 minutes
        60    // 60 second timeout
    );
    
    HealthCheck.PodSpec podSpec = new HealthCheck.PodSpec();
    podSpec.setImage("curlimages/curl:latest");
    podSpec.setCommand(Arrays.asList("sh", "-c"));
    podSpec.setArgs(Arrays.asList(
        "curl -f https://api.example.com/health || exit 1"
    ));
    
    check.setPodSpec(podSpec);
    return check;
}
```

Then register it:
```java
controller.registerHealthCheck(createCustomCheck());
```

## Cleanup

### Remove from Kubernetes

```bash
make undeploy
```

### Delete kind Cluster

```bash
make delete-kind-cluster
```

## Getting Help

- Check the [README.md](README.md) for detailed documentation
- Review logs: `make logs`
- Check pod events: `kubectl describe pod -n kuberhealthy <pod-name>`

## Useful Commands Cheat Sheet

```bash
# Build
make build                    # Build Java application
make docker-build            # Build Docker image

# Deploy
make deploy                  # Deploy to Kubernetes
make undeploy               # Remove from Kubernetes

# Monitor
make logs                    # View application logs
make status                  # Check deployment status

# Local Development
make run                     # Run locally
make test                    # Run tests

# kind Cluster
make create-kind-cluster     # Create local cluster
make delete-kind-cluster     # Delete local cluster
make load-image-kind        # Load image to kind
```

Happy Health Checking! 🏥