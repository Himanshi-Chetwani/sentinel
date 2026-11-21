# ---------- Stage 1: build ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy only the build files first, so dependency downloads are cached
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

# Now copy source and build the JAR
COPY src/ src/
RUN ./mvnw -q -DskipTests package

# ---------- Stage 2: run ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Create an unprivileged user with no login shell
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

COPY --from=build /app/target/*.jar app.jar
RUN chown app:app app.jar

# everything after this runs as 'app', not root
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
