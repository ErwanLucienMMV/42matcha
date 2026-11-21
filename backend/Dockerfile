FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre-jammy@sha256:a8bff88f760e87e0c8844f8461f807c203fd5e8f78c951c843024647e965eaf5
RUN groupadd --gid 10001 matcha && useradd --uid 10001 --gid matcha --no-create-home matcha
WORKDIR /app
COPY --from=build --chown=matcha:matcha /app/target/matcha-api.jar app.jar
USER matcha
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", "-jar", "app.jar"]
