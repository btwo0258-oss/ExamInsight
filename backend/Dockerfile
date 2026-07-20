FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system --gid 10001 examinsight \
    && useradd --system --uid 10001 --gid examinsight --home-dir /app examinsight

WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar
RUN mkdir -p /app/data/uploads \
    && chown -R examinsight:examinsight /app

USER examinsight
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
