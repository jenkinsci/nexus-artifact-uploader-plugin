package sp.sd.nexusartifactuploader;

import com.google.common.base.Strings;
import hudson.model.TaskListener;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.File;
import java.io.IOException;


/**
 * Created by suresh on 5/20/2016.
 */
public final class Utils {
    private Utils() {
    }

    public static Artifact toArtifact(sp.sd.nexusartifactuploader.Artifact artifact, String groupId, String version, File artifactFile) {
        return new DefaultArtifact(groupId, artifact.getArtifactId(), artifact.getClassifier(),
                artifact.getType(), version).setFile(artifactFile);
    }

    public static Boolean uploadArtifacts(TaskListener Listener, String ResolvedNexusUser,
                                         String ResolvedNexusPassword, String ResolvedNexusUrl,
                                         String ResolvedRepository, String ResolvedProtocol,
                                         String ResolvedNexusVersion, Artifact... artifacts) {
        Boolean result = false;
        if (Strings.isNullOrEmpty(ResolvedNexusUrl)) {
            Listener.getLogger().println("Url of the Nexus is empty. Please enter Nexus Url.");
            return false;
        }
        try {
            for (Artifact artifact : artifacts) {
                Listener.getLogger().println("Uploading artifact " + artifact.getFile().getName() + " started....");
                Listener.getLogger().println("GroupId: " + artifact.getGroupId());
                Listener.getLogger().println("ArtifactId: " + artifact.getArtifactId());
                Listener.getLogger().println("Classifier: " + artifact.getClassifier());
                Listener.getLogger().println("Type: " + artifact.getExtension());
                Listener.getLogger().println("Version: " + artifact.getVersion());
                Listener.getLogger().println("File: " + artifact.getFile().getName());
                Listener.getLogger().println("Repository:" + ResolvedRepository);
            }
            String repositoryPath = "/content/repositories/";
            if (ResolvedNexusVersion.contentEquals("nexus3")) {
                repositoryPath = "/repository/";
            }
            ArtifactRepositoryManager artifactRepositoryManager = new ArtifactRepositoryManager(ResolvedProtocol + "://"
                    + ResolvedNexusUrl + repositoryPath + ResolvedRepository, ResolvedNexusUser,
                    ResolvedNexusPassword, ResolvedRepository, Listener);


            artifactRepositoryManager.upload(artifacts);
            for (Artifact artifact : artifacts) {
                Listener.getLogger().println("Uploading artifact " + artifact.getFile().getName() + " completed.");
            }
            result = true;
        } catch (Exception e) {
            Listener.getLogger().println(e.getMessage());
        }
        return result;
    }
}
