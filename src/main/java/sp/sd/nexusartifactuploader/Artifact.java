package sp.sd.nexusartifactuploader;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;

/**
 * Created by suresh on 10/15/2016.
 */
public class Artifact extends AbstractDescribableImpl<Artifact> implements Serializable {

    private static final long serialVersionUID = 1905162041950251407L;
    
    private final String artifactId;
    private final String type;
    private final String classifier;
    private final String file;
    private final String pomFile;

    @DataBoundConstructor
    public Artifact(String artifactId, String type, String classifier, String file, String pomFile) {
        this.artifactId = artifactId;
        this.type = type;
        this.classifier = classifier;
        this.file = file != null ? file.trim() : null;
        this.pomFile = pomFile != null ? pomFile.trim() : null;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getType() {
        return type;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getFile() {
        return file;
    }

    public String getPomFile() {
        return pomFile;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Artifact> {

        @Override
        public String getDisplayName() {
            return "";
        }

        public FormValidation doCheckArtifactId(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation.error("ArtifactId must not be empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckType(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation.error("Type must not be empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckClassifier(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckFile(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation.error("File must not be empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckPomFile(@QueryParameter String value) {
            return FormValidation.ok();
        }

    }
}
