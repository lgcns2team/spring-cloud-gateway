# Build Stage
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# Gradle Wrapper 복사
COPY gradlew ./
COPY gradle ./gradle

# 의존성 파일 복사
COPY build.gradle settings.gradle ./

# 실행 권한 부여
RUN chmod +x ./gradlew

# 의존성 다운로드 (캐시 활용)
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드 복사 (resources 포함!)
COPY src ./src

# 리소스 파일 확인 (디버깅용)
RUN ls -la src/main/resources/

# 애플리케이션 빌드
RUN ./gradlew clean bootJar -x test --no-daemon

# 빌드 결과 확인 (디버깅용)
RUN ls -la build/libs/
RUN jar -tf build/libs/*.jar | grep application || echo "WARNING: application.yml not found!"

# Runtime Stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# curl과 unzip 설치 (헬스체크 및 디버깅용)
RUN apt-get update && \
    apt-get install -y curl unzip && \
    rm -rf /var/lib/apt/lists/*

# 보안을 위해 non-root 유저로 실행
RUN groupadd -r spring && useradd -r -g spring spring

# 빌드된 JAR 파일 복사 (명시적으로 bootJar만)
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

# 소유권 변경
RUN chown spring:spring app.jar

# non-root 유저로 전환
USER spring:spring

# 환경 변수 설정
ENV JAVA_OPTS="-Xms256m -Xmx512m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod

# 포트 노출
EXPOSE 8080

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]