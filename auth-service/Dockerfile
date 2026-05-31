FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle.kts .
COPY auth-service/build.gradle.kts auth-service/
RUN ./gradlew :auth-service:dependencies --no-daemon
COPY auth-service/src/ auth-service/src/
RUN ./gradlew :auth-service:build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN apk update && apk upgrade --no-cache
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /app/auth-service/build/libs/auth-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
