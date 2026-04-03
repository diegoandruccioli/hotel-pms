# Multi-stage Dockerfile for Food & Beverage Service
FROM eclipse-temurin:21-jdk-alpine AS builder
# Set working directory
WORKDIR /app
# Copy gradle files and source code
COPY gradlew .
COPY gradle gradle
COPY settings.gradle.kts .
COPY fb-service/build.gradle.kts fb-service/
COPY fb-service/src fb-service/src
# Build the application
RUN ./gradlew :fb-service:bootJar --no-daemon
# Final runtime stage
FROM eclipse-temurin:21-jre-alpine
# Add a non-root group and user 
RUN addgroup -S spring && adduser -S spring -G spring
# Switch to the non-root user
USER spring:spring
# Set working directory
WORKDIR /app
# Copy the built jar from the builder stage
COPY --from=builder /app/fb-service/build/libs/*.jar app.jar
# Expose the application port
EXPOSE 8086
# Health check (requires curl or you can use actuator if exposed)
# Using full path for actuator if health endpoint is available
HEALTHCHECK --interval=30s --timeout=3s \
    CMD wget -q --spider http://localhost:8086/actuator/health || exit 1
# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
