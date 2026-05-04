# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY ./queue-service/queue-settings.gradle settings.gradle
COPY gradle gradle
COPY shared-kernel shared-kernel
COPY queue-service/build.gradle queue-service/build.gradle

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew :queue-service:dependencies --no-daemon

COPY queue-service/src queue-service/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :queue-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/queue-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
