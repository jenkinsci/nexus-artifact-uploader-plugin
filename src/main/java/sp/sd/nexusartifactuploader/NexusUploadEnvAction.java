package sp.sd.nexusartifactuploader;

import hudson.EnvVars;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exposes the uploaded artifact URLs as environment variables to later build steps.
 *
 * <p>Contributed variables:
 *
 * <ul>
 *   <li>{@code NEXUS_ARTIFACT_COUNT} — number of artifacts uploaded so far in this build
 *   <li>{@code NEXUS_ARTIFACT_URLS} — all URLs, comma separated
 *   <li>{@code NEXUS_ARTIFACT_URL_1} … {@code NEXUS_ARTIFACT_URL_n} — one variable per artifact
 *   <li>{@code NEXUS_ARTIFACT_URL} — the first URL, for the common single-artifact case
 * </ul>
 *
 * <p><strong>Scope, honestly stated:</strong> {@link hudson.model.EnvironmentContributingAction} is
 * consulted by {@link Run#getEnvironment}, which is what Freestyle build steps and post-build
 * actions use. Declarative and scripted Pipeline resolve {@code env} differently, so these
 * variables are <em>not</em> reliably visible as {@code env.NEXUS_ARTIFACT_URL} inside a Pipeline.
 * In Pipeline read the URLs from the build's {@link NexusUploadBuildAction} instead, for example
 * through the REST API. The variables are still useful in a Pipeline that shells out through a Freestyle-style
 * build step, or for plugins that read the build environment.
 *
 * <p>Several uploads in one build accumulate: each new upload rewrites the action's list with the
 * full set so the numbering stays stable and gapless.
 */
public class NexusUploadEnvAction extends InvisibleAction
        implements hudson.model.EnvironmentContributingAction, Serializable {

    private static final long serialVersionUID = 1L;

    public static final String COUNT_VAR = "NEXUS_ARTIFACT_COUNT";
    public static final String URLS_VAR = "NEXUS_ARTIFACT_URLS";
    public static final String URL_VAR = "NEXUS_ARTIFACT_URL";
    public static final String URL_VAR_PREFIX = "NEXUS_ARTIFACT_URL_";

    // ponytail: one lock for all builds; per-run locks if uploads ever contend across builds.
    private static final Object CONTRIBUTE_LOCK = new Object();

    private final List<String> urls;

    public NexusUploadEnvAction(List<String> urls) {
        this.urls = urls == null ? new ArrayList<>() : new ArrayList<>(urls);
    }

    public List<String> getUrls() {
        return Collections.unmodifiableList(urls);
    }

    /**
     * Adds the given URLs to the build's single {@link NexusUploadEnvAction}, creating it on first
     * use. Returns the action in effect.
     */
    public static NexusUploadEnvAction contribute(Run<?, ?> run, List<String> newUrls) {
        if (run == null) {
            return new NexusUploadEnvAction(newUrls);
        }
        // Parallel Pipeline branches upload concurrently; without the lock two of them could both
        // read the same action and one branch's URLs would be lost.
        synchronized (CONTRIBUTE_LOCK) {
            NexusUploadEnvAction existing = run.getAction(NexusUploadEnvAction.class);
            List<String> merged = new ArrayList<>();
            if (existing != null) {
                merged.addAll(existing.getUrls());
                run.removeAction(existing);
            }
            if (newUrls != null) {
                merged.addAll(newUrls);
            }
            NexusUploadEnvAction action = new NexusUploadEnvAction(merged);
            run.addAction(action);
            return action;
        }
    }

    @Override
    public void buildEnvironment(Run<?, ?> run, EnvVars env) {
        if (urls.isEmpty()) {
            return;
        }
        env.put(COUNT_VAR, Integer.toString(urls.size()));
        env.put(URLS_VAR, String.join(",", urls));
        env.put(URL_VAR, urls.get(0));
        for (int i = 0; i < urls.size(); i++) {
            env.put(URL_VAR_PREFIX + (i + 1), urls.get(i));
        }
    }
}
