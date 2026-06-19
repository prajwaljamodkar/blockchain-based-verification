# ============================================================
#  Stage 1 — Build the Spring Boot JAR
# ============================================================
FROM gradle:8-jdk17 AS builder

WORKDIR /workspace

# Copy Gradle config (layer caching)
COPY build.gradle settings.gradle ./

# Copy source code
COPY src ./src

# Build the fat JAR (skip tests for faster builds)
# Using gradle from the base image (gradle:8-jdk17 has it preinstalled)
RUN gradle bootJar -x test --no-daemon

# ============================================================
#  Stage 2 — Slim Runtime
# ============================================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR
COPY --from=builder /workspace/build/libs/*.jar app.jar

# Create storage directory
RUN mkdir -p /app/certificate-storage

# Render sets PORT automatically; default to 8080
ENV PORT=8080
EXPOSE 8080

# Start Spring Boot
CMD ["java", "-jar", "app.jar"]
