# M-SAL2.0 backend image (m-sal2.0)

# ── Build ─────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B package -DskipTests

# ── Run ───────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S msal && adduser -S msal -G msal
WORKDIR /app
COPY --from=build /build/target/msal2-*.jar app.jar
USER msal
# The container must fit in 512MB (Render Starter, CLAUDE.md rule 13)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
