# nexus-artifact-uploader

This plugin goal is to upload artifacts generated from non-maven projects to Nexus

This plugin now supports Nexus-2.x & Nexus-3.x.

Uploading snapshots is not supported by this plugin.

# Job DSL example

    freeStyleJob('NexusArtifactUploaderJob') {
        steps {
          nexusArtifactUploader {
            nexusVersion('nexus2')
            protocol('http')
            nexusUrl('localhost:8080/nexus')
            groupId('sp.sd')
            version('2.4')
            repository('NexusArtifactUploader')
            credentialsId('44620c50-1589-4617-a677-7563985e46e1')
            artifact {
                artifactId('nexus-artifact-uploader')
                type('jar')
                classifier('debug')
                file('nexus-artifact-uploader.jar')
                pomFile('pom.xml')
            }
            artifact {
                artifactId('nexus-artifact-uploader')
                type('hpi')
                classifier('debug')
                file('nexus-artifact-uploader.hpi')
            }
          }
        }
    }

# Jenkins pipeline example

    nexusArtifactUploader(
        nexusVersion: 'nexus3',
        protocol: 'http',
        nexusUrl: 'my.nexus.address',
        groupId: 'com.example',
        version: version,
        repository: 'RepositoryName',
        credentialsId: 'CredentialsId',
        artifacts: [
            [artifactId: projectName,
             classifier: '',
             file: 'my-service-' + version + '.jar',
             pomFile: 'pom.xml'
             type: 'jar']
        ]
     )
