# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21.0.8_9-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package && \
    java -Djarmode=tools -jar target/cab-*.jar extract --layers --destination /workspace/layers

FROM eclipse-temurin:21.0.8_9-jre-jammy
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
