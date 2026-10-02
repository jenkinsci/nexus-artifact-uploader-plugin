package sp.sd.nexusartifactuploader;

import hudson.model.TaskListener;
import java.io.Serializable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * Confirms download URLs through the Nexus 3 search API.
 *
 * <p>A Maven deploy tells us what we sent, not what the server stored under which name. For
 * releases those coincide; for snapshots the server rewrites the file name with a timestamp, and
 * only the server knows the final value. {@code GET /service/rest/v1/search/assets} answers the
 * question authoritatively by returning a {@code downloadUrl} per asset.
 *
 * <p><strong>This runs on the controller</strong>, after the upload has finished on the agent, and
 * uses {@code net.sf.json} from Jenkins core plus the JDK HTTP client, so it adds no dependency.
 * The consequence is a real constraint: if Nexus is only reachable from the agent network and not
 * from the controller, verification will fail. Failure is never fatal — the deterministic or
 * transfer-observed URL is kept and a warning is logged, because the upload itself has already
 * succeeded at that point.
 *
 * <p>The API only exists in Nexus 3. For {@code nexus2} this client is a no-op.
 */
public class NexusSearchClient implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Deliberately short: verification is a nicety, it must not stall a build. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final String protocol;
    private final String nexusUrl;
    private final String nexusVersion;
    private final String repository;
    private final String username;
    private final String password;

    public NexusSearchClient(
            String protocol,
            String nexusUrl,
            String nexusVersion,
            String repository,
            String username,
            String password) {
        this.protocol = protocol;
        this.nexusUrl = nexusUrl;
        this.nexusVersion = nexusVersion;
        this.repository = repository;
        this.username = username;
        this.password = password;
    }

    /** True if this Nexus flavour exposes the search API at all. */
    public boolean isSupported() {
        return nexusVersion != null && "nexus3".equals(nexusVersion.trim().toLowerCase(Locale.ENGLISH));
    }

    /**
     * Returns the artifacts with their URLs replaced by the ones the server reports.
     *
     * <p>Artifacts the server does not know about, or that cannot be queried, are returned
     * unchanged so that the caller always gets a complete list.
     */
    public List<UploadedArtifact> verify(List<UploadedArtifact> artifacts, TaskListener listener) {
        if (artifacts == null || artifacts.isEmpty()) {
            return Collections.emptyList();
        }
        if (!isSupported()) {
            listener.getLogger()
                    .println("[nexus-artifact-uploader] Skipping URL verification: the search API requires Nexus 3.");
            return artifacts;
        }
        List<UploadedArtifact> verified = new ArrayList<>(artifacts.size());
        for (UploadedArtifact artifact : artifacts) {
            verified.add(verifyOne(artifact, listener));
        }
        return verified;
    }

    private UploadedArtifact verifyOne(UploadedArtifact artifact, TaskListener listener) {
        try {
            String body = get(searchUri(artifact));
            List<String> urls = parseDownloadUrls(body);
            if (urls.isEmpty()) {
                listener.getLogger()
                        .println("[nexus-artifact-uploader] Search API returned no asset for "
                                + artifact.getArtifactId() + ":" + artifact.getVersion()
                                + "; keeping the computed URL.");
                return artifact;
            }
            // The last entry is the most recently published asset, which matters for snapshots
            // where several timestamped variants of the same base version coexist.
            String url = urls.get(urls.size() - 1);
            if (urls.size() > 1) {
                listener.getLogger()
                        .println("[nexus-artifact-uploader] Search API returned " + urls.size() + " assets for "
                                + artifact.getArtifactId() + ":" + artifact.getVersion() + "; using the last one.");
            }
            return artifact.withUrl(url, true);
        } catch (Exception e) {
            // Never fail the build for this: the artifact is already uploaded.
            listener.getLogger()
                    .println("[nexus-artifact-uploader] Could not verify the URL of " + artifact.getArtifactId()
                            + " through the search API (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                            + "); keeping the computed URL.");
            return artifact;
        }
    }

    /** Builds the search request for one artifact. Package-private so it can be unit tested. */
    URI searchUri(UploadedArtifact artifact) {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme()).append("://").append(host());
        sb.append("/service/rest/v1/search/assets");
        sb.append("?repository=").append(encode(repository));
        sb.append("&maven.groupId=").append(encode(artifact.getGroupId()));
        sb.append("&maven.artifactId=").append(encode(artifact.getArtifactId()));
        sb.append("&maven.baseVersion=").append(encode(artifact.getVersion()));
        if (artifact.getType() != null && !artifact.getType().trim().isEmpty()) {
            sb.append("&maven.extension=").append(encode(artifact.getType()));
        }
        if (artifact.getClassifier() != null && !artifact.getClassifier().trim().isEmpty()) {
            sb.append("&maven.classifier=").append(encode(artifact.getClassifier()));
        }
        sb.append("&sort=version&direction=asc");
        return URI.create(sb.toString());
    }

    /**
     * Extracts the {@code downloadUrl} of every item in a search response.
     *
     * <p>Package-private and free of I/O so that the response shape can be tested without a server.
     */
    static List<String> parseDownloadUrls(String body) {
        List<String> urls = new ArrayList<>();
        if (body == null || body.trim().isEmpty()) {
            return urls;
        }
        JSONObject root;
        try {
            root = JSONObject.fromObject(body);
        } catch (JSONException e) {
            // The exception message quotes the whole body, which must not end up in the build log.
            throw new IllegalArgumentException("response is not valid search JSON");
        }
        if (!root.containsKey("items")) {
            return urls;
        }
        JSONArray items = root.getJSONArray("items");
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item.containsKey("downloadUrl")) {
                String url = item.getString("downloadUrl");
                // Rendered as a link on the build page, so anything but http(s) is refused.
                if (NexusUrlBuilder.isHttpUrl(url)) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    private String get(URI uri) throws Exception {
        HttpClient.Builder clientBuilder =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER);
        HttpRequest.Builder requestBuilder =
                HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header("Accept", "application/json").GET();
        if (username != null && !username.isEmpty()) {
            // Preemptive basic auth: Nexus answers anonymous asset searches for public repositories
            // but returns an empty item list for private ones instead of a 401, which would look
            // like "artifact not found" rather than "not authorised".
            String token = Base64.getEncoder()
                    .encodeToString(
                            (username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
            requestBuilder.header("Authorization", "Basic " + token);
        }
        HttpClient client = clientBuilder.build();
        HttpResponse<String> response =
                client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new java.io.IOException("HTTP " + status + " from " + uri.getPath());
        }
        return response.body();
    }

    private String scheme() {
        String value = protocol == null ? "" : protocol.trim();
        return value.isEmpty() ? "http" : value;
    }

    private String host() {
        String value = nexusUrl == null ? "" : nexusUrl.trim();
        if (NexusUrlBuilder.isHttpUrl(value)) {
            value = value.substring(value.indexOf("://") + 3);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
