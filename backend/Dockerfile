# ═══════════════════════════════════════════
# TeacherLicence Backend — Multi-stage build
# ═══════════════════════════════════════════

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy artifact
COPY --from=build /app/target/backend-*.jar app.jar

# Create directories for templates and uploads
RUN mkdir -p /app/templates /app/uploads /app/config && \
    chown -R appuser:appgroup /app

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", "-Xms512m", "-Xmx1536m", "-XX:+UseG1GC", "-jar", "app.jar"]
