package sp.sd.nexusartifactuploader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.EnvVars;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class NexusUploadEnvActionTest {

    private static final String FIRST = "https://nexus.example.com/repository/releases/a/1.0/a-1.0.jar";
    private static final String SECOND = "https://nexus.example.com/repository/releases/b/1.0/b-1.0.zip";

    @Test
    void urlsAreExposedIndividuallyAndAsAList() {
        EnvVars env = new EnvVars();
        new NexusUploadEnvAction(Arrays.asList(FIRST, SECOND)).buildEnvironment(null, env);

        assertThat(env.get(NexusUploadEnvAction.COUNT_VAR), is("2"));
        assertThat(env.get(NexusUploadEnvAction.URLS_VAR), is(FIRST + "," + SECOND));
        assertThat(env.get(NexusUploadEnvAction.URL_VAR), is(FIRST));
        assertThat(env.get(NexusUploadEnvAction.URL_VAR_PREFIX + "1"), is(FIRST));
        assertThat(env.get(NexusUploadEnvAction.URL_VAR_PREFIX + "2"), is(SECOND));
    }

    @Test
    void noUrlsContributesNothing() {
        // An empty upload must not leave a misleading NEXUS_ARTIFACT_URL= behind.
        EnvVars env = new EnvVars();
        new NexusUploadEnvAction(Collections.<String>emptyList()).buildEnvironment(null, env);

        assertThat(env, not(hasKey(NexusUploadEnvAction.COUNT_VAR)));
        assertThat(env, not(hasKey(NexusUploadEnvAction.URL_VAR)));
    }

    @Test
    void nullUrlListIsTolerated() {
        EnvVars env = new EnvVars();
        new NexusUploadEnvAction(null).buildEnvironment(null, env);
        assertTrue(env.isEmpty());
    }

    @Test
    void actionIsInvisibleInTheUi() {
        NexusUploadEnvAction action = new NexusUploadEnvAction(Collections.singletonList(FIRST));
        assertThat(action.getIconFileName(), is((String) null));
        assertThat(action.getUrlName(), is((String) null));
    }
}
