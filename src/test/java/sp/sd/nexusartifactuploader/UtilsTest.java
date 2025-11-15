/*
 * The MIT License
 *
 * Copyright 2025 Mark Waite.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package sp.sd.nexusartifactuploader;

import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import org.junit.Test;
import org.sonatype.aether.artifact.Artifact;

public class UtilsTest {

    public UtilsTest() {
    }

    private final String artifactId = "my-id";
    private final String classifier = "test";
    private final String artifactType = "a-type";
    private final String fileName = "my-file";
    private final sp.sd.nexusartifactuploader.Artifact artifact = new sp.sd.nexusartifactuploader.Artifact(artifactId, artifactType, classifier, fileName);
    private final String groupId = "my-groupId";
    private final String version = "0.0.1";
    private final File artifactFile = new File(".");

    @Test
    public void testToArtifact() {
        Artifact result = Utils.toArtifact(artifact, groupId, version, artifactFile);
        assertThat(result.getArtifactId(), is(artifactId));
        assertThat(result.getClassifier(), is(classifier));
        assertThat(result.getVersion(), is(version));
        assertThat(result.getFile(), is(artifactFile));
        assertThat(result.getProperties().entrySet(), hasSize(0));
    }

    @Test
    public void testUploadArtifactsEmptyURL() throws Exception {
        TaskListener Listener = TaskListener.NULL;
        String resolvedNexusUrl = "";
        Artifact[] artifacts = null;
        assertFalse(Utils.uploadArtifacts(Listener, "", "", "", resolvedNexusUrl, "", "", artifacts));
    }

    private final TaskListener listener = TaskListener.NULL;
    private final String username = "userName";
    private final String password = "password";
    private final String hostname = "nexus.example.com";
    private final String repository = "my-repository";
    private final String protocol = "https";

    @Test
    public void testUploadArtifactsNexus2Throws() throws Exception {
        String nexusVersion = "nexus2";
        Artifact[] artifacts = {Utils.toArtifact(artifact, groupId, version, artifactFile),};
        IOException e = assertThrows(IOException.class, () -> {
            Utils.uploadArtifacts(listener, username, password, hostname, repository, protocol, nexusVersion, artifacts);
        });
        assertThat(e.getMessage(), containsString("Failed to deploy artifacts: "
                + "Could not transfer artifact my-groupId:my-id:a-type:test:0.0.1 "
                + "from/to my-repository (https://nexus.example.com/content/repositories/my-repository): "
                + "transfer failed for https://nexus.example.com/content/repositories/my-repository/my-groupId/my-id/0.0.1/my-id-0.0.1-test.a-type"));
    }

    @Test
    public void testUploadArtifactsNexus3Throws() throws Exception {
        String nexusVersion = "nexus3";
        Artifact[] artifacts = {Utils.toArtifact(artifact, groupId, version, artifactFile),};
        IOException e = assertThrows(IOException.class, () -> {
            Utils.uploadArtifacts(listener, username, password, hostname, repository, protocol, nexusVersion, artifacts);
        });
        assertThat(e.getMessage(), containsString("Failed to deploy artifacts: "
                + "Could not transfer artifact my-groupId:my-id:a-type:test:0.0.1 "
                + "from/to my-repository (https://nexus.example.com/repository/my-repository): "
                + "transfer failed for https://nexus.example.com/repository/my-repository/my-groupId/my-id/0.0.1/my-id-0.0.1-test.a-type"));
    }
}
