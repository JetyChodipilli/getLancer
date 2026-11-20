ARG API_JAR_SOURCE=build

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
# Database integration tests run in CI against PostgreSQL before image promotion.
RUN mvn -B -DskipTests package

# CI can supply the JAR already verified in the backend job as a named context.
FROM ${API_JAR_SOURCE} AS api-package

FROM eclipse-temurin:17-jre-alpine
RUN apk add --upgrade --no-cache 'libexpat>=2.9.0-r0' 'brotli-libs>=1.2.1-r0' 'gnutls>=3.8.14-r0' \
    && addgroup -S getlancer && adduser -S getlancer -G getlancer
USER getlancer
WORKDIR /app
COPY --from=api-package /app/target/getlancer-api-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
