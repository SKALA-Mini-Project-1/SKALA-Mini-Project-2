# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY incident-api/incident-api-settings.gradle settings.gradle
COPY gradle gradle
COPY incident-api/build.gradle incident-api/build.gradle

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew :incident-api:dependencies --no-daemon

COPY incident-api/src incident-api/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :incident-api:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/incident-api/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
