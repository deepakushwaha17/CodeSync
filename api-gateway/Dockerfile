# ─── Stage 1: Build ───────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests

# ─── Stage 2: Run ─────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S codesync && adduser -S codesync -G codesync

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN chown codesync:codesync app.jar

USER codesync

# Expose API Gateway port
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]