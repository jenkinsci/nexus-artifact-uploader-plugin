package sp.sd.nexusartifactuploader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UploadedArtifactTest {

    private static final String URL =
            "https://nexus.example.com/repository/releases/com/example/my-app/1.0.0/my-app-1.0.0.jar";

    private static UploadedArtifact artifact(boolean verified) {
        return new UploadedArtifact(
                "com.example", "my-app", "1.0.0", "linux", "jar", "my-app-1.0.0.jar", "releases", URL, verified);
    }

    @Test
    void mapContainsOnlyStringsSoItSurvivesCps() {
        Map<String, String> map = artifact(true).toMap();

        assertThat(map.get("url"), is(URL));
        assertThat(map.get("groupId"), is("com.example"));
        assertThat(map.get("artifactId"), is("my-app"));
        assertThat(map.get("version"), is("1.0.0"));
        assertThat(map.get("classifier"), is("linux"));
        assertThat(map.get("type"), is("jar"));
        assertThat(map.get("fileName"), is("my-app-1.0.0.jar"));
        assertThat(map.get("repository"), is("releases"));
        assertThat(map.get("verified"), is("true"));
        for (Object value : map.values()) {
            assertTrue(value instanceof String, "every value must be a String, got " + value.getClass());
        }
    }

    @Test
    void nullFieldsBecomeEmptyStringsRatherThanNulls() {
        UploadedArtifact artifact = new UploadedArtifact(null, null, null, null, null, null, null, null, false);
        Map<String, String> map = artifact.toMap();
        // "verified" is a boolean and always renders as a literal, the rest degrade to "".
        assertThat(map.get("verified"), is("false"));
        map.remove("verified");
        for (Map.Entry<String, String> entry : map.entrySet()) {
            assertThat(entry.getKey() + " must not be null", entry.getValue(), is(""));
        }
    }

    @Test
    void replacingTheUrlAlsoUpdatesTheFileName() {
        String verifiedUrl = "https://nexus.example.com/repository/snapshots/com/example/my-app/1.0.0-SNAPSHOT/"
                + "my-app-1.0.0-20260915.104233-7.jar";
        UploadedArtifact replaced = artifact(false).withUrl(verifiedUrl, true);

        assertThat(replaced.getUrl(), is(verifiedUrl));
        assertThat(replaced.getFileName(), is("my-app-1.0.0-20260915.104233-7.jar"));
        assertTrue(replaced.isVerified());
        // The original is untouched.
        assertFalse(artifact(false).isVerified());
    }

    @Test
    void resultExposesUrlsAndFlattenedArtifacts() {
        NexusUploadResult result = new NexusUploadResult(
                true, "https://nexus.example.com/repository/releases", Collections.singletonList(artifact(true)));

        assertTrue(result.isSuccess());
        assertThat(result.getUrls(), contains(URL));
        List<Map<String, String>> list = result.toList();
        assertThat(list.size(), is(1));
        assertThat(list.get(0).get("url"), is(URL));
    }

    @Test
    void failureResultIsEmptyAndUnsuccessful() {
        NexusUploadResult result = NexusUploadResult.failure();

        assertFalse(result.isSuccess());
        assertThat(result.getArtifacts().size(), is(0));
        assertThat(result.getUrls().size(), is(0));
        assertThat(result.toList().size(), is(0));
    }
}
