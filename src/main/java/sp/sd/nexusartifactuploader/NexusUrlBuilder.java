package sp.sd.nexusartifactuploader;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds the URLs under which artifacts are published, following the Maven 2 repository layout.
 *
 * <p>This class deliberately has no Jenkins or Aether dependency so that it can be unit tested
 * without a Jenkins instance. It is the <em>deterministic</em> half of URL resolution: it computes
 * where an artifact <em>should</em> end up. For release versions that is exact. For SNAPSHOT
 * versions Maven rewrites the file name with a timestamp and build number, so the deterministic
 * URL is only a base-version approximation and must be confirmed against what was actually
 * transferred (see {@link TransferListener#getUploadedUrls()}) or against the Nexus search API
 * (see {@link NexusSearchClient}).
 */
public final class NexusUrlBuilder {

    /** Resources that Maven uploads alongside artifacts but that are not artifacts themselves. */
    private static final Pattern NON_ARTIFACT_RESOURCE = Pattern.compile(
            ".*(\\.sha1|\\.sha256|\\.sha512|\\.md5|\\.asc|maven-metadata\\.xml)$", Pattern.CASE_INSENSITIVE);

    private static final String NEXUS3_REPOSITORY_PATH = "/repository/";
    private static final String NEXUS2_REPOSITORY_PATH = "/content/repositories/";

    private NexusUrlBuilder() {}

    /**
     * Returns the repository path segment for the given Nexus major version.
     *
     * @param nexusVersion {@code nexus2} or {@code nexus3}; anything else is treated as Nexus 2,
     *     which is what {@link Utils} has always done.
     */
    public static String repositoryPath(String nexusVersion) {
        if (nexusVersion != null && "nexus3".equals(nexusVersion.trim().toLowerCase(Locale.ENGLISH))) {
            return NEXUS3_REPOSITORY_PATH;
        }
        return NEXUS2_REPOSITORY_PATH;
    }

    /**
     * Builds the base URL of the repository, e.g. {@code https://nexus.example.com/repository/releases}.
     *
     * <p>The returned URL never ends with a slash. The {@code nexusUrl} argument is expected to be a
     * bare host (optionally with port and context path) as the plugin's form validation enforces,
     * but a leading scheme is tolerated and stripped rather than producing a corrupt URL.
     */
    public static String repositoryBaseUrl(String protocol, String nexusUrl, String nexusVersion, String repository) {
        String host = stripScheme(nullToEmpty(nexusUrl).trim());
        host = stripTrailingSlashes(host);
        String repo = stripSlashes(nullToEmpty(repository).trim());
        String scheme = nullToEmpty(protocol).trim();
        if (scheme.isEmpty()) {
            scheme = "http";
        }
        return scheme + "://" + host + repositoryPath(nexusVersion) + repo;
    }

    /**
     * Builds the path of an artifact relative to the repository root, without a leading slash.
     *
     * @param classifier may be null or empty
     */
    public static String artifactPath(
            String groupId, String artifactId, String version, String classifier, String extension) {
        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(groupId).trim().replace('.', '/'));
        sb.append('/').append(nullToEmpty(artifactId).trim());
        sb.append('/').append(nullToEmpty(version).trim());
        sb.append('/').append(fileName(artifactId, version, classifier, extension));
        return stripLeadingSlashes(sb.toString());
    }

    /** Builds the expected file name of an artifact, e.g. {@code my-app-1.0.0-linux.zip}. */
    public static String fileName(String artifactId, String version, String classifier, String extension) {
        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(artifactId).trim());
        sb.append('-').append(nullToEmpty(version).trim());
        String cls = nullToEmpty(classifier).trim();
        if (!cls.isEmpty()) {
            sb.append('-').append(cls);
        }
        String ext = nullToEmpty(extension).trim();
        if (!ext.isEmpty()) {
            sb.append('.').append(ext);
        }
        return sb.toString();
    }

    /**
     * Builds the full download URL of an artifact.
     *
     * <p>For SNAPSHOT versions the result points at the base-version directory and carries the
     * base-version file name, which normally does <em>not</em> exist on the server: Maven publishes
     * {@code my-app-1.0.0-20260915.104233-7.jar} instead. Treat the value as unverified in that case.
     */
    public static String downloadUrl(
            String protocol,
            String nexusUrl,
            String nexusVersion,
            String repository,
            String groupId,
            String artifactId,
            String version,
            String classifier,
            String extension) {
        return repositoryBaseUrl(protocol, nexusUrl, nexusVersion, repository) + "/"
                + artifactPath(groupId, artifactId, version, classifier, extension);
    }

    /** True if the version denotes a Maven snapshot, whose published file name is timestamped. */
    public static boolean isSnapshot(String version) {
        return nullToEmpty(version).trim().toUpperCase(Locale.ENGLISH).endsWith("-SNAPSHOT");
    }

    /**
     * True if the resource looks like a real artifact rather than a checksum, signature or metadata
     * file that Maven transfers as a side effect of a deploy.
     */
    public static boolean isArtifactResource(String resourceName) {
        String name = nullToEmpty(resourceName).trim();
        if (name.isEmpty()) {
            return false;
        }
        return !NON_ARTIFACT_RESOURCE.matcher(name).matches();
    }

    /**
     * Matches a URL that was actually transferred against the coordinates of a requested artifact.
     *
     * <p>Release artifacts match on the exact file name. Snapshot artifacts match on the
     * {@code artifactId-} prefix, the optional classifier and the extension, because the published
     * file name contains a timestamp that is only known to the server.
     */
    public static boolean matches(String url, String artifactId, String version, String classifier, String extension) {
        String candidate = nullToEmpty(url);
        int slash = candidate.lastIndexOf('/');
        String name = slash >= 0 ? candidate.substring(slash + 1) : candidate;
        if (name.isEmpty()) {
            return false;
        }
        String expected = fileName(artifactId, version, classifier, extension);
        if (name.equals(expected)) {
            return true;
        }
        if (!isSnapshot(version)) {
            return false;
        }
        String id = nullToEmpty(artifactId).trim();
        String ext = nullToEmpty(extension).trim();
        String cls = nullToEmpty(classifier).trim();
        if (!name.startsWith(id + "-")) {
            return false;
        }
        if (!ext.isEmpty() && !name.endsWith("." + ext)) {
            return false;
        }
        String stem = ext.isEmpty() ? name : name.substring(0, name.length() - ext.length() - 1);
        if (cls.isEmpty()) {
            // A classifier-less artifact must not accidentally match a classified one.
            return !hasClassifierSuffix(stem, id);
        }
        return stem.endsWith("-" + cls);
    }

    /**
     * Heuristic used by {@link #matches}: after stripping {@code artifactId-} and a timestamped
     * version of the form {@code yyyyMMdd.HHmmss-buildNumber}, anything left is a classifier.
     */
    private static boolean hasClassifierSuffix(String stem, String artifactId) {
        String rest = stem.substring(Math.min(stem.length(), artifactId.length() + 1));
        // A timestamped snapshot version looks like 1.0.0-20260915.104233-7 (three dash-separated
        // groups at most once the base version is included), a classified one adds a further group.
        return rest.matches(".*\\d{8}\\.\\d{6}-\\d+-.+");
    }

    /** True for an http or https URL; anything else must not be rendered as a link. */
    static boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ENGLISH);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String stripScheme(String value) {
        if (isHttpUrl(value)) {
            int idx = value.indexOf("://");
            return value.substring(idx + 3);
        }
        return value;
    }

    private static String stripSlashes(String value) {
        return stripTrailingSlashes(stripLeadingSlashes(value));
    }

    private static String stripLeadingSlashes(String value) {
        int start = 0;
        while (start < value.length() && value.charAt(start) == '/') {
            start++;
        }
        return value.substring(start);
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
