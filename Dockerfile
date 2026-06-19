# ============================================================
#  Stage 1 — Build the Spring Boot JAR
# ============================================================
FROM gradle:8-jdk17 AS builder

WORKDIR /workspace

# Copy Gradle wrapper & config first for layer caching
COPY backend/build.gradle backend/settings.gradle ./
COPY backend/gradle ./gradle
COPY backend/gradlew ./

# Copy source code
COPY backend/src ./src

# Build the fat JAR (skip tests for faster CI builds)
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# ============================================================
#  Stage 2 — Runtime Image
# ============================================================
FROM eclipse-temurin:17-jre

# Install Node.js 18 (for Hardhat) and curl (for health checks)
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl gnupg && \
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y --no-install-recommends nodejs && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR
COPY --from=builder /workspace/build/libs/*.jar app.jar

# Copy blockchain project (contracts, scripts, config)
COPY blockchain/ ./blockchain/

# Copy startup script
COPY entrypoint.sh ./entrypoint.sh
RUN chmod +x entrypoint.sh

# Create storage directory
RUN mkdir -p /app/certificate-storage

# Render sets PORT automatically; default to 8080
ENV PORT=8080
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD curl -f http://localhost:${PORT}/actuator/health || exit 1

ENTRYPOINT ["./entrypoint.sh"]
