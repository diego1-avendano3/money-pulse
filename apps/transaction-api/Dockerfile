# Build context is the repo root (not this directory): a Gradle multi-module
# build needs settings.gradle.kts, gradle/, and every module's build.gradle.kts
# to configure the project graph, even when building only this module's jar.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew.bat ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY apps/transaction-api/build.gradle.kts apps/transaction-api/build.gradle.kts
COPY apps/transaction-processor/build.gradle.kts apps/transaction-processor/build.gradle.kts
COPY apps/transaction-api/src apps/transaction-api/src
RUN ./gradlew :apps:transaction-api:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre AS runtime
# curl is needed by compose.yaml's healthcheck for this service.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/apps/transaction-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
