# Build tools are pinned to reviewed multi-platform manifests. No local runtime tree enters this image.
FROM node:24.14.0-bookworm-slim@sha256:d8e448a56fc63242f70026718378bd4b00f8c82e78d20eefb199224a4d8e33d8 AS workbench
WORKDIR /workbench
COPY workbench/package.json workbench/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY workbench/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk-noble@sha256:80f67fb3d6f76e2d817d98a4cc2a9cf05f054d4e6adc5e2144bde671d722e63f AS java-build
WORKDIR /workspace
COPY . .
COPY --from=workbench /workbench/dist ./workbench/dist
# The independent Node stage already type-checked and built the UI. Dependency verification remains enabled.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :integration-app:bootJar -x :workbenchBuild --no-daemon --console=plain

FROM eclipse-temurin:25-jre-noble@sha256:e3b6f7887a50f20d8f07abf279d8a81a5953fb398a9555bd9edd56b9bd15968c
RUN groupadd --gid 10001 pqclab && useradd --uid 10001 --gid 10001 --no-create-home pqclab \
    && groupadd --gid 10002 receiver && useradd --uid 10002 --gid 10002 --no-create-home receiver \
    && install -d -o 10001 -g 10001 -m 0700 /data/publisher /secrets \
    && install -d -o 10002 -g 10002 -m 0700 /data/receiver \
    && install -d -o 10001 -g 10001 -m 0755 /data/trust /opt/pqclab
WORKDIR /opt/pqclab
COPY --from=java-build /workspace/integration-app/build/libs/integration-app-0.2.0.jar ./app.jar
COPY docker/network-security.properties ./network-security.properties
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=2 -XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-Djava.security.properties=/opt/pqclab/network-security.properties", "-jar", "/opt/pqclab/app.jar"]
