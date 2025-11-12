# syntax=docker/dockerfile:1

### 1) Build do JAR (usa Maven + JDK)
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# baixa dependências em cache para builds mais rápidos
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests clean package

### 2) Runtime enxuto (JRE)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Ajustes básicos de JVM para containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# copia o JAR gerado (ajusta o padrão se usar outro nome)
COPY --from=build /app/target/*SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
