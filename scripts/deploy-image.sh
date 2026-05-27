#!/bin/bash

# Deploy ResortsLite to GCP GKE
# This script deploys the containerized application to Google Kubernetes Engine

set -e
set -o pipefail

echo "=========================================="
echo "ResortsLite - GKE Deployment Script"
echo "=========================================="
echo ""

# Prompt for GCP configuration
echo "=== GCP Configuration ==="
read -p "Enter GCP Project ID: " GCP_PROJECT
read -p "Enter GCP Zone (e.g., us-central1-a): " GCP_ZONE
read -p "Enter GKE Cluster Name: " CLUSTER_NAME

if [ -z "$GCP_PROJECT" ] || [ -z "$GCP_ZONE" ] || [ -z "$CLUSTER_NAME" ]; then
    echo "ERROR: GCP Project ID, Zone, and Cluster Name are required"
    exit 1
fi

echo ""
echo "=== Docker Image Configuration ==="
read -p "Enter Docker Image URI (e.g., us-central1-docker.pkg.dev/project/repo/resortslite:latest): " IMAGE_URI

if [ -z "$IMAGE_URI" ]; then
    echo "ERROR: Docker Image URI is required"
    exit 1
fi

echo ""
echo "=== Application Configuration ==="
echo "Configure external service connections (press Enter to skip optional values)"
echo ""

# Redis Configuration
read -p "Enter Redis Host (default: redis.example.com): " REDIS_HOST
REDIS_HOST=${REDIS_HOST:-redis.example.com}

read -p "Enter Redis Port (default: 6379): " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}

read -p "Enter Redis Password (optional): " REDIS_PASSWORD

# Google Cloud Storage Configuration
read -p "Enter GCP Project ID for GCS (default: $GCP_PROJECT): " GCS_PROJECT_ID
GCS_PROJECT_ID=${GCS_PROJECT_ID:-$GCP_PROJECT}

read -p "Enter GCS Bucket Name (default: resortslite-files): " GCS_BUCKET_NAME
GCS_BUCKET_NAME=${GCS_BUCKET_NAME:-resortslite-files}

# External Service Endpoints
read -p "Enter Payment Service Endpoint (default: http://payment-svc:9090/charge): " APP_PAYMENT_ENDPOINT
APP_PAYMENT_ENDPOINT=${APP_PAYMENT_ENDPOINT:-http://payment-svc:9090/charge}

read -p "Enter Inventory Service Endpoint (default: http://inventory-svc:8081/rooms): " APP_INVENTORY_ENDPOINT
APP_INVENTORY_ENDPOINT=${APP_INVENTORY_ENDPOINT:-http://inventory-svc:8081/rooms}

read -p "Enter Notification Service Endpoint (default: http://notify-svc:7070/send): " APP_NOTIFICATION_ENDPOINT
APP_NOTIFICATION_ENDPOINT=${APP_NOTIFICATION_ENDPOINT:-http://notify-svc:7070/send}

echo ""
echo "=========================================="
echo "Configuration Summary"
echo "=========================================="
echo "GCP Project: $GCP_PROJECT"
echo "GCP Zone: $GCP_ZONE"
echo "GKE Cluster: $CLUSTER_NAME"
echo "Docker Image: $IMAGE_URI"
echo "Redis Host: $REDIS_HOST"
echo "GCS Bucket: $GCS_BUCKET_NAME"
echo "=========================================="
echo ""

read -p "Proceed with deployment? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    echo "Deployment cancelled"
    exit 0
fi

# Configure kubectl to use the GKE cluster
echo ""
echo "=========================================="
echo "Configuring kubectl for GKE cluster..."
echo "=========================================="
gcloud container clusters get-credentials "$CLUSTER_NAME" --zone "$GCP_ZONE" --project "$GCP_PROJECT"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl for GKE cluster"
    exit 1
fi

# Verify cluster connectivity
echo ""
echo "Verifying cluster connectivity..."
kubectl cluster-info || {
    echo "ERROR: Cannot connect to Kubernetes cluster"
    exit 1
}

# Create temporary directory for processed manifests
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Copy manifests to temp directory
cp -r kubernetes/* "$TEMP_DIR/"

# Replace placeholders in deployment manifest
echo ""
echo "=========================================="
echo "Updating Kubernetes manifests..."
echo "=========================================="

sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{GCP_PROJECT_ID}}|$GCS_PROJECT_ID|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{GCS_BUCKET_NAME}}|$GCS_BUCKET_NAME|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_PAYMENT_ENDPOINT}}|$APP_PAYMENT_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_INVENTORY_ENDPOINT}}|$APP_INVENTORY_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_NOTIFICATION_ENDPOINT}}|$APP_NOTIFICATION_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"

echo "✓ Manifests updated successfully"

# Apply Kubernetes manifests
echo ""
echo "=========================================="
echo "Deploying to GKE..."
echo "=========================================="

echo ""
echo "Creating namespace..."
kubectl apply -f "$TEMP_DIR/namespace.yaml"

echo ""
echo "Deploying application..."
kubectl apply -f "$TEMP_DIR/deployment.yaml"

echo ""
echo "Creating service..."
kubectl apply -f "$TEMP_DIR/service.yaml"

echo ""
echo "Creating ingress..."
kubectl apply -f "$TEMP_DIR/ingress.yaml"

# Wait for deployment to complete
echo ""
echo "=========================================="
echo "Waiting for deployment to complete..."
echo "=========================================="
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if [ $? -ne 0 ]; then
    echo "ERROR: Deployment rollout failed"
    echo ""
    echo "Checking pod status..."
    kubectl get pods -n resortslite
    echo ""
    echo "Checking pod logs..."
    kubectl logs -n resortslite -l app=resortslite --tail=50
    exit 1
fi

# Verify deployment
echo ""
echo "=========================================="
echo "Verifying deployment..."
echo "=========================================="
kubectl get pods,svc,ingress -n resortslite

# Get ingress IP
echo ""
echo "=========================================="
echo "Deployment Information"
echo "=========================================="
echo ""
echo "Namespace: resortslite"
echo "Deployment: resortslite"
echo "Service: resortslite-service"
echo ""

INGRESS_IP=$(kubectl get ingress resortslite-ingress -n resortslite -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "pending")
echo "Ingress IP: $INGRESS_IP"

if [ "$INGRESS_IP" = "pending" ]; then
    echo ""
    echo "Note: Ingress IP is still being provisioned. This may take a few minutes."
    echo "Run the following command to check the status:"
    echo "  kubectl get ingress resortslite-ingress -n resortslite"
fi

echo ""
echo "=========================================="
echo "✓ Deployment completed successfully!"
echo "=========================================="
echo ""
echo "Access your application:"
echo "  - Internal: http://resortslite-service.resortslite.svc.cluster.local"
echo "  - External: http://$INGRESS_IP (once IP is assigned)"
echo ""
echo "Useful commands:"
echo "  - View pods: kubectl get pods -n resortslite"
echo "  - View logs: kubectl logs -n resortslite -l app=resortslite"
echo "  - Scale deployment: kubectl scale deployment resortslite -n resortslite --replicas=3"
echo "  - Delete deployment: kubectl delete namespace resortslite"
echo "=========================================="
