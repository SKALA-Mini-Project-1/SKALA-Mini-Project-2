# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY concert-service/concert-settings.gradle settings.gradle
COPY gradle gradle
COPY shared-kernel shared-kernel
COPY concert-service/build.gradle concert-service/build.gradle

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew :concert-service:dependencies --no-daemon

COPY concert-service/src concert-service/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :concert-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/concert-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
