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

# Embedding model (all-MiniLM-L6-v2), pinned to one revision and checked against its SHA-256, so the API starts
# without reaching Hugging Face. Keep in step with DjlSentenceEmbeddingProvider.REVISION and its checksums.
ARG MINILM_REVISION=1110a243fdf4706b3f48f1d95db1a4f5529b4d41
RUN mkdir -p /opt/models/all-minilm-l6-v2 \
  && cd /opt/models/all-minilm-l6-v2 \
  && curl -fsSL --retry 3 -o model.onnx \
     "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/${MINILM_REVISION}/onnx/model.onnx" \
  && curl -fsSL --retry 3 -o tokenizer.json \
     "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/${MINILM_REVISION}/tokenizer.json" \
  && printf '%s  %s\n' \
       6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452 model.onnx \
       be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037 tokenizer.json \
     | sha256sum -c -
ENV ASSURANCE_EVIDENCE_DJL_MODEL_DIR=/opt/models/all-minilm-l6-v2

COPY infra/evgraph-requirements.txt /tmp/evgraph-requirements.txt
RUN python3 -m venv /opt/evgraph \
  && /opt/evgraph/bin/pip install --no-cache-dir --require-hashes -r /tmp/evgraph-requirements.txt \
  && /opt/evgraph/bin/evgraph --help > /dev/null \
  && rm /tmp/evgraph-requirements.txt
ENV EVGRAPH_BIN=/opt/evgraph/bin/evgraph

WORKDIR /app

COPY --from=build /build/app.jar /app/app.jar
# DJL unpacks its native tokenizer and ONNX Runtime libraries on first use; give it a cache the app user can write.
RUN chown app:app /app/app.jar \
  && mkdir -p /app/.djl.ai \
  && chown app:app /app/.djl.ai
ENV DJL_CACHE_DIR=/app/.djl.ai

USER app

EXPOSE 8080

# Generous start-period: Flyway migrations and loading the embedding model
HEALTHCHECK --interval=10s --timeout=5s --start-period=120s --retries=12 \
  CMD curl -fsS http://localhost:8080/actuator/health >/dev/null || exit 1

# Resource note: set container memory limits in Compose/K8s. The heap gets 60%: ONNX Runtime, Tika, thread stacks,
# metaspace and the code cache live outside it, and at 75% a 3 GB container can be OOM-killed under load. Exit on
# OutOfMemoryError so the container restarts instead of limping on.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
