# Multi-stage build for Spring Boot backend
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/*.jar /app/app.jar

# Render provides PORT at runtime; fallback for local docker runs
ENV PORT=8013
EXPOSE 8013

ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --server.port=${PORT}"]
