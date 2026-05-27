@echo off
setlocal enabledelayedexpansion

REM Deploy ResortsLite to GCP GKE (Windows)
REM This script deploys the containerized application to Google Kubernetes Engine

echo ==========================================
echo ResortsLite - GKE Deployment Script
echo ==========================================
echo.

REM Prompt for GCP configuration
echo === GCP Configuration ===
set /p GCP_PROJECT="Enter GCP Project ID: "
set /p GCP_ZONE="Enter GCP Zone (e.g., us-central1-a): "
set /p CLUSTER_NAME="Enter GKE Cluster Name: "

if "!GCP_PROJECT!"=="" (
    echo ERROR: GCP Project ID is required
    exit /b 1
)
if "!GCP_ZONE!"=="" (
    echo ERROR: GCP Zone is required
    exit /b 1
)
if "!CLUSTER_NAME!"=="" (
    echo ERROR: GKE Cluster Name is required
    exit /b 1
)

echo.
echo === Docker Image Configuration ===
set /p IMAGE_URI="Enter Docker Image URI (e.g., us-central1-docker.pkg.dev/project/repo/resortslite:latest): "

if "!IMAGE_URI!"=="" (
    echo ERROR: Docker Image URI is required
    exit /b 1
)

echo.
echo === Application Configuration ===
echo Configure external service connections (press Enter to skip optional values)
echo.

REM Redis Configuration
set /p REDIS_HOST="Enter Redis Host (default: redis.example.com): "
if "!REDIS_HOST!"=="" set REDIS_HOST=redis.example.com

set /p REDIS_PORT="Enter Redis Port (default: 6379): "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p REDIS_PASSWORD="Enter Redis Password (optional): "

REM Google Cloud Storage Configuration
set /p GCS_PROJECT_ID="Enter GCP Project ID for GCS (default: !GCP_PROJECT!): "
if "!GCS_PROJECT_ID!"=="" set GCS_PROJECT_ID=!GCP_PROJECT!

set /p GCS_BUCKET_NAME="Enter GCS Bucket Name (default: resortslite-files): "
if "!GCS_BUCKET_NAME!"=="" set GCS_BUCKET_NAME=resortslite-files

REM External Service Endpoints
set /p APP_PAYMENT_ENDPOINT="Enter Payment Service Endpoint (default: http://payment-svc:9090/charge): "
if "!APP_PAYMENT_ENDPOINT!"=="" set APP_PAYMENT_ENDPOINT=http://payment-svc:9090/charge

set /p APP_INVENTORY_ENDPOINT="Enter Inventory Service Endpoint (default: http://inventory-svc:8081/rooms): "
if "!APP_INVENTORY_ENDPOINT!"=="" set APP_INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms

set /p APP_NOTIFICATION_ENDPOINT="Enter Notification Service Endpoint (default: http://notify-svc:7070/send): "
if "!APP_NOTIFICATION_ENDPOINT!"=="" set APP_NOTIFICATION_ENDPOINT=http://notify-svc:7070/send

echo.
echo ==========================================
echo Configuration Summary
echo ==========================================
echo GCP Project: !GCP_PROJECT!
echo GCP Zone: !GCP_ZONE!
echo GKE Cluster: !CLUSTER_NAME!
echo Docker Image: !IMAGE_URI!
echo Redis Host: !REDIS_HOST!
echo GCS Bucket: !GCS_BUCKET_NAME!
echo ==========================================
echo.

set /p CONFIRM="Proceed with deployment? (yes/no): "
if not "!CONFIRM!"=="yes" (
    echo Deployment cancelled
    exit /b 0
)

REM Configure kubectl to use the GKE cluster
echo.
echo ==========================================
echo Configuring kubectl for GKE cluster...
echo ==========================================
gcloud container clusters get-credentials "!CLUSTER_NAME!" --zone "!GCP_ZONE!" --project "!GCP_PROJECT!"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for GKE cluster
    exit /b 1
)

REM Verify cluster connectivity
echo.
echo Verifying cluster connectivity...
kubectl cluster-info

if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to Kubernetes cluster
    exit /b 1
)

REM Create temporary directory for processed manifests
set TEMP_DIR=%TEMP%\resortslite-deploy-%RANDOM%
mkdir "!TEMP_DIR!"

REM Copy manifests to temp directory
xcopy /E /I /Y kubernetes "!TEMP_DIR!" >nul

REM Replace placeholders in deployment manifest using PowerShell
echo.
echo ==========================================
echo Updating Kubernetes manifests...
echo ==========================================

powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{GCP_PROJECT_ID}}', '!GCS_PROJECT_ID!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{GCS_BUCKET_NAME}}', '!GCS_BUCKET_NAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{APP_PAYMENT_ENDPOINT}}', '!APP_PAYMENT_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{APP_INVENTORY_ENDPOINT}}', '!APP_INVENTORY_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{APP_NOTIFICATION_ENDPOINT}}', '!APP_NOTIFICATION_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo Success: Manifests updated successfully

REM Apply Kubernetes manifests
echo.
echo ==========================================
echo Deploying to GKE...
echo ==========================================

echo.
echo Creating namespace...
kubectl apply -f "!TEMP_DIR!\namespace.yaml"

echo.
echo Deploying application...
kubectl apply -f "!TEMP_DIR!\deployment.yaml"

echo.
echo Creating service...
kubectl apply -f "!TEMP_DIR!\service.yaml"

echo.
echo Creating ingress...
kubectl apply -f "!TEMP_DIR!\ingress.yaml"

REM Wait for deployment to complete
echo.
echo ==========================================
echo Waiting for deployment to complete...
echo ==========================================
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed
    echo.
    echo Checking pod status...
    kubectl get pods -n resortslite
    echo.
    echo Checking pod logs...
    kubectl logs -n resortslite -l app=resortslite --tail=50
    rmdir /S /Q "!TEMP_DIR!"
    exit /b 1
)

REM Verify deployment
echo.
echo ==========================================
echo Verifying deployment...
echo ==========================================
kubectl get pods,svc,ingress -n resortslite

REM Get ingress IP
echo.
echo ==========================================
echo Deployment Information
echo ==========================================
echo.
echo Namespace: resortslite
echo Deployment: resortslite
echo Service: resortslite-service
echo.

for /f "tokens=*" %%i in ('kubectl get ingress resortslite-ingress -n resortslite -o jsonpath^="{.status.loadBalancer.ingress[0].ip}" 2^>nul') do set INGRESS_IP=%%i
if "!INGRESS_IP!"=="" set INGRESS_IP=pending

echo Ingress IP: !INGRESS_IP!

if "!INGRESS_IP!"=="pending" (
    echo.
    echo Note: Ingress IP is still being provisioned. This may take a few minutes.
    echo Run the following command to check the status:
    echo   kubectl get ingress resortslite-ingress -n resortslite
)

echo.
echo ==========================================
echo Success: Deployment completed successfully!
echo ==========================================
echo.
echo Access your application:
echo   - Internal: http://resortslite-service.resortslite.svc.cluster.local
echo   - External: http://!INGRESS_IP! (once IP is assigned)
echo.
echo Useful commands:
echo   - View pods: kubectl get pods -n resortslite
echo   - View logs: kubectl logs -n resortslite -l app=resortslite
echo   - Scale deployment: kubectl scale deployment resortslite -n resortslite --replicas=3
echo   - Delete deployment: kubectl delete namespace resortslite
echo ==========================================

REM Cleanup temp directory
rmdir /S /Q "!TEMP_DIR!"

endlocal
