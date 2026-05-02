FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
COPY shared-kernel shared-kernel
COPY concert-service concert-service
COPY queue-service queue-service
COPY ticketing-service ticketing-service
COPY payment-service payment-service
COPY incident-detector incident-detector
COPY incident-agent incident-agent
COPY incident-api incident-api
COPY user-auth-service user-auth-service
COPY incident-detector incident-detector
COPY incident-agent incident-agent
COPY incident-api incident-api

RUN chmod +x gradlew && ./gradlew :user-auth-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/user-auth-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
