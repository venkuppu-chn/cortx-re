pipeline {

    agent {
        node {
            label 'motr-remote-controller-hw'
        }
    }

    parameters {
        string(name: 'MOTR_REPO', defaultValue: 'https://github.com/seagate/cortx-motr', description: 'Motr github repository url (https)')
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Motr repo branch')
        
    }

    options {
		timestamps ()
        timeout(time: 10, unit: 'HOURS')
	}

    triggers {
        // Scheduled to run on daily ~ 1-2 AM IST
        cron('H 20 * * *')
    }

	stages {

        stage('Checkout') {
            when { expression { true } }
            steps {
                cleanWs()
                dir('motr'){
                    checkout([$class: 'GitSCM', branches: [[name: "*/${MOTR_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${MOTR_REPO}"]]])
                }
                dir('xperior'){
                    git credentialsId: 'cortx-admin-github', url: "https://github.com/Seagate/xperior.git", branch: "main"
                }
                dir('xperior-perl-libs'){
                    git credentialsId: 'cortx-admin-github', url: "https://github.com/Seagate/xperior-perl-libs.git", branch: "main"
                }
                dir('seagate-ci'){
                    git credentialsId: 'cortx-admin-github', url: "https://github.com/Seagate/seagate-ci", branch: "main"
                }
                dir('seagate-eng-tools'){
                    git credentialsId: 'cortx-admin-github', url: "https://github.com/Seagate/seagate-eng-tools.git", branch: "main"
                }
            }
        }
        stage('Static Code Analysis') {
            steps {
                script {

                    sh  label: 'run cppcheck', script:'''
                        mkdir -p html
                        /opt/perlbrew/perls/perl-5.22.0/bin/perl seagate-eng-tools/scripts/build_support/cppcheck.pl --src=motr  --debug --maxconfig=2 --jenkins --xmlreport=diff.xml --cfgfile=seagate-eng-tools/jenkins/build/motr_cppcheck.yaml  --htmldir=html --reporturl="${BUILD_URL}/CppCheck_Report/"
                    '''
                    sh  label: 'get cppcheck result', script:'''
                        no_warning=$(cat html/index.html | grep "total<br/><br/>" | tr -dc '0-9')
                        sca_result="Cppcheck: No new warnings found"
                        if [ "$no_warning" != "0" ]; then 
                            sca_result="Cppcheck: Found $no_warning new warning(s)"
                        fi
                        echo $sca_result > cppcheck_Result
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'html/*.*', fingerprint: true
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: 'html', reportFiles: 'index.html', reportName: 'CppCheck Report', reportTitles: ''])
                }
            }
        }
        stage('Run Test') {
            when { expression { true } }
            steps {
                script{
                    sh '''
                        set -ae
                        set
                        WD=$(pwd)
                        hostname
                        id
                        ls
                        export DO_MOTR_BUILD=yes
                        export MOTR_REFSPEC=""
                        export TESTDIR=motr/.xperior/testds/
                        export XPERIOR="${WD}/xperior"
                        export ITD="${WD}/seagate-ci/xperior"
                        export XPLIB="${WD}/xperior-perl-libs/extlib/lib/perl5"
                        export PERL5LIB="${XPERIOR}/mongo/lib:${XPERIOR}/lib:${ITD}/lib:${XPLIB}/"
                        export PERL_HOME="/opt/perlbrew/perls/perl-5.22.0/"
                        export PATH="${PERL_HOME}/bin/:$PATH:/sbin/:/usr/sbin/"
                        export RWORKDIR='motr/motr_test_github_workdir/workdir'
                        export IPMIDRV=lan
                        export BUILDDIR="/root/${RWORKDIR}"
                        export XPEXCLUDELIST=""
                        export UPLOADTOBOARD=
                        export PRERUN_REBOOT=yes

                        ${PERL_HOME}/bin/perl "${ITD}/contrib/run_motr_single_test.pl"
                    '''
                }
            }
            post {
                always {
                    script {
                        archiveArtifacts artifacts: 'workdir/**/*.*, build*.*, artifacts/*.*', fingerprint: true, allowEmptyArchive: true
                        summary = junit testResults: '**/junit/*.junit', allowEmptyResults : true, testDataPublishers: [[$class: 'AttachmentPublisher']]     

                        cleanWs()
                    }                            
                }
            }
        }    	
    }
}