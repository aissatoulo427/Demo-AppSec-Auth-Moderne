// =============================================================================
// Jenkins CI/CD Pipeline — POC Keycloak Spring Boot
// Stages : DEV → OWASP → SonarQube → Trivy → Docker → UAT → PREPROD
//
// Secrets lus dans HashiCorp Vault (KV v2, montage "secret/") via le plugin
// hashicorp-vault-plugin — auth AppRole "jenkins-ci" configurée automatiquement
// par ci/jenkins/init.groovy.d/03-vault.groovy (credential "vault-approle") :
//   secret/ci/dockerhub        → username, password   (Docker Hub)
//   secret/ci/nvd              → api_key              (OWASP NVD)
//   secret/ci/keycloak-uat     → url, client_secret
//   secret/ci/keycloak-preprod → url, client_secret
//
// Serveur SonarQube configuré sous le nom "SonarQube" :
//   Manage Jenkins > Configure System > SonarQube Servers
// =============================================================================

def dockerHubSecrets() {
    return [[path: 'secret/ci/dockerhub', secretValues: [
        [envVar: 'DOCKER_USER', vaultKey: 'username'],
        [envVar: 'DOCKER_PASS', vaultKey: 'password']
    ]]]
}

def nvdSecrets() {
    return [[path: 'secret/ci/nvd', secretValues: [
        [envVar: 'NVD_KEY', vaultKey: 'api_key']
    ]]]
}

def keycloakSecrets(String env) {
    return [[path: "secret/ci/keycloak-${env}", secretValues: [
        [envVar: 'KEYCLOAK_URL', vaultKey: 'url'],
        [envVar: 'KC_SECRET',    vaultKey: 'client_secret']
    ]]]
}

pipeline {

    agent any

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-17'
    }

    environment {
        DOCKER_HUB_USER   = 'moustaphisene'
        APP_IMAGE         = "${DOCKER_HUB_USER}/poc-keycloak-app"
        SVC_IMAGE         = "${DOCKER_HUB_USER}/poc-keycloak-service"
        IMAGE_TAG         = "v${BUILD_NUMBER}"
        SONAR_KEY_APP     = 'poc-keycloak-app'
        SONAR_KEY_SVC     = 'poc-keycloak-service'
        // Nom complet du plugin : le préfixe "sonar:" seul n'est plus résolu par Maven
        SONAR_MVN_PLUGIN  = 'org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 90, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    // Jenkins tourne en local : GitHub ne peut pas lui envoyer de webhook.
    // Il interroge donc lui-même le dépôt toutes les 2 minutes et ne lance
    // un build que si un nouveau commit a été poussé.
    triggers {
        pollSCM('H/2 * * * *')
    }

    stages {

        // =====================================================================
        // CHECKOUT
        // =====================================================================
        stage('Checkout') {
            steps {
                checkout scm
//                 sh 'echo "Branch: ${GIT_BRANCH} | Commit: ${GIT_COMMIT[0..7]}"'
//                 script {
//                     def shortCommit = env.GIT_COMMIT.take(8)
//                     sh "echo Branch: ${env.GIT_BRANCH} | Commit: ${shortCommit}"
//                 }
                   script {
                       def shortCommit = env.GIT_COMMIT.take(8)
                       echo "Branch: ${env.GIT_BRANCH} | Commit: ${shortCommit}"
                   }
            }
        }

        // =====================================================================
        // DEV — Build & Tests unitaires (parallèle)
        // =====================================================================
        stage('DEV - Build & Unit Tests') {
            parallel {
                stage('App - Package') {
                    steps {
                        dir('demo/app-springboot') {
                            sh 'mvn clean package -DskipTests -B'
                        }
                    }
                }
                stage('Service - Verify') {
                    steps {
                        dir('demo/service-springboot-rest') {
                            sh 'mvn clean verify -B'
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: 'demo/service-springboot-rest/target/surefire-reports/*.xml'
                        }
                    }
                }
            }
        }

        // =====================================================================
        // OWASP Dependency Check — CVE score >= 7 → unstable
        // =====================================================================
        stage('OWASP Dependency Check') {
            steps {
                withVault(vaultSecrets: nvdSecrets()) {
                    dir('demo/app-springboot') {
                        sh '''
                            mvn org.owasp:dependency-check-maven:10.0.3:check \
                                -DnvdApiKey=${NVD_KEY} \
                                -DfailBuildOnCVSS=10 \
                                -DcveValidForHours=12 \
                                -Dformat=ALL \
                                -DsuppressionFile=owasp-suppressions.xml \
                                -B || true
                        '''
                    }
                    dir('demo/service-springboot-rest') {
                        sh '''
                            mvn org.owasp:dependency-check-maven:10.0.3:check \
                                -DnvdApiKey=${NVD_KEY} \
                                -DfailBuildOnCVSS=10 \
                                -DcveValidForHours=12 \
                                -Dformat=ALL \
                                -B || true
                        '''
                    }
                }
            }
            post {
                always {
                    dependencyCheckPublisher(
                        pattern: '**/target/dependency-check-report.xml',
                        failedTotalCritical: 1,
                        unstableTotalHigh: 5
                    )
                }
            }
        }

        // =====================================================================
        // SonarQube — Code & Quality Gate Analysis
        // =====================================================================
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    dir('demo/app-springboot') {
                        sh '''
                            mvn ${SONAR_MVN_PLUGIN}:sonar \
                                -Dsonar.projectKey=${SONAR_KEY_APP} \
                                -Dsonar.projectName="POC Keycloak - App" \
                                -Dsonar.java.source=17 \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                -B
                        '''
                    }
                    dir('demo/service-springboot-rest') {
                        sh '''
                            mvn ${SONAR_MVN_PLUGIN}:sonar \
                                -Dsonar.projectKey=${SONAR_KEY_SVC} \
                                -Dsonar.projectName="POC Keycloak - Service REST" \
                                -Dsonar.java.source=17 \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                -B
                        '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // =====================================================================
        // Trivy — Filesystem Scan (dépendances + secrets dans le code)
        // =====================================================================
        stage('Trivy Filesystem Scan') {
            steps {
                sh '''
                    trivy fs \
                        --exit-code 0 \
                        --severity HIGH,CRITICAL \
                        --format table \
                        --output trivy-fs-app.txt \
                        demo/app-springboot

                    trivy fs \
                        --exit-code 0 \
                        --severity HIGH,CRITICAL \
                        --format table \
                        --output trivy-fs-service.txt \
                        demo/service-springboot-rest

                    echo "====== APP FILESYSTEM SCAN ======"
                    cat trivy-fs-app.txt
                    echo "====== SERVICE FILESYSTEM SCAN ======"
                    cat trivy-fs-service.txt
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-fs-*.txt', allowEmptyArchive: true
                }
            }
        }

        // =====================================================================
        // Docker — Build images
        // =====================================================================
        stage('Docker Build') {
            parallel {
                stage('Build App Image') {
                    steps {
                        sh '''
                            docker build \
                                -f demo/app-springboot/Dockerfile \
                                -t ${APP_IMAGE}:${IMAGE_TAG} \
                                -t ${APP_IMAGE}:latest \
                                --label "build=${BUILD_NUMBER}" \
                                --label "commit=${GIT_COMMIT}" \
                                demo/app-springboot
                        '''
                    }
                }
                stage('Build Service Image') {
                    steps {
                        sh '''
                            docker build \
                                -f demo/service-springboot-rest/Dockerfile \
                                -t ${SVC_IMAGE}:${IMAGE_TAG} \
                                -t ${SVC_IMAGE}:latest \
                                --label "build=${BUILD_NUMBER}" \
                                --label "commit=${GIT_COMMIT}" \
                                demo/service-springboot-rest
                        '''
                    }
                }
            }
        }

        // =====================================================================
        // Trivy — Image Scan (avant push)
        // Scans séquentiels : en parallèle, les deux trivy se disputent le
        // verrou du cache partagé ("Failed to acquire cache or database lock").
        // =====================================================================
        stage('Trivy Image Scan') {
            stages {
                stage('Scan App Image') {
                    steps {
                        sh '''
                            trivy image \
                                --exit-code 0 \
                                --severity HIGH,CRITICAL \
                                --format table \
                                --output trivy-image-app.txt \
                                ${APP_IMAGE}:${IMAGE_TAG}
                            cat trivy-image-app.txt
                        '''
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'trivy-image-app.txt', allowEmptyArchive: true
                        }
                    }
                }
                stage('Scan Service Image') {
                    steps {
                        sh '''
                            trivy image \
                                --exit-code 0 \
                                --severity HIGH,CRITICAL \
                                --format table \
                                --output trivy-image-service.txt \
                                ${SVC_IMAGE}:${IMAGE_TAG}
                            cat trivy-image-service.txt
                        '''
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'trivy-image-service.txt', allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        // =====================================================================
        // Docker Push
        // =====================================================================
        stage('Docker Push') {
            steps {
                withVault(vaultSecrets: dockerHubSecrets()) {
                    sh '''
                        echo "${DOCKER_PASS}" | docker login -u "${DOCKER_USER}" --password-stdin

                        docker push ${APP_IMAGE}:${IMAGE_TAG}
                        docker push ${APP_IMAGE}:latest

                        docker push ${SVC_IMAGE}:${IMAGE_TAG}
                        docker push ${SVC_IMAGE}:latest

                        echo "Images pushed: ${IMAGE_TAG}"
                    '''
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        // =====================================================================
        // UAT — Approbation manuelle + Déploiement
        // =====================================================================
        stage('UAT - Approval') {
            steps {
                timeout(time: 1, unit: 'HOURS') {
                    input(
                        message: "Déployer la version ${IMAGE_TAG} en UAT ?",
                        ok: 'Approuver',
                        submitter: 'admin,release-manager',
                        parameters: [
                            string(name: 'DEPLOY_COMMENT',
                                   description: 'Commentaire (optionnel)',
                                   defaultValue: '')
                        ]
                    )
                }
            }
        }

        stage('UAT - Deploy') {
            steps {
                withVault(vaultSecrets: dockerHubSecrets() + keycloakSecrets('uat')) {
                    sh '''
                        echo "${DOCKER_PASS}" | docker login -u "${DOCKER_USER}" --password-stdin
                        docker pull ${APP_IMAGE}:${IMAGE_TAG}

                        docker stop poc-keycloak-uat  2>/dev/null || true
                        docker rm   poc-keycloak-uat  2>/dev/null || true

                        docker run -d \
                            --name poc-keycloak-uat \
                            -p 8091:8090 \
                            -e SPRING_PROFILES_ACTIVE=uat \
                            -e KEYCLOAK_CLIENT_SECRET="${KC_SECRET}" \
                            -e KEYCLOAK_ISSUER_URI="${KEYCLOAK_URL}/realms/bzhcamp" \
                            --health-cmd="wget -qO- http://localhost:8090/actuator/health || exit 1" \
                            --health-interval=15s \
                            --health-retries=5 \
                            --restart unless-stopped \
                            ${APP_IMAGE}:${IMAGE_TAG}

                        echo "UAT deploye sur le port 8091 -> image ${IMAGE_TAG}"
                    '''
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        // =====================================================================
        // PREPROD — Approbation manuelle + Déploiement
        // =====================================================================
        stage('PREPROD - Approval') {
            steps {
                timeout(time: 2, unit: 'HOURS') {
                    input(
                        message: "Déployer la version ${IMAGE_TAG} en PREPROD ?",
                        ok: 'Approuver',
                        submitter: 'admin,release-manager',
                        parameters: [
                            string(name: 'DEPLOY_COMMENT',
                                   description: 'Commentaire (optionnel)',
                                   defaultValue: '')
                        ]
                    )
                }
            }
        }

        stage('PREPROD - Deploy') {
            steps {
                withVault(vaultSecrets: dockerHubSecrets() + keycloakSecrets('preprod')) {
                    sh '''
                        echo "${DOCKER_PASS}" | docker login -u "${DOCKER_USER}" --password-stdin
                        docker pull ${APP_IMAGE}:${IMAGE_TAG}

                        docker stop poc-keycloak-preprod  2>/dev/null || true
                        docker rm   poc-keycloak-preprod  2>/dev/null || true

                        docker run -d \
                            --name poc-keycloak-preprod \
                            -p 8092:8090 \
                            -e SPRING_PROFILES_ACTIVE=preprod \
                            -e KEYCLOAK_CLIENT_SECRET="${KC_SECRET}" \
                            -e KEYCLOAK_ISSUER_URI="${KEYCLOAK_URL}/realms/bzhcamp" \
                            --health-cmd="wget -qO- http://localhost:8090/actuator/health || exit 1" \
                            --health-interval=15s \
                            --health-retries=5 \
                            --restart unless-stopped \
                            ${APP_IMAGE}:${IMAGE_TAG}

                        echo "PREPROD deploye sur le port 8092 -> image ${IMAGE_TAG}"
                    '''
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

    } // end stages

    // =========================================================================
    // POST
    // =========================================================================
    post {
        success {
            echo "PIPELINE SUCCESS — Build: ${IMAGE_TAG} | Job: ${JOB_NAME}#${BUILD_NUMBER}"
            // Décommentez pour activer les notifications email :
            // emailext(
            //     to: 'killamo5@gmail.com',
            //     subject: "[SUCCESS] ${JOB_NAME} #${BUILD_NUMBER} — ${IMAGE_TAG}",
            //     body: """Build ${IMAGE_TAG} a passé tous les contrôles qualité/sécurité
            //              et est déployé en PREPROD."""
            // )
        }
        failure {
            echo "PIPELINE FAILURE — Build: ${IMAGE_TAG} | Job: ${JOB_NAME}#${BUILD_NUMBER}"
            // emailext(
            //     to: 'killamo5@gmail.com',
            //     subject: "[FAILURE] ${JOB_NAME} #${BUILD_NUMBER}",
            //     body: "Le pipeline a échoué. Consultez les logs Jenkins."
            // )
        }
        always {
            sh 'docker logout || true'
            cleanWs()
        }
    }

}