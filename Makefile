.PHONY: help build test clean docker-build docker-push deploy undeploy run

IMAGE_NAME ?= kuberhealthy-java
IMAGE_TAG ?= latest
REGISTRY ?= docker.io
NAMESPACE ?= kuberhealthy

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-15s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: ## Build the Java application
	@echo "Building application..."
	mvn clean package

test: ## Run tests
	@echo "Running tests..."
	mvn test

clean: ## Clean build artifacts
	@echo "Cleaning..."
	mvn clean
	rm -rf target/

docker-build: build ## Build Docker image
	@echo "Building Docker image..."
	docker build -t $(IMAGE_NAME):$(IMAGE_TAG) .

docker-push: docker-build ## Push Docker image to registry
	@echo "Pushing Docker image..."
	docker tag $(IMAGE_NAME):$(IMAGE_TAG) $(REGISTRY)/$(IMAGE_NAME):$(IMAGE_TAG)
	docker push $(REGISTRY)/$(IMAGE_NAME):$(IMAGE_TAG)

deploy: ## Deploy to Kubernetes
	@echo "Deploying to Kubernetes..."
	kubectl apply -f kubernetes/deployment.yaml
	@echo "Waiting for deployment..."
	kubectl wait --for=condition=available --timeout=300s deployment/kuberhealthy -n $(NAMESPACE)

undeploy: ## Remove from Kubernetes
	@echo "Removing from Kubernetes..."
	kubectl delete -f kubernetes/deployment.yaml

run: build ## Run locally
	@echo "Running application locally..."
	java -jar target/kuberhealthy-java-1.0.0.jar

logs: ## Show application logs
	kubectl logs -n $(NAMESPACE) deployment/kuberhealthy -f

status: ## Show deployment status
	@echo "Deployment status:"
	kubectl get all -n $(NAMESPACE)
	@echo "\nHealth check status:"
	kubectl port-forward -n $(NAMESPACE) svc/kuberhealthy 8080:8080 & \
	sleep 2 && \
	curl -s http://localhost:8080/status | jq . && \
	pkill -f "port-forward"

install-kind: ## Install kind (Kubernetes in Docker) for local testing
	@echo "Installing kind..."
	@if ! command -v kind &> /dev/null; then \
		curl -Lo ./kind https://kind.sigs.k8s.io/dl/latest/kind-linux-amd64; \
		chmod +x ./kind; \
		sudo mv ./kind /usr/local/bin/kind; \
	else \
		echo "kind already installed"; \
	fi

create-kind-cluster: install-kind ## Create a local kind cluster
	@echo "Creating kind cluster..."
	kind create cluster --name kuberhealthy-test

delete-kind-cluster: ## Delete the local kind cluster
	@echo "Deleting kind cluster..."
	kind delete cluster --name kuberhealthy-test

load-image-kind: docker-build ## Load Docker image into kind cluster
	@echo "Loading image into kind cluster..."
	kind load docker-image $(IMAGE_NAME):$(IMAGE_TAG) --name kuberhealthy-test