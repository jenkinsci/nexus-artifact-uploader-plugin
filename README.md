# Nexus Artifact Uploader

This plugin uploads artifacts generated from non-maven projects to Nexus.

This plugin now supports Nexus-2.x & Nexus-3.x.

Uploading snapshots is not supported by this plugin.

### Job DSL example

```groovy
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
            verifyUploads(false)
            artifact {
                artifactId('nexus-artifact-uploader')
                type('jar')
                classifier('debug')
                file('nexus-artifact-uploader.jar')
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
```

# Jenkins pipeline example

```groovy
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
             type: 'jar']
        ]
     )
```

## Getting the URLs the artifacts are now available at

Every upload records where each artifact ended up: in the step's return value, in the build's
environment, on the build page and through the REST API.

The Pipeline step returns a list with one entry per uploaded artifact:

```groovy
    def uploaded = nexusArtifactUploader(
        nexusVersion: 'nexus3',
        protocol: 'https',
        nexusUrl: 'my.nexus.address',
        groupId: 'com.example',
        version: version,
        repository: 'RepositoryName',
        credentialsId: 'CredentialsId',
        artifacts: [[artifactId: 'my-service', classifier: '', file: "my-service-${version}.jar", type: 'jar']]
    )

    echo uploaded[0].url
    // https://my.nexus.address/repository/RepositoryName/com/example/my-service/1.0.0/my-service-1.0.0.jar

    echo uploaded.collect { it.url }.join('\n')
```

Each entry is a plain `Map` of strings:

| Key          | Meaning |
|--------------|---------|
| `url`        | where the artifact can be downloaded from |
| `groupId`    | as configured |
| `artifactId` | as configured |
| `version`    | as configured |
| `classifier` | as configured, empty string if none |
| `type`       | as configured |
| `fileName`   | the published file name, which for snapshots carries the server-assigned timestamp |
| `repository` | the target repository |
| `verified`   | `"true"` if the URL was observed during the transfer or confirmed by the search API, `"false"` if it was computed from the coordinates and not confirmed |

Plain strings in plain collections are used on purpose: they survive CPS serialization and can be
read from a sandboxed script without any script-security whitelisting.

### Compatibility with the previous return value

The step used to return a `Boolean`. A successful upload now returns a non-empty list and a failure
still throws, so `if (nexusArtifactUploader(...)) { ... }` keeps working through Groovy truthiness.
Scripts that compare the result with `==` — `if (nexusArtifactUploader(...) == true)` — must be
updated.


### Environment variables

Every upload also contributes environment variables to the build:

| Variable | Value |
|----------|-------|
| `NEXUS_ARTIFACT_COUNT` | number of artifacts uploaded in this build |
| `NEXUS_ARTIFACT_URLS`  | all URLs, comma separated |
| `NEXUS_ARTIFACT_URL`   | the first URL |
| `NEXUS_ARTIFACT_URL_1` … `NEXUS_ARTIFACT_URL_n` | one variable per artifact |

These are contributed through `EnvironmentContributingAction`, which is consulted by
`Run#getEnvironment`. That makes them dependable in **Freestyle** build steps and post-build
actions. Declarative and scripted Pipeline resolve `env` differently, so do **not** rely on
`env.NEXUS_ARTIFACT_URL` inside a Pipeline — use the step's return value there.

### On the build page

Each build that uploaded something gets a *Nexus artifacts* entry in its side bar, leading to a
table of every artifact with its repository, coordinates, classifier, type and download link. The
build page itself lists the download URLs. Several uploads in one build share that one entry and
one list. The data is stored in `build.xml`, so it survives restarts and is readable through the
Jenkins REST API:

```
curl -s "$JENKINS_URL/job/my-job/42/api/json?tree=actions[artifacts[url,fileName,verified]]"
```

In the REST API each upload is a separate entry in `actions`, so iterate over all of them.

### How the URL is determined, and when it is only a guess

1. The URLs observed during the deploy are used first. They are exact, including for snapshots,
   whose published file name is assigned by the server.
2. If no observed URL matches an artifact, the URL is computed from the coordinates using the
   Maven 2 repository layout and flagged `verified: false`. For a release that computed URL is
   almost certainly right; for a snapshot it points at a base-version file name that usually does
   not exist on the server.
3. Optionally, set `verifyUploads: true` to confirm each URL against the Nexus 3
   `/service/rest/v1/search/assets` API and replace it with the `downloadUrl` the server reports.

`verifyUploads` is off by default and comes with one real constraint: the query is issued by the
**controller**, not by the agent that performed the upload. If Nexus is only reachable from the
agent network, verification logs a warning and keeps the unverified URL. It never fails the build,
because by that point the artifacts are already in Nexus. It requires Nexus 3 and is ignored for
Nexus 2.

## Changelog in [GitHub Releases](https://github.com/jenkinsci/nexus-artifact-uploader-plugin/releases)

Release notes have been recorded in [GitHub Releases](https://github.com/jenkinsci/nexus-artifact-uploader-plugin/releases) since 2.14.
Prior release notes were recorded in the [change log file](https://github.com/jenkinsci/nexus-artifact-uploader-plugin/blob/nexus-artifact-uploader-2.14/CHANGELOG.md).
