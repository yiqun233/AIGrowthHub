ARG BASE_REGISTRY=docker.io/library
FROM ${BASE_REGISTRY}/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/ ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package
COPY infra/HealthCheck.java /health/HealthCheck.java
RUN javac -d /health /health/HealthCheck.java
FROM ${BASE_REGISTRY}/eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/target/growthhub-0.1.0-SNAPSHOT.jar /app/app.jar
COPY --from=build --chown=10001:10001 /health/HealthCheck.class /app/health/HealthCheck.class
USER 10001:10001
ENV SERVER_ADDRESS=0.0.0.0 AUTH_ENABLED=true SESSION_COOKIE_SECURE=true JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 CMD ["java","-cp","/app/health","HealthCheck"]
ENTRYPOINT ["java","-jar","/app/app.jar"]
