@echo off
setlocal enabledelayedexpansion

REM Build and Push Docker Image Script for ResortsLite (Windows)
REM Supports Google Artifact Registry and Docker Hub

echo ==========================================
echo ResortsLite - Docker Build and Push Script
echo ==========================================
echo.

REM Project configuration
set PROJECT_NAME=resortslite

REM Sanitize project name for Docker image naming
set IMAGE_NAME=%PROJECT_NAME%
set IMAGE_NAME=!IMAGE_NAME: =-!
for %%i in (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) do set IMAGE_NAME=!IMAGE_NAME:%%i=%%i!
set IMAGE_NAME=%IMAGE_NAME: =%

echo Project: %PROJECT_NAME%
echo Image Name: %IMAGE_NAME%
echo.

REM Prompt for image tag
set /p IMAGE_TAG="Enter image tag (default: latest): "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

echo Using tag: !IMAGE_TAG!
echo.

REM Select registry type
echo Select Docker Registry:
echo 1. Google Artifact Registry
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    echo.
    echo === Google Artifact Registry Configuration ===
    
    REM Prompt for GCP details
    set /p GCP_PROJECT="Enter GCP Project ID: "
    set /p GCP_REGION="Enter GCP Region (e.g., us-central1): "
    set /p ARTIFACT_REPO="Enter Artifact Registry Repository Name: "
    
    REM Construct full image name
    set FULL_IMAGE_NAME=!GCP_REGION!-docker.pkg.dev/!GCP_PROJECT!/!ARTIFACT_REPO!/!IMAGE_NAME!:!IMAGE_TAG!
    
    echo.
    echo Full Image Name: !FULL_IMAGE_NAME!
    echo.
    
    REM Authenticate with GCP
    echo Authenticating with Google Cloud...
    gcloud auth login
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: GCP authentication failed
        exit /b 1
    )
    
    REM Configure Docker for Artifact Registry
    echo Configuring Docker for Artifact Registry...
    gcloud auth configure-docker !GCP_REGION!-docker.pkg.dev
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker configuration for Artifact Registry failed
        exit /b 1
    )
    
) else if "!REGISTRY_CHOICE!"=="2" (
    echo.
    echo === Docker Hub Configuration ===
    
    REM Prompt for Docker Hub credentials
    set /p DOCKER_USERNAME="Enter Docker Hub Username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub Password/Token: "
    
    REM Construct full image name
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!
    
    echo.
    echo Full Image Name: !FULL_IMAGE_NAME!
    echo.
    
    REM Authenticate with Docker Hub
    echo Authenticating with Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub authentication failed
        exit /b 1
    )
    
) else (
    echo ERROR: Invalid choice. Please select 1 or 2.
    exit /b 1
)

REM Build Docker image
echo.
echo ==========================================
echo Building Docker Image...
echo ==========================================
docker build -t "!FULL_IMAGE_NAME!" .

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed
    exit /b 1
)

echo.
echo Success: Docker image built successfully: !FULL_IMAGE_NAME!

REM Push Docker image
echo.
echo ==========================================
echo Pushing Docker Image...
echo ==========================================
docker push "!FULL_IMAGE_NAME!"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed
    exit /b 1
)

echo.
echo ==========================================
echo SUCCESS!
echo ==========================================
echo Image pushed successfully: !FULL_IMAGE_NAME!
echo.
echo Next steps:
echo 1. Use this image URI in your Kubernetes deployment
echo 2. Run deploy-image.bat to deploy to GKE
echo ==========================================

endlocal
