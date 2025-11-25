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
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.sonatype.aether.artifact.Artifact;

class UtilsTest {

    private static final String ARTIFACT_ID = "my-id";
    private static final String CLASSIFIER = "test";
    private static final String ARTIFACT_TYPE = "a-type";
    private static final String FILE_NAME = "my-file";
    private static final sp.sd.nexusartifactuploader.Artifact ARTIFACT = new sp.sd.nexusartifactuploader.Artifact(ARTIFACT_ID, ARTIFACT_TYPE, CLASSIFIER, FILE_NAME);
    private static final String GROUP_ID = "my-groupId";
    private static final String VERSION = "0.0.1";
    private static final File ARTIFACT_FILE = new File(".");

    private static final TaskListener TASK_LISTENER = TaskListener.NULL;
    private static final String USER_NAME = "userName";
    private static final String PASSWORD = "password";
    private static final String HOSTNAME = "nexus.example.com";
    private static final String REPOSITORY = "my-repository";
    private static final String PROTOCOL = "https";

    @Test
    void testToArtifact() {
        Artifact result = Utils.toArtifact(ARTIFACT, GROUP_ID, VERSION, ARTIFACT_FILE);
        assertThat(result.getArtifactId(), is(ARTIFACT_ID));
        assertThat(result.getClassifier(), is(CLASSIFIER));
        assertThat(result.getVersion(), is(VERSION));
        assertThat(result.getFile(), is(ARTIFACT_FILE));
        assertThat(result.getProperties().entrySet(), hasSize(0));
    }

    @Test
    void testUploadArtifactsEmptyURL() throws Exception {
        String resolvedNexusUrl = "";
        Artifact[] artifacts = null;
        assertFalse(Utils.uploadArtifacts(TASK_LISTENER, "", "", "", resolvedNexusUrl, "", "", artifacts));
    }

    @Test
    void testUploadArtifactsNexus2Throws() {
        String nexusVersion = "nexus2";
        Artifact[] artifacts = { Utils.toArtifact(ARTIFACT, GROUP_ID, VERSION, ARTIFACT_FILE) };
        IOException e = assertThrows(IOException.class, () ->
            Utils.uploadArtifacts(TASK_LISTENER, USER_NAME, PASSWORD, HOSTNAME, REPOSITORY, PROTOCOL, nexusVersion, artifacts));
        assertThat(e.getMessage(), containsString("Failed to deploy artifacts: "
                + "Could not transfer artifact my-groupId:my-id:a-type:test:0.0.1 "
                + "from/to my-repository (https://nexus.example.com/content/repositories/my-repository): "
                + "transfer failed for https://nexus.example.com/content/repositories/my-repository/my-groupId/my-id/0.0.1/my-id-0.0.1-test.a-type"));
    }

    @Test
    void testUploadArtifactsNexus3Throws() {
        String nexusVersion = "nexus3";
        Artifact[] artifacts = { Utils.toArtifact(ARTIFACT, GROUP_ID, VERSION, ARTIFACT_FILE) };
        IOException e = assertThrows(IOException.class, () ->
            Utils.uploadArtifacts(TASK_LISTENER, USER_NAME, PASSWORD, HOSTNAME, REPOSITORY, PROTOCOL, nexusVersion, artifacts));
        assertThat(e.getMessage(), containsString("Failed to deploy artifacts: "
                + "Could not transfer artifact my-groupId:my-id:a-type:test:0.0.1 "
                + "from/to my-repository (https://nexus.example.com/repository/my-repository): "
                + "transfer failed for https://nexus.example.com/repository/my-repository/my-groupId/my-id/0.0.1/my-id-0.0.1-test.a-type"));
    }
}
