@echo off
setlocal enabledelayedexpansion

REM Deploy to AWS ECS Fargate Script for ResortsLite Application (Windows)
REM This script deploys the Docker image to AWS ECS Fargate

echo ==========================================
echo ResortsLite - AWS ECS Fargate Deployment
echo ==========================================
echo.

REM Prompt for AWS configuration
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter ECS Cluster Name: "
set /p VPC_ID="Enter VPC ID: "
set /p SUBNETS_INPUT="Enter Subnet IDs (comma-separated, at least 2): "
set /p SECURITY_GROUP="Enter Security Group ID: "
set /p IMAGE_URI="Enter Docker Image URI: "

REM Parse subnets
for /f "tokens=1,2 delims=," %%a in ("!SUBNETS_INPUT!") do (
    set SUBNET_1=%%a
    set SUBNET_2=%%b
)
set SUBNET_1=!SUBNET_1: =!
set SUBNET_2=!SUBNET_2: =!

echo.
echo === External Service Configuration ===
set /p REDIS_HOST="Enter Redis Host: "
set /p REDIS_PORT="Enter Redis Port (default: 6379): "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379
set /p REDIS_PASSWORD="Enter Redis Password (leave empty if none): "

set /p S3_BUCKET_NAME="Enter S3 Bucket Name: "

set /p PAYMENT_ENDPOINT="Enter Payment Service Endpoint (default: http://payment-svc:9090/charge): "
if "!PAYMENT_ENDPOINT!"=="" set PAYMENT_ENDPOINT=http://payment-svc:9090/charge

set /p INVENTORY_ENDPOINT="Enter Inventory Service Endpoint (default: http://inventory-svc:8081/rooms): "
if "!INVENTORY_ENDPOINT!"=="" set INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms

set /p NOTIFICATION_ENDPOINT="Enter Notification Service Endpoint (default: http://notify-svc:7070/send): "
if "!NOTIFICATION_ENDPOINT!"=="" set NOTIFICATION_ENDPOINT=http://notify-svc:7070/send

REM Get AWS Account ID
echo.
echo Retrieving AWS Account ID...
for /f "tokens=*" %%i in ('aws sts get-caller-identity --query Account --output text') do set ACCOUNT_ID=%%i
echo Account ID: !ACCOUNT_ID!

REM Check if cluster exists
echo.
echo Checking ECS cluster...
aws ecs describe-clusters --clusters "!CLUSTER_NAME!" --region "!AWS_REGION!" >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Creating ECS cluster: !CLUSTER_NAME!
    aws ecs create-cluster --cluster-name "!CLUSTER_NAME!" --region "!AWS_REGION!"
)

REM Create CloudWatch log group
echo.
echo Creating CloudWatch log group...
aws logs create-log-group --log-group-name "/ecs/resortslite" --region "!AWS_REGION!" 2>nul

REM Load balancer configuration
echo.
set /p NEED_LB="Do you need a load balancer for this service? (y/n): "

if /i "!NEED_LB!"=="y" (
    echo.
    echo Creating Application Load Balancer and Target Group...
    
    set ALB_NAME=resortslite-alb
    echo Creating ALB: !ALB_NAME!
    
    for /f "tokens=*" %%i in ('aws elbv2 create-load-balancer --name "!ALB_NAME!" --subnets "!SUBNET_1!" "!SUBNET_2!" --security-groups "!SECURITY_GROUP!" --scheme internet-facing --type application --ip-address-type ipv4 --region "!AWS_REGION!" --query "LoadBalancers[0].LoadBalancerArn" --output text 2^>nul') do set ALB_ARN=%%i
    
    if "!ALB_ARN!"=="" (
        for /f "tokens=*" %%i in ('aws elbv2 describe-load-balancers --names "!ALB_NAME!" --region "!AWS_REGION!" --query "LoadBalancers[0].LoadBalancerArn" --output text') do set ALB_ARN=%%i
    )
    
    echo ALB ARN: !ALB_ARN!
    
    for /f "tokens=*" %%i in ('aws elbv2 describe-load-balancers --load-balancer-arns "!ALB_ARN!" --region "!AWS_REGION!" --query "LoadBalancers[0].DNSName" --output text') do set ALB_DNS=%%i
    
    set TG_NAME=resortslite-tg
    echo Creating Target Group: !TG_NAME!
    
    for /f "tokens=*" %%i in ('aws elbv2 create-target-group --name "!TG_NAME!" --protocol HTTP --port 8080 --vpc-id "!VPC_ID!" --target-type ip --health-check-enabled --health-check-protocol HTTP --health-check-path "/actuator/health" --health-check-interval-seconds 30 --health-check-timeout-seconds 5 --healthy-threshold-count 2 --unhealthy-threshold-count 3 --region "!AWS_REGION!" --query "TargetGroups[0].TargetGroupArn" --output text 2^>nul') do set TARGET_GROUP_ARN=%%i
    
    if "!TARGET_GROUP_ARN!"=="" (
        for /f "tokens=*" %%i in ('aws elbv2 describe-target-groups --names "!TG_NAME!" --region "!AWS_REGION!" --query "TargetGroups[0].TargetGroupArn" --output text') do set TARGET_GROUP_ARN=%%i
    )
    
    echo Target Group ARN: !TARGET_GROUP_ARN!
    
    echo Creating ALB listener...
    aws elbv2 create-listener --load-balancer-arn "!ALB_ARN!" --protocol HTTP --port 80 --default-actions Type=forward,TargetGroupArn="!TARGET_GROUP_ARN!" --region "!AWS_REGION!" 2>nul
    
    echo Load balancer setup complete!
    echo ALB DNS: !ALB_DNS!
) else (
    echo Skipping load balancer creation
    set TARGET_GROUP_ARN=
)

REM Prepare task definition
echo.
echo Preparing task definition...
set TASK_DEF_FILE=ecs\task-definition.json
set TASK_DEF_TEMP=%TEMP%\task-definition-%RANDOM%.json

copy "!TASK_DEF_FILE!" "!TASK_DEF_TEMP!" >nul

powershell -Command "(Get-Content '!TASK_DEF_TEMP!') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TASK_DEF_TEMP!'"
powershell -Command "(Get-Content '!TASK_DEF_TEMP!') -replace '{{AWS_REGION}}', '!AWS_REGION!' | Set-Content '!TASK_DEF_TEMP!'"
powershell -Command "(Get-Content '!TASK_DEF_TEMP!') -replace '{{ACCOUNT_ID}}', '!ACCOUNT_ID!' | Set-Content '!TASK_DEF_TEMP!'"
powershell -Command "(Get-Content '!TASK_DEF_TEMP!') -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content '!TASK_DEF_TEMP!'"
powershell -Command "(Get-Content '!TASK_DEF_TEMP!') -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content '!TASK_DEF_TEMP!'"
powershell -Command "(Get-Content '!TASK_DEF_TEMP!') -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' | Set-Content '!TASK_DEF_TEMP!'"
powershell -Command "(Get-Content '!TASK_DEF_TEMP!') -replace '{{S3_BUCKET_NAME}}', '!S3_BUCKET_NAME!' | Set-Content '!TASK_DEF_TEMP!'"
powershell -Command "(Get-Content '!TASK_DEF_TEMP!') -replace '{{PAYMENT_ENDPOINT}}', '!PAYMENT_ENDPOINT!' | Set-Content '!TASK_DEF_TEMP!'"
powershell -Command "(Get-Content '!TASK_DEF_TEMP!') -replace '{{INVENTORY_ENDPOINT}}', '!INVENTORY_ENDPOINT!' | Set-Content '!TASK_DEF_TEMP!'"
powershell -Command "(Get-Content '!TASK_DEF_TEMP!') -replace '{{NOTIFICATION_ENDPOINT}}', '!NOTIFICATION_ENDPOINT!' | Set-Content '!TASK_DEF_TEMP!'"

REM Register task definition
echo Registering task definition...
for /f "tokens=*" %%i in ('aws ecs register-task-definition --cli-input-json file://"!TASK_DEF_TEMP!" --region "!AWS_REGION!" --query "taskDefinition.taskDefinitionArn" --output text') do set TASK_DEF_ARN=%%i

echo Task Definition ARN: !TASK_DEF_ARN!

del "!TASK_DEF_TEMP!" 2>nul

REM Prepare service definition
echo.
echo Preparing service definition...
set SERVICE_DEF_FILE=ecs\service-definition.json
set SERVICE_DEF_TEMP=%TEMP%\service-definition-%RANDOM%.json

copy "!SERVICE_DEF_FILE!" "!SERVICE_DEF_TEMP!" >nul

powershell -Command "(Get-Content '!SERVICE_DEF_TEMP!') -replace '{{CLUSTER_NAME}}', '!CLUSTER_NAME!' | Set-Content '!SERVICE_DEF_TEMP!'"
powershell -Command "(Get-Content '!SERVICE_DEF_TEMP!') -replace '{{SUBNET_1}}', '!SUBNET_1!' | Set-Content '!SERVICE_DEF_TEMP!'"
powershell -Command "(Get-Content '!SERVICE_DEF_TEMP!') -replace '{{SUBNET_2}}', '!SUBNET_2!' | Set-Content '!SERVICE_DEF_TEMP!'"
powershell -Command "(Get-Content '!SERVICE_DEF_TEMP!') -replace '{{SECURITY_GROUP}}', '!SECURITY_GROUP!' | Set-Content '!SERVICE_DEF_TEMP!'"

if /i "!NEED_LB!"=="y" (
    powershell -Command "(Get-Content '!SERVICE_DEF_TEMP!') -replace '{{TARGET_GROUP_ARN}}', '!TARGET_GROUP_ARN!' | Set-Content '!SERVICE_DEF_TEMP!'"
) else (
    powershell -Command "$content = Get-Content '!SERVICE_DEF_TEMP!' -Raw; $content = $content -replace '(?s)\"loadBalancers\":\s*\[.*?\],\s*', ''; $content = $content -replace '\"healthCheckGracePeriodSeconds\":\s*\d+,\s*', ''; $content | Set-Content '!SERVICE_DEF_TEMP!'"
)

REM Check if service exists
echo.
echo Checking if service exists...
set SERVICE_NAME=resortslite-service
for /f "tokens=*" %%i in ('aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!" --query "services[?status==`ACTIVE`].serviceName" --output text 2^>nul') do set EXISTING_SERVICE=%%i

if "!EXISTING_SERVICE!"=="" (
    echo Creating new ECS service...
    aws ecs create-service --cli-input-json file://"!SERVICE_DEF_TEMP!" --region "!AWS_REGION!"
) else (
    echo Updating existing ECS service...
    aws ecs update-service --cluster "!CLUSTER_NAME!" --service "!SERVICE_NAME!" --task-definition "!TASK_DEF_ARN!" --desired-count 2 --region "!AWS_REGION!"
)

del "!SERVICE_DEF_TEMP!" 2>nul

REM Wait for service stability
echo.
echo Waiting for service to become stable (this may take a few minutes)...
aws ecs wait services-stable --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!"

REM Verify deployment
echo.
echo ==========================================
echo Deployment Status
echo ==========================================
aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!" --query "services[0].[serviceName,status,runningCount,desiredCount]" --output table

echo.
echo ==========================================
echo SUCCESS!
echo ==========================================
echo Service: !SERVICE_NAME!
echo Cluster: !CLUSTER_NAME!
echo Region: !AWS_REGION!
echo Task Definition: !TASK_DEF_ARN!

if /i "!NEED_LB!"=="y" (
    echo.
    echo Application URL: http://!ALB_DNS!
    echo Health Check: http://!ALB_DNS!/actuator/health
)

echo.
echo CloudWatch Logs: /ecs/resortslite
echo.
echo To view logs:
echo   aws logs tail /ecs/resortslite --follow --region !AWS_REGION!
echo.
echo To check service status:
echo   aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION!
echo.

endlocal
