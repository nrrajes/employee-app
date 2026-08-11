pipeline {

    agent any

    stages {

        stage('Checkout Code') {
            steps {
                git branch: 'main',
                    url: 'git@github.com:nrrajes/employee-app.git'
            }
        }

        stage('Build Application') {
            steps {
                sh '''
                    chmod +x mvnw
                    ./mvnw clean package
                '''
            }
        }

        stage('SonarQube Analysis') {
            steps {
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
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    docker build -t employee-app:${BUILD_NUMBER} .
                '''
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
                        export EMPLOYEE_APP_IMAGE=employee-app:${BUILD_NUMBER}

                        echo "Stopping previous deployment..."
                        docker compose down || true

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
                        if curl -fs http://localhost:8084/ > /dev/null 2>&1; then
                            echo "Application is ready!"
                            READY=true
                            break
                        fi

                        echo "Application not ready yet... attempt $i/30"
                        sleep 2
                    done

                    if [ "$READY" != "true" ]; then
                        echo "ERROR: Application did not become ready within 60 seconds."
                        echo ""
                        echo "Application container status:"
                        docker compose ps

                        echo ""
                        echo "Application container logs:"
                        docker logs --tail 100 employee-app

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
        success {
            echo "=========================================="
            echo "PIPELINE COMPLETED SUCCESSFULLY"
            echo "Build Number: ${BUILD_NUMBER}"
            echo "Docker Image: employee-app:${BUILD_NUMBER}"
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

        always {
            echo "Pipeline execution completed."
        }
    }
}
