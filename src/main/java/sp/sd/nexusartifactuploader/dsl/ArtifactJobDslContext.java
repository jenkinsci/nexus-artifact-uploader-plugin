package sp.sd.nexusartifactuploader.dsl;

import javaposse.jobdsl.dsl.Context;

/**
 * Created by suresh on 10/23/2016.
 */
public class ArtifactJobDslContext implements Context {

    String artifactId;
    String type;
    String classifier;
    String file;
    String pomFile;

    void artifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    void type(String type) {
        this.type = type;
    }

    void classifier(String classifier) {
        this.classifier = classifier;
    }

    void file(String file) {
        this.file = file;
    }

    void pomFile(String pomFile) {
        this.pomFile = pomFile;
    }
}
