# Builds the whole app (backend + frontend, packaged together) into one image.
# The build context is the repository root because backend/pom.xml packages ../frontend.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY frontend frontend
RUN mvn -B -ntp -q -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home restorant
WORKDIR /app
COPY --from=build /build/backend/target/resto-rant-0.0.1-SNAPSHOT.jar app.jar
USER restorant
# Listen on every interface inside the container. All secrets come from environment variables set on the host.
ENV SERVER_ADDRESS=0.0.0.0 COOKIE_SECURE=true
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
