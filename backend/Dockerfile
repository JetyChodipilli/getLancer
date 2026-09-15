FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
# Database integration tests run in CI against PostgreSQL before image promotion.
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S getlancer && adduser -S getlancer -G getlancer
USER getlancer
WORKDIR /app
COPY --from=build /app/target/getlancer-api-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
