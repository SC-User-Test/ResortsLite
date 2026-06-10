# ResortsLite - AWS ECS Fargate Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
5. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
6. [ECS Task Definition Explained](#ecs-task-definition-explained)
7. [ECS Service Configuration](#ecs-service-configuration)
8. [Deployment to AWS ECS Fargate](#deployment-to-aws-ecs-fargate)
9. [Configuration Management](#configuration-management)
10. [Monitoring and Logging](#monitoring-and-logging)
11. [Troubleshooting](#troubleshooting)
12. [Security Considerations](#security-considerations)
13. [Scaling and Management](#scaling-and-management)

---

## Overview

ResortsLite is a Spring Boot 2.7.18 application running on Java 8, designed for containerized deployment on AWS ECS Fargate. This guide provides comprehensive instructions for building, deploying, and managing the application in a production environment.

**Application Details:**
- **Framework:** Spring Boot 2.7.18
- **Java Version:** 8
- **Build Tool:** Maven
- **Application Port:** 8080
- **Health Endpoint:** `/actuator/health`
- **Target Platform:** AWS ECS Fargate

---

## Prerequisites

### Required Software
- **Docker:** Version 20.10 or higher
- **Docker Compose:** Version 2.0 or higher
- **AWS CLI:** Version 2.x
- **Maven:** Version 3.6+ (for local builds)
- **Java JDK:** Version 8 (for local development)

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with permissions for:
  - ECS (create/update clusters, services, task definitions)
  - ECR (create repositories, push images)
  - CloudWatch Logs (create log groups)
  - VPC (describe subnets, security groups)
  - IAM (pass role for task execution)
  - Elastic Load Balancing (create/manage ALB and target groups)

### External Services
The application requires the following external services:
- **Redis:** For distributed session management
- **AWS S3:** For file storage
- **Payment Service:** External payment processing endpoint
- **Inventory Service:** Room inventory management endpoint
- **Notification Service:** Email/SMS notification endpoint

---

## Local Development Setup

### 1. Clone the Repository
```bash
cd /path/to/mono-rahcomp
```

### 2. Build the Application Locally
```bash
mvn clean package -DskipTests
```

### 3. Run with Docker Compose
```bash
# Set environment variables
export REDIS_HOST=your-redis-host
export REDIS_PORT=6379
export REDIS_PASSWORD=your-redis-password
export S3_BUCKET_NAME=your-s3-bucket
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key

# Start the application
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the application
docker-compose down
```

### 4. Access the Application
- **Application:** http://localhost:8080
- **Health Check:** http://localhost:8080/actuator/health
- **H2 Console:** http://localhost:8080/h2-console

---

## Building and Pushing Docker Images

### Using Linux/macOS (build-push.sh)

```bash
# Make the script executable
chmod +x scripts/build-push.sh

# Run the script
./scripts/build-push.sh
```

**Script Workflow:**
1. Prompts for image tag (default: latest)
2. Asks for registry selection (AWS ECR or Docker Hub)
3. Collects registry credentials
4. Builds the Docker image
5. Authenticates with the selected registry
6. Pushes the image to the registry

**Example - AWS ECR:**
```bash
./scripts/build-push.sh
# Enter image tag: v1.0.0
# Select registry: 1 (AWS ECR)
# Enter AWS Region: us-east-1
# Enter AWS Account ID: 123456789012
# Enter ECR Repository Name: resortslite
```

**Example - Docker Hub:**
```bash
./scripts/build-push.sh
# Enter image tag: v1.0.0
# Select registry: 2 (Docker Hub)
# Enter Docker Hub username: myusername
# Enter Docker Hub password: ********
```

### Using Windows (build-push.bat)

```cmd
# Run the script
scripts\build-push.bat
```

Follow the same prompts as the Linux/macOS version.

---

## AWS ECS Fargate Prerequisites

### 1. VPC Configuration
Ensure you have a VPC with:
- At least 2 subnets in different availability zones
- Internet Gateway attached (for public access)
- Route tables configured for internet access

```bash
# List available VPCs
aws ec2 describe-vpcs --query 'Vpcs[*].[VpcId,CidrBlock,Tags[?Key==`Name`].Value|[0]]' --output table

# List subnets in a VPC
aws ec2 describe-subnets --filters "Name=vpc-id,Values=vpc-xxxxx" --query 'Subnets[*].[SubnetId,AvailabilityZone,CidrBlock]' --output table
```

### 2. Security Group Configuration
Create a security group that allows:
- **Inbound:** Port 8080 (application) from ALB security group or 0.0.0.0/0
- **Inbound:** Port 80 (ALB) from 0.0.0.0/0
- **Outbound:** All traffic (for external service access)

```bash
# Create security group
aws ec2 create-security-group \
  --group-name resortslite-sg \
  --description "Security group for ResortsLite ECS tasks" \
  --vpc-id vpc-xxxxx

# Add inbound rule for application port
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxx \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0

# Add inbound rule for ALB
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxx \
  --protocol tcp \
  --port 80 \
  --cidr 0.0.0.0/0
```

### 3. IAM Roles

#### ECS Task Execution Role
This role allows ECS to pull images from ECR and write logs to CloudWatch.

```bash
# Create trust policy file
cat > ecs-task-execution-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

# Create the role
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach AWS managed policy
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

#### ECS Task Role
This role allows the application to access AWS services (S3, etc.).

```bash
# Create task role
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Create policy for S3 access
cat > s3-access-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::resortslite-reports",
        "arn:aws:s3:::resortslite-reports/*"
      ]
    }
  ]
}
EOF

# Attach policy to task role
aws iam put-role-policy \
  --role-name ecsTaskRole \
  --policy-name S3AccessPolicy \
  --policy-document file://s3-access-policy.json
```

### 4. CloudWatch Log Group
```bash
# Create log group
aws logs create-log-group --log-group-name /ecs/resortslite --region us-east-1

# Set retention policy (optional)
aws logs put-retention-policy \
  --log-group-name /ecs/resortslite \
  --retention-in-days 7 \
  --region us-east-1
```

---

## ECS Task Definition Explained

The task definition (`ecs/task-definition.json`) defines how your container runs on ECS Fargate.

### Key Components

#### 1. Launch Type Configuration
```json
{
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc"
}
```
- **FARGATE:** Serverless compute engine for containers
- **awsvpc:** Each task gets its own elastic network interface

#### 2. CPU and Memory
```json
{
  "cpu": "512",
  "memory": "1024"
}
```

**Valid Fargate CPU/Memory Combinations:**
| CPU (vCPU) | Memory (MB) |
|------------|-------------|
| 256 (.25)  | 512, 1024, 2048 |
| 512 (.5)   | 1024, 2048, 3072, 4096 |
| 1024 (1)   | 2048-8192 (increments of 1024) |
| 2048 (2)   | 4096-16384 (increments of 1024) |
| 4096 (4)   | 8192-30720 (increments of 1024) |

#### 3. Container Definition
```json
{
  "containerDefinitions": [
    {
      "name": "resortslite",
      "image": "{{IMAGE_URI}}",
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ]
    }
  ]
}
```

#### 4. Environment Variables
Configure application behavior through environment variables:
- `SERVER_PORT`: Application port (8080)
- `SPRING_PROFILES_ACTIVE`: Active Spring profile
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`: Redis configuration
- `S3_BUCKET_NAME`, `AWS_REGION`: AWS S3 configuration
- `JAVA_OPTS`: JVM tuning parameters

#### 5. Logging Configuration
```json
{
  "logConfiguration": {
    "logDriver": "awslogs",
    "options": {
      "awslogs-group": "/ecs/resortslite",
      "awslogs-region": "{{AWS_REGION}}",
      "awslogs-stream-prefix": "ecs"
    }
  }
}
```

---

## ECS Service Configuration

The service definition (`ecs/service-definition.json`) manages the deployment and scaling of your tasks.

### Key Components

#### 1. Service Configuration
```json
{
  "serviceName": "resortslite-service",
  "desiredCount": 2,
  "launchType": "FARGATE"
}
```
- **desiredCount:** Number of task instances to run (2 for high availability)

#### 2. Network Configuration
```json
{
  "networkConfiguration": {
    "awsvpcConfiguration": {
      "subnets": ["subnet-xxxxx", "subnet-yyyyy"],
      "securityGroups": ["sg-xxxxx"],
      "assignPublicIp": "ENABLED"
    }
  }
}
```
- **subnets:** At least 2 subnets in different AZs
- **assignPublicIp:** ENABLED for internet access

#### 3. Load Balancer Integration
```json
{
  "loadBalancers": [
    {
      "targetGroupArn": "arn:aws:elasticloadbalancing:...",
      "containerName": "resortslite",
      "containerPort": 8080
    }
  ],
  "healthCheckGracePeriodSeconds": 300
}
```

#### 4. Deployment Configuration
```json
{
  "deploymentConfiguration": {
    "maximumPercent": 200,
    "minimumHealthyPercent": 50,
    "deploymentCircuitBreaker": {
      "enable": true,
      "rollback": true
    }
  }
}
```
- **maximumPercent:** Maximum tasks during deployment (200% = 4 tasks)
- **minimumHealthyPercent:** Minimum healthy tasks (50% = 1 task)
- **deploymentCircuitBreaker:** Automatic rollback on failure

---

## Deployment to AWS ECS Fargate

### Using Linux/macOS (deploy-image.sh)

```bash
# Make the script executable
chmod +x scripts/deploy-image.sh

# Run the deployment script
./scripts/deploy-image.sh
```

**Script Workflow:**
1. Prompts for AWS configuration (region, cluster, VPC, subnets, security group)
2. Prompts for Docker image URI
3. Prompts for external service configuration (Redis, S3, etc.)
4. Asks if load balancer is needed
5. Creates/updates ECS cluster
6. Creates CloudWatch log group
7. Creates Application Load Balancer and Target Group (if requested)
8. Registers task definition with provided configuration
9. Creates or updates ECS service
10. Waits for service to become stable
11. Displays deployment status and access URLs

**Example Deployment:**
```bash
./scripts/deploy-image.sh

# AWS Configuration
Enter AWS Region: us-east-1
Enter ECS Cluster Name: resortslite-cluster
Enter VPC ID: vpc-xxxxx
Enter Subnet IDs: subnet-xxxxx,subnet-yyyyy
Enter Security Group ID: sg-xxxxx
Enter Docker Image URI: 123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:v1.0.0

# External Services
Enter Redis Host: redis.example.com
Enter Redis Port: 6379
Enter Redis Password: ********
Enter S3 Bucket Name: resortslite-reports
Enter Payment Service Endpoint: http://payment-svc:9090/charge
Enter Inventory Service Endpoint: http://inventory-svc:8081/rooms
Enter Notification Service Endpoint: http://notify-svc:7070/send

# Load Balancer
Do you need a load balancer? (y/n): y
```

### Using Windows (deploy-image.bat)

```cmd
# Run the deployment script
scripts\deploy-image.bat
```

Follow the same prompts as the Linux/macOS version.

---

## Configuration Management

### Environment Variables

The application uses environment variables for configuration. Update these in the task definition:

#### Database Configuration
```bash
DB_URL=jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1
DB_USERNAME=sa
DB_PASSWORD=
```

#### Redis Configuration
```bash
REDIS_HOST=redis.example.com
REDIS_PORT=6379
REDIS_PASSWORD=your-password
```

#### AWS S3 Configuration
```bash
S3_BUCKET_NAME=resortslite-reports
AWS_REGION=us-east-1
```

#### External Service Endpoints
```bash
PAYMENT_ENDPOINT=http://payment-svc:9090/charge
INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms
NOTIFICATION_ENDPOINT=http://notify-svc:7070/send
```

#### JVM Configuration
```bash
JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

### Spring Profiles

The application supports multiple Spring profiles:
- **default:** Local development with H2 database
- **docker:** Docker container deployment
- **production:** Production environment with external services

Activate a profile using:
```bash
SPRING_PROFILES_ACTIVE=docker
```

---

## Monitoring and Logging

### CloudWatch Logs

View application logs in CloudWatch:

```bash
# Tail logs in real-time
aws logs tail /ecs/resortslite --follow --region us-east-1

# View logs for specific time range
aws logs tail /ecs/resortslite \
  --since 1h \
  --region us-east-1

# Filter logs by pattern
aws logs tail /ecs/resortslite \
  --follow \
  --filter-pattern "ERROR" \
  --region us-east-1
```

### ECS Service Metrics

Monitor service health and performance:

```bash
# Describe service status
aws ecs describe-services \
  --cluster resortslite-cluster \
  --services resortslite-service \
  --region us-east-1

# List running tasks
aws ecs list-tasks \
  --cluster resortslite-cluster \
  --service-name resortslite-service \
  --region us-east-1

# Describe task details
aws ecs describe-tasks \
  --cluster resortslite-cluster \
  --tasks task-id \
  --region us-east-1
```

### Application Health Checks

The application exposes health endpoints:
- **Health:** `/actuator/health`
- **Info:** `/actuator/info`

Access via load balancer:
```bash
curl http://your-alb-dns/actuator/health
```

### CloudWatch Alarms

Create alarms for critical metrics:

```bash
# CPU utilization alarm
aws cloudwatch put-metric-alarm \
  --alarm-name resortslite-high-cpu \
  --alarm-description "Alert when CPU exceeds 80%" \
  --metric-name CPUUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --dimensions Name=ServiceName,Value=resortslite-service Name=ClusterName,Value=resortslite-cluster

# Memory utilization alarm
aws cloudwatch put-metric-alarm \
  --alarm-name resortslite-high-memory \
  --alarm-description "Alert when memory exceeds 80%" \
  --metric-name MemoryUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --dimensions Name=ServiceName,Value=resortslite-service Name=ClusterName,Value=resortslite-cluster
```

---

## Troubleshooting

### Common Issues

#### 1. Task Fails to Start

**Symptoms:**
- Tasks start and immediately stop
- "Essential container exited" error

**Solutions:**
```bash
# Check task logs
aws logs tail /ecs/resortslite --since 30m --region us-east-1

# Describe stopped tasks
aws ecs describe-tasks \
  --cluster resortslite-cluster \
  --tasks task-id \
  --region us-east-1 \
  --query 'tasks[0].stoppedReason'

# Common causes:
# - Invalid environment variables
# - Missing IAM permissions
# - Application startup errors
# - Health check failures
```

#### 2. Cannot Pull Image from ECR

**Symptoms:**
- "CannotPullContainerError"
- "Image not found"

**Solutions:**
```bash
# Verify image exists in ECR
aws ecr describe-images \
  --repository-name resortslite \
  --region us-east-1

# Check task execution role permissions
aws iam get-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-name AmazonECSTaskExecutionRolePolicy

# Verify ECR authentication
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-east-1.amazonaws.com
```

#### 3. Network Connectivity Issues

**Symptoms:**
- Cannot access external services (Redis, S3)
- Timeout errors

**Solutions:**
```bash
# Check security group rules
aws ec2 describe-security-groups \
  --group-ids sg-xxxxx \
  --region us-east-1

# Verify subnet route tables
aws ec2 describe-route-tables \
  --filters "Name=association.subnet-id,Values=subnet-xxxxx" \
  --region us-east-1

# Ensure NAT Gateway or Internet Gateway is configured
# Check VPC endpoints for AWS services
```

#### 4. Invalid CPU/Memory Configuration

**Symptoms:**
- "Invalid CPU or memory value specified"

**Solutions:**
- Use valid Fargate CPU/memory combinations (see table above)
- Default safe values: cpu="512", memory="1024"

#### 5. Service Fails to Reach Steady State

**Symptoms:**
- Service stuck in "DRAINING" or "PENDING"
- Tasks repeatedly fail health checks

**Solutions:**
```bash
# Check service events
aws ecs describe-services \
  --cluster resortslite-cluster \
  --services resortslite-service \
  --region us-east-1 \
  --query 'services[0].events[0:10]'

# Verify health check configuration
# Increase healthCheckGracePeriodSeconds if needed
# Check application logs for startup errors
```

#### 6. Load Balancer Health Check Failures

**Symptoms:**
- Targets marked as unhealthy
- 502/503 errors from ALB

**Solutions:**
```bash
# Check target health
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:... \
  --region us-east-1

# Verify health check path
curl http://task-ip:8080/actuator/health

# Common causes:
# - Incorrect health check path
# - Application not listening on correct port
# - Security group blocking ALB traffic
# - Application startup time exceeds grace period
```

### Debug Commands

```bash
# Get task details
aws ecs describe-tasks \
  --cluster resortslite-cluster \
  --tasks task-id \
  --region us-east-1

# Get container logs
aws logs get-log-events \
  --log-group-name /ecs/resortslite \
  --log-stream-name ecs/resortslite/task-id \
  --region us-east-1

# Execute command in running container
aws ecs execute-command \
  --cluster resortslite-cluster \
  --task task-id \
  --container resortslite \
  --interactive \
  --command "/bin/sh" \
  --region us-east-1
```

---

## Security Considerations

### 1. Container Security

- **Non-root user:** Application runs as non-root user (appuser)
- **Read-only root filesystem:** Consider enabling for enhanced security
- **Minimal base image:** Uses Amazon Corretto 8 (slim image)

### 2. Network Security

- **Security groups:** Restrict inbound traffic to necessary ports only
- **Private subnets:** Consider deploying tasks in private subnets with NAT Gateway
- **VPC endpoints:** Use VPC endpoints for AWS services (S3, ECR, CloudWatch)

### 3. Secrets Management

**Use AWS Secrets Manager or Parameter Store for sensitive data:**

```bash
# Store secret in Secrets Manager
aws secretsmanager create-secret \
  --name resortslite/redis-password \
  --secret-string "your-password" \
  --region us-east-1

# Reference in task definition
{
  "secrets": [
    {
      "name": "REDIS_PASSWORD",
      "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:resortslite/redis-password"
    }
  ]
}
```

### 4. IAM Best Practices

- **Least privilege:** Grant only necessary permissions
- **Task role:** Use task role for application AWS access
- **Execution role:** Use execution role for ECS infrastructure access
- **Rotate credentials:** Regularly rotate access keys and passwords

### 5. Image Security

- **Scan images:** Use ECR image scanning
- **Update dependencies:** Regularly update base images and dependencies
- **Vulnerability management:** Address CVEs promptly

```bash
# Enable ECR image scanning
aws ecr put-image-scanning-configuration \
  --repository-name resortslite \
  --image-scanning-configuration scanOnPush=true \
  --region us-east-1

# Get scan results
aws ecr describe-image-scan-findings \
  --repository-name resortslite \
  --image-id imageTag=v1.0.0 \
  --region us-east-1
```

---

## Scaling and Management

### Auto Scaling

Configure ECS Service Auto Scaling based on metrics:

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/resortslite-cluster/resortslite-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10 \
  --region us-east-1

# Create scaling policy (CPU-based)
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/resortslite-cluster/resortslite-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name cpu-scaling-policy \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
    },
    "ScaleInCooldown": 300,
    "ScaleOutCooldown": 60
  }' \
  --region us-east-1
```

### Manual Scaling

```bash
# Scale up
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --desired-count 4 \
  --region us-east-1

# Scale down
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --desired-count 2 \
  --region us-east-1
```

### Blue/Green Deployments

Use AWS CodeDeploy for blue/green deployments:

```bash
# Create CodeDeploy application
aws deploy create-application \
  --application-name resortslite-app \
  --compute-platform ECS \
  --region us-east-1

# Create deployment group
aws deploy create-deployment-group \
  --application-name resortslite-app \
  --deployment-group-name resortslite-dg \
  --service-role-arn arn:aws:iam::123456789012:role/CodeDeployServiceRole \
  --ecs-services clusterName=resortslite-cluster,serviceName=resortslite-service \
  --load-balancer-info targetGroupPairInfoList=[{targetGroups=[{name=resortslite-tg-blue},{name=resortslite-tg-green}],prodTrafficRoute={listenerArns=[arn:aws:elasticloadbalancing:...]}}] \
  --deployment-style deploymentType=BLUE_GREEN,deploymentOption=WITH_TRAFFIC_CONTROL \
  --blue-green-deployment-configuration '{
    "terminateBlueInstancesOnDeploymentSuccess": {
      "action": "TERMINATE",
      "terminationWaitTimeInMinutes": 5
    },
    "deploymentReadyOption": {
      "actionOnTimeout": "CONTINUE_DEPLOYMENT"
    }
  }' \
  --region us-east-1
```

### Rolling Updates

Update service with new task definition:

```bash
# Register new task definition
aws ecs register-task-definition \
  --cli-input-json file://ecs/task-definition.json \
  --region us-east-1

# Update service
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --task-definition resortslite-task:2 \
  --force-new-deployment \
  --region us-east-1
```

### Rollback

```bash
# Rollback to previous task definition
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --task-definition resortslite-task:1 \
  --force-new-deployment \
  --region us-east-1
```

---

## Technology-Specific Notes

### Spring Boot Configuration

#### JVM Tuning for Containers
The application uses container-aware JVM settings:
```bash
JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

**Recommendations:**
- **Heap Size:** Set to 75% of container memory
- **GC Algorithm:** Use G1GC for better pause times
- **Container Support:** Enable `-XX:+UseContainerSupport`

#### Spring Boot Actuator
Health endpoints are exposed for monitoring:
- `/actuator/health` - Application health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

#### Session Management
The application uses Redis for distributed session management:
```properties
spring.session.store-type=redis
spring.session.redis.namespace=resortslite:session
```

### Maven Build Optimization

The Dockerfile uses multi-stage builds for optimization:
1. **Builder stage:** Downloads dependencies and builds JAR
2. **Runtime stage:** Copies only the JAR file

**Dependency caching:**
```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B
```

### Java 8 Considerations

- **Base Image:** Amazon Corretto 8 (as specified)
- **Security:** Ensure Java 8 is patched and up-to-date
- **Migration:** Consider upgrading to Java 11 or 17 for long-term support

---

## Additional Resources

### AWS Documentation
- [ECS Fargate Documentation](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [ECS Task Definitions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html)
- [ECS Service Auto Scaling](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html)

### Spring Boot Documentation
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/actuator.html)
- [Spring Session](https://docs.spring.io/spring-session/docs/current/reference/html5/)

### Docker Documentation
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Multi-stage Builds](https://docs.docker.com/build/building/multi-stage/)

---

## Support and Maintenance

### Regular Maintenance Tasks

1. **Update Dependencies:** Regularly update Spring Boot and dependencies
2. **Patch Base Images:** Update Docker base images for security patches
3. **Review Logs:** Monitor CloudWatch logs for errors and warnings
4. **Performance Tuning:** Adjust JVM settings based on metrics
5. **Cost Optimization:** Review and optimize resource allocation

### Monitoring Checklist

- [ ] CloudWatch alarms configured
- [ ] Log retention policies set
- [ ] Health checks passing
- [ ] Auto-scaling policies tested
- [ ] Backup and disaster recovery plan in place

---

## Conclusion

This deployment guide provides comprehensive instructions for deploying ResortsLite to AWS ECS Fargate. Follow the steps carefully, and refer to the troubleshooting section for common issues. For additional support, consult the AWS and Spring Boot documentation linked above.

**Quick Start Summary:**
1. Build and push Docker image: `./scripts/build-push.sh`
2. Deploy to ECS: `./scripts/deploy-image.sh`
3. Monitor logs: `aws logs tail /ecs/resortslite --follow`
4. Access application: `http://your-alb-dns`

Happy deploying! 🚀
