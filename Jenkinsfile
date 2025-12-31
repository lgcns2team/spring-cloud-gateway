pipeline {
    agent any
    
    environment {
        PROJECT_NAME = 'history-ai'
        ENVIRONMENT = 'prod'
        AWS_REGION = 'ap-northeast-2'
        SERVICE_NAME = 'gateway'
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo '=== Checking out code ==='
                checkout scm
            }
        }
        
        stage('Setup Environment') {
            steps {
                echo '=== Checking environment ==='
                sh '''
                    echo "Project: ${PROJECT_NAME}"
                    echo "Environment: ${ENVIRONMENT}"
                    echo "Service: ${SERVICE_NAME}"
                    docker --version
                    aws --version
                '''
            }
        }
        
        stage('Setup Docker Buildx') {
            steps {
                echo '=== Setting up Docker buildx ==='
                sh '''
                    if ! docker buildx ls | grep -q multiarch; then
                        docker buildx create --name multiarch --use
                        docker buildx inspect --bootstrap
                    else
                        docker buildx use multiarch
                    fi
                '''
            }
        }
        
        stage('AWS ECR Login') {
            steps {
                echo '=== Logging in to ECR ==='
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials'
                ]]) {
                    sh '''
                        AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
                        echo "AWS Account: ${AWS_ACCOUNT_ID}"
                        
                        aws ecr get-login-password --region ${AWS_REGION} | \
                            docker login --username AWS --password-stdin \
                            ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
                    '''
                }
            }
        }
        
        stage('Ensure ECR Repository') {
            steps {
                echo '=== Ensuring ECR repository exists ==='
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials'
                ]]) {
                    sh '''
                        AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
                        REPO_NAME="${PROJECT_NAME}-${ENVIRONMENT}-${SERVICE_NAME}"
                        
                        if aws ecr describe-repositories --repository-names ${REPO_NAME} --region ${AWS_REGION} 2>/dev/null; then
                            echo "✅ ECR repository exists: ${REPO_NAME}"
                        else
                            echo "📦 Creating ECR repository: ${REPO_NAME}"
                            aws ecr create-repository \
                                --repository-name ${REPO_NAME} \
                                --region ${AWS_REGION} \
                                --image-scanning-configuration scanOnPush=true \
                                --encryption-configuration encryptionType=AES256
                        fi
                    '''
                }
            }
        }
        
        stage('Build and Push Docker Image') {
            steps {
                echo '=== Building Gateway image for AMD64 ==='
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials'
                ]]) {
                    sh '''
                        AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
                        ECR_REPO="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${PROJECT_NAME}-${ENVIRONMENT}-${SERVICE_NAME}"
                        GIT_COMMIT=$(git rev-parse --short HEAD)
                        
                        echo "Building and pushing to ${ECR_REPO}"
                        
                        docker buildx build \
                            --platform linux/amd64 \
                            --cache-from type=registry,ref=${ECR_REPO}:latest \
                            --cache-to type=inline \
                            -t ${ECR_REPO}:latest \
                            -t ${ECR_REPO}:${GIT_COMMIT} \
                            -t ${ECR_REPO}:build-${BUILD_NUMBER} \
                            --push \
                            .
                        
                        echo "✅ Image pushed successfully!"
                    '''
                }
            }
        }
        
        stage('Update ECS Service') {
            steps {
                echo '=== Updating ECS Service ==='
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials'
                ]]) {
                    sh '''
                        CLUSTER_NAME="${PROJECT_NAME}-${ENVIRONMENT}-cluster"
                        SERVICE_FULL_NAME="${PROJECT_NAME}-${ENVIRONMENT}-${SERVICE_NAME}"
                        
                        if aws ecs describe-clusters --clusters ${CLUSTER_NAME} --region ${AWS_REGION} 2>/dev/null | grep -q "ACTIVE"; then
                            if aws ecs describe-services \
                                --cluster ${CLUSTER_NAME} \
                                --services ${SERVICE_FULL_NAME} \
                                --region ${AWS_REGION} 2>/dev/null | grep -q "ACTIVE"; then
                                
                                echo "🔄 Updating ${SERVICE_FULL_NAME}..."
                                aws ecs update-service \
                                    --cluster ${CLUSTER_NAME} \
                                    --service ${SERVICE_FULL_NAME} \
                                    --force-new-deployment \
                                    --region ${AWS_REGION} \
                                    --no-cli-pager
                                
                                echo "✅ Service update initiated!"
                            else
                                echo "⚠️ Service not found or not active"
                            fi
                        else
                            echo "⚠️ Cluster not found"
                        fi
                    '''
                }
            }
        }
    }
    
    post {
        success {
            echo '✅ Gateway deployment succeeded!'
        }
        failure {
            echo '❌ Gateway deployment failed!'
        }
        always {
            sh 'docker system prune -f || true'
        }
    }
}

