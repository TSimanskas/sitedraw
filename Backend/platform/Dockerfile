# UI
FROM node:22-bookworm-slim AS ui
WORKDIR /ui
ENV NODE_OPTIONS=--max-old-space-size=1536
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npx vite build

# API + static UI in one jar
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true
ARG BUILD_ID=20260928-local-prod-split
COPY src src
COPY --from=ui /ui/dist src/main/resources/static
RUN mvn -B package -DskipTests -q

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
RUN mkdir -p /data/storage
COPY --from=build /app/target/platform-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
