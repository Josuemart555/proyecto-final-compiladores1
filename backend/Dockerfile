FROM maven:3.9.11-eclipse-temurin-21 AS dev
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends inotify-tools wget \
    && rm -rf /var/lib/apt/lists/*
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY docker/dev-entrypoint.sh /usr/local/bin/dev-entrypoint.sh
RUN chmod +x mvnw /usr/local/bin/dev-entrypoint.sh \
    && ./mvnw dependency:go-offline -B
EXPOSE 8080
CMD ["dev-entrypoint.sh"]

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget \
    && addgroup -S spring \
    && adduser -S spring -G spring \
    && mkdir -p /app/logs \
    && chown -R spring:spring /app
COPY --from=build /app/target/*.jar app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
