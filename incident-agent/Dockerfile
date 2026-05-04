# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY incident-agent/incident-agent-settings.gradle settings.gradle
COPY gradle gradle
COPY incident-agent/build.gradle incident-agent/build.gradle

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew :incident-agent:dependencies --no-daemon

COPY incident-agent/src incident-agent/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :incident-agent:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/incident-agent/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
