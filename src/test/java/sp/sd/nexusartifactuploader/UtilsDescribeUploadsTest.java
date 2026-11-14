package sp.sd.nexusartifactuploader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonatype.aether.artifact.Artifact;

/**
 * Covers the part of the feature that decides <em>which</em> URL belongs to <em>which</em>
 * artifact, and whether that URL is a fact or a guess. Runs without a Nexus or a Jenkins instance.
 */
class UtilsDescribeUploadsTest {

    private static final String REPO = "releases";
    private static final String HOST = "nexus.example.com";
    private static final String GROUP = "com.example";
    private static final File FILE = new File(".");

    private static Artifact artifact(String artifactId, String version, String classifier, String type) {
        return Utils.toArtifact(
                new sp.sd.nexusartifactuploader.Artifact(artifactId, type, classifier, "ignored"),
                GROUP,
                version,
                FILE);
    }

    private static List<UploadedArtifact> describe(List<String> observed, Artifact... artifacts) {
        return Utils.describeUploads(observed, REPO, "https", HOST, "nexus3", artifacts);
    }

    @Test
    void observedUrlIsUsedAndMarkedVerified() {
        String url = "https://nexus.example.com/repository/releases/com/example/my-app/1.0.0/my-app-1.0.0.jar";
        List<UploadedArtifact> result = describe(Collections.singletonList(url), artifact("my-app", "1.0.0", "", "jar"));

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getUrl(), is(url));
        assertThat(result.get(0).getFileName(), is("my-app-1.0.0.jar"));
        assertTrue(result.get(0).isVerified());
    }

    @Test
    void withoutAnObservedUrlTheComputedOneIsUsedAndMarkedUnverified() {
        List<UploadedArtifact> result =
                describe(Collections.<String>emptyList(), artifact("my-app", "1.0.0", "", "jar"));

        assertThat(result, hasSize(1));
        assertThat(
                result.get(0).getUrl(),
                is("https://nexus.example.com/repository/releases/com/example/my-app/1.0.0/my-app-1.0.0.jar"));
        assertFalse(result.get(0).isVerified());
    }

    @Test
    void snapshotTimestampIsTakenFromTheTransferNotGuessed() {
        String url = "https://nexus.example.com/repository/snapshots/com/example/my-app/1.0.0-SNAPSHOT/"
                + "my-app-1.0.0-20260915.104233-7.jar";
        List<UploadedArtifact> result =
                describe(Collections.singletonList(url), artifact("my-app", "1.0.0-SNAPSHOT", "", "jar"));

        assertThat(result.get(0).getUrl(), is(url));
        assertThat(result.get(0).getFileName(), is("my-app-1.0.0-20260915.104233-7.jar"));
        assertTrue(result.get(0).isVerified());
    }

    @Test
    void eachArtifactGetsItsOwnUrl() {
        String jar = "https://nexus.example.com/repository/releases/com/example/my-app/1.0.0/my-app-1.0.0.jar";
        String zip = "https://nexus.example.com/repository/releases/com/example/my-app/1.0.0/my-app-1.0.0-linux.zip";
        List<UploadedArtifact> result = describe(
                Arrays.asList(zip, jar),
                artifact("my-app", "1.0.0", "", "jar"),
                artifact("my-app", "1.0.0", "linux", "zip"));

        assertThat(result, hasSize(2));
        assertThat(result.get(0).getUrl(), is(jar));
        assertThat(result.get(1).getUrl(), is(zip));
    }

    @Test
    void oneUrlIsNotClaimedByTwoArtifacts() {
        // Two artifacts with identical coordinates would otherwise both latch onto the same URL.
        String jar = "https://nexus.example.com/repository/releases/com/example/my-app/1.0.0/my-app-1.0.0.jar";
        List<UploadedArtifact> result = describe(
                Collections.singletonList(jar),
                artifact("my-app", "1.0.0", "", "jar"),
                artifact("my-app", "1.0.0", "", "jar"));

        assertThat(result, hasSize(2));
        assertTrue(result.get(0).isVerified());
        assertFalse(result.get(1).isVerified());
    }

    @Test
    void checksumUrlsDoNotBecomeArtifactUrls() {
        // TransferListener filters them, but describeUploads must not fall for them either.
        String checksum =
                "https://nexus.example.com/repository/releases/com/example/my-app/1.0.0/my-app-1.0.0.jar.sha1";
        List<UploadedArtifact> result =
                describe(Collections.singletonList(checksum), artifact("my-app", "1.0.0", "", "jar"));

        assertFalse(result.get(0).isVerified());
        assertThat(
                result.get(0).getUrl(),
                is("https://nexus.example.com/repository/releases/com/example/my-app/1.0.0/my-app-1.0.0.jar"));
    }

    @Test
    void nullObservedListIsTolerated() {
        List<UploadedArtifact> result = describe(null, artifact("my-app", "1.0.0", "", "jar"));
        assertThat(result, hasSize(1));
        assertFalse(result.get(0).isVerified());
    }

    @Test
    void noArtifactsProducesAnEmptyList() {
        assertThat(describe(Collections.<String>emptyList()), hasSize(0));
        assertThat(Utils.describeUploads(null, REPO, "https", HOST, "nexus3", (Artifact[]) null), hasSize(0));
    }

    @Test
    void coordinatesAreCarriedIntoTheResult() {
        List<UploadedArtifact> result =
                describe(Collections.<String>emptyList(), artifact("my-app", "1.0.0", "linux", "zip"));
        UploadedArtifact uploaded = result.get(0);

        assertThat(uploaded.getGroupId(), is(GROUP));
        assertThat(uploaded.getArtifactId(), is("my-app"));
        assertThat(uploaded.getVersion(), is("1.0.0"));
        assertThat(uploaded.getClassifier(), is("linux"));
        assertThat(uploaded.getType(), is("zip"));
        assertThat(uploaded.getRepository(), is(REPO));
    }
}
