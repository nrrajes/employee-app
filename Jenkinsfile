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

        stage('Build Docker Image') {
            steps {
                sh 'docker build -t employee-app:${BUILD_NUMBER} .'
            }
        }

        stage('Deploy Container') {
            steps {
                sh '''
                docker stop employee-app || true
                docker rm employee-app || true

                docker run -d \
                --name employee-app \
                --network employee-network \
                -p 8084:8080 \
                -e SPRING_PROFILES_ACTIVE=docker \
                employee-app:${BUILD_NUMBER}
                '''
            }
        }
    }
}
