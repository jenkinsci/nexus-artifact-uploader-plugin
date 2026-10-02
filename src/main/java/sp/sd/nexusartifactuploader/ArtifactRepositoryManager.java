package sp.sd.nexusartifactuploader;

import hudson.model.TaskListener;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.apache.maven.repository.internal.*;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.sonatype.aether.*;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory;
import org.sonatype.aether.connector.wagon.WagonProvider;
import org.sonatype.aether.connector.wagon.WagonRepositoryConnectorFactory;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.impl.ArtifactDescriptorReader;
import org.sonatype.aether.impl.MetadataGeneratorFactory;
import org.sonatype.aether.impl.VersionRangeResolver;
import org.sonatype.aether.impl.VersionResolver;
import org.sonatype.aether.impl.internal.DefaultServiceLocator;
import org.sonatype.aether.repository.*;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;

public class ArtifactRepositoryManager {

    private String url;
    private String username;
    private String password;
    private String repo;

    private static final String USER_HOME = System.getProperty("user.home");
    private static final File MAVEN_USER_HOME = new File(USER_HOME, ".m2");

    private RepositorySystem repositorySystem;
    private RepositorySystemSession session;

    /**
     * Kept as a field so that the URLs observed during the deploy can be read back after
     * {@link #upload(Artifact...)} returns.
     */
    private final sp.sd.nexusartifactuploader.TransferListener transferListener;

    public ArtifactRepositoryManager(String url, String username, String password, String repo, TaskListener Listener)
            throws SettingsBuildingException {
        this.url = url;
        this.username = username;
        this.password = password;
        this.repo = repo;
        // Created here rather than in upload() so that the URLs it observes can be read back
        // afterwards; this also removes the need to hold on to the TaskListener separately.
        this.transferListener = new sp.sd.nexusartifactuploader.TransferListener(Listener);
    }

    private RemoteRepository makeRemoteRepository() {
        return new RemoteRepository(this.repo, "default", this.url)
                .setAuthentication(new Authentication(this.username, this.password));
    }

    public void upload(Artifact... artifacts) throws Exception {
        RemoteRepository remoteRepository = makeRemoteRepository();
        DeployRequest deployRequest = new DeployRequest();
        for (Artifact artifact : artifacts) {
            deployRequest.addArtifact(artifact);
        }
        deployRequest.setRepository(remoteRepository);

        final SettingsBuildingRequest request =
                new DefaultSettingsBuildingRequest().setSystemProperties(System.getProperties());

        Settings settings =
                new DefaultSettingsBuilderFactory().newInstance().build(request).getEffectiveSettings();

        repositorySystem = new DefaultServiceLocator()
                .addService(RepositoryConnectorFactory.class, FileRepositoryConnectorFactory.class)
                .addService(RepositoryConnectorFactory.class, WagonRepositoryConnectorFactory.class)
                .addService(VersionResolver.class, DefaultVersionResolver.class)
                .addService(VersionRangeResolver.class, DefaultVersionRangeResolver.class)
                .addService(ArtifactDescriptorReader.class, DefaultArtifactDescriptorReader.class)
                .addService(MetadataGeneratorFactory.class, SnapshotMetadataGeneratorFactory.class)
                .setServices(WagonProvider.class, new ManualWagonProvider())
                .getService(RepositorySystem.class);

        String localRepository = settings.getLocalRepository();
        if (localRepository == null || localRepository.trim().isEmpty()) {
            localRepository = new File(MAVEN_USER_HOME, "repository").getAbsolutePath();
        }

        LocalRepository localRepositoryFromPath = new LocalRepository(localRepository);
        LocalRepositoryManager localRepositoryManager =
                repositorySystem.newLocalRepositoryManager(localRepositoryFromPath);

        session = new MavenRepositorySystemSession()
                .setLocalRepositoryManager(localRepositoryManager)
                .setRepositoryListener(new RepositoryListener())
                .setTransferListener(transferListener);
        repositorySystem.deploy(session, deployRequest);
    }

    /** Base URL of the target repository, as passed to the constructor. */
    public String getUrl() {
        return url;
    }

    /**
     * URLs of the artifacts that were actually transferred, in transfer order. Empty before
     * {@link #upload(Artifact...)} has run.
     */
    public List<String> getUploadedUrls() {
        return transferListener == null
                ? Collections.<String>emptyList()
                : transferListener.getUploadedUrls();
    }
}
