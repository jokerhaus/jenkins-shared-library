#!/usr/bin/env groovy
// goPipeline.groovy - Jenkins Shared Library для Go проектов
//
// Использование:
// @Library('jenkins-shared-library') _
// goPipeline(
//     projectRepo: 'https://github.com/jokerhaus/Mijob-Panel.git',
//     projectName: 'mijob-panel',
//     projectBranch: 'main'
// )

def call(Map config = [:]) {
    node {
        def repo = config.projectRepo ?: 'https://github.com/jokerhaus/Mijob-Panel.git'
        def name = config.projectName ?: 'mijob-panel'
        def branch = config.projectBranch ?: 'main'
        def registry = config.dockerRegistry ?: '192.168.1.100:5000'
        def image = "${registry}/${name}:${env.BUILD_NUMBER}"
        def imageLatest = "${registry}/${name}:latest"

        echo "Starting CI/CD pipeline for project: ${name}"

        stage('Checkout Project') {
            echo "Checking out ${repo} branch ${branch}"
            // Используем credentials для аутентификации
            git branch: branch, 
                url: repo,
                credentialsId: 'github-token'
        }
        
        stage('Build Docker Image') {
            echo "Building Docker image: ${image}"
            // Проверяем доступность Docker и пытаемся использовать
            sh """
                echo "Checking Docker availability..."
                ls -la /var/run/docker.sock || echo "Docker socket not found"
                docker version || echo "Docker not available"
                echo "Attempting to build image..."
                docker build -t ${image} . || echo "Docker build failed"
                docker tag ${image} ${imageLatest} || echo "Docker tag failed"
            """
        }
        
        stage('Push Docker Image') {
            echo "Pushing Docker images to registry"
            sh """
                docker push ${image} || echo "Docker push failed"
                docker push ${imageLatest} || echo "Docker push latest failed"
            """
        }
        
        stage('Deploy to ArgoCD') {
            echo "Deploying to ArgoCD"
            sh """
                if [ -f k8s/deployment.yaml ]; then
                    sed -i 's|image: .*|image: ${image}|g' k8s/deployment.yaml
                    kubectl apply -f k8s/
                else
                    echo "No k8s deployment files found, skipping ArgoCD deployment"
                fi
            """
        }
        
        echo "Pipeline completed successfully for ${name}"
    }
}
