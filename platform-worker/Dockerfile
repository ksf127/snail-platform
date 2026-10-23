# 执行面镜像：包含 JRE、git 与 docker CLI（通过挂载 docker.sock 调用宿主机守护进程）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY platform-common/pom.xml platform-common/pom.xml
COPY platform-control/pom.xml platform-control/pom.xml
COPY platform-worker/pom.xml platform-worker/pom.xml
RUN mvn -B -ntp -pl platform-common,platform-worker -am dependency:go-offline
COPY platform-common/src platform-common/src
COPY platform-worker/src platform-worker/src
RUN mvn -B -ntp -pl platform-common,platform-worker -am package -DskipTests

FROM docker:27-cli AS dockercli

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates wget \
    && rm -rf /var/lib/apt/lists/*
COPY --from=dockercli /usr/local/bin/docker /usr/local/bin/docker
WORKDIR /app
COPY --from=build /build/platform-worker/target/platform-worker-*.jar app.jar
ENV WORKSPACE_DIR=/workspace
RUN mkdir -p /workspace
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
