# Cloud Readiness Configuration Guide

## Overview
This application has been transformed to be cloud-ready for AWS deployment. All cloud compatibility issues have been resolved.

## Environment Variables

### Required Environment Variables for AWS Deployment

#### Server Configuration
- `SERVER_PORT` - Application server port (default: 8080)
  - Set by ECS/EKS for dynamic port assignment

#### Database Configuration
- `DB_URL` - Database JDBC URL
- `DB_USERNAME` - Database username
- `DB_PASSWORD` - Database password

#### AWS Configuration
- `AWS_REGION` - AWS region (default: us-east-1)
- `AWS_S3_BUCKET_NAME` - S3 bucket name for report storage
- `AWS_DB_SECRET_NAME` - Secrets Manager secret name for database credentials

#### Redis Configuration (ElastiCache)
- `REDIS_HOST` - Redis host (ElastiCache endpoint)
- `REDIS_PORT` - Redis port (default: 6379)
- `REDIS_PASSWORD` - Redis password (if authentication enabled)
- `REDIS_SSL` - Enable SSL for Redis connection (default: false)

#### Service Endpoints
- `PAYMENT_ENDPOINT` - Payment service endpoint URL
- `INVENTORY_ENDPOINT` - Inventory service endpoint URL
- `NOTIFICATION_ENDPOINT` - Notification service endpoint URL
- `REPORT_SERVICE_URL` - Report service base URL

## AWS Services Required

### 1. Amazon S3
- **Purpose**: Durable file storage for reports
- **Configuration**: Create S3 bucket and set `AWS_S3_BUCKET_NAME`
- **IAM Permissions Required**:
  - `s3:PutObject`
  - `s3:GetObject`
  - `s3:ListBucket`

### 2. AWS Secrets Manager
- **Purpose**: Secure storage of database credentials
- **Configuration**: Create secret with JSON format:
  ```json
  {
    "host": "database-host",
    "username": "db-user",
    "password": "db-password"
  }
  ```
- **IAM Permissions Required**:
  - `secretsmanager:GetSecretValue`

### 3. AWS Systems Manager Parameter Store
- **Purpose**: Configuration management for service endpoints
- **Configuration**: Create parameters:
  - `/resortslite/report/service/url`
- **IAM Permissions Required**:
  - `ssm:GetParameter`

### 4. Amazon ElastiCache for Redis
- **Purpose**: Distributed session management and caching
- **Configuration**: 
  - Create Redis cluster
  - Set `REDIS_HOST` to cluster endpoint
  - Enable encryption in transit (recommended)
- **Features**:
  - Stateless application instances
  - Horizontal scaling support
  - Session persistence across instances

## Cloud Readiness Fixes Applied

### 1. File System Dependencies (Blockers 1-7)
- **Issue**: Hard-coded file paths and local file system operations
- **Fix**: Migrated to Amazon S3 for durable object storage
- **Files Modified**: `ReportService.java`

### 2. Hard-coded Credentials (Blockers 8-9)
- **Issue**: Database credentials in source code
- **Fix**: Migrated to AWS Secrets Manager with automatic rotation support
- **Files Modified**: `BookingService.java`

### 3. Hard-coded URLs (Blockers 10-11)
- **Issue**: Environment-specific URLs in code
- **Fix**: Externalized to AWS Systems Manager Parameter Store
- **Files Modified**: `BookingController.java`, `ReportService.java`

### 4. Hard-coded Ports (Blocker 12)
- **Issue**: Fixed port numbers preventing dynamic assignment
- **Fix**: Externalized to environment variables
- **Files Modified**: `ReportService.java`, `application.properties`

### 5. HTTP Session State (Blockers 13-17)
- **Issue**: In-memory session storage preventing horizontal scaling
- **Fix**: Migrated to Amazon ElastiCache for Redis with Spring Session
- **Files Modified**: `BookingController.java`, `RedisConfig.java`

### 6. File-based Authentication (Blocker 18)
- **Issue**: Local file storage for credentials
- **Fix**: Migrated to AWS Secrets Manager
- **Files Modified**: `BookingService.java`

### 7. Clock/Time Dependencies (Blocker 19)
- **Issue**: Local timezone dependencies
- **Fix**: Migrated to java.time API with UTC standardization
- **Files Modified**: `ReportService.java`

### 8. In-Memory Caching (Blocker 20)
- **Issue**: Unbounded in-memory cache without TTL
- **Fix**: Migrated to Amazon ElastiCache for Redis with TTL policies
- **Files Modified**: `BookingController.java`, `RedisConfig.java`

## Deployment Checklist

### Pre-deployment
- [ ] Create S3 bucket for reports
- [ ] Create Secrets Manager secret for database credentials
- [ ] Create Parameter Store parameters for service endpoints
- [ ] Create ElastiCache Redis cluster
- [ ] Configure IAM role with required permissions
- [ ] Set all required environment variables

### Post-deployment
- [ ] Verify S3 connectivity and permissions
- [ ] Test Secrets Manager credential retrieval
- [ ] Verify Redis connectivity for session management
- [ ] Test report generation and S3 upload
- [ ] Verify distributed session management across instances
- [ ] Monitor CloudWatch logs for any errors

## Security Improvements
1. Database credentials stored in AWS Secrets Manager (encrypted at rest)
2. Parameterized SQL queries to prevent SQL injection
3. SHA-256 hashing instead of insecure MD5
4. HTTPS endpoints for service communication
5. Redis authentication support for ElastiCache

## Scalability Improvements
1. Stateless application design
2. Distributed session management via Redis
3. Horizontal scaling support
4. Cloud-native storage with S3
5. Centralized configuration management

## 12-Factor App Compliance
- ✅ I. Codebase: Single codebase tracked in version control
- ✅ II. Dependencies: Explicitly declared in pom.xml
- ✅ III. Config: Externalized to environment variables
- ✅ IV. Backing services: Treated as attached resources (S3, Redis, Secrets Manager)
- ✅ V. Build, release, run: Strictly separated
- ✅ VI. Processes: Stateless with shared-nothing architecture
- ✅ VII. Port binding: Dynamic port assignment via environment variable
- ✅ VIII. Concurrency: Horizontal scaling enabled
- ✅ IX. Disposability: Fast startup and graceful shutdown
- ✅ X. Dev/prod parity: Same backing services across environments
- ✅ XI. Logs: Structured logging to stdout
- ✅ XII. Admin processes: Run as one-off processes

## Monitoring and Observability
- Application logs sent to CloudWatch Logs
- Metrics available via CloudWatch
- Distributed tracing ready for AWS X-Ray integration
- Health check endpoints for load balancer

## Support
For issues or questions, refer to AWS documentation:
- [Amazon S3](https://docs.aws.amazon.com/s3/)
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/)
- [Amazon ElastiCache](https://docs.aws.amazon.com/elasticache/)
- [AWS Systems Manager Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html)
