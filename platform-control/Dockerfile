# 控制面镜像：多阶段构建，最终镜像仅包含 JRE 与 jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY platform-common/pom.xml platform-common/pom.xml
COPY platform-control/pom.xml platform-control/pom.xml
COPY platform-worker/pom.xml platform-worker/pom.xml
RUN mvn -B -ntp -pl platform-common,platform-control -am dependency:go-offline
COPY platform-common/src platform-common/src
COPY platform-control/src platform-control/src
RUN mvn -B -ntp -pl platform-common,platform-control -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/platform-control/target/platform-control-*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
