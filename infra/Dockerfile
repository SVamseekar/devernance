# Multi-stage production image for the Spring Boot API (Part 9).
# Build context: repository root
#   docker build -f infra/Dockerfile -t devernance-api .

# -----------------------------------------------------------------------------
# Stage 1: Maven package (skip tests — run CI / mvn test separately)
# -----------------------------------------------------------------------------
# Use multi-arch images (amd64 + arm64). Avoid alpine Maven tags — not all are multi-arch.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependency layer cache
COPY services/api/pom.xml ./pom.xml
RUN mvn -B -q -DskipTests dependency:go-offline

COPY services/api/src ./src
RUN mvn -B -DskipTests package \
  && cp target/api-*.jar /build/app.jar

# -----------------------------------------------------------------------------
# Stage 2: JRE runtime (non-root)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl python3 python3-venv \
  && rm -rf /var/lib/apt/lists/* \
  && groupadd --system app \
  && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

COPY infra/evgraph-requirements.txt /tmp/evgraph-requirements.txt
RUN python3 -m venv /opt/evgraph \
  && /opt/evgraph/bin/pip install --no-cache-dir --require-hashes -r /tmp/evgraph-requirements.txt \
  && /opt/evgraph/bin/evgraph --help > /dev/null \
  && rm /tmp/evgraph-requirements.txt
ENV EVGRAPH_BIN=/opt/evgraph/bin/evgraph

WORKDIR /app

COPY --from=build /build/app.jar /app/app.jar
RUN chown app:app /app/app.jar

USER app

EXPOSE 8080

# Generous start-period: Flyway + optional DJL model download on first boot
HEALTHCHECK --interval=10s --timeout=5s --start-period=120s --retries=12 \
  CMD curl -fsS http://localhost:8080/actuator/health >/dev/null || exit 1

# Resource note: set container memory limits in Compose/K8s; MaxRAMPercentage
# leaves headroom for native (DJL/ONNX) allocations outside the heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
