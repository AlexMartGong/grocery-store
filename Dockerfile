# Runtime-only image — the JAR is built in GitHub Actions and SCP'd alongside
# this Dockerfile (see .github/workflows/deploy.yml)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S ventacore && adduser -S ventacore -G ventacore

# *.jar matches only the runnable jar (repackaged backup is *.jar.original)
COPY target/*.jar app.jar

RUN chown -R ventacore:ventacore /app
USER ventacore

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
