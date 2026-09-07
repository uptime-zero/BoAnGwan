# Build stage
FROM --platform=$BUILDPLATFORM amazoncorretto:25-alpine AS builder
WORKDIR /app

COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
RUN ./gradlew dependencies --no-daemon -q 2>/dev/null || true
COPY src src
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon

# Runtime stage
FROM --platform=$BUILDPLATFORM amazoncorretto:25-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
