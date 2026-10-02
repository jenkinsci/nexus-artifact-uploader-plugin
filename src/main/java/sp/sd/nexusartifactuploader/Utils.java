package sp.sd.nexusartifactuploader;

import com.google.common.base.Strings;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.util.artifact.DefaultArtifact;

/**
 * Created by suresh on 5/20/2016.
 */
public final class Utils {
    private Utils() {}

    public static Artifact toArtifact(
            sp.sd.nexusartifactuploader.Artifact artifact, String groupId, String version, File artifactFile) {
        return new DefaultArtifact(
                        groupId, artifact.getArtifactId(), artifact.getClassifier(), artifact.getType(), version)
                .setFile(artifactFile);
    }

    /**
     * Uploads the artifacts and reports where they ended up.
     *
     * <p>Runs on whichever node holds the workspace, so the returned value crosses remoting and is
     * kept free of Jenkins and Aether types.
     *
     * <p>URL resolution has two tiers. The URLs observed by {@link TransferListener} during the
     * deploy are authoritative and are marked verified. If no observed URL matches an artifact -
     * which can happen if Aether reuses a transfer or if a future version stops emitting the event
     * - the URL is computed from the coordinates and marked unverified, so callers can tell a fact
     * from a guess instead of silently trusting a possibly wrong link.
     *
     * @return the outcome; never null. On an empty Nexus URL the result is
     *     {@link NexusUploadResult#failure()} rather than an exception, preserving the behaviour the
     *     boolean-returning API had.
     * @throws IOException if the deploy itself fails
     */
    public static NexusUploadResult uploadArtifactsWithResult(
            TaskListener Listener,
            String ResolvedNexusUser,
            String ResolvedNexusPassword,
            String ResolvedNexusUrl,
            String ResolvedRepository,
            String ResolvedProtocol,
            String ResolvedNexusVersion,
            Artifact... artifacts)
            throws IOException {
        if (Strings.isNullOrEmpty(ResolvedNexusUrl)) {
            Listener.getLogger().println("Url of the Nexus is empty. Please enter Nexus Url.");
            return NexusUploadResult.failure();
        }
        String repositoryBaseUrl = NexusUrlBuilder.repositoryBaseUrl(
                ResolvedProtocol, ResolvedNexusUrl, ResolvedNexusVersion, ResolvedRepository);
        try {
            for (Artifact artifact : artifacts) {
                Listener.getLogger()
                        .println("Uploading artifact " + artifact.getFile().getName() + " started....");
                Listener.getLogger().println("GroupId: " + artifact.getGroupId());
                Listener.getLogger().println("ArtifactId: " + artifact.getArtifactId());
                Listener.getLogger().println("Classifier: " + artifact.getClassifier());
                Listener.getLogger().println("Type: " + artifact.getExtension());
                Listener.getLogger().println("Version: " + artifact.getVersion());
                Listener.getLogger().println("File: " + artifact.getFile().getName());
                Listener.getLogger().println("Repository:" + ResolvedRepository);
            }
            ArtifactRepositoryManager artifactRepositoryManager = new ArtifactRepositoryManager(
                    repositoryBaseUrl, ResolvedNexusUser, ResolvedNexusPassword, ResolvedRepository, Listener);

            artifactRepositoryManager.upload(artifacts);
            for (Artifact artifact : artifacts) {
                Listener.getLogger()
                        .println("Uploading artifact " + artifact.getFile().getName() + " completed.");
            }

            List<UploadedArtifact> uploaded = describeUploads(
                    artifactRepositoryManager.getUploadedUrls(),
                    ResolvedRepository,
                    ResolvedProtocol,
                    ResolvedNexusUrl,
                    ResolvedNexusVersion,
                    artifacts);
            logUrls(Listener, uploaded);
            return new NexusUploadResult(true, repositoryBaseUrl, uploaded);
        } catch (Exception e) {
            Listener.getLogger().println(e.getMessage());
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Pairs each requested artifact with the URL it is now available at.
     *
     * <p>Package-private so the matching logic can be unit tested without a deploy.
     */
    static List<UploadedArtifact> describeUploads(
            List<String> observedUrls,
            String repository,
            String protocol,
            String nexusUrl,
            String nexusVersion,
            Artifact... artifacts) {
        List<UploadedArtifact> uploaded = new ArrayList<>();
        if (artifacts == null) {
            return uploaded;
        }
        List<String> remaining = observedUrls == null ? new ArrayList<>() : new ArrayList<>(observedUrls);
        for (Artifact artifact : artifacts) {
            String matched = null;
            for (String candidate : remaining) {
                if (NexusUrlBuilder.matches(
                        candidate,
                        artifact.getArtifactId(),
                        artifact.getVersion(),
                        artifact.getClassifier(),
                        artifact.getExtension())) {
                    matched = candidate;
                    break;
                }
            }
            boolean verified = matched != null;
            if (verified) {
                // Consume the match so two artifacts cannot claim the same URL.
                remaining.remove(matched);
            }
            String url = verified
                    ? matched
                    : NexusUrlBuilder.downloadUrl(
                            protocol,
                            nexusUrl,
                            nexusVersion,
                            repository,
                            artifact.getGroupId(),
                            artifact.getArtifactId(),
                            artifact.getVersion(),
                            artifact.getClassifier(),
                            artifact.getExtension());
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            uploaded.add(new UploadedArtifact(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getVersion(),
                    artifact.getClassifier(),
                    artifact.getExtension(),
                    fileName,
                    repository,
                    url,
                    verified));
        }
        return uploaded;
    }

    private static void logUrls(TaskListener listener, List<UploadedArtifact> uploaded) {
        for (UploadedArtifact artifact : uploaded) {
            if (artifact.isVerified()) {
                listener.getLogger().println("Artifact available at: " + artifact.getUrl());
            } else {
                listener.getLogger()
                        .println("Artifact expected at (not confirmed by the transfer): " + artifact.getUrl());
            }
        }
    }

    /**
     * @deprecated use {@link #uploadArtifactsWithResult} instead, which reports the URLs the
     *     artifacts are now available at. Kept so that existing callers, including Job DSL scripts
     *     and other plugins, keep compiling and behaving as before.
     */
    @Deprecated
    public static Boolean uploadArtifacts(
            TaskListener Listener,
            String ResolvedNexusUser,
            String ResolvedNexusPassword,
            String ResolvedNexusUrl,
            String ResolvedRepository,
            String ResolvedProtocol,
            String ResolvedNexusVersion,
            Artifact... artifacts)
            throws IOException {
        return uploadArtifactsWithResult(
                        Listener,
                        ResolvedNexusUser,
                        ResolvedNexusPassword,
                        ResolvedNexusUrl,
                        ResolvedRepository,
                        ResolvedProtocol,
                        ResolvedNexusVersion,
                        artifacts)
                .isSuccess();
    }
}
