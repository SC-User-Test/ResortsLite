# Multi-stage Dockerfile for ResortsLite Spring Boot Application
# Stage 1: Build stage using Maven with Java 8
FROM maven:3.9.4-eclipse-temurin-8 AS builder

# Set working directory
WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage using Amazon Corretto 8 (as specified)
FROM amazoncorretto:8

# Set working directory
WORKDIR /app

# Create non-root user for security
RUN yum install -y shadow-utils && \
    groupadd -r appuser && \
    useradd -r -g appuser -s /sbin/nologin appuser && \
    yum clean all && \
    rm -rf /var/cache/yum

# Copy JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Create directories for logs and config
RUN mkdir -p /app/logs /app/config && \
    chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Set JVM options for containerized environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Set timezone
ENV TZ=UTC

# Expose application port
EXPOSE 8080

# Health check using application endpoint (no curl needed - ECS will handle this)
# Note: Health checks are handled by ECS service configuration

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
