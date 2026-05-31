FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle.kts .
COPY api-gateway/build.gradle.kts api-gateway/
RUN ./gradlew :api-gateway:dependencies --no-daemon
COPY api-gateway/src/ api-gateway/src/
RUN ./gradlew :api-gateway:build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN apk update && apk upgrade --no-cache
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /app/api-gateway/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
