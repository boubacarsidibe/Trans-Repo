# ---- Build ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# mvnw est commite sans le bit d'execution (cf. CONTRIBUTING.md) et telecharge
# Maven lui-meme (distributionType=only-script) : curl est necessaire.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sh mvnw -B dependency:go-offline

COPY src src
RUN sh mvnw -B package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S supervision && adduser -S supervision -G supervision

# app.rapports.repertoire (application.properties) est relatif au repertoire
# de travail : cree et rendu inscriptible d'avance pour l'utilisateur non-root.
RUN mkdir -p rapports && chown -R supervision:supervision /app

COPY --from=build --chown=supervision:supervision /app/target/backend_trans-*.jar app.jar

USER supervision
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
