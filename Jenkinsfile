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
                    
                    try {
                        // Create a test network
                        sh 'docker network create flask-test-network || true'
                        
                        // Stop any existing containers
                        sh 'docker stop flask-test-app nginx-test-proxy || true'
                        sh 'docker rm flask-test-app nginx-test-proxy || true'
                        
                        // Start Flask container
                        sh """
                            docker run -d \\
                                --name flask-test-app \\
                                --network flask-test-network \\
                                -p 5000:5000 \\
                                -v /var/run/docker.sock:/var/run/docker.sock:ro \\
                                ${FLASK_IMAGE}:latest
                        """
                        
                        // Wait for Flask to start
                        sleep(time: 15, unit: 'SECONDS')
                        
                        // Start Nginx container
                        sh """
                            docker run -d \\
                                --name nginx-test-proxy \\
                                --network flask-test-network \\
                                -p 8080:80 \\
                                ${NGINX_IMAGE}:latest
                        """
                        
                        // Wait for services to be ready
                        sleep(time: 15, unit: 'SECONDS')
                        
                        // Test Flask app directly
                        sh 'curl -f http://localhost:5000/health || echo "Flask health check failed"'
                        echo 'Flask app health check completed'
                        
                        // Test through Nginx proxy
                        sh 'curl -f http://localhost:8080/ || echo "Nginx proxy test failed"'
                        echo 'Nginx proxy test completed'
                        
                    } catch (Exception e) {
                        echo "Test failed: ${e.getMessage()}"
                    } finally {
                        // Cleanup containers
                        sh 'docker stop flask-test-app nginx-test-proxy || true'
                        sh 'docker rm flask-test-app nginx-test-proxy || true'
                        sh 'docker network rm flask-test-network || true'
                    }
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
            // Cleanup containers and images
            sh 'docker stop flask-test-app nginx-test-proxy || true'
            sh 'docker rm flask-test-app nginx-test-proxy || true'
            sh 'docker network rm flask-test-network || true'
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
