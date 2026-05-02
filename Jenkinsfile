pipeline {
    agent any

    environment {
        AWS_REGION      = 'ap-northeast-2'
        // ECR_REGISTRY: Jenkins 서버 환경변수로 관리 (Manage Jenkins → System → Global properties)
        K8S_NAMESPACE   = 'fairline'
        DOCKER_HOST     = 'tcp://localhost:2375'
        DOCKER_BUILDKIT = '1'
        GITOPS_REPO     = 'https://github.com/SKALA-Mini-Project-1/fairline-k8s.git'
    }

    stages {
        stage('Detect Changed Services') {
            steps {
                script {
                    env.GIT_SHA = env.GIT_COMMIT.take(8)

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
                        'payment-service',
                        'incident-detector',
                        'incident-agent',
                        'incident-api'
                    ]

                    env.CHANGED_SERVICES = springServices
                        .findAll { svc -> changed.contains("${svc}/") }
                        .join(',')

                    env.BUILD_FRONTEND = changed.contains('frontend/') ? 'true' : 'false'

                    echo "Services to build : ${env.CHANGED_SERVICES ?: '(none)'}"
                    echo "Build frontend    : ${env.BUILD_FRONTEND}"
                    echo "Git SHA           : ${env.GIT_SHA}"
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
                    def gitSha = env.GIT_SHA
                    def parallelBuilds = env.CHANGED_SERVICES.split(',').collectEntries { svc ->
                        ["Build ${svc}": {
                            sh """
                                docker build --platform linux/amd64 \
                                    -t ${ECR_REGISTRY}/team4-${svc}:${gitSha} \
                                    -f ${svc}/Dockerfile .
                                docker push ${ECR_REGISTRY}/team4-${svc}:${gitSha}
                            """
                        }]
                    }
                    parallel parallelBuilds
                }
            }
        }

        stage('Build & Push Frontend') {
            when {
                environment name: 'BUILD_FRONTEND', value: 'true'
            }
            steps {
                script {
                    def gitSha = env.GIT_SHA
                    sh """
                        docker build --no-cache --platform linux/amd64 \
                            -t ${ECR_REGISTRY}/team4-frontend:${gitSha} \
                            -f frontend/Dockerfile ./frontend
                        docker push ${ECR_REGISTRY}/team4-frontend:${gitSha}
                    """
                }
            }
        }

        stage('Update GitOps Repo') {
            when {
                expression {
                    (env.CHANGED_SERVICES?.trim()) || env.BUILD_FRONTEND == 'true'
                }
            }
            steps {
                script {
                    def gitSha = env.GIT_SHA
                    def workDir = "/tmp/fairline-k8s-${BUILD_NUMBER}"

                    withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
                        sh """
                            git clone https://x-access-token:\${GITHUB_TOKEN}@github.com/SKALA-Mini-Project-1/fairline-k8s.git ${workDir}
                            git -C ${workDir} config user.email "jenkins@fairline"
                            git -C ${workDir} config user.name "Jenkins"
                        """

                        if (env.CHANGED_SERVICES?.trim()) {
                            env.CHANGED_SERVICES.split(',').each { svc ->
                                sh """
                                    sed -i 's|image: ${ECR_REGISTRY}/team4-${svc}:.*|image: ${ECR_REGISTRY}/team4-${svc}:${gitSha}|g' \
                                        ${workDir}/${svc}/deployment.yaml
                                """
                            }
                        }

                        if (env.BUILD_FRONTEND == 'true') {
                            sh """
                                sed -i 's|image: ${ECR_REGISTRY}/team4-frontend:.*|image: ${ECR_REGISTRY}/team4-frontend:${gitSha}|g' \
                                    ${workDir}/frontend/deployment.yaml
                            """
                        }

                        sh """
                            git -C ${workDir} add .
                            git -C ${workDir} diff --cached --quiet || \
                                git -C ${workDir} commit -m "ci: update image tags to ${gitSha} [skip ci]"
                            git -C ${workDir} push
                            rm -rf ${workDir}
                        """
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
