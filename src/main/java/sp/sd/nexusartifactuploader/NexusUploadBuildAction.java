package sp.sd.nexusartifactuploader;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Run;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Attached to a build to record which artifacts it published and where.
 *
 * <p>The action is persisted in {@code build.xml}, so the URLs survive a Jenkins restart and remain
 * available to anything that reads the build later (REST API, other plugins, a downstream job).
 * Several uploads in one build produce several actions; {@link #getAll(Run)} collects them.
 *
 * <p>Rendering is split in two: {@code summary.jelly} adds a flat list of URLs to the build page,
 * and {@code index.jelly} backs the entry in the build's side bar with a table of the coordinates.
 * Both cover every upload of the build and are rendered by the {@link #isPrimary() first} action
 * only, so that several uploads still produce one side-bar entry and one list.
 */
@ExportedBean
public class NexusUploadBuildAction implements RunAction2, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Shipped with the plugin rather than referenced as a core {@code symbol-*} name, because the
     * set of bundled symbols varies between Jenkins versions and a name that is absent renders as
     * a broken image.
     */
    static final String ICON = "/plugin/nexus-artifact-uploader/images/nexus-artifacts.svg";

    private final List<UploadedArtifact> artifacts;
    private final String repositoryUrl;

    private transient Run<?, ?> run;

    public NexusUploadBuildAction(String repositoryUrl, List<UploadedArtifact> artifacts) {
        this.repositoryUrl = repositoryUrl;
        this.artifacts = artifacts == null ? new ArrayList<>() : new ArrayList<>(artifacts);
    }

    @Exported(visibility = 2)
    public List<UploadedArtifact> getArtifacts() {
        return Collections.unmodifiableList(artifacts);
    }

    @Exported(visibility = 2)
    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    /**
     * True for the first upload action of the build, which renders the views for all of them. Every
     * action shares the same {@link #getUrlName() URL name}, and Stapler resolves it to the first.
     */
    public boolean isPrimary() {
        List<NexusUploadBuildAction> all = getAll(run);
        return all.isEmpty() || all.get(0) == this;
    }

    /** The artifacts of every upload in the build, which is what the views show. */
    public List<UploadedArtifact> getBuildArtifacts() {
        return run == null ? getArtifacts() : getAllArtifacts(run);
    }

    @CheckForNull
    public Run<?, ?> getRun() {
        return run;
    }

    /** Every upload action of a build, in the order the uploads happened. */
    public static List<NexusUploadBuildAction> getAll(Run<?, ?> run) {
        return run == null
                ? Collections.<NexusUploadBuildAction>emptyList()
                : run.getActions(NexusUploadBuildAction.class);
    }

    /** All artifacts published by a build, across every upload step it ran. */
    public static List<UploadedArtifact> getAllArtifacts(Run<?, ?> run) {
        List<UploadedArtifact> all = new ArrayList<>();
        for (NexusUploadBuildAction action : getAll(run)) {
            all.addAll(action.getArtifacts());
        }
        return all;
    }

    @Override
    public String getIconFileName() {
        return isPrimary() && !getBuildArtifacts().isEmpty() ? ICON : null;
    }

    @Override
    public String getDisplayName() {
        return "Nexus artifacts";
    }

    @Override
    public String getUrlName() {
        return "nexus-artifacts";
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
    }
}
