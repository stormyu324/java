# Multi-stage build: frontend -> Spring Boot jar -> small JRE runtime image.

FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# vite writes into ../backend/src/main/resources/static
RUN mkdir -p ../backend/src/main/resources && npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 quant && mkdir /data && chown quant /data
USER quant
WORKDIR /app
COPY --from=backend /app/backend/target/quant-trader-*.jar app.jar
ENV DATA_DIR=/data \
    PORT=8080 \
    TZ=America/New_York
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
