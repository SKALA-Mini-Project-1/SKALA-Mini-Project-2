FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY ticketing-service/ticketing-settings.gradle settings.gradle
COPY gradle gradle
COPY shared-kernel shared-kernel
COPY ticketing-service ticketing-service

RUN chmod +x gradlew && ./gradlew :ticketing-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/ticketing-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
