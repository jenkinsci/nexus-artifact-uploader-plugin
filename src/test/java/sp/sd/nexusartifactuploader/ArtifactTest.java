package sp.sd.nexusartifactuploader;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class ArtifactTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    @WithoutJenkins
    public void testDefaults() {
        Artifact artifact = new Artifact("nexus-artifact-uploader", "jpi", "debug",
                "target/nexus-artifact-uploader.jpi", "pom.xml");
        assertEquals("nexus-artifact-uploader", artifact.getArtifactId());
        assertEquals("jpi", artifact.getType());
        assertEquals("debug", artifact.getClassifier());
        assertEquals("target/nexus-artifact-uploader.jpi", artifact.getFile());
    }

    @Test
    @WithoutJenkins
    public void testFileNameTrimming() {
        Artifact artifact = new Artifact("nexus-artifact-uploader", "jpi", "debug",
                "target/nexus-artifact-uploader.jpi ", "pom.xml");
        assertEquals("target/nexus-artifact-uploader.jpi", artifact.getFile());
    }
}