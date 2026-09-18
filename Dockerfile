FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /app/target/rate-limiter-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

USER nobody
ENTRYPOINT ["java", "-jar", "app.jar"]
