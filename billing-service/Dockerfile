FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle.kts .
COPY billing-service/build.gradle.kts billing-service/
COPY billing-service/src billing-service/src
RUN ./gradlew :billing-service:build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /app/billing-service/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
