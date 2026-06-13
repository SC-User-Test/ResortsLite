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

ResortsLite is a Spring Boot 2.7.18 application built with Java 8, designed for containerized deployment on AWS ECS Fargate. This guide provides comprehensive instructions for building, deploying, and managing the application in a cloud-native environment.

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
- **Git:** For version control
- **Java 8 JDK:** For local development (optional)
- **Maven 3.6+:** For local builds (optional)

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with permissions for:
  - ECS (Full access)
  - ECR (Full access)
  - CloudWatch Logs (Write access)
  - VPC (Read access)
  - IAM (Role creation/management)
  - Elastic Load Balancing (if using ALB)

### Install AWS CLI

**Linux/macOS:**
```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
```

**Windows:**
Download and run the AWS CLI MSI installer from: https://aws.amazon.com/cli/

**Configure AWS CLI:**
```bash
aws configure
# Enter your AWS Access Key ID
# Enter your AWS Secret Access Key
# Enter default region (e.g., us-east-1)
# Enter default output format (json)
```

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd RLcompTest
```

### 2. Build with Docker Compose

The application includes a `docker-compose.yml` file for local development:

```bash
# Build and start the application
docker-compose up --build

# Run in detached mode
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the application
docker-compose down
```

### 3. Access the Application

Once running, access the application at:
- **Application:** http://localhost:8080
- **Health Check:** http://localhost:8080/actuator/health
- **H2 Console:** http://localhost:8080/h2-console

### 4. Local Development with Maven (Optional)

```bash
# Build the application
mvn clean package -DskipTests

# Run the application
java -jar target/resortsLite-1.0.0.jar

# Or use Spring Boot Maven plugin
mvn spring-boot:run
```

---

## Building and Pushing Docker Images

### Using the Build Script (Recommended)

The project includes automated build scripts for both Linux/macOS and Windows.

#### Linux/macOS:
```bash
cd scripts
chmod +x build-push.sh
./build-push.sh
```

#### Windows:
```cmd
cd scripts
build-push.bat
```

### Script Features:
1. **Interactive Registry Selection:**
   - AWS ECR (Elastic Container Registry)
   - Docker Hub

2. **Automatic ECR Repository Creation:**
   - Checks if repository exists
   - Creates repository if needed

3. **Tag Sanitization:**
   - Converts to lowercase
   - Replaces invalid characters
   - Defaults to 'latest' if empty

4. **Authentication Handling:**
   - ECR: Uses AWS CLI credentials
   - Docker Hub: Prompts for username/password

### Manual Build Process

If you prefer to build manually:

```bash
# Build the Docker image
docker build -t resortslite:latest .

# Tag for ECR
docker tag resortslite:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest

# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com

# Push to ECR
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest
```

---

## AWS ECS Fargate Prerequisites

### 1. VPC Configuration

You need a VPC with at least 2 subnets in different availability zones:

```bash
# Create VPC (if needed)
aws ec2 create-vpc --cidr-block 10.0.0.0/16 --region us-east-1

# Create subnets
aws ec2 create-subnet --vpc-id vpc-xxxxx --cidr-block 10.0.1.0/24 --availability-zone us-east-1a
aws ec2 create-subnet --vpc-id vpc-xxxxx --cidr-block 10.0.2.0/24 --availability-zone us-east-1b
```

### 2. Security Group Configuration

Create a security group that allows:
- **Inbound:** Port 8080 (application) from ALB or 0.0.0.0/0
- **Inbound:** Port 80 (ALB) from 0.0.0.0/0 (if using load balancer)
- **Outbound:** All traffic (for external service calls)

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
```

### 3. IAM Roles

#### ECS Task Execution Role (Required)

This role allows ECS to pull images from ECR and write logs to CloudWatch:

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

#### ECS Task Role (Optional)

This role grants permissions to the application (e.g., S3 access):

```bash
# Create task role
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach policies for S3, etc.
aws iam attach-role-policy \
  --role-name ecsTaskRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonS3FullAccess
```

### 4. CloudWatch Log Group

Create a log group for application logs:

```bash
aws logs create-log-group --log-group-name /ecs/resortslite --region us-east-1
```

---

## ECS Task Definition Explained

The task definition (`ecs/task-definition.json`) defines how your container runs on Fargate.

### Key Components:

#### 1. Launch Type Configuration
```json
{
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc"
}
```
- **FARGATE:** Serverless compute engine
- **awsvpc:** Each task gets its own ENI and private IP

#### 2. CPU and Memory

**Valid Fargate CPU/Memory Combinations:**

| CPU (vCPU) | Memory (MB) |
|------------|-------------|
| 256 (.25)  | 512, 1024, 2048 |
| 512 (.5)   | 1024, 2048, 3072, 4096 |
| 1024 (1)   | 2048-8192 (increments of 1024) |
| 2048 (2)   | 4096-16384 (increments of 1024) |
| 4096 (4)   | 8192-30720 (increments of 1024) |

**Default Configuration:**
```json
{
  "cpu": "512",
  "memory": "1024"
}
```

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
      ],
      "environment": [...],
      "logConfiguration": {...}
    }
  ]
}
```

#### 4. Environment Variables

The task definition includes environment variables for:
- **Application Configuration:** SERVER_PORT, SPRING_PROFILES_ACTIVE
- **JVM Settings:** JAVA_OPTS
- **Database Configuration:** DB_URL, DB_USERNAME, DB_PASSWORD
- **Redis Configuration:** REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
- **AWS S3:** S3_BUCKET_NAME, AWS_REGION
- **External Services:** PAYMENT_ENDPOINT, INVENTORY_ENDPOINT, NOTIFICATION_ENDPOINT

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

The service definition (`ecs/service-definition.json`) manages task deployment and scaling.

### Key Components:

#### 1. Service Configuration
```json
{
  "serviceName": "resortslite-service",
  "cluster": "{{CLUSTER_NAME}}",
  "taskDefinition": "resortslite-task",
  "desiredCount": 2,
  "launchType": "FARGATE"
}
```

#### 2. Network Configuration
```json
{
  "networkConfiguration": {
    "awsvpcConfiguration": {
      "subnets": ["{{SUBNET_1}}", "{{SUBNET_2}}"],
      "securityGroups": ["{{SECURITY_GROUP}}"],
      "assignPublicIp": "ENABLED"
    }
  }
}
```

#### 3. Deployment Configuration
```json
{
  "deploymentConfiguration": {
    "maximumPercent": 200,
    "minimumHealthyPercent": 50
  }
}
```
- **maximumPercent:** Allows 2x tasks during deployment
- **minimumHealthyPercent:** Maintains at least 50% capacity

#### 4. Load Balancer Integration (Optional)
```json
{
  "loadBalancers": [
    {
      "targetGroupArn": "{{TARGET_GROUP_ARN}}",
      "containerName": "resortslite",
      "containerPort": 8080
    }
  ],
  "healthCheckGracePeriodSeconds": 300
}
```

---

## Deployment to AWS ECS Fargate

### Using the Deployment Script (Recommended)

#### Linux/macOS:
```bash
cd scripts
chmod +x deploy-image.sh
./deploy-image.sh
```

#### Windows:
```cmd
cd scripts
deploy-image.bat
```

### Script Workflow:

1. **Prompts for Configuration:**
   - AWS Region
   - ECS Cluster Name
   - VPC ID
   - Subnet IDs (comma-separated)
   - Security Group ID
   - ECR Image URI

2. **Retrieves AWS Account ID:**
   - Uses AWS CLI to get account ID

3. **Cluster Management:**
   - Checks if cluster exists
   - Creates cluster if needed

4. **Load Balancer Setup (Optional):**
   - Prompts if load balancer is needed
   - Creates Application Load Balancer
   - Creates Target Group (target-type: ip)
   - Creates Listener (HTTP:80)
   - Configures health checks

5. **Environment Configuration:**
   - Uses default values from application.properties
   - Can be customized via environment variables

6. **CloudWatch Logs:**
   - Creates log group if not exists

7. **Task Definition Registration:**
   - Replaces placeholders with actual values
   - Registers task definition with ECS

8. **Service Deployment:**
   - Checks if service exists
   - Creates new service or updates existing
   - Waits for service to become stable

9. **Verification:**
   - Displays service details
   - Shows CloudWatch log group
   - Provides application URL (if ALB created)

### Manual Deployment Process

If you prefer manual deployment:

```bash
# 1. Register task definition
aws ecs register-task-definition \
  --cli-input-json file://ecs/task-definition.json \
  --region us-east-1

# 2. Create ECS cluster
aws ecs create-cluster \
  --cluster-name resortslite-cluster \
  --region us-east-1

# 3. Create service
aws ecs create-service \
  --cli-input-json file://ecs/service-definition.json \
  --region us-east-1

# 4. Wait for service to stabilize
aws ecs wait services-stable \
  --cluster resortslite-cluster \
  --services resortslite-service \
  --region us-east-1
```

---

## Configuration Management

### Environment Variables

The application uses environment variables for configuration. These can be set in:

1. **Task Definition:** Static configuration
2. **Service Definition:** Service-level overrides
3. **Parameter Store/Secrets Manager:** Sensitive data

### Key Configuration Areas:

#### Database Configuration
```bash
DB_URL=jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1
DB_USERNAME=sa
DB_PASSWORD=
```

#### Redis Configuration
```bash
REDIS_HOST=your-redis-host.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
```

#### AWS S3 Configuration
```bash
S3_BUCKET_NAME=resortslite-reports
AWS_REGION=us-east-1
```

#### External Service Endpoints
```bash
PAYMENT_ENDPOINT=http://payment-svc.internal:9090/charge
INVENTORY_ENDPOINT=http://inventory-svc.internal:8081/rooms
NOTIFICATION_ENDPOINT=http://notify.internal:7070/send
```

### Using AWS Systems Manager Parameter Store

Store sensitive configuration:

```bash
# Store parameter
aws ssm put-parameter \
  --name /resortslite/redis/password \
  --value "your-password" \
  --type SecureString \
  --region us-east-1

# Reference in task definition
{
  "secrets": [
    {
      "name": "REDIS_PASSWORD",
      "valueFrom": "/resortslite/redis/password"
    }
  ]
}
```

---

## Monitoring and Logging

### CloudWatch Logs

View application logs:

```bash
# Tail logs in real-time
aws logs tail /ecs/resortslite --follow --region us-east-1

# View specific log stream
aws logs get-log-events \
  --log-group-name /ecs/resortslite \
  --log-stream-name ecs/resortslite/task-id \
  --region us-east-1
```

### CloudWatch Metrics

ECS automatically publishes metrics:
- **CPUUtilization:** Task CPU usage
- **MemoryUtilization:** Task memory usage
- **NetworkRxBytes/NetworkTxBytes:** Network traffic

### Application Health Checks

The application exposes Spring Boot Actuator endpoints:

- **Health:** `/actuator/health`
- **Info:** `/actuator/info`
- **Metrics:** `/actuator/metrics`

### Setting Up CloudWatch Alarms

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
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Task Fails to Start

**Symptoms:**
- Tasks transition to STOPPED state immediately
- Error: "CannotPullContainerError"

**Solutions:**
```bash
# Check task stopped reason
aws ecs describe-tasks \
  --cluster resortslite-cluster \
  --tasks task-id \
  --region us-east-1

# Verify ECR permissions
aws ecr get-login-password --region us-east-1

# Check execution role has ECR permissions
aws iam get-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-name ECRAccessPolicy
```

#### 2. Invalid CPU/Memory Configuration

**Symptoms:**
- Error: "Invalid CPU or memory value specified"

**Solution:**
Ensure you're using valid Fargate combinations:
```json
{
  "cpu": "512",
  "memory": "1024"
}
```

#### 3. Network Issues

**Symptoms:**
- Tasks can't reach external services
- Health checks failing

**Solutions:**
```bash
# Verify security group allows outbound traffic
aws ec2 describe-security-groups --group-ids sg-xxxxx

# Check subnet route table has internet gateway
aws ec2 describe-route-tables --filters "Name=association.subnet-id,Values=subnet-xxxxx"

# Ensure assignPublicIp is ENABLED for public subnets
```

#### 4. Application Not Accessible via ALB

**Symptoms:**
- ALB returns 503 Service Unavailable
- Target health checks failing

**Solutions:**
```bash
# Check target group health
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:...

# Verify security group allows traffic from ALB
# Ensure health check path is correct: /actuator/health

# Check application logs
aws logs tail /ecs/resortslite --follow
```

#### 5. High Memory Usage / OOM Errors

**Symptoms:**
- Tasks being killed due to memory
- Error: "OutOfMemoryError"

**Solutions:**
```bash
# Increase task memory in task definition
# Adjust JVM heap settings
JAVA_OPTS="-Xmx768m -Xms256m -XX:MaxRAMPercentage=75.0"

# Monitor memory usage
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name MemoryUtilization \
  --dimensions Name=ServiceName,Value=resortslite-service \
  --start-time 2024-01-01T00:00:00Z \
  --end-time 2024-01-01T23:59:59Z \
  --period 3600 \
  --statistics Average
```

#### 6. Service Update Stuck

**Symptoms:**
- Service update doesn't complete
- Old tasks not draining

**Solutions:**
```bash
# Force new deployment
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --force-new-deployment \
  --region us-east-1

# Check service events
aws ecs describe-services \
  --cluster resortslite-cluster \
  --services resortslite-service \
  --region us-east-1 \
  --query 'services[0].events'
```

### Debugging Commands

```bash
# List all tasks in cluster
aws ecs list-tasks --cluster resortslite-cluster --region us-east-1

# Describe specific task
aws ecs describe-tasks --cluster resortslite-cluster --tasks task-id --region us-east-1

# View service events
aws ecs describe-services --cluster resortslite-cluster --services resortslite-service --region us-east-1

# Check CloudWatch logs
aws logs tail /ecs/resortslite --follow --region us-east-1

# Verify task definition
aws ecs describe-task-definition --task-definition resortslite-task --region us-east-1
```

---

## Security Considerations

### 1. IAM Roles and Permissions

**Principle of Least Privilege:**
- Grant only necessary permissions
- Use separate roles for execution and task
- Regularly audit IAM policies

**Task Execution Role:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "*"
    }
  ]
}
```

### 2. Network Security

**Security Group Best Practices:**
- Restrict inbound traffic to necessary ports only
- Use security group references instead of CIDR blocks
- Enable VPC Flow Logs for network monitoring

**Example Security Group Rules:**
```bash
# Allow inbound from ALB only
aws ec2 authorize-security-group-ingress \
  --group-id sg-task \
  --protocol tcp \
  --port 8080 \
  --source-group sg-alb

# Allow outbound to specific services
aws ec2 authorize-security-group-egress \
  --group-id sg-task \
  --protocol tcp \
  --port 6379 \
  --destination-group sg-redis
```

### 3. Secrets Management

**Use AWS Secrets Manager or Parameter Store:**

```bash
# Store secret
aws secretsmanager create-secret \
  --name resortslite/db/password \
  --secret-string "your-password" \
  --region us-east-1

# Reference in task definition
{
  "secrets": [
    {
      "name": "DB_PASSWORD",
      "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:resortslite/db/password"
    }
  ]
}
```

### 4. Container Security

**Best Practices:**
- Use non-root user in Dockerfile (already implemented)
- Scan images for vulnerabilities
- Keep base images updated
- Use specific image tags (not 'latest')

**Scan Docker Image:**
```bash
# Using AWS ECR image scanning
aws ecr start-image-scan \
  --repository-name resortslite \
  --image-id imageTag=latest \
  --region us-east-1

# View scan results
aws ecr describe-image-scan-findings \
  --repository-name resortslite \
  --image-id imageTag=latest \
  --region us-east-1
```

### 5. Encryption

**Enable Encryption:**
- **ECS Task Volumes:** Use encrypted EBS volumes
- **CloudWatch Logs:** Enable log group encryption
- **Secrets:** Use KMS encryption for secrets
- **ALB:** Use HTTPS with ACM certificates

```bash
# Create encrypted log group
aws logs create-log-group \
  --log-group-name /ecs/resortslite \
  --kms-key-id arn:aws:kms:us-east-1:123456789:key/xxxxx \
  --region us-east-1
```

---

## Scaling and Management

### Auto Scaling

#### 1. Service Auto Scaling

Configure auto scaling based on metrics:

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
  --target-tracking-scaling-policy-configuration file://scaling-policy.json \
  --region us-east-1
```

**scaling-policy.json:**
```json
{
  "TargetValue": 70.0,
  "PredefinedMetricSpecification": {
    "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
  },
  "ScaleInCooldown": 300,
  "ScaleOutCooldown": 60
}
```

#### 2. Scheduled Scaling

Scale based on time:

```bash
aws application-autoscaling put-scheduled-action \
  --service-namespace ecs \
  --resource-id service/resortslite-cluster/resortslite-service \
  --scalable-dimension ecs:service:DesiredCount \
  --scheduled-action-name scale-up-morning \
  --schedule "cron(0 8 * * ? *)" \
  --scalable-target-action MinCapacity=5,MaxCapacity=10 \
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
  --deployment-config-name CodeDeployDefault.ECSAllAtOnce \
  --service-role-arn arn:aws:iam::123456789:role/CodeDeployServiceRole \
  --ecs-services clusterName=resortslite-cluster,serviceName=resortslite-service \
  --load-balancer-info targetGroupInfoList=[{name=resortslite-tg}] \
  --blue-green-deployment-configuration ... \
  --region us-east-1
```

### Rolling Updates

Update service with new task definition:

```bash
# Update service
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --task-definition resortslite-task:2 \
  --region us-east-1

# Monitor deployment
aws ecs describe-services \
  --cluster resortslite-cluster \
  --services resortslite-service \
  --region us-east-1 \
  --query 'services[0].deployments'
```

### Capacity Providers

Use Fargate Spot for cost optimization:

```bash
# Create capacity provider strategy
aws ecs put-cluster-capacity-providers \
  --cluster resortslite-cluster \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy \
    capacityProvider=FARGATE,weight=1,base=2 \
    capacityProvider=FARGATE_SPOT,weight=4 \
  --region us-east-1
```

---

## Technology-Specific Notes

### Spring Boot Configuration

#### 1. Profiles

The application uses Spring profiles for environment-specific configuration:

```bash
# Set active profile
SPRING_PROFILES_ACTIVE=docker
```

#### 2. Actuator Endpoints

Health and monitoring endpoints:
- `/actuator/health` - Application health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

#### 3. JVM Tuning for Containers

Recommended JVM options:
```bash
JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions"
```

**Explanation:**
- `-Xmx512m`: Maximum heap size
- `-Xms256m`: Initial heap size
- `-XX:+UseContainerSupport`: Enable container awareness
- `-XX:MaxRAMPercentage=75.0`: Use 75% of container memory for heap

#### 4. Graceful Shutdown

Spring Boot handles graceful shutdown automatically. Configure timeout:

```properties
# application.properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

### Redis Session Management

The application uses Redis for distributed session management:

```properties
spring.session.store-type=redis
spring.redis.host=${REDIS_HOST}
spring.redis.port=${REDIS_PORT}
```

**Setup Amazon ElastiCache for Redis:**
```bash
aws elasticache create-cache-cluster \
  --cache-cluster-id resortslite-redis \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --num-cache-nodes 1 \
  --region us-east-1
```

### AWS S3 Integration

The application uses S3 for file storage:

```properties
aws.s3.bucket-name=${S3_BUCKET_NAME}
aws.s3.region=${AWS_REGION}
```

**Create S3 Bucket:**
```bash
aws s3 mb s3://resortslite-reports --region us-east-1
```

---

## Additional Resources

### AWS Documentation
- [ECS Fargate Documentation](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [ECS Task Definitions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html)
- [ECS Service Auto Scaling](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html)

### Spring Boot Resources
- [Spring Boot Docker Guide](https://spring.io/guides/gs/spring-boot-docker/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Spring Session with Redis](https://docs.spring.io/spring-session/docs/current/reference/html5/)

### Docker Resources
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Multi-stage Builds](https://docs.docker.com/develop/develop-images/multistage-build/)

---

## Support and Maintenance

### Regular Maintenance Tasks

1. **Update Dependencies:**
   ```bash
   # Update Maven dependencies
   mvn versions:display-dependency-updates
   ```

2. **Rotate Secrets:**
   ```bash
   # Update secrets in Secrets Manager
   aws secretsmanager update-secret \
     --secret-id resortslite/db/password \
     --secret-string "new-password"
   ```

3. **Review CloudWatch Logs:**
   ```bash
   # Check for errors
   aws logs filter-log-events \
     --log-group-name /ecs/resortslite \
     --filter-pattern "ERROR"
   ```

4. **Monitor Costs:**
   ```bash
   # View ECS costs
   aws ce get-cost-and-usage \
     --time-period Start=2024-01-01,End=2024-01-31 \
     --granularity MONTHLY \
     --metrics BlendedCost \
     --filter file://cost-filter.json
   ```

### Getting Help

- **AWS Support:** https://console.aws.amazon.com/support/
- **Spring Boot Community:** https://spring.io/community
- **Docker Community:** https://forums.docker.com/

---

## Conclusion

This deployment guide provides comprehensive instructions for deploying ResortsLite to AWS ECS Fargate. Follow the steps carefully, and refer to the troubleshooting section if you encounter issues.

For production deployments, ensure you:
- ✅ Use proper IAM roles with least privilege
- ✅ Enable encryption for data at rest and in transit
- ✅ Configure auto scaling for high availability
- ✅ Set up monitoring and alerting
- ✅ Implement proper backup and disaster recovery
- ✅ Follow security best practices

**Happy Deploying! 🚀**
