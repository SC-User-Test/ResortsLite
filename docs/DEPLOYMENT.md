# ResortsLite — AWS ECS Fargate Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development with Docker Compose](#local-development-with-docker-compose)
4. [Build and Push Docker Image](#build-and-push-docker-image)
5. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
6. [ECS Task Definition Explained](#ecs-task-definition-explained)
7. [ECS Service Configuration](#ecs-service-configuration)
8. [ECS Fargate Deployment Walkthrough](#ecs-fargate-deployment-walkthrough)
9. [ECS-Specific Troubleshooting](#ecs-specific-troubleshooting)
10. [ECS Fargate Scaling and Management](#ecs-fargate-scaling-and-management)
11. [Configuration Management](#configuration-management)
12. [Security Considerations](#security-considerations)
13. [Java-Specific Notes](#java-specific-notes)

---

## Overview

**Application**: ResortsLite  
**Framework**: Spring Boot 2.7.x  
**Java Version**: 8  
**Build Tool**: Maven  
**Package Type**: JAR (executable)  
**Application Port**: 8080  
**Health Endpoint**: `/actuator/health` and `/health`  
**Target Platform**: AWS ECS Fargate  

ResortsLite is a legacy resort booking REST API modernised for cloud-native deployment on AWS ECS Fargate. It uses stateless JWT authentication, Amazon ElastiCache for Memcached (distributed caching), Amazon EFS for persistent report storage, and ECS Service Connect for inter-service communication.

---

## Prerequisites

### Local Development
- Docker Desktop 24.x or later
- Docker Compose v2.x or later
- Java 8 JDK (for local builds outside Docker)
- Maven 3.9.x (for local builds outside Docker)

### AWS Deployment
- AWS CLI v2 configured with appropriate credentials (`aws configure`)
- IAM permissions: `ecs:*`, `ecr:*`, `iam:PassRole`, `logs:*`, `elbv2:*`, `sts:GetCallerIdentity`
- An existing AWS VPC with at least two subnets in different Availability Zones
- Security group allowing inbound TCP on port 8080 (and port 80/443 if using ALB)

---

## Local Development with Docker Compose

### 1. Clone and configure

```bash
git clone <repository-url>
cd CompRL
```

### 2. Set environment variables

Create a `.env` file in the project root (never commit this file):

```env
JWT_SECRET=your-strong-secret-at-least-32-chars
MEMCACHED_ENDPOINT=localhost:11211
PAYMENT_SERVICE_URL=http://payment-service:9090/payments/charge
INVENTORY_SERVICE_URL=http://inventory-service:8081/rooms/available
REPORT_BASE_PATH=/mnt/efs/reports/
```

### 3. Start the application

```bash
docker compose up --build
```

### 4. Verify the application is running

```bash
# Application health
curl http://localhost:8080/actuator/health

# Custom health endpoint
curl http://localhost:8080/health

# Create a booking
curl -X POST "http://localhost:8080/api/bookings/create?guestName=John&roomType=STANDARD&checkIn=2024-06-01&checkOut=2024-06-05"
```

### 5. Stop the application

```bash
docker compose down
```

---

## Build and Push Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

The script will prompt you to:
1. Enter an image tag (default: `latest`)
2. Select a registry: **AWS ECR** or **Docker Hub**
3. Provide registry-specific credentials and repository details

### Windows

```cmd
scripts\build-push.bat
```

### Manual Docker Build

```bash
# Build the image
docker build -t resortsLite:latest .

# Tag for ECR
docker tag resortsLite:latest <ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/resortsLite:latest

# Push to ECR
aws ecr get-login-password --region <REGION> | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com
docker push <ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/resortsLite:latest
```

---

## AWS ECS Fargate Prerequisites

### 1. IAM Roles

#### ECS Task Execution Role (`ecsTaskExecutionRole`)
This role allows ECS to pull images from ECR and write logs to CloudWatch.

```bash
# Create the role (if it doesn't exist)
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

# Attach the managed policy
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

#### ECS Task Role (`ecsTaskRole`)
This role grants the running container permissions to access AWS services (SSM, Secrets Manager, EFS).

```bash
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

# Attach policies for SSM Parameter Store and Secrets Manager
aws iam attach-role-policy \
  --role-name ecsTaskRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMReadOnlyAccess
```

### 2. AWS Secrets Manager — JWT Secret

```bash
aws secretsmanager create-secret \
  --name "resortsLite/jwt-secret" \
  --secret-string "your-strong-jwt-secret-at-least-32-chars" \
  --region <REGION>
```

Update the `valueFrom` ARN in `ecs/task-definition.json` with the actual secret ARN.

### 3. Amazon ElastiCache for Memcached

```bash
# Create a Memcached cluster in the same VPC as ECS
aws elasticache create-cache-cluster \
  --cache-cluster-id resortsLite-cache \
  --engine memcached \
  --cache-node-type cache.t3.micro \
  --num-cache-nodes 1 \
  --region <REGION>
```

Note the cluster endpoint and set it as `MEMCACHED_ENDPOINT` in the task definition.

### 4. Amazon EFS (for report storage)

```bash
# Create EFS file system
aws efs create-file-system \
  --performance-mode generalPurpose \
  --region <REGION>
```

Note the `FileSystemId` and update `{{EFS_FILE_SYSTEM_ID}}` in `ecs/task-definition.json`.

### 5. CloudWatch Log Group

```bash
aws logs create-log-group \
  --log-group-name /ecs/resortsLite \
  --region <REGION>
```

---

## ECS Task Definition Explained

The task definition (`ecs/task-definition.json`) configures how ECS runs the ResortsLite container.

### Key Fields

| Field | Value | Description |
|-------|-------|-------------|
| `family` | `resortsLite-task` | Task definition family name |
| `requiresCompatibilities` | `["FARGATE"]` | Fargate launch type |
| `networkMode` | `awsvpc` | Required for Fargate; each task gets its own ENI |
| `cpu` | `"512"` | 0.5 vCPU |
| `memory` | `"1024"` | 1 GB RAM |
| `executionRoleArn` | `ecsTaskExecutionRole` | Allows ECR pull and CloudWatch logging |
| `taskRoleArn` | `ecsTaskRole` | Grants container access to AWS services |

### Valid Fargate CPU/Memory Combinations

| CPU | Valid Memory Options |
|-----|---------------------|
| 256 (.25 vCPU) | 512, 1024, 2048 MB |
| **512 (.5 vCPU)** | **1024, 2048, 3072, 4096 MB** ← *Used by ResortsLite* |
| 1024 (1 vCPU) | 2048–8192 MB |
| 2048 (2 vCPU) | 4096–16384 MB |
| 4096 (4 vCPU) | 8192–30720 MB |

### Container Environment Variables

| Variable | Description | Source |
|----------|-------------|--------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | Task definition |
| `JAVA_OPTS` | JVM flags | Task definition |
| `JWT_SECRET` | JWT signing key | AWS Secrets Manager |
| `MEMCACHED_ENDPOINT` | ElastiCache endpoint | Task definition |
| `INVENTORY_SERVICE_URL` | Inventory service URL | ECS Service Connect |
| `PAYMENT_SERVICE_URL` | Payment service URL | ECS Service Connect |
| `REPORT_BASE_PATH` | EFS mount path | Task definition |

---

## ECS Service Configuration

The service definition (`ecs/service-definition.json`) controls how ECS manages running tasks.

### Key Settings

- **`launchType: FARGATE`** — Serverless container execution; no EC2 instances to manage
- **`desiredCount: 2`** — Two tasks for high availability across AZs
- **`networkMode: awsvpc`** — Each task gets a dedicated ENI with its own private IP
- **`assignPublicIp: ENABLED`** — Required if tasks need to pull images from ECR without NAT Gateway
- **`maximumPercent: 200`** — Allows up to 4 tasks during rolling deployments
- **`minimumHealthyPercent: 50`** — Keeps at least 1 task running during deployments

---

## ECS Fargate Deployment Walkthrough

### Step 1: Build and push the Docker image

```bash
./scripts/build-push.sh
# Select AWS ECR, provide region and account details
```

### Step 2: Run the deployment script

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script will prompt for:
- AWS Region
- ECS Cluster name
- VPC ID, Subnet IDs, Security Group ID
- ECR Image URI
- EFS File System ID
- Memcached endpoint
- Whether to create an Application Load Balancer

### Step 3: Verify the deployment

```bash
# Check service status
aws ecs describe-services \
  --cluster resortsLite-cluster \
  --services resortsLite-service \
  --region <REGION>

# List running tasks
aws ecs list-tasks \
  --cluster resortsLite-cluster \
  --region <REGION>

# View application logs
aws logs tail /ecs/resortsLite --follow --region <REGION>
```

### Step 4: Test the application

```bash
# If using ALB
curl http://<ALB_DNS>/actuator/health

# If accessing task directly (requires public IP or VPN)
curl http://<TASK_PUBLIC_IP>:8080/actuator/health
```

---

## ECS-Specific Troubleshooting

### Task fails to start

```bash
# Describe stopped tasks to see failure reason
aws ecs describe-tasks \
  --cluster resortsLite-cluster \
  --tasks <TASK_ARN> \
  --region <REGION> \
  --query "tasks[0].stoppedReason"
```

Common causes:
- **Image pull failure**: Verify ECR permissions on `ecsTaskExecutionRole`
- **Port conflict**: Ensure security group allows inbound on port 8080
- **Memory exceeded**: Increase `memory` in task definition (use valid Fargate combinations)
- **Secret not found**: Verify Secrets Manager ARN in task definition

### Network connectivity issues

- Ensure subnets have a route to the internet (via Internet Gateway or NAT Gateway)
- Verify security group allows outbound traffic on port 443 (for ECR, Secrets Manager)
- For Fargate tasks in private subnets, configure VPC endpoints for ECR, S3, and CloudWatch

### CPU/Memory errors

```
INVALID: The provided CPU value is not valid for the provided memory value
```

Use only valid Fargate combinations. Default: `cpu: "512"`, `memory: "1024"`.

### Service not stabilising

```bash
# Check service events
aws ecs describe-services \
  --cluster resortsLite-cluster \
  --services resortsLite-service \
  --region <REGION> \
  --query "services[0].events[:5]"
```

### Memcached connection failures

The application logs `[cz-java-0070] Memcached write/read failed` but continues operating. This is expected if ElastiCache is not yet provisioned. The application degrades gracefully without caching.

---

## ECS Fargate Scaling and Management

### Manual scaling

```bash
aws ecs update-service \
  --cluster resortsLite-cluster \
  --service resortsLite-service \
  --desired-count 4 \
  --region <REGION>
```

### Auto Scaling

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/resortsLite-cluster/resortsLite-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10

# Create CPU-based scaling policy
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/resortsLite-cluster/resortsLite-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name resortsLite-cpu-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
    },
    "ScaleInCooldown": 300,
    "ScaleOutCooldown": 60
  }'
```

### Blue/Green Deployments

For zero-downtime deployments, use AWS CodeDeploy with ECS:

1. Change `deploymentController` in service definition to `{"type": "CODE_DEPLOY"}`
2. Create a CodeDeploy application and deployment group targeting the ECS service
3. Use `appspec.yaml` to define the deployment lifecycle

### Rolling Updates

The current configuration uses rolling updates:
- `maximumPercent: 200` — ECS can run up to 4 tasks during deployment
- `minimumHealthyPercent: 50` — At least 1 task stays healthy

---

## Configuration Management

### Environment-specific configuration

Use Spring profiles to manage environment-specific settings:

```bash
# Development
SPRING_PROFILES_ACTIVE=dev

# Production (ECS)
SPRING_PROFILES_ACTIVE=docker
```

### Secrets Management

Never store secrets in the Docker image or task definition environment variables in plaintext. Use:

1. **AWS Secrets Manager** for sensitive values (JWT_SECRET, database passwords)
2. **AWS SSM Parameter Store** for non-sensitive configuration (MEMCACHED_ENDPOINT)

Reference in task definition:
```json
"secrets": [
  {
    "name": "JWT_SECRET",
    "valueFrom": "arn:aws:secretsmanager:<REGION>:<ACCOUNT_ID>:secret:resortsLite/jwt-secret"
  }
]
```

---

## Security Considerations

1. **Non-root container user**: The Dockerfile creates and uses `appuser` (non-root) for security
2. **JWT authentication**: Stateless JWT tokens replace server-side sessions; secret stored in Secrets Manager
3. **No hardcoded credentials**: All secrets injected at runtime via ECS task definition
4. **Network isolation**: Use private subnets with NAT Gateway for production workloads
5. **Security groups**: Restrict inbound to ALB only; restrict outbound to required services
6. **ECR image scanning**: Enable ECR image scanning on push to detect vulnerabilities
7. **EFS encryption**: EFS volume configured with `transitEncryption: ENABLED`
8. **Log retention**: Set CloudWatch log group retention policy (e.g., 30 days)

```bash
aws logs put-retention-policy \
  --log-group-name /ecs/resortsLite \
  --retention-in-days 30 \
  --region <REGION>
```

---

## Java-Specific Notes

### JVM Configuration

The container uses these JVM flags (set via `JAVA_OPTS`):

| Flag | Purpose |
|------|---------|
| `-Xmx512m` | Maximum heap size (matches container memory limit) |
| `-Xms256m` | Initial heap size |
| `-XX:+UseContainerSupport` | Enables JVM container awareness (Java 8u191+) |
| `-XX:MaxRAMPercentage=75.0` | Limits heap to 75% of container RAM |
| `-XX:+UnlockExperimentalVMOptions` | Required for some container-aware flags on Java 8 |
| `-Djava.security.egd=file:/dev/./urandom` | Faster random number generation in containers |
| `-Dfile.encoding=UTF-8` | Consistent character encoding |
| `-Duser.timezone=UTC` | Consistent timezone |

### Spring Boot Actuator

Health endpoints exposed:
- `GET /actuator/health` — Liveness and readiness probe (Spring Boot Actuator)
- `GET /health` — Custom health endpoint (HealthController)
- `GET /actuator/info` — Application information

### Spring Boot Startup Time

Java 8 + Spring Boot 2.7.x typically takes 10–20 seconds to start in a container. The ECS health check grace period is set to 60 seconds to accommodate JVM warm-up.

### Graceful Shutdown

The Dockerfile uses `exec java $JAVA_OPTS -jar /app/app.jar` (exec form) to ensure the JVM receives SIGTERM directly from the container runtime, enabling Spring Boot's graceful shutdown.

To enable graceful shutdown in Spring Boot 2.7.x, add to `application.properties`:
```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

### Dependency Caching in Docker

The Dockerfile copies `pom.xml` first and runs `mvn dependency:go-offline` before copying source code. This ensures Maven dependencies are cached in a separate Docker layer and only re-downloaded when `pom.xml` changes.

### Multi-Architecture Builds

For ARM-based Fargate (Graviton2), build with:
```bash
docker buildx build --platform linux/arm64 -t resortsLite:latest .
```

Update the task definition `runtimePlatform`:
```json
"runtimePlatform": {
  "cpuArchitecture": "ARM64",
  "operatingSystemFamily": "LINUX"
}
```
