#!/usr/bin/env groovy

/**
 * Update Kubernetes manifests with new image tags
 */
def call(Map config = [:]) {
    def imageName = config.imageName ?: error("Image name is required")
    def imageTag = config.imageTag ?: error("Image tag is required")
    def manifestsPath = config.manifestsPath ?: 'kubernetes'
    def gitCredentials = config.gitCredentials ?: 'github-credentials'
    def gitUserName = config.gitUserName ?: 'Jenkins CI'

    // Strips "origin/" if it exists
    CLEAN_BRANCH = "${env.GIT_BRANCH}".replaceAll('^origin/', '')

    // Append /prod to manifestsPath if branch is main
    if (CLEAN_BRANCH == 'main') {
        manifestsPath = "${manifestsPath}/prod"
    }

    // Use the env object
    echo "Clean Branch: ${CLEAN_BRANCH}"

    echo "Update k8 for ${imageName}"
    echo "Updating Kubernetes manifests with image tag: ${imageTag}"
    
    withCredentials([usernamePassword(
        credentialsId: gitCredentials,
        usernameVariable: 'GIT_USERNAME',
        passwordVariable: 'GIT_PASSWORD'
    )]) {
        // Configure Git
        sh """
            git config user.name "${gitUserName}"
        """
        
        // Update deployment manifests with new image tags
        sh """
            # Debug: show path and current image line
            echo "Manifests path: ${manifestsPath}"
            echo "Looking for image: ${imageName}"
            grep -n "image:" ${manifestsPath}/02-deployment.yaml || echo "No image lines found"

            # Update deployment (handles with or without existing tag)
            sed -i "s|image: ${imageName}.*|image: ${imageName}:${imageTag}|g" ${manifestsPath}/02-deployment.yaml

            # Update migration job if it exists
            if [ -f "${manifestsPath}/04-migration-job.yaml" ]; then
                sed -i "s|image: ${imageName}.*|image: ${imageName}:${imageTag}|g" ${manifestsPath}/04-migration-job.yaml
            fi
            
            # Ensure ingress is using the correct domain
            #if [ -f "${manifestsPath}/10-ingress.yaml" ]; then
            #    sed -i "s|host: .*|host: domain.gigbanc.co|g" ${manifestsPath}/10-ingress.yaml
            #fi
            
            # Check for changes
            if git diff --quiet; then
                echo "No changes to commit"
            else
                # Commit and push changes
                git add ${manifestsPath}/*.yaml
                git commit -m "Update image tags to ${imageTag} and ensure correct domain [ci skip]"
                
                # Set up credentials for push
                git remote set-url origin https://\${GIT_USERNAME}:\${GIT_PASSWORD}@github.com/gigbanc/payment-service.git
                git push origin HEAD:${CLEAN_BRANCH}
            fi
        """
    }
}
