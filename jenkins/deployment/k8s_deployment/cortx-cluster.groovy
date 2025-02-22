pipeline {
    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }
    
    options {
        timeout(time: 45, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '20', numToKeepStr: '20'))
        disableConcurrentBuilds()
    }
    environment {
        DOCKER_IMAGE_LOCATION = "https://github.com/Seagate/cortx-re/pkgs/container/cortx-all"
        // Need to make this variable dynamic based on repository selected
    }
    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'rocky-linux-custom-ci', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/shailesh-vaidya/cortx-re', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_ALL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-all:2.0.0-latest-custom-ci', description: 'CORTX-ALL image', trim: true)
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-all:2.0.0-latest-custom-ci', description: 'CORTX-ALL image', trim: true)

        choice (
            choices: ['DEVOPS', 'ALL', 'DEBUG'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
        )
        // Please configure hosts, CORTX_SCRIPTS_BRANCH, CORTX_SCRIPTS_REPO, SNS and DIX parameter in Jenkins job configuration.

    }
    stages {
        stage ("Define Build Variables") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    env.allhost = sh( script: '''
                    echo $hosts | tr ' ' '\n' | awk -F["="] '{print $2}'|cut -d',' -f1
                    ''', returnStdout: true).trim()
                
                    env.master_node = sh( script: '''
                    echo $hosts | tr ' ' '\n' | head -1 | awk -F["="] '{print $2}' | cut -d',' -f1
                    ''', returnStdout: true).trim()

                    env.hostpasswd = sh( script: '''
                    echo $hosts | tr ' ' '\n' | head -1 | awk -F["="] '{print $4}'
                    ''', returnStdout: true).trim()

                    env.numberofnodes = sh( script: '''
                    echo $hosts | tr ' ' '\n' | tail -n +1 | wc -l
                    ''', returnStdout: true).trim()

                }
            }
        }

        stage ("Deploy CORTX Cluster") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def cortxCluster = build job: '/Cortx-Automation/RGW/setup-cortx-rgw-cluster', wait: true,
                    parameters: [
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                        string(name: 'CORTX_ALL_IMAGE', value: "${CORTX_ALL_IMAGE}"),
                        string(name: 'CORTX_SERVER_IMAGE', value: "${CORTX_SERVER_IMAGE}"),
                        text(name: 'hosts', value: "${hosts}"),
                        string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
                        string(name: 'CORTX_SCRIPTS_REPO', value: "${CORTX_SCRIPTS_REPO}"),
                        string(name: 'EXTERNAL_EXPOSURE_SERVICE', value: "NodePort"),
                        string(name: 'SNS_CONFIG', value: "${SNS_CONFIG}"),
                        string(name: 'DIX_CONFIG', value: "${DIX_CONFIG}")
                    ]
                    env.cortxcluster_build_url = cortxCluster.absoluteUrl
                    env.cortxCluster_status = cortxCluster.currentResult
                }
            }
        }
    }
    post {
        always {
            script {
                echo "${env.cortxCluster_status}"
                if ( "${env.cortxCluster_status}" == "SUCCESS") {
                    MESSAGE = "K8s Post Merge Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=Passed"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                    env.deployment_result = "SUCCESS"
                    currentBuild.result = "SUCCESS"
                } else if ( "${env.cortxCluster_status}" == "FAILURE") {
                    manager.buildFailure()
                    MESSAGE = "K8s Post Merge Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=failed"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
                    env.deployment_result = "FAILURE"
                    currentBuild.result = "FAILURE"
                } else {
                    MESSAGE = "K8s Post Merge Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=unstable"
                    ICON = "unstable.gif"
                    STATUS = "UNSTABLE"
                    env.deployment_result = "UNSTABLE"
                    currentBuild.result = "UNSTABLE"
                }
                env.build_setupcortx_url = sh( script: "echo ${env.cortxcluster_build_url}/artifact/artifacts/cortx-cluster-status.txt", returnStdout: true)
                env.host = "${env.allhost}"
                env.build_id = "${CORTX_ALL_IMAGE}"
                env.build_location = "${DOCKER_IMAGE_LOCATION}"
                env.deployment_status = "${MESSAGE}"
                env.cluster_status = "${env.build_setupcortx_url}"
                env.CORTX_DOCKER_IMAGE = "${env.dockerimage_id}"
                if ( params.EMAIL_RECIPIENTS == "ALL" ) {
                    mailRecipients = "shailesh.vaidya@seagate.com"
                } else if ( params.EMAIL_RECIPIENTS == "DEVOPS" ) {
                    mailRecipients = "shailesh.vaidya@seagate.com"
                } else if ( params.EMAIL_RECIPIENTS == "DEBUG" ) {
                    mailRecipients = "shailesh.vaidya@seagate.com"
                }
                catchError(stageResult: 'FAILURE') {
                    emailext (
                        body: '''${SCRIPT, template="K8s-deployment-email_2.template"}''',
                        mimeType: 'text/html',
                        subject: "${MESSAGE}",
                        to: "${mailRecipients}",
                        recipientProviders: [[$class: 'RequesterRecipientProvider']]
                    )
                }
                cleanWs()
            }
        }
    }
}