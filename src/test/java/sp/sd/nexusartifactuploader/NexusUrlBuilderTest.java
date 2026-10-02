package sp.sd.nexusartifactuploader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NexusUrlBuilderTest {

    private static final String HOST = "nexus.example.com";
    private static final String REPO = "my-repository";

    @Test
    void nexus3UsesRepositoryPath() {
        assertThat(
                NexusUrlBuilder.repositoryBaseUrl("https", HOST, "nexus3", REPO),
                is("https://nexus.example.com/repository/my-repository"));
    }

    @Test
    void nexus2UsesContentRepositoriesPath() {
        assertThat(
                NexusUrlBuilder.repositoryBaseUrl("https", HOST, "nexus2", REPO),
                is("https://nexus.example.com/content/repositories/my-repository"));
    }

    @Test
    void unknownNexusVersionFallsBackToNexus2Layout() {
        // Matches what Utils has always done: anything that is not nexus3 gets the Nexus 2 path.
        assertThat(
                NexusUrlBuilder.repositoryBaseUrl("https", HOST, null, REPO),
                is("https://nexus.example.com/content/repositories/my-repository"));
        assertThat(
                NexusUrlBuilder.repositoryBaseUrl("https", HOST, "nexus4", REPO),
                is("https://nexus.example.com/content/repositories/my-repository"));
    }

    @Test
    void nexusVersionIsCaseInsensitiveAndTrimmed() {
        assertThat(
                NexusUrlBuilder.repositoryBaseUrl("https", HOST, " NEXUS3 ", REPO),
                is("https://nexus.example.com/repository/my-repository"));
    }

    @Test
    void schemeInHostIsStrippedRatherThanDuplicated() {
        // The form validation rejects a scheme, but a job configured before that validation existed
        // must not produce https://https://...
        assertThat(
                NexusUrlBuilder.repositoryBaseUrl("https", "https://" + HOST, "nexus3", REPO),
                is("https://nexus.example.com/repository/my-repository"));
    }

    @Test
    void redundantSlashesAreCollapsed() {
        assertThat(
                NexusUrlBuilder.repositoryBaseUrl("https", HOST + "/", "nexus3", "/" + REPO + "/"),
                is("https://nexus.example.com/repository/my-repository"));
    }

    @Test
    void hostWithPortAndContextPathIsPreserved() {
        assertThat(
                NexusUrlBuilder.repositoryBaseUrl("http", "localhost:8081/nexus", "nexus3", REPO),
                is("http://localhost:8081/nexus/repository/my-repository"));
    }

    @Test
    void emptyProtocolDefaultsToHttp() {
        assertThat(
                NexusUrlBuilder.repositoryBaseUrl("", HOST, "nexus3", REPO),
                is("http://nexus.example.com/repository/my-repository"));
    }

    @Test
    void groupIdDotsBecomePathSeparators() {
        assertThat(
                NexusUrlBuilder.artifactPath("com.example.tools", "my-app", "1.0.0", "", "jar"),
                is("com/example/tools/my-app/1.0.0/my-app-1.0.0.jar"));
    }

    @Test
    void classifierIsInsertedBeforeExtension() {
        assertThat(
                NexusUrlBuilder.artifactPath("com.example", "my-app", "1.0.0", "linux", "zip"),
                is("com/example/my-app/1.0.0/my-app-1.0.0-linux.zip"));
    }

    @Test
    void nullClassifierIsOmitted() {
        assertThat(NexusUrlBuilder.fileName("my-app", "1.0.0", null, "jar"), is("my-app-1.0.0.jar"));
    }

    @Test
    void fullDownloadUrlIsAssembled() {
        assertThat(
                NexusUrlBuilder.downloadUrl(
                        "https", HOST, "nexus3", REPO, "com.example", "my-app", "2.1.3", "linux", "tar.gz"),
                is(
                        "https://nexus.example.com/repository/my-repository/com/example/my-app/2.1.3/my-app-2.1.3-linux.tar.gz"));
    }

    @Test
    void snapshotVersionsAreRecognised() {
        assertTrue(NexusUrlBuilder.isSnapshot("1.0.0-SNAPSHOT"));
        assertTrue(NexusUrlBuilder.isSnapshot("1.0.0-snapshot"));
        assertFalse(NexusUrlBuilder.isSnapshot("1.0.0"));
        assertFalse(NexusUrlBuilder.isSnapshot(null));
    }

    @Test
    void checksumsSignaturesAndMetadataAreNotArtifacts() {
        assertFalse(NexusUrlBuilder.isArtifactResource("com/example/my-app/1.0.0/my-app-1.0.0.jar.sha1"));
        assertFalse(NexusUrlBuilder.isArtifactResource("com/example/my-app/1.0.0/my-app-1.0.0.jar.sha256"));
        assertFalse(NexusUrlBuilder.isArtifactResource("com/example/my-app/1.0.0/my-app-1.0.0.jar.sha512"));
        assertFalse(NexusUrlBuilder.isArtifactResource("com/example/my-app/1.0.0/my-app-1.0.0.jar.md5"));
        assertFalse(NexusUrlBuilder.isArtifactResource("com/example/my-app/1.0.0/my-app-1.0.0.jar.asc"));
        assertFalse(NexusUrlBuilder.isArtifactResource("com/example/my-app/maven-metadata.xml"));
        assertFalse(NexusUrlBuilder.isArtifactResource(""));
        assertFalse(NexusUrlBuilder.isArtifactResource(null));
        assertTrue(NexusUrlBuilder.isArtifactResource("com/example/my-app/1.0.0/my-app-1.0.0.jar"));
    }

    @Test
    void releaseUrlMatchesOnExactFileName() {
        String url = "https://nexus.example.com/repository/releases/com/example/my-app/1.0.0/my-app-1.0.0.jar";
        assertTrue(NexusUrlBuilder.matches(url, "my-app", "1.0.0", "", "jar"));
        assertFalse(NexusUrlBuilder.matches(url, "my-app", "1.0.1", "", "jar"));
        assertFalse(NexusUrlBuilder.matches(url, "other-app", "1.0.0", "", "jar"));
        assertFalse(NexusUrlBuilder.matches(url, "my-app", "1.0.0", "linux", "jar"));
    }

    @Test
    void snapshotUrlMatchesDespiteServerAssignedTimestamp() {
        // This is the case a deterministic URL cannot cover: Maven renames the file on deploy.
        String url = "https://nexus.example.com/repository/snapshots/com/example/my-app/1.0.0-SNAPSHOT/"
                + "my-app-1.0.0-20260915.104233-7.jar";
        assertTrue(NexusUrlBuilder.matches(url, "my-app", "1.0.0-SNAPSHOT", "", "jar"));
        assertFalse(NexusUrlBuilder.matches(url, "my-app", "1.0.0-SNAPSHOT", "", "zip"));
        assertFalse(NexusUrlBuilder.matches(url, "other-app", "1.0.0-SNAPSHOT", "", "jar"));
    }

    @Test
    void classifiedSnapshotMatchesOnlyItsOwnClassifier() {
        String classified = "https://nexus.example.com/repository/snapshots/com/example/my-app/1.0.0-SNAPSHOT/"
                + "my-app-1.0.0-20260915.104233-7-linux.zip";
        assertTrue(NexusUrlBuilder.matches(classified, "my-app", "1.0.0-SNAPSHOT", "linux", "zip"));
        assertFalse(NexusUrlBuilder.matches(classified, "my-app", "1.0.0-SNAPSHOT", "windows", "zip"));
    }

    @Test
    void classifierlessSnapshotDoesNotClaimAClassifiedArtifact() {
        // Without this guard a job uploading both my-app.zip and my-app-linux.zip would report the
        // wrong URL for one of them.
        String classified = "https://nexus.example.com/repository/snapshots/com/example/my-app/1.0.0-SNAPSHOT/"
                + "my-app-1.0.0-20260915.104233-7-linux.zip";
        assertFalse(NexusUrlBuilder.matches(classified, "my-app", "1.0.0-SNAPSHOT", "", "zip"));
    }

    @Test
    void emptyUrlNeverMatches() {
        assertFalse(NexusUrlBuilder.matches("", "my-app", "1.0.0", "", "jar"));
        assertFalse(NexusUrlBuilder.matches(null, "my-app", "1.0.0", "", "jar"));
    }
}
