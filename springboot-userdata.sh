#!/bin/bash
set -e

# 로그 파일 설정
exec > >(tee /var/log/user-data.log)
exec 2>&1

echo "======================================"
echo "Gateway EC2 Setup Started"
echo "Time: $(date)"
echo "======================================"

# 시스템 업데이트
echo "Updating system packages..."
apt-get update
apt-get upgrade -y

# Docker 설치
echo "Installing Docker..."
apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

systemctl start docker
systemctl enable docker
usermod -aG docker ubuntu

# AWS CLI 설치 (Ubuntu AMI에 이미 있을 수 있음)
echo "Installing AWS CLI..."
apt-get install -y awscli unzip
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
./aws/install --update
rm -rf aws awscliv2.zip

# Java 17 설치 (Spring Cloud Gateway용)
echo "Installing Java 17..."
apt-get install -y openjdk-17-jdk

# ECR 로그인
echo "Logging into ECR..."
aws ecr get-login-password --region ${aws_region} | \
  docker login --username AWS --password-stdin ${ecr_registry}

# Docker Compose 파일 생성
echo "Creating docker-compose.yml..."
cat > /home/ubuntu/docker-compose.yml <<'EOF'
version: '3.8'

services:
  gateway:
    image: ${ecr_registry}/spring-cloud-gateway:latest
    container_name: gateway
    restart: always
    ports:
      - "8081:8081"
      - "80:8081"
    environment:
      - SERVER_PORT=8081
      - SPRING_REDIS_HOST=${redis_host}
      - SPRING_REDIS_PORT=6379
      - SPRINGBOOT_HOST=${springboot_host}
      - SPRINGBOOT_PORT=8081
      - FASTAPI_HOST=${fastapi_host}
      - FASTAPI_PORT=8000
      - AWS_REGION=${aws_region}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
EOF

# Docker 이미지 Pull 및 실행
echo "Starting Gateway service..."
cd /home/ubuntu

# 만약 이미지가 ECR에 없으면 대기 (수동 푸시 필요)
# docker-compose pull gateway || echo "Warning: Gateway image not found in ECR. Please push it manually."
docker-compose up -d

# CloudWatch Agent 설치 (선택사항)
echo "Installing CloudWatch Agent..."
wget https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb
dpkg -i -E ./amazon-cloudwatch-agent.deb
rm amazon-cloudwatch-agent.deb

# 파일 권한 설정
chown -R ubuntu:ubuntu /home/ubuntu

echo "======================================"
echo "Gateway EC2 Setup Completed!"
echo "Time: $(date)"
echo "======================================"
echo "Gateway will be available at: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):8081"