FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system orderhub && useradd --system --gid orderhub orderhub
RUN mkdir -p /app/data/images && chown -R orderhub:orderhub /app/data
WORKDIR /app
COPY --from=build /workspace/target/orderhub-0.0.1-SNAPSHOT.jar app.jar
USER orderhub
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
