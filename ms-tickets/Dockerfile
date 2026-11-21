# ==== Etapa 1: Compilacion ====
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
# Reemplazar go-offline por dependency:resolve
RUN mvn dependency:resolve
COPY src ./src
RUN mvn clean package -DskipTests

# ==== Etapa 2: Ejecucion ====
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 spring && adduser -S -D -H -u 10001 -G spring spring
WORKDIR /app
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar
EXPOSE 8084
USER spring:spring
ENTRYPOINT ["java", "-jar", "app.jar"]
