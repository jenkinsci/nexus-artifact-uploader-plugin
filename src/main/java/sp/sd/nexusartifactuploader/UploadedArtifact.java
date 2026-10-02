package sp.sd.nexusartifactuploader;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * One artifact that was uploaded, together with the URL under which it is now downloadable.
 *
 * <p>Instances travel back from the agent to the controller over remoting and are persisted into
 * {@code build.xml} as part of {@link NexusUploadBuildAction}, so the class must stay serializable
 * and must not reference Aether or Jenkins types.
 *
 * <p>{@link #isVerified()} distinguishes a URL that was observed in the transfer itself (exact)
 * from one that was computed from the coordinates (a best guess, which is wrong for snapshots
 * whose published file name is timestamped by the server).
 */
@ExportedBean
public class UploadedArtifact implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final String type;
    private final String fileName;
    private final String repository;
    private final String url;
    private final boolean verified;

    public UploadedArtifact(
            String groupId,
            String artifactId,
            String version,
            String classifier,
            String type,
            String fileName,
            String repository,
            String url,
            boolean verified) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.type = type;
        this.fileName = fileName;
        this.repository = repository;
        this.url = url;
        this.verified = verified;
    }

    @Exported(visibility = 3)
    public String getGroupId() {
        return groupId;
    }

    @Exported(visibility = 3)
    public String getArtifactId() {
        return artifactId;
    }

    @Exported(visibility = 3)
    public String getVersion() {
        return version;
    }

    @Exported(visibility = 3)
    public String getClassifier() {
        return classifier;
    }

    @Exported(visibility = 3)
    public String getType() {
        return type;
    }

    /** The file name as published, which for snapshots includes the server-assigned timestamp. */
    @Exported(visibility = 3)
    public String getFileName() {
        return fileName;
    }

    @Exported(visibility = 3)
    public String getRepository() {
        return repository;
    }

    /** The URL the artifact can be downloaded from. */
    @Exported(visibility = 3)
    public String getUrl() {
        return url;
    }

    /** The URL to link to, or null if it is not http(s) and must be shown as plain text. */
    public String getLinkUrl() {
        return NexusUrlBuilder.isHttpUrl(url) ? url : null;
    }

    /**
     * True if the URL was observed during the transfer or confirmed through the Nexus search API;
     * false if it was derived from the coordinates and has not been confirmed.
     */
    @Exported(visibility = 3)
    public boolean isVerified() {
        return verified;
    }

    /** Returns a copy with a different URL and verification state, used when search confirms a URL. */
    public UploadedArtifact withUrl(String newUrl, boolean nowVerified) {
        String newFileName = fileName;
        if (newUrl != null) {
            int slash = newUrl.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < newUrl.length()) {
                newFileName = newUrl.substring(slash + 1);
            }
        }
        return new UploadedArtifact(
                groupId, artifactId, version, classifier, type, newFileName, repository, newUrl, nowVerified);
    }

    /**
     * Flattens the artifact into a map of plain strings.
     *
     * <p>This is the representation handed to Pipeline. Plain {@code String} values inside a
     * {@code LinkedHashMap} need no script-security whitelisting and survive CPS serialization,
     * unlike the getters of this class.
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("url", nullToEmpty(url));
        map.put("groupId", nullToEmpty(groupId));
        map.put("artifactId", nullToEmpty(artifactId));
        map.put("version", nullToEmpty(version));
        map.put("classifier", nullToEmpty(classifier));
        map.put("type", nullToEmpty(type));
        map.put("fileName", nullToEmpty(fileName));
        map.put("repository", nullToEmpty(repository));
        map.put("verified", Boolean.toString(verified));
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UploadedArtifact)) {
            return false;
        }
        UploadedArtifact other = (UploadedArtifact) o;
        return verified == other.verified
                && Objects.equals(groupId, other.groupId)
                && Objects.equals(artifactId, other.artifactId)
                && Objects.equals(version, other.version)
                && Objects.equals(classifier, other.classifier)
                && Objects.equals(type, other.type)
                && Objects.equals(fileName, other.fileName)
                && Objects.equals(repository, other.repository)
                && Objects.equals(url, other.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier, type, fileName, repository, url, verified);
    }

    @Override
    public String toString() {
        return url + (verified ? "" : " (unverified)");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
