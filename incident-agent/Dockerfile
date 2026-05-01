FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY gradle gradle
COPY shared-kernel shared-kernel
COPY incident-agent incident-agent

RUN echo "rootProject.name = 'skala-mini-project-2'\ninclude 'shared-kernel'\ninclude 'incident-agent'" > settings.gradle && \
    chmod +x gradlew && \
    ./gradlew :incident-agent:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/incident-agent/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
