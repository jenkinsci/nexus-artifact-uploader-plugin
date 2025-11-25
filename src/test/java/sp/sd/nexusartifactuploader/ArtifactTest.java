package sp.sd.nexusartifactuploader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtifactTest {

    @Test
    void testDefaults() {
        Artifact artifact = new Artifact("nexus-artifact-uploader", "jpi", "debug",
                "target/nexus-artifact-uploader.jpi");
        assertEquals("nexus-artifact-uploader", artifact.getArtifactId());
        assertEquals("jpi", artifact.getType());
        assertEquals("debug", artifact.getClassifier());
        assertEquals("target/nexus-artifact-uploader.jpi", artifact.getFile());
    }

    @Test
    void testFileNameTrimming() {
        Artifact artifact = new Artifact("nexus-artifact-uploader", "jpi", "debug",
                "target/nexus-artifact-uploader.jpi ");
        assertEquals("target/nexus-artifact-uploader.jpi", artifact.getFile());
    }
}