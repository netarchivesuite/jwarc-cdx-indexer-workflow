#!/usr/bin/env groovy


openshift.withCluster() { // Use "default" cluster or fallback to OpenShift cluster detection


    echo "Hello from the project running Jenkins: ${openshift.project()}"

    //Create template with maven settings.xml, so we have credentials for nexus
    podTemplate(
	    // If the project uses Java 11 instead of Java 17, change to
            //   inheritFrom: 'maven',
            // and update the envVars section
            inheritFrom: 'kb-jenkins-agent-java',
            cloud: 'openshift', //cloud must be openshift
            envVars: [
                    // If the project uses Java 11 instead of Java 17, remove the lines with
                    //   USE_JAVA_VERSION and MAVEN_SKIP_RC
                    // and update inheritsFrom a few lines above
                    envVar(key: 'USE_JAVA_VERSION', value: 'java-17'),
                    envVar(key: 'MAVEN_SKIP_RC', value: 'true'),

                    //This fixes the error with en_US.utf8 not being found
                    envVar(key:"LC_ALL", value:"C.utf8")
            ],
            volumes: [ //mount the settings.xml
                       secretVolume(mountPath: '/etc/m2', secretName: 'maven-settings')
            ]) {


        String projectName = encodeName("${JOB_NAME}")
        echo "name=${projectName}"

        try {
            //GO to a node with maven and settings.xml
            node(POD_LABEL) {
                //Do not use concurrent builds
                properties([disableConcurrentBuilds()])

                def mvnCmd = "mvn -s /etc/m2/settings.xml --batch-mode"
                //settings.xml could be in ~/.m2/settings.xml but I did not want to find the username and home

                stage('checkout') {
                    checkout scm
                }

                stage('Mvn clean package') {
                    sh "${mvnCmd} -PallTests clean package"
                }

                stage('Analyze build results') {
                    recordIssues aggregatingResults: true,
                        tools: [java(),
                                javaDoc(),
                                mavenConsole(),
                                taskScanner(highTags:'FIXME', normalTags:'TODO', includePattern: '**/*.java', excludePattern: 'target/**/*')]
                }

                stage('Push to Nexus (if Master)') {
                    sh 'env'
                    echo "Branch name ${env.BRANCH_NAME}"
                    if (env.BRANCH_NAME == 'master') {
	                    sh "${mvnCmd} deploy -DskipTests=true"
                    } else {
	                    echo "Branch ${env.BRANCH_NAME} is not master, so no mvn deploy"
                    }
                }
            }
        } catch (e) {
            currentBuild.result = 'FAILURE'
            throw e
        }
    }
}


private void recreateProject(String projectName) {
    echo "Delete the project ${projectName}, ignore errors if the project does not exist"
    try {
        openshift.selector("project/${projectName}").delete()

        openshift.selector("project/${projectName}").watch {
            echo "Waiting for the project ${projectName} to be deleted"
            return it.count() == 0
        }

    } catch (e) {

    }
//
//    //Wait for the project to be gone
//    sh "until ! oc get project ${projectName}; do date;sleep 2; done; exit 0"

    echo "Create the project ${projectName}"
    openshift.newProject(projectName)
}

/**
 * Encode the jobname as a valid openshift project name
 * @param jobName the name of the job
 * @return the jobname as a valid openshift project name
 */
private static String encodeName(groovy.lang.GString jobName) {
    def jobTokens = jobName.tokenize("/")
    def repo = jobTokens[0]
    if(repo.contains('-')) {
        repo = repo.tokenize("-").collect{it.take(1)}.join("")
    } else {
        repo = repo.take(3)
    }

    def name = ([repo] + jobTokens.drop(1)).join("-")
            .replaceAll("\\s", "-")
            .replaceAll("_", "-")
            .replace("/", '-')
            .replaceAll("^openshift-", "")
            .toLowerCase()
    return name
}

