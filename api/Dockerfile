# YojanaMitra Spring Boot API — multi-stage build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Point at the GenAI service; override for your deployment topology.
ENV YOJANAMITRA_AI_BASE_URL=http://ai:8000
ENTRYPOINT ["java", "-jar", "app.jar"]
