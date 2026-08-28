@echo off
setlocal enabledelayedexpansion

:: =============================================================================
:: deploy-image.bat — Deploy ResortsLite to AWS ECS Fargate (Windows)
:: Usage: scripts\deploy-image.bat
:: Run from the repository root directory.
:: =============================================================================

set SERVICE_NAME=resortsLite-service
set TASK_FAMILY=resortsLite-task
set LOG_GROUP=/ecs/resortsLite
set TASK_DEF_FILE=ecs\task-definition.json
set SERVICE_DEF_FILE=ecs\service-definition.json

echo ==============================================
echo   ResortsLite - AWS ECS Fargate Deployment
echo ==============================================
echo.

:: ── AWS Region ────────────────────────────────────────────────────────────────
set /p AWS_REGION="AWS Region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

:: ── ECS Cluster ───────────────────────────────────────────────────────────────
set /p CLUSTER_NAME="ECS Cluster name [resortsLite-cluster]: "
if "!CLUSTER_NAME!"=="" set CLUSTER_NAME=resortsLite-cluster

:: ── Network configuration ─────────────────────────────────────────────────────
echo.
echo --- Network Configuration ---
set /p VPC_ID="VPC ID: "
set /p SUBNETS_RAW="Subnet IDs (comma-separated, e.g. subnet-aaa,subnet-bbb): "
set /p SECURITY_GROUP="Security Group ID: "

:: Split subnets
for /f "tokens=1,2 delims=," %%a in ("!SUBNETS_RAW!") do (
    set SUBNET_1=%%a
    set SUBNET_2=%%b
)
if "!SUBNET_2!"=="" set SUBNET_2=!SUBNET_1!

:: ── ECR Image URI ─────────────────────────────────────────────────────────────
echo.
set /p IMAGE_URI="Full ECR Image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/resortsLite:latest): "

:: ── AWS Account ID ────────────────────────────────────────────────────────────
echo.
echo Fetching AWS Account ID...
for /f "delims=" %%i in ('aws sts get-caller-identity --query Account --output text') do set ACCOUNT_ID=%%i
echo Account ID: !ACCOUNT_ID!

:: ── EFS File System ───────────────────────────────────────────────────────────
echo.
set /p EFS_FILE_SYSTEM_ID="EFS File System ID for reports volume (leave blank to skip): "
if "!EFS_FILE_SYSTEM_ID!"=="" set EFS_FILE_SYSTEM_ID=fs-placeholder

:: ── Memcached endpoint ────────────────────────────────────────────────────────
set /p MEMCACHED_ENDPOINT="ElastiCache Memcached endpoint [localhost:11211]: "
if "!MEMCACHED_ENDPOINT!"=="" set MEMCACHED_ENDPOINT=localhost:11211

:: ── CloudWatch Log Group ──────────────────────────────────────────────────────
echo.
echo Ensuring CloudWatch log group '!LOG_GROUP!' exists...
aws logs create-log-group --log-group-name !LOG_GROUP! --region !AWS_REGION! >nul 2>&1
echo Log group ready.

:: ── ECS Cluster ───────────────────────────────────────────────────────────────
echo.
echo Checking ECS cluster '!CLUSTER_NAME!'...
for /f "delims=" %%i in ('aws ecs describe-clusters --clusters !CLUSTER_NAME! --region !AWS_REGION! --query "clusters[0].status" --output text 2^>nul') do set CLUSTER_STATUS=%%i
if not "!CLUSTER_STATUS!"=="ACTIVE" (
    echo Creating ECS cluster '!CLUSTER_NAME!'...
    aws ecs create-cluster --cluster-name !CLUSTER_NAME! --region !AWS_REGION!
)
echo Cluster ready.

:: ── Load Balancer ─────────────────────────────────────────────────────────────
echo.
set /p NEED_ALB="Do you need an Application Load Balancer for this service? (y/n) [n]: "
if "!NEED_ALB!"=="" set NEED_ALB=n

set TARGET_GROUP_ARN=
set ALB_DNS=

if /i "!NEED_ALB!"=="y" (
    echo.
    echo Creating Application Load Balancer...

    for /f "delims=" %%i in ('aws elbv2 create-load-balancer --name resortsLite-alb --subnets !SUBNET_1! !SUBNET_2! --security-groups !SECURITY_GROUP! --scheme internet-facing --type application --region !AWS_REGION! --query "LoadBalancers[0].LoadBalancerArn" --output text') do set ALB_ARN=%%i
    echo ALB ARN: !ALB_ARN!

    for /f "delims=" %%i in ('aws elbv2 describe-load-balancers --load-balancer-arns !ALB_ARN! --region !AWS_REGION! --query "LoadBalancers[0].DNSName" --output text') do set ALB_DNS=%%i

    for /f "delims=" %%i in ('aws elbv2 create-target-group --name resortsLite-tg --protocol HTTP --port 8080 --vpc-id !VPC_ID! --target-type ip --health-check-path "/actuator/health" --health-check-interval-seconds 30 --healthy-threshold-count 2 --unhealthy-threshold-count 3 --region !AWS_REGION! --query "TargetGroups[0].TargetGroupArn" --output text') do set TARGET_GROUP_ARN=%%i
    echo Target Group ARN: !TARGET_GROUP_ARN!

    aws elbv2 create-listener --load-balancer-arn !ALB_ARN! --protocol HTTP --port 80 --default-actions "Type=forward,TargetGroupArn=!TARGET_GROUP_ARN!" --region !AWS_REGION! >nul
    echo ALB listener created.
)

:: ── Prepare task definition JSON ──────────────────────────────────────────────
echo.
echo Preparing task definition...
copy /Y !TASK_DEF_FILE! %TEMP%\task-definition-deploy.json >nul

powershell -Command "(Get-Content '%TEMP%\task-definition-deploy.json') -replace '{{ACCOUNT_ID}}','!ACCOUNT_ID!' -replace '{{AWS_REGION}}','!AWS_REGION!' -replace '{{IMAGE_URI}}','!IMAGE_URI!' -replace '{{MEMCACHED_ENDPOINT}}','!MEMCACHED_ENDPOINT!' -replace '{{EFS_FILE_SYSTEM_ID}}','!EFS_FILE_SYSTEM_ID!' | Set-Content '%TEMP%\task-definition-deploy.json'"

:: ── Register task definition ──────────────────────────────────────────────────
echo Registering task definition...
for /f "delims=" %%i in ('aws ecs register-task-definition --cli-input-json file://%TEMP%\task-definition-deploy.json --region !AWS_REGION! --query "taskDefinition.taskDefinitionArn" --output text') do set TASK_DEF_ARN=%%i
echo Task Definition ARN: !TASK_DEF_ARN!

:: ── Prepare service definition JSON ──────────────────────────────────────────
echo.
echo Preparing service definition...
copy /Y !SERVICE_DEF_FILE! %TEMP%\service-definition-deploy.json >nul

powershell -Command "(Get-Content '%TEMP%\service-definition-deploy.json') -replace '{{CLUSTER_NAME}}','!CLUSTER_NAME!' -replace '{{SUBNET_1}}','!SUBNET_1!' -replace '{{SUBNET_2}}','!SUBNET_2!' -replace '{{SECURITY_GROUP}}','!SECURITY_GROUP!' | Set-Content '%TEMP%\service-definition-deploy.json'"

:: ── Inject load balancer config if ALB was created ───────────────────────────
if not "!TARGET_GROUP_ARN!"=="" (
    powershell -Command "$svc = Get-Content '%TEMP%\service-definition-deploy.json' | ConvertFrom-Json; $lb = @{targetGroupArn='!TARGET_GROUP_ARN!'; containerName='resortsLite'; containerPort=8080}; $svc | Add-Member -NotePropertyName 'loadBalancers' -NotePropertyValue @($lb) -Force; $svc | Add-Member -NotePropertyName 'healthCheckGracePeriodSeconds' -NotePropertyValue 300 -Force; $svc | ConvertTo-Json -Depth 10 | Set-Content '%TEMP%\service-definition-deploy.json'"
)

:: ── Inject task definition ARN into service JSON ─────────────────────────────
powershell -Command "(Get-Content '%TEMP%\service-definition-deploy.json') -replace '\"taskDefinition\": \"resortsLite-task\"','\"taskDefinition\": \"!TASK_DEF_ARN!\"' | Set-Content '%TEMP%\service-definition-deploy.json'"

:: ── Create or update ECS service ─────────────────────────────────────────────
echo.
for /f "delims=" %%i in ('aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION! --query "services[?status==''ACTIVE''].serviceName" --output text 2^>nul') do set EXISTING_SERVICE=%%i

if "!EXISTING_SERVICE!"=="" (
    echo Creating ECS service '!SERVICE_NAME!'...
    aws ecs create-service --cli-input-json file://%TEMP%\service-definition-deploy.json --region !AWS_REGION!
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ECS service.
        exit /b 1
    )
) else (
    echo Updating existing ECS service '!SERVICE_NAME!'...
    aws ecs update-service --cluster !CLUSTER_NAME! --service !SERVICE_NAME! --task-definition !TASK_DEF_ARN! --region !AWS_REGION! >nul
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to update ECS service.
        exit /b 1
    )
)

:: ── Wait for service stability ────────────────────────────────────────────────
echo.
echo Waiting for service to stabilise (this may take a few minutes)...
aws ecs wait services-stable --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION!

:: ── Verify deployment ─────────────────────────────────────────────────────────
echo.
echo ==============================================
echo   Deployment Complete!
echo ==============================================
for /f "delims=" %%i in ('aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION! --query "services[0].runningCount" --output text') do set RUNNING_COUNT=%%i
echo   Running tasks : !RUNNING_COUNT!
echo   Cluster       : !CLUSTER_NAME!
echo   Service       : !SERVICE_NAME!
echo   Task Def ARN  : !TASK_DEF_ARN!
echo   Log Group     : !LOG_GROUP!
if not "!ALB_DNS!"=="" echo   ALB DNS       : http://!ALB_DNS!
echo.
echo Troubleshooting tips:
echo   - View logs  : aws logs tail !LOG_GROUP! --follow --region !AWS_REGION!
echo   - List tasks : aws ecs list-tasks --cluster !CLUSTER_NAME! --region !AWS_REGION!

endlocal
