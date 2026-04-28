FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
COPY shared-kernel shared-kernel
COPY user-auth-service user-auth-service
COPY concert-service concert-service
COPY ticketing-service ticketing-service
COPY payment-service payment-service
COPY queue-service queue-service

RUN chmod +x gradlew && ./gradlew :queue-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/queue-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
