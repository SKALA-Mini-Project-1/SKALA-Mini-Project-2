FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle ./
COPY ./user-auth-service/user-auth-settings.gradle settings.gradle
COPY gradle gradle
COPY shared-kernel shared-kernel
COPY user-auth-service/build.gradle user-auth-service/build.gradle

RUN chmod +x gradlew && ./gradlew :user-auth-service:dependencies --no-daemon

COPY user-auth-service/src user-auth-service/src

RUN ./gradlew :user-auth-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/user-auth-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
