FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle.kts .
COPY config-service/build.gradle.kts config-service/
COPY config-service/src/ config-service/src/
RUN ./gradlew :config-service:build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /app/config-service/build/libs/config-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
