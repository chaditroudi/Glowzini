# ─── Stage 1: Build ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Cache Maven wrapper and dependencies separately for faster rebuilds
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && \
    ./mvnw dependency:go-offline -B -q

# Copy source and build
COPY src/ src/
RUN ./mvnw package -DskipTests -B -q && \
    # Extract Spring Boot layered JAR for optimal Docker layer caching
    java -Djarmode=layertools -jar target/*.jar extract --destination /extracted

# ─── Stage 2: Runtime ───────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy layered JAR contents (each layer cached independently)
COPY --from=builder /extracted/dependencies/ ./
COPY --from=builder /extracted/spring-boot-loader/ ./
COPY --from=builder /extracted/snapshot-dependencies/ ./
COPY --from=builder /extracted/application/ ./

# Metadata
LABEL maintainer="glowzi-team" \
      service="identity-service" \
      org.opencontainers.image.source="https://github.com/glowzi/glowzi-backend"

EXPOSE 8081

USER appuser

# Health check using Spring Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
