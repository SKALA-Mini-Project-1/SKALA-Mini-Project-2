pipeline {
    agent any

    environment {
        AWS_REGION    = 'ap-northeast-2'
        // ECR_REGISTRY: Jenkins 서버 환경변수로 관리 (Manage Jenkins → System → Global properties)
        K8S_NAMESPACE = 'fairline'
        DOCKER_HOST   = 'tcp://localhost:2375'
    }

    stages {
        stage('Detect Changed Services') {
            steps {
                script {
                    // 이전 성공 커밋 기준으로 diff; 없으면 HEAD~1
                    def base = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: 'HEAD~1'
                    def changed = sh(
                        script: "git diff --name-only ${base} HEAD 2>/dev/null || git diff --name-only HEAD~1 HEAD",
                        returnStdout: true
                    ).trim()

                    echo "Changed files:\n${changed}"

                    def springServices = [
                        'concert-service',
                        'user-auth-service',
                        'queue-service',
                        'ticketing-service',
                        'payment-service'
                    ]

                    env.CHANGED_SERVICES = springServices
                        .findAll { svc -> changed.contains("${svc}/") }
                        .join(',')

                    env.BUILD_FRONTEND = changed.contains('frontend/') ? 'true' : 'false'

                    echo "Services to build : ${env.CHANGED_SERVICES ?: '(none)'}"
                    echo "Build frontend    : ${env.BUILD_FRONTEND}"
                }
            }
        }

        stage('ECR Login') {
            when {
                expression {
                    (env.CHANGED_SERVICES?.trim()) || env.BUILD_FRONTEND == 'true'
                }
            }
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    sh '''
                        aws ecr get-login-password --region $AWS_REGION | \
                            docker login --username AWS --password-stdin $ECR_REGISTRY
                    '''
                }
            }
        }

        stage('Build & Push Spring Services') {
            when {
                expression { env.CHANGED_SERVICES?.trim() }
            }
            steps {
                script {
                    env.CHANGED_SERVICES.split(',').each { svc ->
                        echo "========== Building: ${svc} =========="
                        sh """
                            docker build --platform linux/amd64 \
                                -t ${ECR_REGISTRY}/team4-${svc}:latest \
                                -f ${svc}/Dockerfile .
                            docker push ${ECR_REGISTRY}/team4-${svc}:latest
                        """
                    }
                }
            }
        }

        stage('Build & Push Frontend') {
            when {
                environment name: 'BUILD_FRONTEND', value: 'true'
            }
            steps {
                sh '''
                    docker build --no-cache --platform linux/amd64 \
                        -t $ECR_REGISTRY/team4-frontend:latest \
                        -f frontend/Dockerfile ./frontend
                    docker push $ECR_REGISTRY/team4-frontend:latest
                '''
            }
        }

        stage('Deploy to EKS') {
            steps {
                script {
                    if (env.CHANGED_SERVICES?.trim()) {
                        env.CHANGED_SERVICES.split(',').each { svc ->
                            echo "Deploying: ${svc}"
                            sh "kubectl rollout restart deployment/${svc} -n ${K8S_NAMESPACE}"
                            sh "kubectl rollout status deployment/${svc} -n ${K8S_NAMESPACE} --timeout=300s"
                        }
                    }
                    if (env.BUILD_FRONTEND == 'true') {
                        echo "Deploying: frontend"
                        sh "kubectl rollout restart deployment/frontend -n ${K8S_NAMESPACE}"
                        sh "kubectl rollout status deployment/frontend -n ${K8S_NAMESPACE} --timeout=300s"
                    }
                    if (!env.CHANGED_SERVICES?.trim() && env.BUILD_FRONTEND != 'true') {
                        echo "No services changed. Skipping deploy."
                    }
                }
            }
        }
    }

    post {
        always {
            sh "docker logout ${ECR_REGISTRY} || true"
        }
        success {
            echo "Pipeline completed successfully."
        }
        failure {
            echo "Pipeline failed. Check logs above."
        }
    }
}
