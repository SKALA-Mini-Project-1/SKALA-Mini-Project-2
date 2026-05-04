FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY incident-detector/incident-detector-settings.gradle settings.gradle
COPY gradle gradle
COPY incident-detector/build.gradle incident-detector/build.gradle

RUN chmod +x gradlew && ./gradlew :incident-detector:dependencies --no-daemon

COPY incident-detector/src incident-detector/src

RUN ./gradlew :incident-detector:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/incident-detector/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
