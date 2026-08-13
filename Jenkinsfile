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

                    script {

                        echo "=========================================="
                        echo "CAPTURING CURRENT DEPLOYMENT"
                        echo "=========================================="

                        sh '''
                            CURRENT_IMAGE=$(docker inspect employee-app \
                                --format '{{.Config.Image}}' 2>/dev/null || true)

                            echo "Currently deployed image: ${CURRENT_IMAGE}"

                            if [ -z "$CURRENT_IMAGE" ]; then
                                echo "ERROR: No currently deployed employee-app container found."
                                exit 1
                            fi

                            echo "$CURRENT_IMAGE" > previous_image.txt

                            echo "Previous image saved:"
                            cat previous_image.txt
                        '''

                        env.PREVIOUS_IMAGE = sh(
                            script: 'cat previous_image.txt',
                            returnStdout: true
                        ).trim()

                        echo "Previous deployment: ${env.PREVIOUS_IMAGE}"
                        echo "New deployment: ${env.DOCKER_IMAGE}"

                        sh '''
                            export COMPOSE_PROJECT_NAME=employee-app
                            export MYSQL_DATABASE=devopslab
                            export EMPLOYEE_APP_IMAGE=${DOCKER_IMAGE}

                            echo "=========================================="
                            echo "DEPLOYING NEW VERSION"
                            echo "=========================================="

                            echo "New image: ${EMPLOYEE_APP_IMAGE}"

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
        }

        stage('Smoke Test') {
            steps {

                script {

                    try {

                        sh '''
                            echo "=========================================="
                            echo "STARTING SMOKE TEST"
                            echo "=========================================="

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

                        echo "=========================================="
                        echo "NEW DEPLOYMENT VERIFIED"
                        echo "=========================================="
                        echo "Image ${env.DOCKER_IMAGE} passed smoke tests."

                    } catch (Exception e) {

                        echo "=========================================="
                        echo "SMOKE TEST FAILED"
                        echo "=========================================="

                        echo "New deployment failed smoke testing."

                        echo "Application container logs:"
                        sh '''
                            docker logs employee-app --tail 100 || true
                        '''

                        echo "Docker Compose status:"
                        sh '''
                            docker compose ps || true
                        '''

                        /*
                         * IMPORTANT:
                         * Rollback logic is inside script{}.
                         * This avoids the previous:
                         * "Expected a step" Jenkins compilation error.
                         */

                        if (!env.PREVIOUS_IMAGE?.trim()) {
                            error(
                                "Previous image is not available. " +
                                "Automatic rollback cannot continue."
                            )
                        }

                        echo "=========================================="
                        echo "STARTING AUTOMATIC ROLLBACK"
                        echo "=========================================="

                        echo "Previous image: ${env.PREVIOUS_IMAGE}"
                        echo "Failed image: ${env.DOCKER_IMAGE}"

                        withCredentials([
                            string(
                                credentialsId: 'mysql-root-password',
                                variable: 'MYSQL_ROOT_PASSWORD'
                            )
                        ]) {

                            sh '''
                                export COMPOSE_PROJECT_NAME=employee-app
                                export MYSQL_DATABASE=devopslab
                                export EMPLOYEE_APP_IMAGE=${PREVIOUS_IMAGE}

                                echo "=========================================="
                                echo "PREPARING ROLLBACK IMAGE"
                                echo "=========================================="

                                echo "Pulling previous image from Docker Hub:"
                                echo "${EMPLOYEE_APP_IMAGE}"

                                docker pull "${EMPLOYEE_APP_IMAGE}"

                                echo "Stopping failed deployment..."
                                docker compose down

                                echo "Starting previous deployment..."
                                docker compose up -d

                                echo "Rollback container status:"
                                docker compose ps
                            '''
                        }

                        echo "=========================================="
                        echo "VERIFYING ROLLBACK"
                        echo "=========================================="

                        sh '''
                            echo "Waiting for rolled-back application..."

                            READY=false

                            for i in $(seq 1 30); do

                                if curl -fs http://localhost:8084/ > /dev/null; then
                                    echo "Rolled-back application is ready!"
                                    READY=true
                                    break
                                fi

                                echo "Rollback application not ready... attempt $i/30"
                                sleep 2
                            done

                            if [ "$READY" != "true" ]; then
                                echo "CRITICAL: Rollback application failed to become ready."
                                exit 1
                            fi

                            echo ""
                            echo "Testing rolled-back root endpoint..."

                            curl -f http://localhost:8084/

                            echo ""
                            echo ""
                            echo "Testing rolled-back employees API..."

                            curl -f http://localhost:8084/employees

                            echo ""
                            echo ""
                            echo "Rollback smoke test passed successfully!"
                        '''

                        echo "=========================================="
                        echo "VERIFYING DEPLOYED IMAGE"
                        echo "=========================================="

                        sh '''
                            DEPLOYED_IMAGE=$(docker inspect employee-app \
                                --format '{{.Config.Image}}')

                            echo "Currently deployed image:"
                            echo "${DEPLOYED_IMAGE}"

                            if [ "${DEPLOYED_IMAGE}" != "${PREVIOUS_IMAGE}" ]; then
                                echo "CRITICAL: Rollback image verification failed."
                                echo "Expected: ${PREVIOUS_IMAGE}"
                                echo "Actual:   ${DEPLOYED_IMAGE}"
                                exit 1
                            fi

                            echo "Rollback image verification successful!"
                        '''

                        echo "=========================================="
                        echo "AUTOMATIC ROLLBACK COMPLETED"
                        echo "=========================================="

                        echo "Restored image: ${env.PREVIOUS_IMAGE}"

                        error(
                            "Deployment failed smoke testing. " +
                            "Automatic rollback completed successfully."
                        )
                    }
                }
            }
        }
    }

    post {

        always {
            echo "=========================================="
            echo "Pipeline execution completed."
            echo "=========================================="
        }

        success {
            echo "=========================================="
            echo "PIPELINE COMPLETED SUCCESSFULLY"
            echo "=========================================="

            echo "Build Number: ${BUILD_NUMBER}"
            echo "Docker Image: ${DOCKER_IMAGE}"
            echo "Latest Image: ${DOCKER_LATEST}"
            echo "Application: http://localhost:8084"

            echo "=========================================="
        }

        failure {
            script {

                echo "=========================================="
                echo "PIPELINE FAILED"
                echo "=========================================="

                echo "Build Number: ${BUILD_NUMBER}"

                if (env.PREVIOUS_IMAGE?.trim()) {
                    echo "Previous Image: ${env.PREVIOUS_IMAGE}"
                }

                echo "New Image: ${DOCKER_IMAGE}"

                echo "Check the failed stage and console output."

                echo "=========================================="
            }
        }
    }
}
