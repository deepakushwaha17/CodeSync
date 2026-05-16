# ─── Stage 1: Build ───────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set working directory inside container
WORKDIR /app

# Copy pom.xml first (leverage Docker layer caching for dependencies)
COPY pom.xml .

# Download all dependencies (cached if pom.xml hasn't changed)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the JAR, skip tests for faster image build
RUN mvn clean package -DskipTests

# ─── Stage 2: Run ─────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Add a non-root user for security
RUN addgroup -S codesync && adduser -S codesync -G codesync

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown codesync:codesync app.jar

# Switch to non-root user
USER codesync

# Expose Eureka dashboard port
EXPOSE 8761

# Health check — Docker will mark container unhealthy if this fails
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]