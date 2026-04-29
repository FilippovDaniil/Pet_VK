# Single-stage build — uses only eclipse-temurin:21-jdk-alpine
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy Gradle wrapper and config first (Docker layer cache)
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# Download dependencies (cached layer if build.gradle unchanged)
RUN ./gradlew dependencies --no-daemon -q || true

# Copy source and build JAR
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Create uploads dir and expose port
RUN mkdir -p uploads/avatars

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "build/libs/social-network-0.0.1-SNAPSHOT.jar"]
