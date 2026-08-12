pipeline {

    agent any

    environment {
        DOCKERHUB_USERNAME = 'nrrajes'
        DOCKER_IMAGE = "${DOCKERHUB_USERNAME}/employee-app:${BUILD_NUMBER}"
        DOCKER_LATEST = "${DOCKERHUB_USERNAME}/employee-app:latest"
    }

    stages {

        stage('Checkout Code') {
            steps {
                echo "Checking out source code from GitHub..."

                git branch: 'main',
                    url: 'git@github.com:nrrajes/employee-app.git'
            }
        }

        stage('Build Application') {
            steps {
                echo "Building Spring Boot application..."

                sh '''
                    chmod +x mvnw
                    ./mvnw clean package
                '''
            }
        }

        stage('SonarQube Analysis') {
            steps {
                echo "Running SonarQube analysis..."

                withSonarQubeEnv('SonarQube') {
                    sh '''
                        /opt/sonar-scanner/bin/sonar-scanner \
                          -Dsonar.projectKey=employee-app \
                          -Dsonar.projectName=Employee-App \
                          -Dsonar.sources=src/main \
                          -Dsonar.java.binaries=target/classes \
                          -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                echo "Waiting for SonarQube Quality Gate..."

                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "Building Docker images..."

                sh '''
                    docker build \
                        -t employee-app:${BUILD_NUMBER} \
                        -t ${DOCKER_IMAGE} \
                        -t ${DOCKER_LATEST} \
                        .
                '''

                sh '''
                    echo "Docker images created:"
                    docker images | grep employee-app
                '''
            }
        }

        stage('Push Docker Image') {
            steps {
                echo "Pushing Docker images to Docker Hub..."

                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        echo "$DOCKER_PASSWORD" | docker login \
                            --username "$DOCKER_USERNAME" \
                            --password-stdin

                        echo "Pushing versioned image..."
                        docker push ${DOCKER_IMAGE}

                        echo "Pushing latest image..."
                        docker push ${DOCKER_LATEST}

                        docker logout
                    '''
                }
            }
        }

        stage('Deploy with Docker Compose') {
            steps {

                withCredentials([
                    string(
                        credentialsId: 'mysql-root-password',
                        variable: 'MYSQL_ROOT_PASSWORD'
                    )
                ]) {

                    sh '''
                        export COMPOSE_PROJECT_NAME=employee-app
                        export MYSQL_DATABASE=devopslab
                        export EMPLOYEE_APP_IMAGE=${DOCKER_IMAGE}

                        echo "Stopping previous deployment..."
                        docker compose down

                        echo "Starting new deployment..."
                        docker compose up -d

                        echo "Checking container status..."
                        docker compose ps
                    '''
                }
            }
        }

        stage('Smoke Test') {
            steps {
                sh '''
                    echo "Waiting for application to become ready..."

                    READY=false

                    for i in $(seq 1 30); do

                        if curl -fs http://localhost:8084/ > /dev/null; then
                            echo "Application is ready!"
                            READY=true
                            break
                        fi

                        echo "Application not ready yet... attempt $i/30"
                        sleep 2
                    done

                    if [ "$READY" != "true" ]; then
                        echo "Application failed to become ready."

                        echo "Application container logs:"
                        docker logs employee-app --tail 100 || true

                        echo "Docker Compose status:"
                        docker compose ps

                        exit 1
                    fi

                    echo ""
                    echo "Testing application root endpoint..."
                    curl -f http://localhost:8084/

                    echo ""
                    echo ""
                    echo "Testing employees API..."
                    curl -f http://localhost:8084/employees

                    echo ""
                    echo ""
                    echo "Smoke tests passed successfully!"
                '''
            }
        }
    }

    post {

        always {
            echo "Pipeline execution completed."
        }

        success {
            echo "=========================================="
            echo "PIPELINE COMPLETED SUCCESSFULLY"
            echo "Build Number: ${BUILD_NUMBER}"
            echo "Docker Image: ${DOCKER_IMAGE}"
            echo "Latest Image: ${DOCKER_LATEST}"
            echo "Application: http://localhost:8084"
            echo "=========================================="
        }

        failure {
            echo "=========================================="
            echo "PIPELINE FAILED"
            echo "Build Number: ${BUILD_NUMBER}"
            echo "Check the failed stage and console output."
            echo "=========================================="
        }
    }
}
