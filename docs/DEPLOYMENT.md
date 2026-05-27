# ResortsLite - Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Docker Deployment](#docker-deployment)
5. [GCP GKE Deployment](#gcp-gke-deployment)
6. [Configuration Management](#configuration-management)
7. [Troubleshooting](#troubleshooting)
8. [Security Considerations](#security-considerations)
9. [Technology-Specific Notes](#technology-specific-notes)

---

## Overview

ResortsLite is a Spring Boot 2.7.x application built with Java 8, designed for containerized deployment on Google Kubernetes Engine (GKE). This guide provides comprehensive instructions for deploying the application in various environments.

**Application Details:**
- **Framework:** Spring Boot 2.7.18
- **Java Version:** Java 8 (1.8)
- **Build Tool:** Maven 3.x
- **Application Port:** 8080
- **Health Check Endpoint:** `/actuator/health`
- **Database:** H2 (in-memory)
- **External Dependencies:** Redis, Google Cloud Storage

---

## Prerequisites

### Required Software

#### For Local Development:
- **Java Development Kit (JDK) 8** or higher
- **Maven 3.6+** (for building)
- **Docker 20.10+** (for containerization)
- **Docker Compose 1.29+** (for local orchestration)

#### For GCP GKE Deployment:
- **Google Cloud SDK (gcloud)** - [Install Guide](https://cloud.google.com/sdk/docs/install)
- **kubectl** - Kubernetes command-line tool
- **Docker 20.10+** - For building and pushing images
- **GCP Account** with appropriate permissions

### GCP Permissions Required:
- `container.clusters.get`
- `container.clusters.update`
- `container.deployments.create`
- `container.services.create`
- `artifactregistry.repositories.uploadArtifacts` (if using Artifact Registry)

### External Services:
- **Redis** - For distributed session management
- **Google Cloud Storage** - For file storage
- **Payment Service** - External payment processing endpoint
- **Inventory Service** - External inventory management endpoint
- **Notification Service** - External notification endpoint

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd CompTest
```

### 2. Build the Application
```bash
# Using Maven
mvn clean package -DskipTests

# The JAR file will be created at: target/resortsLite-1.0.0.jar
```

### 3. Run Locally (Without Docker)
```bash
# Set environment variables
export SERVER_PORT=8080
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Run the application
java -jar target/resortsLite-1.0.0.jar
```

### 4. Access the Application
- **Application:** http://localhost:8080
- **Health Check:** http://localhost:8080/actuator/health
- **H2 Console:** http://localhost:8080/h2-console

---

## Docker Deployment

### 1. Build Docker Image Locally
```bash
# Build the image
docker build -t resortslite:latest .

# Verify the image
docker images | grep resortslite
```

### 2. Run with Docker Compose
```bash
# Start the application
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the application
docker-compose down
```

### 3. Access the Containerized Application
- **Application:** http://localhost:8080
- **Health Check:** http://localhost:8080/actuator/health

### 4. Environment Variables for Docker Compose
Edit `docker-compose.yml` to configure:
- `REDIS_HOST` - Redis server hostname
- `REDIS_PORT` - Redis server port
- `REDIS_PASSWORD` - Redis authentication password
- `GCP_PROJECT_ID` - Google Cloud project ID
- `GCS_BUCKET_NAME` - Google Cloud Storage bucket name
- External service endpoints

---

## GCP GKE Deployment

### Step 1: Prepare GCP Environment

#### 1.1 Install and Configure gcloud CLI
```bash
# Install gcloud (if not already installed)
# Follow: https://cloud.google.com/sdk/docs/install

# Initialize gcloud
gcloud init

# Set your project
gcloud config set project YOUR_PROJECT_ID

# Authenticate
gcloud auth login
```

#### 1.2 Create GKE Cluster (if not exists)
```bash
# Create a GKE cluster
gcloud container clusters create resortslite-cluster \
  --zone us-central1-a \
  --num-nodes 3 \
  --machine-type n1-standard-2 \
  --enable-autoscaling \
  --min-nodes 2 \
  --max-nodes 5

# Get cluster credentials
gcloud container clusters get-credentials resortslite-cluster \
  --zone us-central1-a
```

#### 1.3 Create Artifact Registry Repository (Recommended)
```bash
# Create repository
gcloud artifacts repositories create resortslite-repo \
  --repository-format=docker \
  --location=us-central1 \
  --description="ResortsLite Docker images"

# Configure Docker authentication
gcloud auth configure-docker us-central1-docker.pkg.dev
```

### Step 2: Build and Push Docker Image

#### Option A: Using build-push.sh (Linux/macOS)
```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run the script
./scripts/build-push.sh

# Follow the prompts:
# 1. Select registry type (Artifact Registry or Docker Hub)
# 2. Enter registry details
# 3. Enter image tag (e.g., v1.0.0 or latest)
```

#### Option B: Using build-push.bat (Windows)
```cmd
# Run the script
scripts\build-push.bat

# Follow the prompts
```

#### Option C: Manual Build and Push
```bash
# For Google Artifact Registry
IMAGE_URI="us-central1-docker.pkg.dev/YOUR_PROJECT/resortslite-repo/resortslite:latest"

# Build
docker build -t $IMAGE_URI .

# Push
docker push $IMAGE_URI
```

### Step 3: Deploy to GKE

#### Option A: Using deploy-image.sh (Linux/macOS)
```bash
# Make script executable
chmod +x scripts/deploy-image.sh

# Run the script
./scripts/deploy-image.sh

# Follow the prompts:
# 1. Enter GCP Project ID
# 2. Enter GCP Zone
# 3. Enter GKE Cluster Name
# 4. Enter Docker Image URI
# 5. Configure external services (Redis, GCS, etc.)
```

#### Option B: Using deploy-image.bat (Windows)
```cmd
# Run the script
scripts\deploy-image.bat

# Follow the prompts
```

#### Option C: Manual Deployment
```bash
# Set your image URI
IMAGE_URI="us-central1-docker.pkg.dev/YOUR_PROJECT/resortslite-repo/resortslite:latest"

# Update deployment manifest
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml

# Update other placeholders (Redis, GCS, etc.)
sed -i "s|{{REDIS_HOST}}|your-redis-host|g" kubernetes/deployment.yaml
sed -i "s|{{REDIS_PORT}}|6379|g" kubernetes/deployment.yaml
# ... (update other placeholders)

# Apply manifests
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for deployment
kubectl rollout status deployment/resortslite -n resortslite

# Verify
kubectl get pods,svc,ingress -n resortslite
```

### Step 4: Access the Application

#### Get Ingress IP Address
```bash
kubectl get ingress resortslite-ingress -n resortslite

# Wait for EXTERNAL-IP to be assigned (may take 5-10 minutes)
```

#### Access the Application
```bash
# Get the external IP
EXTERNAL_IP=$(kubectl get ingress resortslite-ingress -n resortslite -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# Access the application
curl http://$EXTERNAL_IP/actuator/health

# Or open in browser
echo "Application URL: http://$EXTERNAL_IP"
```

### Step 5: Configure DNS (Optional)
```bash
# Update your DNS records to point to the Ingress IP
# Example: resortslite.example.com -> EXTERNAL_IP

# Update ingress.yaml with your domain
# Then reapply:
kubectl apply -f kubernetes/ingress.yaml
```

---

## Configuration Management

### Environment Variables

The application uses the following environment variables:

#### Application Configuration
- `SERVER_PORT` - Application port (default: 8080)
- `SPRING_PROFILES_ACTIVE` - Spring profile (default: docker)

#### Database Configuration
- `SPRING_DATASOURCE_URL` - Database connection URL
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password

#### Redis Configuration
- `REDIS_HOST` - Redis server hostname
- `REDIS_PORT` - Redis server port (default: 6379)
- `REDIS_PASSWORD` - Redis authentication password

#### Google Cloud Storage Configuration
- `GCP_PROJECT_ID` - GCP project ID
- `GCP_CREDENTIALS_PATH` - Path to GCP credentials JSON file
- `GCS_BUCKET_NAME` - GCS bucket name

#### External Service Endpoints
- `APP_PAYMENT_ENDPOINT` - Payment service URL
- `APP_INVENTORY_ENDPOINT` - Inventory service URL
- `APP_NOTIFICATION_ENDPOINT` - Notification service URL

#### JVM Configuration
- `JAVA_OPTS` - JVM options (default: "-Xmx512m -Xms256m -XX:+UseContainerSupport")

### Kubernetes ConfigMaps and Secrets

#### Create ConfigMap for Application Properties
```bash
kubectl create configmap resortslite-config \
  --from-literal=SERVER_PORT=8080 \
  --from-literal=REDIS_HOST=redis.example.com \
  --from-literal=REDIS_PORT=6379 \
  -n resortslite
```

#### Create Secret for Sensitive Data
```bash
kubectl create secret generic resortslite-secrets \
  --from-literal=REDIS_PASSWORD=your-redis-password \
  --from-literal=GCP_PROJECT_ID=your-gcp-project \
  -n resortslite
```

#### Update Deployment to Use ConfigMap/Secret
Edit `kubernetes/deployment.yaml` to reference ConfigMap and Secret:
```yaml
env:
- name: SERVER_PORT
  valueFrom:
    configMapKeyRef:
      name: resortslite-config
      key: SERVER_PORT
- name: REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: resortslite-secrets
      key: REDIS_PASSWORD
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Pod Fails to Start

**Symptoms:**
- Pods in `CrashLoopBackOff` or `Error` state

**Diagnosis:**
```bash
# Check pod status
kubectl get pods -n resortslite

# View pod logs
kubectl logs -n resortslite -l app=resortslite --tail=100

# Describe pod for events
kubectl describe pod <pod-name> -n resortslite
```

**Common Causes:**
- Missing or incorrect environment variables
- Redis connection failure
- GCS authentication issues
- Insufficient memory/CPU resources

**Solutions:**
- Verify all environment variables are set correctly
- Check Redis connectivity: `kubectl run -it --rm debug --image=redis:alpine --restart=Never -- redis-cli -h $REDIS_HOST ping`
- Verify GCS credentials and permissions
- Increase resource limits in `deployment.yaml`

#### 2. Health Check Failures

**Symptoms:**
- Pods restarting frequently
- Readiness probe failures

**Diagnosis:**
```bash
# Check health endpoint
kubectl exec -it <pod-name> -n resortslite -- wget -O- http://localhost:8080/actuator/health

# View pod events
kubectl describe pod <pod-name> -n resortslite
```

**Solutions:**
- Increase `initialDelaySeconds` in liveness/readiness probes (Java apps need more startup time)
- Verify health endpoint is accessible
- Check application logs for startup errors

#### 3. Service Not Accessible

**Symptoms:**
- Cannot access application via Ingress IP
- Connection timeout errors

**Diagnosis:**
```bash
# Check service
kubectl get svc -n resortslite

# Check ingress
kubectl get ingress -n resortslite

# Check ingress events
kubectl describe ingress resortslite-ingress -n resortslite

# Test service internally
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- curl http://resortslite-service.resortslite.svc.cluster.local/actuator/health
```

**Solutions:**
- Wait for Ingress IP to be provisioned (can take 5-10 minutes)
- Verify firewall rules allow traffic on port 80/443
- Check GKE Ingress controller logs
- Verify service selector matches pod labels

#### 4. Image Pull Errors

**Symptoms:**
- `ImagePullBackOff` or `ErrImagePull` errors

**Diagnosis:**
```bash
kubectl describe pod <pod-name> -n resortslite
```

**Solutions:**
- Verify image URI is correct
- Check Artifact Registry/Docker Hub authentication
- Create image pull secret if using private registry:
```bash
kubectl create secret docker-registry regcred \
  --docker-server=us-central1-docker.pkg.dev \
  --docker-username=_json_key \
  --docker-password="$(cat key.json)" \
  -n resortslite
```

#### 5. Redis Connection Issues

**Symptoms:**
- Application logs show Redis connection errors
- Session management not working

**Diagnosis:**
```bash
# Check Redis connectivity from pod
kubectl exec -it <pod-name> -n resortslite -- sh
# Inside pod:
# telnet $REDIS_HOST $REDIS_PORT
```

**Solutions:**
- Verify Redis host and port are correct
- Check Redis password if authentication is enabled
- Ensure Redis is accessible from GKE cluster (network policies, firewall rules)
- Consider deploying Redis in the same GKE cluster

#### 6. Out of Memory Errors

**Symptoms:**
- Pods killed with `OOMKilled` status
- Application crashes under load

**Diagnosis:**
```bash
# Check pod resource usage
kubectl top pods -n resortslite

# View pod events
kubectl describe pod <pod-name> -n resortslite
```

**Solutions:**
- Increase memory limits in `deployment.yaml`:
```yaml
resources:
  limits:
    memory: "2Gi"
  requests:
    memory: "1Gi"
```
- Adjust JVM heap size: `JAVA_OPTS="-Xmx1536m -Xms768m"`
- Enable horizontal pod autoscaling

### Useful Debugging Commands

```bash
# View all resources in namespace
kubectl get all -n resortslite

# View pod logs (follow mode)
kubectl logs -f -n resortslite -l app=resortslite

# View logs from previous container instance
kubectl logs -n resortslite <pod-name> --previous

# Execute commands in pod
kubectl exec -it <pod-name> -n resortslite -- /bin/sh

# Port forward to local machine
kubectl port-forward -n resortslite svc/resortslite-service 8080:80

# View cluster events
kubectl get events -n resortslite --sort-by='.lastTimestamp'

# Check resource usage
kubectl top nodes
kubectl top pods -n resortslite
```

---

## Security Considerations

### 1. Container Security

#### Use Non-Root User
The Dockerfile already creates and uses a non-root user (`appuser`). Verify:
```bash
kubectl exec -it <pod-name> -n resortslite -- id
```

#### Scan Images for Vulnerabilities
```bash
# Using Google Container Analysis
gcloud container images scan $IMAGE_URI

# Using Trivy
trivy image $IMAGE_URI
```

### 2. Secrets Management

#### Never Hardcode Secrets
- Use Kubernetes Secrets for sensitive data
- Consider using Google Secret Manager:
```bash
# Create secret in Secret Manager
gcloud secrets create redis-password --data-file=-

# Grant access to GKE service account
gcloud secrets add-iam-policy-binding redis-password \
  --member="serviceAccount:YOUR_SERVICE_ACCOUNT" \
  --role="roles/secretmanager.secretAccessor"
```

#### Use Workload Identity (Recommended)
```bash
# Enable Workload Identity on cluster
gcloud container clusters update resortslite-cluster \
  --workload-pool=YOUR_PROJECT.svc.id.goog \
  --zone us-central1-a

# Create Kubernetes service account
kubectl create serviceaccount resortslite-sa -n resortslite

# Bind to GCP service account
gcloud iam service-accounts add-iam-policy-binding \
  YOUR_SERVICE_ACCOUNT@YOUR_PROJECT.iam.gserviceaccount.com \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:YOUR_PROJECT.svc.id.goog[resortslite/resortslite-sa]"

# Annotate Kubernetes service account
kubectl annotate serviceaccount resortslite-sa \
  -n resortslite \
  iam.gke.io/gcp-service-account=YOUR_SERVICE_ACCOUNT@YOUR_PROJECT.iam.gserviceaccount.com
```

### 3. Network Security

#### Use Network Policies
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: resortslite-netpol
  namespace: resortslite
spec:
  podSelector:
    matchLabels:
      app: resortslite
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector: {}
  - to:
    - podSelector: {}
```

#### Enable TLS/HTTPS
```bash
# Create managed certificate
kubectl apply -f - <<EOF
apiVersion: networking.gke.io/v1
kind: ManagedCertificate
metadata:
  name: resortslite-cert
  namespace: resortslite
spec:
  domains:
  - resortslite.example.com
EOF

# Update ingress to use HTTPS
```

### 4. RBAC (Role-Based Access Control)

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: resortslite-role
  namespace: resortslite
rules:
- apiGroups: [""]
  resources: ["pods", "services"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: resortslite-rolebinding
  namespace: resortslite
subjects:
- kind: ServiceAccount
  name: resortslite-sa
  namespace: resortslite
roleRef:
  kind: Role
  name: resortslite-role
  apiGroup: rbac.authorization.k8s.io
```

---

## Technology-Specific Notes

### Spring Boot 2.7.x Configuration

#### 1. Actuator Endpoints
The application exposes Spring Boot Actuator endpoints for monitoring:
- `/actuator/health` - Health check (used by Kubernetes probes)
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

#### 2. Spring Profiles
- `default` - Local development profile
- `docker` - Docker/container profile
- `prod` - Production profile (create `application-prod.properties` if needed)

Activate profile via environment variable:
```bash
SPRING_PROFILES_ACTIVE=prod
```

#### 3. Logging Configuration
Configure logging in `application.properties`:
```properties
logging.level.root=INFO
logging.level.com.demo.resortslite=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
```

For JSON logging (recommended for GKE):
```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.3</version>
</dependency>
```

#### 4. Database Migration
For production, consider using Flyway or Liquibase:
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

### Java 8 Considerations

#### 1. JVM Memory Settings
Java 8 requires explicit container support flags:
```bash
JAVA_OPTS="-Xmx512m -Xms256m -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"
```

#### 2. Garbage Collection
For containerized environments:
```bash
JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

#### 3. Monitoring
Enable JMX for monitoring:
```bash
JAVA_OPTS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
```

### Maven Build Optimization

#### 1. Dependency Caching
The Dockerfile uses layer caching for dependencies:
```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B
```

#### 2. Skip Tests in Docker Build
Tests are skipped during Docker build for faster builds. Run tests separately:
```bash
mvn test
```

#### 3. Multi-Module Projects
For multi-module Maven projects, adjust the Dockerfile:
```dockerfile
COPY pom.xml .
COPY module1/pom.xml module1/
COPY module2/pom.xml module2/
RUN mvn dependency:go-offline -B
COPY . .
RUN mvn clean package -DskipTests -B
```

---

## Scaling and Performance

### Horizontal Pod Autoscaling (HPA)

```bash
# Create HPA
kubectl autoscale deployment resortslite \
  -n resortslite \
  --cpu-percent=70 \
  --min=2 \
  --max=10

# View HPA status
kubectl get hpa -n resortslite
```

### Vertical Pod Autoscaling (VPA)

```yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: resortslite-vpa
  namespace: resortslite
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: resortslite
  updatePolicy:
    updateMode: "Auto"
```

### Performance Tuning

#### 1. JVM Tuning
```bash
JAVA_OPTS="-Xmx1536m -Xms768m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"
```

#### 2. Connection Pooling
Configure HikariCP (default in Spring Boot):
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

#### 3. Redis Connection Pooling
```properties
spring.redis.lettuce.pool.max-active=20
spring.redis.lettuce.pool.max-idle=10
spring.redis.lettuce.pool.min-idle=5
```

---

## Monitoring and Observability

### 1. Google Cloud Monitoring

```bash
# Enable monitoring
gcloud services enable monitoring.googleapis.com

# View metrics in Cloud Console
# https://console.cloud.google.com/monitoring
```

### 2. Prometheus and Grafana

```bash
# Install Prometheus Operator
kubectl create namespace monitoring
helm install prometheus prometheus-community/kube-prometheus-stack -n monitoring

# Expose Grafana
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
```

### 3. Application Logs

```bash
# View logs in Cloud Logging
gcloud logging read "resource.type=k8s_container AND resource.labels.namespace_name=resortslite" --limit 50

# Stream logs
kubectl logs -f -n resortslite -l app=resortslite
```

---

## Backup and Disaster Recovery

### 1. Backup Kubernetes Resources

```bash
# Backup all resources in namespace
kubectl get all -n resortslite -o yaml > resortslite-backup.yaml

# Backup specific resources
kubectl get deployment,service,ingress -n resortslite -o yaml > resortslite-resources.yaml
```

### 2. Disaster Recovery Plan

1. **Regular Backups**: Schedule regular backups of Kubernetes manifests and application data
2. **Multi-Region Deployment**: Deploy to multiple GKE clusters in different regions
3. **Database Backups**: Implement regular database backups (if using persistent database)
4. **Monitoring and Alerts**: Set up alerts for critical failures

---

## Maintenance and Updates

### Rolling Updates

```bash
# Update image
kubectl set image deployment/resortslite resortslite=NEW_IMAGE_URI -n resortslite

# Monitor rollout
kubectl rollout status deployment/resortslite -n resortslite

# Rollback if needed
kubectl rollout undo deployment/resortslite -n resortslite
```

### Blue-Green Deployment

```bash
# Deploy new version (green)
kubectl apply -f kubernetes/deployment-green.yaml

# Test green deployment
kubectl port-forward -n resortslite svc/resortslite-service-green 8081:80

# Switch traffic to green
kubectl patch service resortslite-service -n resortslite -p '{"spec":{"selector":{"version":"green"}}}'

# Remove blue deployment
kubectl delete deployment resortslite-blue -n resortslite
```

---

## Support and Resources

### Documentation
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/)
- [Google Kubernetes Engine Documentation](https://cloud.google.com/kubernetes-engine/docs)
- [Kubernetes Documentation](https://kubernetes.io/docs/)

### Useful Links
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/actuator.html)
- [GKE Best Practices](https://cloud.google.com/kubernetes-engine/docs/best-practices)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)

### Getting Help
- Check application logs: `kubectl logs -n resortslite -l app=resortslite`
- Review Kubernetes events: `kubectl get events -n resortslite`
- Contact support team or open an issue in the repository

---

## Appendix

### A. Complete Environment Variable Reference

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| SERVER_PORT | Application HTTP port | 8080 | No |
| SPRING_PROFILES_ACTIVE | Active Spring profile | docker | No |
| SPRING_DATASOURCE_URL | Database connection URL | jdbc:h2:mem:resortdb | No |
| SPRING_DATASOURCE_USERNAME | Database username | sa | No |
| SPRING_DATASOURCE_PASSWORD | Database password | (empty) | No |
| REDIS_HOST | Redis server hostname | localhost | Yes |
| REDIS_PORT | Redis server port | 6379 | No |
| REDIS_PASSWORD | Redis password | (empty) | No |
| GCP_PROJECT_ID | GCP project ID | - | Yes |
| GCP_CREDENTIALS_PATH | Path to GCP credentials | - | No |
| GCS_BUCKET_NAME | GCS bucket name | resortslite-files | Yes |
| APP_PAYMENT_ENDPOINT | Payment service URL | - | Yes |
| APP_INVENTORY_ENDPOINT | Inventory service URL | - | Yes |
| APP_NOTIFICATION_ENDPOINT | Notification service URL | - | Yes |
| JAVA_OPTS | JVM options | -Xmx512m -Xms256m | No |

### B. Port Reference

| Port | Service | Description |
|------|---------|-------------|
| 8080 | Application | Main HTTP port |
| 8080 | Health Check | Actuator health endpoint |

### C. Health Check Endpoints

| Endpoint | Description | Authentication |
|----------|-------------|----------------|
| /actuator/health | Application health status | None |
| /actuator/info | Application information | None |
| /actuator/metrics | Application metrics | None |

---

**Document Version:** 1.0.0  
**Last Updated:** 2024  
**Maintained By:** DevOps Team
