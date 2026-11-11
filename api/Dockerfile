# Build from the repository root: docker build -f api/Dockerfile -t statecraft-api .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
ENV JAVA_TOOL_OPTIONS="--enable-preview --add-modules=jdk.incubator.vector"
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core ./core
COPY engines ./engines
COPY app ./app
COPY api ./api
RUN chmod +x gradlew && ./gradlew --no-daemon :api:quarkusBuild

FROM eclipse-temurin:25-jre
WORKDIR /deployments
COPY --from=build --chown=10001:0 /workspace/api/build/quarkus-app/ ./
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "--add-modules=jdk.incubator.vector", "-XX:MaxRAMPercentage=75", "-jar", "quarkus-run.jar"]
