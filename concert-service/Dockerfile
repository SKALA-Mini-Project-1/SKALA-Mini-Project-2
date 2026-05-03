FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY concert-service/concert-settings.gradle settings.gradle
COPY gradle gradle
COPY shared-kernel shared-kernel
COPY concert-service concert-service

RUN chmod +x gradlew && ./gradlew :concert-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/concert-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
