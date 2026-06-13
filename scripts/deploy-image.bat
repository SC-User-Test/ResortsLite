@echo off
setlocal enabledelayedexpansion

REM Deploy ResortsLite to AWS ECS Fargate (Windows)
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
set /p IMAGE_URI="Enter ECR Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): "

REM Parse subnets
for /f "tokens=1,2 delims=," %%a in ("!SUBNETS_INPUT!") do (
    set SUBNET_1=%%a
    set SUBNET_2=%%b
)
set SUBNET_1=!SUBNET_1: =!
set SUBNET_2=!SUBNET_2: =!

echo.
echo Configuration Summary:
echo   Region: !AWS_REGION!
echo   Cluster: !CLUSTER_NAME!
echo   VPC: !VPC_ID!
echo   Subnets: !SUBNET_1!, !SUBNET_2!
echo   Security Group: !SECURITY_GROUP!
echo   Image: !IMAGE_URI!
echo.

REM Get AWS Account ID
echo Retrieving AWS Account ID...
for /f "tokens=*" %%i in ('aws sts get-caller-identity --query Account --output text') do set ACCOUNT_ID=%%i
echo Account ID: !ACCOUNT_ID!
echo.

REM Check if ECS cluster exists, create if not
echo Checking if ECS cluster exists...
aws ecs describe-clusters --clusters "!CLUSTER_NAME!" --region "!AWS_REGION!" >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Cluster does not exist. Creating ECS cluster: !CLUSTER_NAME!
    aws ecs create-cluster --cluster-name "!CLUSTER_NAME!" --region "!AWS_REGION!"
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ECS cluster
        exit /b 1
    )
    echo ECS cluster created successfully
)
echo.

REM Prompt for load balancer
set /p NEED_LB="Do you need a load balancer for this service? (y/n): "

if /i "!NEED_LB!"=="y" (
    echo.
    echo Creating Application Load Balancer and Target Group...
    
    REM Create ALB
    set ALB_NAME=resortslite-alb
    echo Creating ALB: !ALB_NAME!
    for /f "tokens=*" %%i in ('aws elbv2 create-load-balancer --name "!ALB_NAME!" --subnets "!SUBNET_1!" "!SUBNET_2!" --security-groups "!SECURITY_GROUP!" --scheme internet-facing --type application --ip-address-type ipv4 --region "!AWS_REGION!" --query "LoadBalancers[0].LoadBalancerArn" --output text') do set ALB_ARN=%%i
    
    echo ALB created: !ALB_ARN!
    
    REM Create Target Group with target-type ip
    set TG_NAME=resortslite-tg
    echo Creating Target Group: !TG_NAME!
    for /f "tokens=*" %%i in ('aws elbv2 create-target-group --name "!TG_NAME!" --protocol HTTP --port 8080 --vpc-id "!VPC_ID!" --target-type ip --health-check-enabled --health-check-protocol HTTP --health-check-path "/actuator/health" --health-check-interval-seconds 30 --health-check-timeout-seconds 5 --healthy-threshold-count 2 --unhealthy-threshold-count 3 --region "!AWS_REGION!" --query "TargetGroups[0].TargetGroupArn" --output text') do set TARGET_GROUP_ARN=%%i
    
    echo Target Group created: !TARGET_GROUP_ARN!
    
    REM Create Listener
    echo Creating ALB Listener...
    aws elbv2 create-listener --load-balancer-arn "!ALB_ARN!" --protocol HTTP --port 80 --default-actions Type=forward,TargetGroupArn="!TARGET_GROUP_ARN!" --region "!AWS_REGION!" >nul
    
    echo ALB Listener created
    
    REM Get ALB DNS name
    for /f "tokens=*" %%i in ('aws elbv2 describe-load-balancers --load-balancer-arns "!ALB_ARN!" --region "!AWS_REGION!" --query "LoadBalancers[0].DNSName" --output text') do set ALB_DNS=%%i
    
    echo Load Balancer DNS: !ALB_DNS!
    echo.
) else (
    echo Skipping load balancer creation
    set TARGET_GROUP_ARN=
    echo.
)

REM Environment variables with defaults
set REDIS_HOST=localhost
set REDIS_PORT=6379
set REDIS_PASSWORD=
set S3_BUCKET_NAME=resortslite-reports
set PAYMENT_ENDPOINT=http://payment-svc.internal:9090/charge
set INVENTORY_ENDPOINT=http://inventory-svc.internal:8081/rooms
set NOTIFICATION_ENDPOINT=http://notify.internal:7070/send

echo.
echo Creating CloudWatch Log Group...
aws logs create-log-group --log-group-name "/ecs/resortslite" --region "!AWS_REGION!" 2>nul
echo.

REM Create temporary files
set TEMP_TASK_DEF=%TEMP%\task-def-%RANDOM%.json
set TEMP_SERVICE_DEF=%TEMP%\service-def-%RANDOM%.json

REM Replace placeholders in task definition
powershell -Command "(Get-Content ecs\task-definition.json) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' -replace '{{AWS_REGION}}', '!AWS_REGION!' -replace '{{ACCOUNT_ID}}', '!ACCOUNT_ID!' -replace '{{REDIS_HOST}}', '!REDIS_HOST!' -replace '{{REDIS_PORT}}', '!REDIS_PORT!' -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' -replace '{{S3_BUCKET_NAME}}', '!S3_BUCKET_NAME!' -replace '{{PAYMENT_ENDPOINT}}', '!PAYMENT_ENDPOINT!' -replace '{{INVENTORY_ENDPOINT}}', '!INVENTORY_ENDPOINT!' -replace '{{NOTIFICATION_ENDPOINT}}', '!NOTIFICATION_ENDPOINT!' | Set-Content '!TEMP_TASK_DEF!'"

REM Replace placeholders in service definition
if /i "!NEED_LB!"=="y" (
    powershell -Command "(Get-Content ecs\service-definition.json) -replace '{{CLUSTER_NAME}}', '!CLUSTER_NAME!' -replace '{{SUBNET_1}}', '!SUBNET_1!' -replace '{{SUBNET_2}}', '!SUBNET_2!' -replace '{{SECURITY_GROUP}}', '!SECURITY_GROUP!' -replace '{{TARGET_GROUP_ARN}}', '!TARGET_GROUP_ARN!' | Set-Content '!TEMP_SERVICE_DEF!'"
) else (
    powershell -Command "$json = Get-Content ecs\service-definition.json | ConvertFrom-Json; $json.PSObject.Properties.Remove('loadBalancers'); $json.PSObject.Properties.Remove('healthCheckGracePeriodSeconds'); $json | ConvertTo-Json -Depth 10 | ForEach-Object { $_ -replace '{{CLUSTER_NAME}}', '!CLUSTER_NAME!' -replace '{{SUBNET_1}}', '!SUBNET_1!' -replace '{{SUBNET_2}}', '!SUBNET_2!' -replace '{{SECURITY_GROUP}}', '!SECURITY_GROUP!' } | Set-Content '!TEMP_SERVICE_DEF!'"
)

echo Registering ECS Task Definition...
for /f "tokens=*" %%i in ('aws ecs register-task-definition --cli-input-json file://!TEMP_TASK_DEF! --region "!AWS_REGION!" --query "taskDefinition.taskDefinitionArn" --output text') do set TASK_DEF_ARN=%%i

echo Task Definition registered: !TASK_DEF_ARN!
echo.

REM Check if service exists
set SERVICE_NAME=resortslite-service
echo Checking if ECS service exists...
for /f "tokens=*" %%i in ('aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!" --query "services[?status==`ACTIVE`].serviceName" --output text') do set EXISTING_SERVICE=%%i

if "!EXISTING_SERVICE!"=="" (
    echo Service does not exist. Creating new service...
    aws ecs create-service --cli-input-json file://!TEMP_SERVICE_DEF! --region "!AWS_REGION!" >nul
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ECS service
        exit /b 1
    )
    echo ECS service created successfully
) else (
    echo Service exists. Updating service...
    aws ecs update-service --cluster "!CLUSTER_NAME!" --service "!SERVICE_NAME!" --task-definition "!TASK_DEF_ARN!" --region "!AWS_REGION!" >nul
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to update ECS service
        exit /b 1
    )
    echo ECS service updated successfully
)

echo.
echo Waiting for service to become stable...
aws ecs wait services-stable --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!"

echo.
echo ==========================================
echo Deployment Completed Successfully
echo ==========================================
echo.

REM Display service details
echo Service Details:
aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!" --query "services[0].[serviceName,status,runningCount,desiredCount]" --output table

echo.
echo CloudWatch Logs: /ecs/resortslite
echo Region: !AWS_REGION!

if /i "!NEED_LB!"=="y" (
    echo.
    echo Application URL: http://!ALB_DNS!
    echo Note: It may take a few minutes for the ALB to become healthy
)

echo.
echo To view logs:
echo   aws logs tail /ecs/resortslite --follow --region !AWS_REGION!
echo.

REM Cleanup temporary files
del /f /q "!TEMP_TASK_DEF!" "!TEMP_SERVICE_DEF!" 2>nul

endlocal
