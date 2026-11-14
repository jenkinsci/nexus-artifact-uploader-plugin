package sp.sd.nexusartifactuploader;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Outcome of one {@code nexusArtifactUploader} invocation.
 *
 * <p>Crosses the remoting boundary from the agent back to the controller, so it is serializable and
 * free of Jenkins and Aether types.
 */
public class NexusUploadResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String repositoryUrl;
    private final List<UploadedArtifact> artifacts;

    public NexusUploadResult(boolean success, String repositoryUrl, List<UploadedArtifact> artifacts) {
        this.success = success;
        this.repositoryUrl = repositoryUrl;
        this.artifacts = artifacts == null
                ? Collections.<UploadedArtifact>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(artifacts));
    }

    /** An empty, failed result, used when the upload could not even be attempted. */
    public static NexusUploadResult failure() {
        return new NexusUploadResult(false, null, Collections.<UploadedArtifact>emptyList());
    }

    public boolean isSuccess() {
        return success;
    }

    /** Base URL of the target repository, without a trailing slash; null if unknown. */
    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public List<UploadedArtifact> getArtifacts() {
        return artifacts;
    }

    /** The download URLs, in upload order. */
    public List<String> getUrls() {
        List<String> urls = new ArrayList<>(artifacts.size());
        for (UploadedArtifact artifact : artifacts) {
            urls.add(artifact.getUrl());
        }
        return urls;
    }

    /**
     * The representation returned to Pipeline: a list of maps of plain strings, which is both
     * CPS-serializable and usable from a sandboxed script without whitelisting.
     */
    public List<Map<String, String>> toList() {
        List<Map<String, String>> list = new ArrayList<>(artifacts.size());
        for (UploadedArtifact artifact : artifacts) {
            list.add(artifact.toMap());
        }
        return list;
    }

    /** Replaces the artifact list, used after the search API has confirmed the URLs. */
    public NexusUploadResult withArtifacts(List<UploadedArtifact> replacement) {
        return new NexusUploadResult(success, repositoryUrl, replacement);
    }
}
