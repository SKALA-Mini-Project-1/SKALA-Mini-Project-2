# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY payment-service/payment-settings.gradle settings.gradle
COPY gradle gradle
COPY shared-kernel shared-kernel
COPY payment-service/build.gradle payment-service/build.gradle

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew :payment-service:dependencies --no-daemon

COPY payment-service/src payment-service/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :payment-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/payment-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
