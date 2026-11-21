FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
COPY healthcheck ./healthcheck
RUN mvn -B -ntp -DskipTests package
RUN javac -d /build/healthcheck-classes healthcheck/HealthCheck.java

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
RUN mkdir -p /data/documents && chown -R app:app /data
WORKDIR /app
COPY --from=build --chown=app:app /build/target/credit-intelligence.jar /app/app.jar
COPY --from=build --chown=app:app /build/healthcheck-classes/HealthCheck.class /app/HealthCheck.class
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
