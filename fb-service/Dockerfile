FROM eclipse-temurin:25-jre-alpine
RUN apk update && apk upgrade --no-cache
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY fb-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8086
HEALTHCHECK --interval=30s --timeout=3s \
    CMD wget -q --spider http://localhost:8086/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
