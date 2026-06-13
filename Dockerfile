# Multi-stage Dockerfile for ResortsLite Spring Boot Application
# Stage 1: Builder - Build the application using Maven
FROM maven:3.9.4-amazoncorretto-8 AS builder

WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime - Run the application
FROM amazoncorretto:8

WORKDIR /app

# Create non-root user for security
RUN yum install -y shadow-utils && \
    groupadd -r appuser && \
    useradd -r -g appuser -s /bin/false appuser && \
    yum clean all

# Copy the built JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Create directories for logs and config
RUN mkdir -p /app/logs /app/config && \
    chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Set JVM options for containerized environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions"

# Set timezone
ENV TZ=UTC

# Expose application port
EXPOSE 8080

# Health check is handled by ECS service - no curl/wget needed in runtime image

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
