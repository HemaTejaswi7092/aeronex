# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Resolve dependencies first so this layer is cached across source-only rebuilds.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

# curl is required for the container HEALTHCHECK against Actuator; not present
# in the base JRE image by default.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system aeronex && useradd --system --gid aeronex --no-create-home aeronex

WORKDIR /app
COPY --from=builder --chown=aeronex:aeronex /build/target/aeronex-*.jar app.jar

USER aeronex

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

# Exec form so the JVM runs as PID 1 and receives SIGTERM directly, allowing
# server.shutdown=graceful to actually take effect on `docker compose stop`.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
