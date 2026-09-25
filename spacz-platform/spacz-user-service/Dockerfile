# ---- build ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:17-jre
RUN groupadd --system spacz && useradd --system --gid spacz --uid 1001 --no-create-home spacz
WORKDIR /app
COPY --from=build /build/target/spacz-user-service.jar app.jar
USER spacz
EXPOSE 8082
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=6 \
  CMD curl -fsS http://localhost:8082/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
