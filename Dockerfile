# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x ./mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S rentflow && adduser -S rentflow -G rentflow
COPY --from=build /workspace/target/*.jar /app/rentflow-api.jar

USER rentflow
EXPOSE 10000

CMD ["sh", "-c", "java -Dserver.port=${PORT:-10000} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -XX:MaxRAMPercentage=75 -jar /app/rentflow-api.jar"]
