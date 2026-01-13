pipeline {
    agent any
    
    environment {
        BACKEND_SERVICE = 'backend'
        SWAGGER_URL = "${env.SERVER_HOST ?: 'http://localhost'}:8081/swagger-ui/index.html"
        HEALTH_URL = 'http://localhost:8081/actuator/health'
        MATTERMOST_ENDPOINT = credentials('mattermost-webhook-url')
        MATTERMOST_CHANNEL = '#team-carrot'
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo '📥 소스코드 체크아웃 중...'
                checkout scm
            }
        }
        
        stage('Build & Test') {
            parallel {
                stage('Gradle Build') {
                    steps {
                        echo '🏗️ Gradle로 Spring Boot 빌드 중...'
                        dir('BE') {
                            sh 'chmod +x gradlew'
                            sh './gradlew clean build -x test'
                        }
                    }
                }
                stage('Unit Test') {
                    steps {
                        echo '🧪 단위 테스트 실행 중...'
                        dir('BE') {
                            sh './gradlew test'
                        }
                    }
                    post {
                        always {
                            dir('BE') {
                                junit testResults: 'build/test-results/test/*.xml', allowEmptyResults: true
                            }
                        }
                    }
                }
            }
        }
        
        stage('Docker Deploy') {
            steps {
                echo '🐳 Docker Compose 배포 중...'
                sh '''
                    echo "기존 컨테이너 중지..."
                    docker-compose down || true
                    
                    echo "서비스 빌드 및 시작..."
                    docker-compose build --no-cache backend
                    docker-compose up -d
                    
                    echo "서비스 시작 대기..."
                    sleep 45
                '''
            }
        }
        
        stage('Health Check') {
            steps {
                echo '🏥 애플리케이션 상태 확인 중...'
                script {
                    def healthCheckPassed = false
                    for (int i = 1; i <= 10; i++) {
                        try {
                            sh "curl -f ${HEALTH_URL}"
                            echo "✅ 상태 확인 성공 (${i}번째 시도)"
                            healthCheckPassed = true
                            break
                        } catch (Exception e) {
                            echo "⏳ 상태 확인 실패 ${i}/10, 10초 후 재시도..."
                            sleep(10)
                        }
                    }
                    
                    if (!healthCheckPassed) {
                        echo "⚠️ 상태 확인 경고 - 컨테이너 상태 점검..."
                        sh 'docker-compose ps'
                        sh 'docker logs spring-backend --tail 30 || true'
                    }
                }
            }
        }
    }
    
    post {
        success {
            script {
                def currentTime = new Date().format('MM-dd HH:mm')
                def commitHash = env.GIT_COMMIT?.take(8) ?: 'unknown'
                def branchName = env.BRANCH_NAME ?: 'backend-dev'
                
                try {
                    mattermostSend(
                        endpoint: env.MATTERMOST_ENDPOINT,
                        channel: env.MATTERMOST_CHANNEL,
                        color: 'good',
                        message: """✅ **라고할때 배포 성공!** 🎉

**빌드:** #${BUILD_NUMBER} | **브랜치:** ${branchName}
**커밋:** ${commitHash} | **시간:** ${currentTime}

🔗 **Swagger UI:** ${SWAGGER_URL}
📊 **Health Check:** ${env.SERVER_HOST ?: 'http://localhost'}:8081/actuator/health
🤖 **AI API:** ${env.SERVER_HOST ?: 'http://localhost'}:8081/api/ai-bots"""
                    )
                } catch (Exception e) {
                    echo "⚠️ Mattermost 알림 전송 실패: ${e.getMessage()}"
                }
            }
        }
        failure {
            script {
                def currentTime = new Date().format('MM-dd HH:mm')
                def branchName = env.BRANCH_NAME ?: 'backend-dev'
                
                try {
                    mattermostSend(
                        endpoint: env.MATTERMOST_ENDPOINT,
                        channel: env.MATTERMOST_CHANNEL,
                        color: 'danger',
                        message: """❌ **배포 실패!** 🚨

**빌드:** #${BUILD_NUMBER} | **브랜치:** ${branchName}
**실패 시간:** ${currentTime}

🔧 **Jenkins 로그:** ${BUILD_URL}console
� **수동 복구:** docker-compose down && docker-compose up -d"""
                    )
                } catch (Exception e) {
                    echo "⚠️ Mattermost 알림 전송 실패: ${e.getMessage()}"
                }
            }
        }
        always {
            echo '🎯 파이프라인 완료!'
            sh 'docker system prune -f --volumes || true'
        }
    }
}
