# syntax=docker/dockerfile:1
# Stage 1: Build the multi-module application
FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /workspace

# Copy Maven wrapper & configurations
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Copy all module POMs and source directories
COPY platform/ platform/
COPY modules/ modules/
COPY app/ app/
RUN --mount=type=cache,target=/root/.m2 \
    find /root/.m2 \( -name "*.lastUpdated" -o -name "*.part" \) -delete 2>/dev/null || true && \
    chmod +x ./mvnw && \
    ./mvnw -B -U -s .mvn/settings.xml -T 1C package -Dmaven.test.skip=true \
        -Dmaven.wagon.http.retryHandler.count=5 \
        -Dmaven.wagon.http.pool=false \
        -Dmaven.wagon.http.retryHandler.requestSentKeepAlive=true \
        -Daether.connector.http.retryHandler.count=5 \
        -Daether.connector.connectTimeout=30000 \
        -Daether.connector.requestTimeout=60000

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

# Non-root user for security and gosu for entrypoint privilege dropping
RUN apt-get update && apt-get install -y --no-install-recommends gosu wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r edufit && useradd -r -g edufit -d /app -s /usr/sbin/nologin edufit \
    && mkdir -p /app/var/credentials \
    && chown -R edufit:edufit /app

COPY --from=builder --chown=edufit:edufit /workspace/app/target/app-1.0-SNAPSHOT.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]

