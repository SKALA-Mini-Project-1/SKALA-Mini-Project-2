FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY incident-api/incident-api-settings.gradle settings.gradle
COPY gradle gradle
COPY incident-api incident-api

RUN chmod +x gradlew && ./gradlew :incident-api:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/incident-api/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
