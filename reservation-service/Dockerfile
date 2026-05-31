# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy gradle wrapper and files (assuming wrapper is present at root, or just rely on gradle)
# Here we copy the entire project in standard ways. Adjust for multi-module if needed.
# Since rules dictate Java 25 Alpine
COPY gradlew .
COPY gradle ./gradle
COPY reservation-service/build.gradle.kts reservation-service/
COPY settings.gradle.kts .
RUN chmod +x gradlew
RUN ./gradlew :reservation-service:dependencies --no-daemon
COPY reservation-service/src reservation-service/src
RUN ./gradlew build -x test --no-daemon

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk update && apk upgrade --no-cache

WORKDIR /app

# Create a non-root spring user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built jar
COPY --from=builder /app/reservation-service/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
