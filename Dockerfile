# Build stage: compile the Java sources
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir out && find src -name '*.java' > sources.txt && javac -encoding UTF-8 -d out @sources.txt

# Run stage: a slim Java runtime with the compiled classes and the web UI
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY web ./web
# Render sets PORT; the app reads it and listens on all interfaces.
EXPOSE 8090
CMD ["java", "-cp", "out", "app.WebApp"]
