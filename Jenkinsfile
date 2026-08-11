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
            sh 'chmod +x mvnw'
            sh './mvnw clean package'
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
                  -Dsonar.java.binaries=target/classes
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
            sh 'docker build -t employee-app:${BUILD_NUMBER} .'
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

                    docker compose down || true
                    docker compose up -d

                    docker compose ps
                '''
            }
        }
    }

    stage('Smoke Test') {
        steps {
            sh '''
                echo "Waiting for application to become ready..."

                for i in {1..30}; do
                    if curl -fs http://localhost:8084/ > /dev/null; then
                        echo "Application is ready!"
                        break
                    fi

                    echo "Application not ready yet... attempt $i/30"
                    sleep 2
                done

                echo "Testing application root endpoint..."
                curl -f http://localhost:8084/

                echo ""
                echo "Testing employees API..."
                curl -f http://localhost:8084/employees

                echo ""
                echo "Smoke tests passed successfully!"
            '''
        }
    }
}

}
