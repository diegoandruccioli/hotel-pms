# Stage 1: Build the application using Gradle and Java 21
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy the Gradle wrapper and project files
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts ./
COPY inventory-service/build.gradle.kts inventory-service/
COPY inventory-service/src inventory-service/src

# Build the application (exclude tests for Docker build)
RUN ./gradlew clean build -x test

# Stage 2: Create the runtime image using a minimal JRE
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add a non-root user for security purposes
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar from the builder stage
COPY --from=builder /app/inventory-service/build/libs/*.jar app.jar

# Expose the application port
EXPOSE 8081

# Set the entrypoint to run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
