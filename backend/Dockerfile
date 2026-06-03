# Multi-stage: build on the full JDK, ship only the JRE plus extracted
# layers so the final image stays small and carries no build tooling.

# ---------- build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Dependencies before source so the dependency download layer stays cached
# across code changes. Reordering these two blocks kills that cache.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# tools jarmode, not the deprecated layertools. Produces the four layer
# folders copied in the runtime stage.
RUN java -Djarmode=tools -jar target/backend-0.0.1-SNAPSHOT.jar extract --layers --destination extracted

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /application

# Non-root so a compromised app does not run as root inside the container.
RUN addgroup -S fitt && adduser -S fitt -G fitt
USER fitt

# Least to most volatile, so a code change only reships the application layer.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# Documentation only. Does not publish the port, Compose does that.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "backend-0.0.1-SNAPSHOT.jar"]