# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21.0.11_10-jdk-jammy@sha256:dbfd085220ae632a0830166e443747d1ee89e9038d92e3b48c3e5e9d8292b9a7 AS build
WORKDIR /workspace

RUN apt-get update && \
    apt-get install --yes --no-install-recommends unzip && \
    rm -rf /var/lib/apt/lists/*

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package && \
    java -Djarmode=tools -jar target/cab-*.jar extract --layers --destination /workspace/layers

FROM eclipse-temurin:21.0.11_10-jre-jammy@sha256:8208b3a3733529f42c4b565bfcbf16d914eb628f3ead8488b6b4d6363fcbe7cf
ARG VERSION=0.0.1-SNAPSHOT
ARG REVISION=unknown
ARG CREATED=unknown
LABEL org.opencontainers.image.title="Cab Marketplace" \
      org.opencontainers.image.description="Multi-tenant ride-hailing marketplace API" \
      org.opencontainers.image.source="https://github.com/rahilsh/cab" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.created="${CREATED}"

RUN groupadd --gid 10001 cab && \
    useradd --uid 10001 --gid cab --no-create-home --shell /usr/sbin/nologin cab && \
    mkdir -p /app /tmp && chown -R cab:cab /app /tmp
WORKDIR /app
COPY --from=build --chown=cab:cab /workspace/layers/dependencies/ ./
COPY --from=build --chown=cab:cab /workspace/layers/spring-boot-loader/ ./
COPY --from=build --chown=cab:cab /workspace/layers/snapshot-dependencies/ ./
COPY --from=build --chown=cab:cab /workspace/layers/application/ ./
RUN mv cab-*.jar app.jar

USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp" \
    SPRING_PROFILES_ACTIVE=prod
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["curl", "--fail", "--silent", "--show-error", "http://127.0.0.1:8080/actuator/health/liveness"]
ENTRYPOINT ["java", "-jar", "app.jar"]
