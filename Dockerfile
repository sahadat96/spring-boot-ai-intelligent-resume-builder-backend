# ---- Stage 1: Build with Java 21 LTS ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Cache Maven dependencies before copying source code
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ---- Stage 2: Minimal Hardened Non-Root Runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Fixed UID/GID 10001 allows least-privilege Docker Secrets (uid: "10001", mode: 0400)
RUN addgroup -g 10001 -S appgroup \
    && adduser -u 10001 -S appuser -G appgroup \
    && apk add --no-cache curl

COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER 10001:10001

EXPOSE 8080

# Container-aware JVM flags (75% container RAM ceiling, fast entropy, G1GC)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/urandom", \
  "-jar", "app.jar"]
