FROM eclipse-temurin:21-jre-alpine
RUN apk update && apk upgrade --no-cache
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY config-service/build/libs/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
