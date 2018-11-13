package sp.sd.nexusartifactuploader;

import com.google.common.base.Strings;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;


/**
 * Created by suresh on 5/20/2016.
 */
public final class Utils {
    private Utils() {
    }

    public static Boolean uploadArtifact(File artifactFile, TaskListener Listener, String ResolvedNexusUser,
                                         String ResolvedNexusPassword, String ResolvedNexusUrl,
                                         String ResolvedGroupId, String ResolvedArtifactId, String ResolvedVersion,
                                         String ResolvedRepository, String ResolvedType,
                                         String ResolvedClassifier, String ResolvedProtocol,
                                         String ResolvedNexusVersion, File pomFile) throws IOException {
        Boolean result = false;
        if (Strings.isNullOrEmpty(ResolvedNexusUrl)) {
            Listener.getLogger().println("Url of the Nexus is empty. Please enter Nexus Url.");
            return false;
        }
        try {
            Listener.getLogger().println("Uploading artifact " + artifactFile.getName() + " started....");
            Listener.getLogger().println("GroupId: " + ResolvedGroupId);
            Listener.getLogger().println("ArtifactId: " + ResolvedArtifactId);
            Listener.getLogger().println("Classifier: " + ResolvedClassifier);
            Listener.getLogger().println("Type: " + ResolvedType);
            Listener.getLogger().println("Version: " + ResolvedVersion);
            Listener.getLogger().println("File: " + artifactFile.getName());
            if (pomFile != null) {
                Listener.getLogger().println("Pom file: " + pomFile.getName());
            }
            Listener.getLogger().println("Repository:" + ResolvedRepository);
            String repositoryPath = "/content/repositories/";
            if (ResolvedNexusVersion.contentEquals("nexus3")) {
                repositoryPath = "/repository/";
            }
            ArtifactRepositoryManager artifactRepositoryManager = new ArtifactRepositoryManager(ResolvedProtocol + "://"
                    + ResolvedNexusUrl + repositoryPath + ResolvedRepository, ResolvedNexusUser,
                    ResolvedNexusPassword, ResolvedRepository, Listener);

            artifactRepositoryManager.upload(ResolvedGroupId, ResolvedArtifactId, ResolvedVersion, artifactFile, ResolvedType, ResolvedClassifier, pomFile);

            Listener.getLogger().println("Uploading artifact " + artifactFile.getName() + " completed.");
            result = true;
        } catch (Exception e) {
            Listener.getLogger().println(e.getMessage());
        }
        return result;
    }
}
