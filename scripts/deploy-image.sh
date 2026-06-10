#!/bin/bash

# Deploy to AWS ECS Fargate Script for ResortsLite Application
# This script deploys the Docker image to AWS ECS Fargate

set -e
set -o pipefail

echo "=========================================="
echo "ResortsLite - AWS ECS Fargate Deployment"
echo "=========================================="
echo ""

# Prompt for AWS configuration
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
read -p "Enter ECS Cluster Name: " CLUSTER_NAME
read -p "Enter VPC ID: " VPC_ID
read -p "Enter Subnet IDs (comma-separated, at least 2): " SUBNETS_INPUT
read -p "Enter Security Group ID: " SECURITY_GROUP
read -p "Enter Docker Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): " IMAGE_URI

# Convert comma-separated subnets to array
IFS=',' read -ra SUBNETS <<< "$SUBNETS_INPUT"
SUBNET_1=$(echo "${SUBNETS[0]}" | xargs)
SUBNET_2=$(echo "${SUBNETS[1]}" | xargs)

echo ""
echo "=== External Service Configuration ==="
read -p "Enter Redis Host (e.g., redis.example.com): " REDIS_HOST
read -p "Enter Redis Port (default: 6379): " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}
read -sp "Enter Redis Password (leave empty if none): " REDIS_PASSWORD
echo ""

read -p "Enter S3 Bucket Name: " S3_BUCKET_NAME

read -p "Enter Payment Service Endpoint (default: http://payment-svc:9090/charge): " PAYMENT_ENDPOINT
PAYMENT_ENDPOINT=${PAYMENT_ENDPOINT:-http://payment-svc:9090/charge}

read -p "Enter Inventory Service Endpoint (default: http://inventory-svc:8081/rooms): " INVENTORY_ENDPOINT
INVENTORY_ENDPOINT=${INVENTORY_ENDPOINT:-http://inventory-svc:8081/rooms}

read -p "Enter Notification Service Endpoint (default: http://notify-svc:7070/send): " NOTIFICATION_ENDPOINT
NOTIFICATION_ENDPOINT=${NOTIFICATION_ENDPOINT:-http://notify-svc:7070/send}

# Get AWS Account ID
echo ""
echo "Retrieving AWS Account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Account ID: $ACCOUNT_ID"

# Check if cluster exists, create if not
echo ""
echo "Checking ECS cluster..."
aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null 2>&1 || {
    echo "Creating ECS cluster: $CLUSTER_NAME"
    aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
}

# Create CloudWatch log group
echo ""
echo "Creating CloudWatch log group..."
aws logs create-log-group --log-group-name "/ecs/resortslite" --region "$AWS_REGION" 2>/dev/null || echo "Log group already exists"

# Load balancer configuration
echo ""
read -p "Do you need a load balancer for this service? (y/n): " NEED_LB

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    echo ""
    echo "Creating Application Load Balancer and Target Group..."
    
    # Create ALB
    ALB_NAME="resortslite-alb"
    echo "Creating ALB: $ALB_NAME"
    ALB_ARN=$(aws elbv2 create-load-balancer \
        --name "$ALB_NAME" \
        --subnets "$SUBNET_1" "$SUBNET_2" \
        --security-groups "$SECURITY_GROUP" \
        --scheme internet-facing \
        --type application \
        --ip-address-type ipv4 \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].LoadBalancerArn' \
        --output text 2>/dev/null || aws elbv2 describe-load-balancers --names "$ALB_NAME" --region "$AWS_REGION" --query 'LoadBalancers[0].LoadBalancerArn' --output text)
    
    echo "ALB ARN: $ALB_ARN"
    
    # Get ALB DNS name
    ALB_DNS=$(aws elbv2 describe-load-balancers --load-balancer-arns "$ALB_ARN" --region "$AWS_REGION" --query 'LoadBalancers[0].DNSName' --output text)
    
    # Create Target Group with target-type ip (required for Fargate)
    TG_NAME="resortslite-tg"
    echo "Creating Target Group: $TG_NAME"
    TARGET_GROUP_ARN=$(aws elbv2 create-target-group \
        --name "$TG_NAME" \
        --protocol HTTP \
        --port 8080 \
        --vpc-id "$VPC_ID" \
        --target-type ip \
        --health-check-enabled \
        --health-check-protocol HTTP \
        --health-check-path "/actuator/health" \
        --health-check-interval-seconds 30 \
        --health-check-timeout-seconds 5 \
        --healthy-threshold-count 2 \
        --unhealthy-threshold-count 3 \
        --region "$AWS_REGION" \
        --query 'TargetGroups[0].TargetGroupArn' \
        --output text 2>/dev/null || aws elbv2 describe-target-groups --names "$TG_NAME" --region "$AWS_REGION" --query 'TargetGroups[0].TargetGroupArn' --output text)
    
    echo "Target Group ARN: $TARGET_GROUP_ARN"
    
    # Create listener
    echo "Creating ALB listener..."
    aws elbv2 create-listener \
        --load-balancer-arn "$ALB_ARN" \
        --protocol HTTP \
        --port 80 \
        --default-actions Type=forward,TargetGroupArn="$TARGET_GROUP_ARN" \
        --region "$AWS_REGION" 2>/dev/null || echo "Listener already exists"
    
    echo "Load balancer setup complete!"
    echo "ALB DNS: $ALB_DNS"
else
    echo "Skipping load balancer creation"
    TARGET_GROUP_ARN=""
fi

# Replace placeholders in task definition
echo ""
echo "Preparing task definition..."
TASK_DEF_FILE="ecs/task-definition.json"
TASK_DEF_TEMP="/tmp/task-definition-$$.json"

cp "$TASK_DEF_FILE" "$TASK_DEF_TEMP"

sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TASK_DEF_TEMP"
sed -i "s|{{AWS_REGION}}|$AWS_REGION|g" "$TASK_DEF_TEMP"
sed -i "s|{{ACCOUNT_ID}}|$ACCOUNT_ID|g" "$TASK_DEF_TEMP"
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" "$TASK_DEF_TEMP"
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g" "$TASK_DEF_TEMP"
sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" "$TASK_DEF_TEMP"
sed -i "s|{{S3_BUCKET_NAME}}|$S3_BUCKET_NAME|g" "$TASK_DEF_TEMP"
sed -i "s|{{PAYMENT_ENDPOINT}}|$PAYMENT_ENDPOINT|g" "$TASK_DEF_TEMP"
sed -i "s|{{INVENTORY_ENDPOINT}}|$INVENTORY_ENDPOINT|g" "$TASK_DEF_TEMP"
sed -i "s|{{NOTIFICATION_ENDPOINT}}|$NOTIFICATION_ENDPOINT|g" "$TASK_DEF_TEMP"

# Register task definition
echo "Registering task definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition \
    --cli-input-json file://"$TASK_DEF_TEMP" \
    --region "$AWS_REGION" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

echo "Task Definition ARN: $TASK_DEF_ARN"

# Clean up temp file
rm -f "$TASK_DEF_TEMP"

# Prepare service definition
echo ""
echo "Preparing service definition..."
SERVICE_DEF_FILE="ecs/service-definition.json"
SERVICE_DEF_TEMP="/tmp/service-definition-$$.json"

cp "$SERVICE_DEF_FILE" "$SERVICE_DEF_TEMP"

sed -i "s|{{CLUSTER_NAME}}|$CLUSTER_NAME|g" "$SERVICE_DEF_TEMP"
sed -i "s|{{SUBNET_1}}|$SUBNET_1|g" "$SERVICE_DEF_TEMP"
sed -i "s|{{SUBNET_2}}|$SUBNET_2|g" "$SERVICE_DEF_TEMP"
sed -i "s|{{SECURITY_GROUP}}|$SECURITY_GROUP|g" "$SERVICE_DEF_TEMP"

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    sed -i "s|{{TARGET_GROUP_ARN}}|$TARGET_GROUP_ARN|g" "$SERVICE_DEF_TEMP"
else
    # Remove loadBalancers section if no LB needed
    sed -i '/"loadBalancers":/,/],/d' "$SERVICE_DEF_TEMP"
    sed -i '/"healthCheckGracePeriodSeconds":/d' "$SERVICE_DEF_TEMP"
fi

# Check if service exists
echo ""
echo "Checking if service exists..."
SERVICE_NAME="resortslite-service"
EXISTING_SERVICE=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[?status==`ACTIVE`].serviceName' \
    --output text 2>/dev/null || echo "")

if [ -z "$EXISTING_SERVICE" ] || [ "$EXISTING_SERVICE" == "None" ]; then
    echo "Creating new ECS service..."
    aws ecs create-service \
        --cli-input-json file://"$SERVICE_DEF_TEMP" \
        --region "$AWS_REGION"
else
    echo "Updating existing ECS service..."
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --task-definition "$TASK_DEF_ARN" \
        --desired-count 2 \
        --region "$AWS_REGION"
fi

# Clean up temp file
rm -f "$SERVICE_DEF_TEMP"

# Wait for service stability
echo ""
echo "Waiting for service to become stable (this may take a few minutes)..."
aws ecs wait services-stable \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION"

# Verify deployment
echo ""
echo "=========================================="
echo "Deployment Status"
echo "=========================================="
aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[0].[serviceName,status,runningCount,desiredCount]' \
    --output table

echo ""
echo "=========================================="
echo "SUCCESS!"
echo "=========================================="
echo "Service: $SERVICE_NAME"
echo "Cluster: $CLUSTER_NAME"
echo "Region: $AWS_REGION"
echo "Task Definition: $TASK_DEF_ARN"

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    echo ""
    echo "Application URL: http://$ALB_DNS"
    echo "Health Check: http://$ALB_DNS/actuator/health"
fi

echo ""
echo "CloudWatch Logs: /ecs/resortslite"
echo ""
echo "To view logs:"
echo "  aws logs tail /ecs/resortslite --follow --region $AWS_REGION"
echo ""
echo "To check service status:"
echo "  aws ecs describe-services --cluster $CLUSTER_NAME --services $SERVICE_NAME --region $AWS_REGION"
echo ""
