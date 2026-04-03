FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy gradle wrapper and source
COPY gradlew .
COPY gradle gradle
COPY stay-service/build.gradle.kts stay-service/
COPY settings.gradle.kts .
COPY stay-service/src stay-service/src

# Build the application
RUN ./gradlew build -x test

FROM eclipse-temurin:21-jre-alpine

# Set non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/stay-service/build/libs/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
