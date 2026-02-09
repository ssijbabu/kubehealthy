# Multi-stage build for KuberHealthy Java
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar from builder stage
COPY --from=builder /build/target/kuberhealthy-java-1.0.0.jar /app/kuberhealthy.jar

# Create non-root user
RUN addgroup -g 1000 kuberhealthy && \
    adduser -D -u 1000 -G kuberhealthy kuberhealthy

USER kuberhealthy

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/kuberhealthy.jar"]