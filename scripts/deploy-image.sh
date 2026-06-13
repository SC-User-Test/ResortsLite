#!/bin/bash
set -e
set -o pipefail

# Deploy ResortsLite to AWS ECS Fargate
# This script deploys the Docker image to AWS ECS Fargate

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
read -p "Enter ECR Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): " IMAGE_URI

# Convert comma-separated subnets to array
IFS=',' read -ra SUBNETS <<< "$SUBNETS_INPUT"
SUBNET_1=$(echo "${SUBNETS[0]}" | xargs)
SUBNET_2=$(echo "${SUBNETS[1]}" | xargs)

echo ""
echo "Configuration Summary:"
echo "  Region: $AWS_REGION"
echo "  Cluster: $CLUSTER_NAME"
echo "  VPC: $VPC_ID"
echo "  Subnets: $SUBNET_1, $SUBNET_2"
echo "  Security Group: $SECURITY_GROUP"
echo "  Image: $IMAGE_URI"
echo ""

# Get AWS Account ID
echo "Retrieving AWS Account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Account ID: $ACCOUNT_ID"
echo ""

# Check if ECS cluster exists, create if not
echo "Checking if ECS cluster exists..."
aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null 2>&1 || {
    echo "Cluster does not exist. Creating ECS cluster: $CLUSTER_NAME"
    aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
    echo "ECS cluster created successfully"
}
echo ""

# Prompt for load balancer
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
        --output text)
    
    echo "ALB created: $ALB_ARN"
    
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
        --output text)
    
    echo "Target Group created: $TARGET_GROUP_ARN"
    
    # Create Listener
    echo "Creating ALB Listener..."
    aws elbv2 create-listener \
        --load-balancer-arn "$ALB_ARN" \
        --protocol HTTP \
        --port 80 \
        --default-actions Type=forward,TargetGroupArn="$TARGET_GROUP_ARN" \
        --region "$AWS_REGION" >/dev/null
    
    echo "ALB Listener created"
    
    # Get ALB DNS name
    ALB_DNS=$(aws elbv2 describe-load-balancers \
        --load-balancer-arns "$ALB_ARN" \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].DNSName' \
        --output text)
    
    echo "Load Balancer DNS: $ALB_DNS"
    echo ""
else
    echo "Skipping load balancer creation"
    TARGET_GROUP_ARN=""
    echo ""
fi

# Prompt for environment variables
echo "Environment Variable Configuration:"
echo "Using default values from application.properties"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
S3_BUCKET_NAME="${S3_BUCKET_NAME:-resortslite-reports}"
PAYMENT_ENDPOINT="${PAYMENT_ENDPOINT:-http://payment-svc.internal:9090/charge}"
INVENTORY_ENDPOINT="${INVENTORY_ENDPOINT:-http://inventory-svc.internal:8081/rooms}"
NOTIFICATION_ENDPOINT="${NOTIFICATION_ENDPOINT:-http://notify.internal:7070/send}"

echo ""
echo "Creating CloudWatch Log Group..."
aws logs create-log-group --log-group-name "/ecs/resortslite" --region "$AWS_REGION" 2>/dev/null || echo "Log group already exists"
echo ""

# Create temporary files with replaced placeholders
TEMP_TASK_DEF=$(mktemp)
TEMP_SERVICE_DEF=$(mktemp)

# Replace placeholders in task definition
sed "s|{{IMAGE_URI}}|$IMAGE_URI|g; \
     s|{{AWS_REGION}}|$AWS_REGION|g; \
     s|{{ACCOUNT_ID}}|$ACCOUNT_ID|g; \
     s|{{REDIS_HOST}}|$REDIS_HOST|g; \
     s|{{REDIS_PORT}}|$REDIS_PORT|g; \
     s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g; \
     s|{{S3_BUCKET_NAME}}|$S3_BUCKET_NAME|g; \
     s|{{PAYMENT_ENDPOINT}}|$PAYMENT_ENDPOINT|g; \
     s|{{INVENTORY_ENDPOINT}}|$INVENTORY_ENDPOINT|g; \
     s|{{NOTIFICATION_ENDPOINT}}|$NOTIFICATION_ENDPOINT|g" \
     ecs/task-definition.json > "$TEMP_TASK_DEF"

# Replace placeholders in service definition
if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    sed "s|{{CLUSTER_NAME}}|$CLUSTER_NAME|g; \
         s|{{SUBNET_1}}|$SUBNET_1|g; \
         s|{{SUBNET_2}}|$SUBNET_2|g; \
         s|{{SECURITY_GROUP}}|$SECURITY_GROUP|g; \
         s|{{TARGET_GROUP_ARN}}|$TARGET_GROUP_ARN|g" \
         ecs/service-definition.json > "$TEMP_SERVICE_DEF"
else
    # Remove loadBalancers section if no LB
    sed "s|{{CLUSTER_NAME}}|$CLUSTER_NAME|g; \
         s|{{SUBNET_1}}|$SUBNET_1|g; \
         s|{{SUBNET_2}}|$SUBNET_2|g; \
         s|{{SECURITY_GROUP}}|$SECURITY_GROUP|g" \
         ecs/service-definition.json | \
         jq 'del(.loadBalancers, .healthCheckGracePeriodSeconds)' > "$TEMP_SERVICE_DEF"
fi

echo "Registering ECS Task Definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition \
    --cli-input-json file://"$TEMP_TASK_DEF" \
    --region "$AWS_REGION" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

echo "Task Definition registered: $TASK_DEF_ARN"
echo ""

# Check if service exists
SERVICE_NAME="resortslite-service"
echo "Checking if ECS service exists..."
EXISTING_SERVICE=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[?status==`ACTIVE`].serviceName' \
    --output text)

if [ -z "$EXISTING_SERVICE" ] || [ "$EXISTING_SERVICE" == "None" ]; then
    echo "Service does not exist. Creating new service..."
    aws ecs create-service \
        --cli-input-json file://"$TEMP_SERVICE_DEF" \
        --region "$AWS_REGION" >/dev/null
    echo "ECS service created successfully"
else
    echo "Service exists. Updating service..."
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --task-definition "$TASK_DEF_ARN" \
        --region "$AWS_REGION" >/dev/null
    echo "ECS service updated successfully"
fi

echo ""
echo "Waiting for service to become stable..."
aws ecs wait services-stable \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION"

echo ""
echo "=========================================="
echo "Deployment Completed Successfully"
echo "=========================================="
echo ""

# Display service details
echo "Service Details:"
aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[0].[serviceName,status,runningCount,desiredCount]' \
    --output table

echo ""
echo "CloudWatch Logs: /ecs/resortslite"
echo "Region: $AWS_REGION"

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    echo ""
    echo "Application URL: http://$ALB_DNS"
    echo "Note: It may take a few minutes for the ALB to become healthy"
fi

echo ""
echo "To view logs:"
echo "  aws logs tail /ecs/resortslite --follow --region $AWS_REGION"
echo ""

# Cleanup temporary files
rm -f "$TEMP_TASK_DEF" "$TEMP_SERVICE_DEF"
