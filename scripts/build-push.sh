#!/bin/bash

# Build and Push Docker Image Script for ResortsLite
# Supports Google Artifact Registry and Docker Hub

set -e

echo "=========================================="
echo "ResortsLite - Docker Build & Push Script"
echo "=========================================="
echo ""

# Project configuration
PROJECT_NAME="resortslite"

# Sanitize project name for Docker image naming
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "Project: $PROJECT_NAME"
echo "Image Name: $IMAGE_NAME"
echo ""

# Prompt for image tag
read -p "Enter image tag (default: latest): " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}

# Sanitize tag
IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')
echo "Using tag: $IMAGE_TAG"
echo ""

# Select registry type
echo "Select Docker Registry:"
echo "1. Google Artifact Registry"
echo "2. Docker Hub"
read -p "Enter choice (1 or 2): " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" = "1" ]; then
    echo ""
    echo "=== Google Artifact Registry Configuration ==="
    
    # Prompt for GCP details
    read -p "Enter GCP Project ID: " GCP_PROJECT
    read -p "Enter GCP Region (e.g., us-central1): " GCP_REGION
    read -p "Enter Artifact Registry Repository Name: " ARTIFACT_REPO
    
    # Construct full image name
    FULL_IMAGE_NAME="${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT}/${ARTIFACT_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"
    
    echo ""
    echo "Full Image Name: $FULL_IMAGE_NAME"
    echo ""
    
    # Authenticate with GCP
    echo "Authenticating with Google Cloud..."
    gcloud auth login
    
    if [ $? -ne 0 ]; then
        echo "ERROR: GCP authentication failed"
        exit 1
    fi
    
    # Configure Docker for Artifact Registry
    echo "Configuring Docker for Artifact Registry..."
    gcloud auth configure-docker ${GCP_REGION}-docker.pkg.dev
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Docker configuration for Artifact Registry failed"
        exit 1
    fi
    
elif [ "$REGISTRY_CHOICE" = "2" ]; then
    echo ""
    echo "=== Docker Hub Configuration ==="
    
    # Prompt for Docker Hub credentials
    read -p "Enter Docker Hub Username: " DOCKER_USERNAME
    read -sp "Enter Docker Hub Password/Token: " DOCKER_PASSWORD
    echo ""
    
    # Construct full image name
    FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
    
    echo ""
    echo "Full Image Name: $FULL_IMAGE_NAME"
    echo ""
    
    # Authenticate with Docker Hub
    echo "Authenticating with Docker Hub..."
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Docker Hub authentication failed"
        exit 1
    fi
    
else
    echo "ERROR: Invalid choice. Please select 1 or 2."
    exit 1
fi

# Build Docker image
echo ""
echo "=========================================="
echo "Building Docker Image..."
echo "=========================================="
docker build -t "$FULL_IMAGE_NAME" .

if [ $? -ne 0 ]; then
    echo "ERROR: Docker build failed"
    exit 1
fi

echo ""
echo "✓ Docker image built successfully: $FULL_IMAGE_NAME"

# Push Docker image
echo ""
echo "=========================================="
echo "Pushing Docker Image..."
echo "=========================================="
docker push "$FULL_IMAGE_NAME"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker push failed"
    exit 1
fi

echo ""
echo "=========================================="
echo "✓ SUCCESS!"
echo "=========================================="
echo "Image pushed successfully: $FULL_IMAGE_NAME"
echo ""
echo "Next steps:"
echo "1. Use this image URI in your Kubernetes deployment"
echo "2. Run deploy-image.sh to deploy to GKE"
echo "=========================================="
