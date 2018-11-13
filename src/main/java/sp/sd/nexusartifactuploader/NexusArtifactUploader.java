package sp.sd.nexusartifactuploader;

import hudson.*;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.ListBoxModel;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import org.jenkinsci.remoting.RoleChecker;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.base.Strings;


public class NexusArtifactUploader extends Builder implements SimpleBuildStep, Serializable {
    private static final long serialVersionUID = 1;

    private final String nexusVersion;
    private final String protocol;
    private final String nexusUrl;
    private final String groupId;
    private final String version;
    private final String repository;
    private final List<Artifact> artifacts;

    private final
    @CheckForNull
    String credentialsId;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public NexusArtifactUploader(String nexusVersion, String protocol, String nexusUrl, String groupId,
                                 String version, String repository, String credentialsId, List<Artifact> artifacts) {
        this.nexusVersion = nexusVersion;
        this.protocol = protocol;
        this.nexusUrl = nexusUrl;
        this.groupId = groupId;
        this.version = version;
        this.repository = repository;
        this.credentialsId = credentialsId;
        this.artifacts = artifacts;
    }

    public String getNexusVersion() {
        return nexusVersion;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getNexusUrl() {
        return nexusUrl;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getVersion() {
        return version;
    }

    public String getRepository() {
        return repository;
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public
    @Nullable
    String getCredentialsId() {
        return credentialsId;
    }

    public StandardUsernameCredentials getCredentials(Item project) {
        StandardUsernameCredentials credentials = null;
        try {

            credentials = credentialsId == null ? null : this.lookupSystemCredentials(credentialsId, project);
            if (credentials != null) {
                return credentials;
            }
        } catch (Throwable t) {

        }

        return credentials;
    }

    public static StandardUsernameCredentials lookupSystemCredentials(String credentialsId, Item project) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider
                        .lookupCredentials(StandardUsernameCredentials.class, project, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId)
        );
    }

    public String getUsername(EnvVars environment, Item project) {
        String Username = "";
        if (!Strings.isNullOrEmpty(credentialsId)) {
            Username = this.getCredentials(project).getUsername();
        }
        return Username;
    }

    public String getPassword(EnvVars environment, Item project) {
        String Password = "";
        if (!Strings.isNullOrEmpty(credentialsId)) {
            Password = Secret.toString(StandardUsernamePasswordCredentials.class.cast(this.getCredentials(project)).getPassword());
        }
        return Password;
    }

    @Override
    public void perform(Run build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        boolean result = false;
        EnvVars envVars = build.getEnvironment(listener);
        Item project = build.getParent();
        for (Artifact artifact : artifacts) {
            FilePath artifactFilePath = new FilePath(workspace, build.getEnvironment(listener).expand(artifact.getFile()));
            if (!artifactFilePath.exists()) {
                listener.getLogger().println(artifactFilePath.getName() + " file doesn't exists");
                throw new IOException(artifactFilePath.getName() + " file doesn't exists");
            } else {

                FilePath artifactPomFilePath = null;
                if (artifact.getPomFile() != null && artifact.getPomFile().trim().length() > 0) {
                    artifactPomFilePath = new FilePath(workspace, build.getEnvironment(listener).expand(artifact.getPomFile()));
                    if (!artifactPomFilePath.exists()) {
                        listener.getLogger().println(artifactPomFilePath.getName() + " pom file doesn't exists");
                        throw new IOException(artifactPomFilePath.getName() + " pom file doesn't exists");
                    }
                }

                result = artifactFilePath.act(new ArtifactFileCallable(listener,
                        this.getUsername(envVars, project),
                        this.getPassword(envVars, project),
                        envVars.expand(nexusUrl),
                        envVars.expand(groupId),
                        envVars.expand(artifact.getArtifactId()),
                        envVars.expand(version),
                        envVars.expand(repository),
                        envVars.expand(artifact.getType()),
                        envVars.expand(artifact.getClassifier()),
                        protocol,
                        nexusVersion,
                        artifactPomFilePath
                ));
            }
            if (!result) {
                throw new AbortException("Uploading file " + artifactFilePath.getName() + " failed.");
            }
        }
    }

    private static final class ArtifactFileCallable extends MasterToSlaveFileCallable<Boolean> {

        private final TaskListener listener;
        private final String resolvedNexusUser;
        private final String resolvedNexusPassword;
        private final String resolvedNexusUrl;
        private final String resolvedGroupId;
        private final String resolvedArtifactId;
        private final String resolvedVersion;
        private final String resolvedRepository;
        private final String resolvedType;
        private final String resolvedClassifier;
        private final String resolvedProtocol;
        private final String resolvedNexusVersion;
        private final FilePath artifactPomFilePath;

        public ArtifactFileCallable(TaskListener Listener, String ResolvedNexusUser, String ResolvedNexusPassword, String ResolvedNexusUrl,
                                    String ResolvedGroupId, String ResolvedArtifactId, String ResolvedVersion,
                                    String ResolvedRepository, String ResolvedType, String ResolvedClassifier,
                                    String ResolvedProtocol, String ResolvedNexusVersion, FilePath artifactPomFilePath) {
            this.listener = Listener;
            this.resolvedNexusUser = ResolvedNexusUser;
            this.resolvedNexusPassword = ResolvedNexusPassword;
            this.resolvedNexusUrl = ResolvedNexusUrl;
            this.resolvedGroupId = ResolvedGroupId;
            this.resolvedArtifactId = ResolvedArtifactId;
            this.resolvedVersion = ResolvedVersion;
            this.resolvedRepository = ResolvedRepository;
            this.resolvedType = ResolvedType;
            this.resolvedClassifier = ResolvedClassifier;
            this.resolvedProtocol = ResolvedProtocol;
            this.resolvedNexusVersion = ResolvedNexusVersion;
            this.artifactPomFilePath = artifactPomFilePath;
        }

        @Override
        public Boolean invoke(File artifactFile, VirtualChannel channel) throws IOException {

            File pomFile = null;
            if (artifactPomFilePath != null && artifactPomFilePath.getRemote() != null) {
                pomFile = new File(artifactPomFilePath.getRemote());
            }

            return Utils.uploadArtifact(artifactFile, listener, resolvedNexusUser, resolvedNexusPassword, resolvedNexusUrl,
                    resolvedGroupId, resolvedArtifactId, resolvedVersion, resolvedRepository, resolvedType, resolvedClassifier,
                    resolvedProtocol, resolvedNexusVersion, pomFile);
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {

        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl<C extends StandardCredentials> extends BuildStepDescriptor<Builder> {

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Nexus Artifact Uploader";
        }

        public FormValidation doCheckNexusUrl(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation.error("URL must not be empty");
            }

            if (value.startsWith("http://") || value.startsWith("https://")) {
                return FormValidation.error("URL must not start with http:// or https://");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckGroupId(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation.error("GroupId must not be empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVersion(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation.error("Version must not be empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckRepository(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation.error("Repository must not be empty");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item owner) {
            if (owner == null || !owner.hasPermission(Item.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel().withEmptySelection().withAll(CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
        }

        public ListBoxModel doFillNexusVersionItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("NEXUS2", "nexus2");
            items.add("NEXUS3", "nexus3");
            return items;
        }

        public ListBoxModel doFillProtocolItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("HTTP", "http");
            items.add("HTTPS", "https");
            return items;
        }
    }

}

