#!/usr/bin/env bash
# =============================================================================
# deploy-image.sh — Deploy ResortsLite to AWS ECS Fargate
# Usage: ./scripts/deploy-image.sh
# Run from the repository root directory.
# =============================================================================
set -e
set -o pipefail

SERVICE_NAME="resortsLite-service"
TASK_FAMILY="resortsLite-task"
LOG_GROUP="/ecs/resortsLite"
TASK_DEF_FILE="ecs/task-definition.json"
SERVICE_DEF_FILE="ecs/service-definition.json"

echo "=============================================="
echo "  ResortsLite — AWS ECS Fargate Deployment"
echo "=============================================="
echo ""

# ── AWS Region ────────────────────────────────────────────────────────────────
read -rp "AWS Region [us-east-1]: " AWS_REGION
AWS_REGION="${AWS_REGION:-us-east-1}"

# ── ECS Cluster ───────────────────────────────────────────────────────────────
read -rp "ECS Cluster name [resortsLite-cluster]: " CLUSTER_NAME
CLUSTER_NAME="${CLUSTER_NAME:-resortsLite-cluster}"

# ── Network configuration ─────────────────────────────────────────────────────
echo ""
echo "--- Network Configuration ---"
read -rp "VPC ID: " VPC_ID
read -rp "Subnet IDs (comma-separated, e.g. subnet-aaa,subnet-bbb): " SUBNETS_RAW
read -rp "Security Group ID: " SECURITY_GROUP

# Split subnets into SUBNET_1 and SUBNET_2
SUBNET_1=$(echo "$SUBNETS_RAW" | cut -d',' -f1 | tr -d ' ')
SUBNET_2=$(echo "$SUBNETS_RAW" | cut -d',' -f2 | tr -d ' ')
if [ -z "$SUBNET_2" ]; then
  SUBNET_2="$SUBNET_1"
fi

# ── ECR Image URI ─────────────────────────────────────────────────────────────
echo ""
read -rp "Full ECR Image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/resortsLite:latest): " IMAGE_URI

# ── AWS Account ID ────────────────────────────────────────────────────────────
echo ""
echo "Fetching AWS Account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Account ID: $ACCOUNT_ID"

# ── EFS File System (optional) ────────────────────────────────────────────────
echo ""
read -rp "EFS File System ID for reports volume (leave blank to skip): " EFS_FILE_SYSTEM_ID
EFS_FILE_SYSTEM_ID="${EFS_FILE_SYSTEM_ID:-fs-placeholder}"

# ── Memcached endpoint ────────────────────────────────────────────────────────
read -rp "ElastiCache Memcached endpoint [localhost:11211]: " MEMCACHED_ENDPOINT
MEMCACHED_ENDPOINT="${MEMCACHED_ENDPOINT:-localhost:11211}"

# ── CloudWatch Log Group ──────────────────────────────────────────────────────
echo ""
echo "Ensuring CloudWatch log group '$LOG_GROUP' exists..."
aws logs create-log-group --log-group-name "$LOG_GROUP" --region "$AWS_REGION" 2>/dev/null || true
echo "Log group ready."

# ── ECS Cluster ───────────────────────────────────────────────────────────────
echo ""
echo "Checking ECS cluster '$CLUSTER_NAME'..."
CLUSTER_STATUS=$(aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" \
  --query "clusters[0].status" --output text 2>/dev/null || echo "MISSING")
if [ "$CLUSTER_STATUS" != "ACTIVE" ]; then
  echo "Creating ECS cluster '$CLUSTER_NAME'..."
  aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
fi
echo "Cluster ready."

# ── Load Balancer ─────────────────────────────────────────────────────────────
echo ""
read -rp "Do you need an Application Load Balancer for this service? (y/n) [n]: " NEED_ALB
NEED_ALB="${NEED_ALB:-n}"

TARGET_GROUP_ARN=""
ALB_DNS=""

if [ "$NEED_ALB" = "y" ] || [ "$NEED_ALB" = "Y" ]; then
  echo ""
  echo "Creating Application Load Balancer..."

  ALB_NAME="resortsLite-alb"
  TG_NAME="resortsLite-tg"

  # Create ALB
  ALB_ARN=$(aws elbv2 create-load-balancer \
    --name "$ALB_NAME" \
    --subnets "$SUBNET_1" "$SUBNET_2" \
    --security-groups "$SECURITY_GROUP" \
    --scheme internet-facing \
    --type application \
    --region "$AWS_REGION" \
    --query "LoadBalancers[0].LoadBalancerArn" \
    --output text)
  echo "ALB ARN: $ALB_ARN"

  ALB_DNS=$(aws elbv2 describe-load-balancers \
    --load-balancer-arns "$ALB_ARN" \
    --region "$AWS_REGION" \
    --query "LoadBalancers[0].DNSName" \
    --output text)

  # Create Target Group (target-type ip required for Fargate awsvpc)
  TARGET_GROUP_ARN=$(aws elbv2 create-target-group \
    --name "$TG_NAME" \
    --protocol HTTP \
    --port 8080 \
    --vpc-id "$VPC_ID" \
    --target-type ip \
    --health-check-path "/actuator/health" \
    --health-check-interval-seconds 30 \
    --healthy-threshold-count 2 \
    --unhealthy-threshold-count 3 \
    --region "$AWS_REGION" \
    --query "TargetGroups[0].TargetGroupArn" \
    --output text)
  echo "Target Group ARN: $TARGET_GROUP_ARN"

  # Create listener
  aws elbv2 create-listener \
    --load-balancer-arn "$ALB_ARN" \
    --protocol HTTP \
    --port 80 \
    --default-actions "Type=forward,TargetGroupArn=$TARGET_GROUP_ARN" \
    --region "$AWS_REGION" >/dev/null
  echo "ALB listener created."
fi

# ── Prepare task definition JSON ──────────────────────────────────────────────
echo ""
echo "Preparing task definition..."
cp "$TASK_DEF_FILE" /tmp/task-definition-deploy.json

sed -i "s|{{ACCOUNT_ID}}|$ACCOUNT_ID|g"             /tmp/task-definition-deploy.json
sed -i "s|{{AWS_REGION}}|$AWS_REGION|g"             /tmp/task-definition-deploy.json
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g"               /tmp/task-definition-deploy.json
sed -i "s|{{MEMCACHED_ENDPOINT}}|$MEMCACHED_ENDPOINT|g" /tmp/task-definition-deploy.json
sed -i "s|{{EFS_FILE_SYSTEM_ID}}|$EFS_FILE_SYSTEM_ID|g" /tmp/task-definition-deploy.json

# ── Register task definition ──────────────────────────────────────────────────
echo "Registering task definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition \
  --cli-input-json file:///tmp/task-definition-deploy.json \
  --region "$AWS_REGION" \
  --query "taskDefinition.taskDefinitionArn" \
  --output text)
echo "Task Definition ARN: $TASK_DEF_ARN"

# ── Prepare service definition JSON ──────────────────────────────────────────
echo ""
echo "Preparing service definition..."
cp "$SERVICE_DEF_FILE" /tmp/service-definition-deploy.json

sed -i "s|{{CLUSTER_NAME}}|$CLUSTER_NAME|g"   /tmp/service-definition-deploy.json
sed -i "s|{{SUBNET_1}}|$SUBNET_1|g"           /tmp/service-definition-deploy.json
sed -i "s|{{SUBNET_2}}|$SUBNET_2|g"           /tmp/service-definition-deploy.json
sed -i "s|{{SECURITY_GROUP}}|$SECURITY_GROUP|g" /tmp/service-definition-deploy.json

# ── Inject load balancer config if ALB was created ───────────────────────────
if [ -n "$TARGET_GROUP_ARN" ]; then
  # Use Python to inject loadBalancers into service JSON
  python3 - <<PYEOF
import json, sys
with open('/tmp/service-definition-deploy.json') as f:
    svc = json.load(f)
svc['loadBalancers'] = [{
    'targetGroupArn': '$TARGET_GROUP_ARN',
    'containerName': 'resortsLite',
    'containerPort': 8080
}]
svc['healthCheckGracePeriodSeconds'] = 300
with open('/tmp/service-definition-deploy.json', 'w') as f:
    json.dump(svc, f, indent=2)
PYEOF
fi

# ── Create or update ECS service ─────────────────────────────────────────────
echo ""
EXISTING_SERVICE=$(aws ecs describe-services \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION" \
  --query "services[?status=='ACTIVE'].serviceName" \
  --output text 2>/dev/null || echo "")

if [ -z "$EXISTING_SERVICE" ] || [ "$EXISTING_SERVICE" = "None" ]; then
  echo "Creating ECS service '$SERVICE_NAME'..."
  # Inject task definition ARN into service JSON
  python3 - <<PYEOF
import json
with open('/tmp/service-definition-deploy.json') as f:
    svc = json.load(f)
svc['taskDefinition'] = '$TASK_DEF_ARN'
with open('/tmp/service-definition-deploy.json', 'w') as f:
    json.dump(svc, f, indent=2)
PYEOF
  aws ecs create-service \
    --cli-input-json file:///tmp/service-definition-deploy.json \
    --region "$AWS_REGION"
else
  echo "Updating existing ECS service '$SERVICE_NAME'..."
  aws ecs update-service \
    --cluster "$CLUSTER_NAME" \
    --service "$SERVICE_NAME" \
    --task-definition "$TASK_DEF_ARN" \
    --region "$AWS_REGION" >/dev/null
fi

# ── Wait for service stability ────────────────────────────────────────────────
echo ""
echo "Waiting for service to stabilise (this may take a few minutes)..."
aws ecs wait services-stable \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION"

# ── Verify deployment ─────────────────────────────────────────────────────────
echo ""
echo "=============================================="
echo "  Deployment Complete!"
echo "=============================================="
RUNNING_COUNT=$(aws ecs describe-services \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION" \
  --query "services[0].runningCount" \
  --output text)
echo "  Running tasks : $RUNNING_COUNT"
echo "  Cluster       : $CLUSTER_NAME"
echo "  Service       : $SERVICE_NAME"
echo "  Task Def ARN  : $TASK_DEF_ARN"
echo "  Log Group     : $LOG_GROUP"
if [ -n "$ALB_DNS" ]; then
  echo "  ALB DNS       : http://$ALB_DNS"
fi
echo ""
echo "Troubleshooting tips:"
echo "  - View logs  : aws logs tail $LOG_GROUP --follow --region $AWS_REGION"
echo "  - List tasks : aws ecs list-tasks --cluster $CLUSTER_NAME --region $AWS_REGION"
echo "  - Task detail: aws ecs describe-tasks --cluster $CLUSTER_NAME --tasks <TASK_ARN> --region $AWS_REGION"
