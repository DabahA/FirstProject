pipeline {
    agent any
    
    environment {
        DOCKER_HUB_REPO = 'your-dockerhub-username'
        FLASK_IMAGE = "${DOCKER_HUB_REPO}/flask-container-app"
        NGINX_IMAGE = "${DOCKER_HUB_REPO}/nginx-proxy"
    }
    
    stages {
        stage('Checkout') {
            steps {
                // Jenkins will automatically checkout the Git repository
                echo 'Code checked out from Git'
            }
        }
        
        stage('Build Flask Image') {
            steps {
                script {
                    echo 'Building Flask application image...'
                    sh "docker build -t ${FLASK_IMAGE}:${BUILD_NUMBER} ."
                    sh "docker tag ${FLASK_IMAGE}:${BUILD_NUMBER} ${FLASK_IMAGE}:latest"
                }
            }
        }
        
        stage('Build Nginx Image') {
            steps {
                script {
                    echo 'Building Nginx proxy image...'
                    sh "docker build -t ${NGINX_IMAGE}:${BUILD_NUMBER} ./nginx"
                    sh "docker tag ${NGINX_IMAGE}:${BUILD_NUMBER} ${NGINX_IMAGE}:latest"
                }
            }
        }
        
        stage('Test Application') {
            steps {
                script {
                    echo 'Starting application for testing...'
                    
                    // Start services using docker-compose
                    sh 'docker-compose down || true'
                    sh 'docker-compose up -d --build'
                    
                    // Wait for services to start
                    sleep(time: 30, unit: 'SECONDS')
                    
                    // Test Flask app
                    sh 'curl -f http://localhost:5000/health || exit 1'
                    echo 'Flask app health check passed'
                    
                    // Test Nginx proxy
                    sh 'curl -f http://localhost:8080/ || exit 1'
                    echo 'Nginx proxy test passed'
                    
                    // Stop services
                    sh 'docker-compose down'
                }
            }
        }
        
        stage('Push to Docker Hub') {
            when {
                branch 'main'
            }
            steps {
                script {
                    echo 'Pushing images to Docker Hub...'
                    
                    // Note: You need to configure Docker Hub credentials in Jenkins
                    withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials', 
                                                    usernameVariable: 'DOCKER_USER', 
                                                    passwordVariable: 'DOCKER_PASS')]) {
                        sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                        sh "docker push ${FLASK_IMAGE}:${BUILD_NUMBER}"
                        sh "docker push ${FLASK_IMAGE}:latest"
                        sh "docker push ${NGINX_IMAGE}:${BUILD_NUMBER}"
                        sh "docker push ${NGINX_IMAGE}:latest"
                    }
                }
            }
        }
    }
    
    post {
        always {
            // Cleanup
            sh 'docker-compose down || true'
            sh "docker rmi ${FLASK_IMAGE}:${BUILD_NUMBER} || true"
            sh "docker rmi ${NGINX_IMAGE}:${BUILD_NUMBER} || true"
        }
        success {
            echo '✅ Pipeline completed successfully!'
        }
        failure {
            echo '❌ Pipeline failed!'
        }
    }
}
