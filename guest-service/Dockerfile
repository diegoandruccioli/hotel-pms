FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace/app

# Gradle wrapper and project files
COPY gradlew .
COPY gradle gradle
COPY guest-service/build.gradle.kts guest-service/
COPY settings.gradle.kts .
COPY guest-service/src guest-service/src

# Make wrapper executable and build
RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test --no-daemon

# Create lightweight production image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add a non-root group and user securely
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built jar from the builder stage
COPY --from=builder /workspace/app/guest-service/build/libs/*-SNAPSHOT.jar /app/guest-service.jar

# Run everything as non-root user
USER spring:spring

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/guest-service.jar"]
