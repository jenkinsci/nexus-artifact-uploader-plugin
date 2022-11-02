# Changelog

### Release 2.10 (13 August 2017)

- \[JENKINS-46109\] Fixed 404 errors in configure page with Dropdown controls.
- \[JENKINS-45995\] Trim artifact filename when spaces added by mistake.

### Release 2.9 (13 December 2016)

- \[JENKINS-39594\] Fixed frequent, intermittent "Failed to deploy artifacts".

### Release 2.8 (21 November 2016)

- \[JENKINS-38950\] Added support for uploading multiple artifacts.

### Release 2.7 (15 October 2016)

- \[FIXED JENKINS-38918\] Build is not failing in pipeline on failed upload.

### Release 2.6 (08 October 2016)

- \[JENKINS-37960\] Added support for Nexus-3 version to upload artifacts.

### Release 2.5 (30 September 2016)

- Added DSL support and above is the example to use this plugin.

### Release 2.4 (20 August 2016)

- Added support for entering classifier and type.
- \[JENKINS-37536\] - Fixed null pointer exception when passing folder level credentials.

### Release 2.3 (28 July 2016)

- Upgraded Credentials plugin to 2.1.0.

### Release 2.2 (24 May 2016)

- Added support for Jenkins Pipeline.
- Added "nexusArtifactUploader" as pipeline step.

### Release 2.1.2 (06 April 2016)

- Fixed protocol issue when configured HTTPS it resets to HTTP

### Release 2.1.1 (05 April 2016)

- Added empty option to Jenkins Credentials

### Release 2.1 (03 April 2016)

- Added Jenkins Credentials to pass username and password

### Release 2.0 (13 March 2016)

- Removing the dependency with TokenMacro for resolving environment variables used Jenkins method to resolve.
- Added help text for the important fields.
- Added error message from server when failed to upload to nexus.
- Added uploaded artifact link in side bar.
- Changed name of the plugin from "Upload artifacts to nexus" to
"Nexus Artifact Uploader"

### Release 1.1 (07 March 2016)

- Fixed Uploading files from Slave machines.
- Fixed validation issue with Nexus Url

### Release 1.0 (06 March 2016)

- First public release
