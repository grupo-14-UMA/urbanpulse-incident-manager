FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/backend-*.jar /app/backend.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
