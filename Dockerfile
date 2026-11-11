# Multi-stage build for smaller image size

# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy project files
COPY pom.xml .
COPY src ./src

# Build application with retries
RUN for i in 1 2 3; do mvn -B -ntp -Dmaven.wagon.http.pool=false clean package -DskipTests && exit 0; echo "Build failed. Retrying in 10 seconds..."; sleep 10; done; exit 1

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
RUN mkdir -p /app/uploads && chown -R spring:spring /app/uploads

# Copy application JAR
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
