package sp.sd.nexusartifactuploader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the request building and response parsing of the search client without touching the
 * network, which is the only part of it that can be tested deterministically.
 */
class NexusSearchClientTest {

    private static NexusSearchClient client(String nexusVersion) {
        return new NexusSearchClient("https", "nexus.example.com", nexusVersion, "releases", "userName", "password");
    }

    private static UploadedArtifact artifact(String classifier, String type) {
        return new UploadedArtifact(
                "com.example", "my-app", "1.0.0-SNAPSHOT", classifier, type, "my-app.jar", "releases", "url", false);
    }

    @Test
    void searchApiIsNexus3Only() {
        assertTrue(client("nexus3").isSupported());
        assertFalse(client("nexus2").isSupported());
        assertFalse(client(null).isSupported());
    }

    @Test
    void requestCarriesTheMavenCoordinates() {
        String uri = client("nexus3").searchUri(artifact("linux", "zip")).toString();

        assertThat(uri, containsString("https://nexus.example.com/service/rest/v1/search/assets"));
        assertThat(uri, containsString("repository=releases"));
        assertThat(uri, containsString("maven.groupId=com.example"));
        assertThat(uri, containsString("maven.artifactId=my-app"));
        // baseVersion, not version: for snapshots this is the coordinate the server indexes on.
        assertThat(uri, containsString("maven.baseVersion=1.0.0-SNAPSHOT"));
        assertThat(uri, containsString("maven.extension=zip"));
        assertThat(uri, containsString("maven.classifier=linux"));
    }

    @Test
    void emptyClassifierIsOmittedFromTheQuery() {
        // Sending maven.classifier= would filter for assets whose classifier is the empty string on
        // some Nexus versions and for nothing at all on others.
        String uri = client("nexus3").searchUri(artifact("", "jar")).toString();
        assertThat(uri, not(containsString("maven.classifier")));
    }

    @Test
    void downloadUrlsAreExtractedInOrder() {
        String body = "{\"items\":["
                + "{\"downloadUrl\":\"https://nexus.example.com/repository/snapshots/a-1.jar\",\"path\":\"/a-1.jar\"},"
                + "{\"downloadUrl\":\"https://nexus.example.com/repository/snapshots/a-2.jar\",\"path\":\"/a-2.jar\"}"
                + "],\"continuationToken\":null}";

        List<String> urls = NexusSearchClient.parseDownloadUrls(body);

        assertThat(
                urls,
                contains(
                        "https://nexus.example.com/repository/snapshots/a-1.jar",
                        "https://nexus.example.com/repository/snapshots/a-2.jar"));
    }

    @Test
    void emptyResultSetYieldsNoUrls() {
        assertThat(NexusSearchClient.parseDownloadUrls("{\"items\":[],\"continuationToken\":null}"), hasSize(0));
    }

    @Test
    void missingItemsFieldYieldsNoUrls() {
        assertThat(NexusSearchClient.parseDownloadUrls("{}"), hasSize(0));
    }

    @Test
    void blankBodyYieldsNoUrls() {
        assertThat(NexusSearchClient.parseDownloadUrls(null), hasSize(0));
        assertThat(NexusSearchClient.parseDownloadUrls("   "), hasSize(0));
    }

    @Test
    void itemWithoutDownloadUrlIsSkipped() {
        // Observed in the wild: some formats return assets without a downloadUrl field.
        String body = "{\"items\":[{\"path\":\"/a-1.jar\"},"
                + "{\"downloadUrl\":\"https://nexus.example.com/repository/snapshots/a-2.jar\"}]}";

        List<String> urls = NexusSearchClient.parseDownloadUrls(body);

        assertThat(urls, hasSize(1));
        assertThat(urls.get(0), is("https://nexus.example.com/repository/snapshots/a-2.jar"));
    }

    @Test
    void nonHttpDownloadUrlIsRefused() {
        // The URL ends up as a link on the build page, so a javascript: URL would be an XSS.
        String body = "{\"items\":[{\"downloadUrl\":\"https://nexus.example.com/repository/snapshots/a-1.jar\"},"
                + "{\"downloadUrl\":\"javascript:alert(1)\"}]}";

        assertThat(
                NexusSearchClient.parseDownloadUrls(body),
                contains("https://nexus.example.com/repository/snapshots/a-1.jar"));
    }

    @Test
    void invalidResponseIsRejectedWithoutQuotingIt() {
        // Whatever the host answered must not reach the build log through the exception message.
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> NexusSearchClient.parseDownloadUrls("<html>internal</html>"));

        assertThat(e.getMessage(), not(containsString("internal")));
    }

    @Test
    void nexus2ShortCircuitsVerificationAndKeepsTheInput() {
        List<UploadedArtifact> input = List.of(artifact("", "jar"));
        List<UploadedArtifact> result = client("nexus2").verify(input, hudson.model.TaskListener.NULL);
        assertThat(result, is(input));
    }
}
