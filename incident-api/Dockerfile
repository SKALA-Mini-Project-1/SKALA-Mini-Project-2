FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY gradle gradle
COPY shared-kernel shared-kernel
COPY incident-api incident-api

RUN echo "rootProject.name = 'skala-mini-project-2'\ninclude 'shared-kernel'\ninclude 'incident-api'" > settings.gradle && \
    chmod +x gradlew && \
    ./gradlew :incident-api:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/incident-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
