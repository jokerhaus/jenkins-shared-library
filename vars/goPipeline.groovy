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
            git branch: branch, url: repo
        }
        
        stage('Build Docker Image') {
            echo "Building Docker image: ${image}"
            sh """
                docker build -t ${image} .
                docker tag ${image} ${imageLatest}
            """
        }
        
        stage('Push Docker Image') {
            echo "Pushing Docker images to registry"
            sh """
                docker push ${image}
                docker push ${imageLatest}
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
