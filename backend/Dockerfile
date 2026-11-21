# Multi-stage: build on the full JDK, ship only the JRE plus extracted
# layers so the final image stays small and carries no build tooling.

# ---------- build ----------
FROM eclipse-temurin:24-jdk AS build
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
FROM eclipse-temurin:24-jre-alpine AS runtime
WORKDIR /application

# Patch the base image's OS packages so the image carries the security fixes
# Alpine has already published for this release but the base layer has not
# picked up yet. Runs as root, before the USER switch, since apk needs root.
# This is what lets the Trivy scan stay strict instead of ignoring a finding
# that actually has a fix.
RUN apk --no-cache upgrade

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

# Lets Docker (and `docker ps`) report whether the app is actually serving, not
# just whether the process exists. BusyBox wget ships with Alpine, so no extra
# package is needed. Generous start period because Flyway runs on boot.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3   CMD wget -q -O /dev/null http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "backend-0.0.1-SNAPSHOT.jar"]