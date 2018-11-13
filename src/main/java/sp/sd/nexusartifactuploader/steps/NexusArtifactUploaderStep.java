package sp.sd.nexusartifactuploader.steps;

/**
 * Created by suresh on 5/19/2016.
 */

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.base.Strings;
import hudson.*;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.MasterToSlaveFileCallable;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import sp.sd.nexusartifactuploader.Artifact;
import sp.sd.nexusartifactuploader.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class NexusArtifactUploaderStep extends AbstractStepImpl {

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

    @DataBoundConstructor
    public NexusArtifactUploaderStep(String nexusVersion, String protocol, String nexusUrl, String groupId,
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

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "nexusArtifactUploader";
        }

        @Override
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

    public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Boolean> {
        @Inject
        private transient NexusArtifactUploaderStep step;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient FilePath ws;

        @StepContextParameter
        private transient Run build;

        @StepContextParameter
        private transient Launcher launcher;

        @Override
        protected Boolean run() throws Exception {
            Boolean result = false;
            Item project = build.getParent();
            EnvVars envVars = build.getEnvironment(listener);
            for (Artifact artifact : step.artifacts) {

                FilePath artifactFilePath = new FilePath(ws, build.getEnvironment(listener).expand(artifact.getFile()));
                if (!artifactFilePath.exists()) {
                    listener.getLogger().println(artifactFilePath.getName() + " file doesn't exists");
                    throw new IOException(artifactFilePath.getName() + " file doesn't exists");
                } else {

                    FilePath artifactPomFilePath = null;
                    if (artifact.getPomFile() != null && artifact.getPomFile().trim().length() > 0) {
                        artifactPomFilePath = new FilePath(ws, build.getEnvironment(listener).expand(artifact.getPomFile()));
                        if (!artifactPomFilePath.exists()) {
                            listener.getLogger().println(artifactPomFilePath.getName() + " pom file doesn't exists");
                            throw new IOException(artifactPomFilePath.getName() + " pom file doesn't exists");
                        }
                    }

                    result = artifactFilePath.act(new ArtifactFileCallable(listener,
                            step.getUsername(envVars, project),
                            step.getPassword(envVars, project),
                            envVars.expand(step.getNexusUrl()),
                            envVars.expand(step.getGroupId()),
                            envVars.expand(artifact.getArtifactId()),
                            envVars.expand(step.getVersion()),
                            envVars.expand(step.getRepository()),
                            envVars.expand(artifact.getType()),
                            envVars.expand(artifact.getClassifier()),
                            step.getProtocol(),
                            step.getNexusVersion(),
                            artifactPomFilePath
                    ));
                }
                if (!result) {
                    throw new AbortException("Uploading file " + artifactFilePath.getName() + " failed.");
                }
            }
            return result;
        }

        private static final long serialVersionUID = 1L;
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

        public ArtifactFileCallable(TaskListener Listener, String ResolvedNexusUser, String ResolvedNexusPassword,
                                    String ResolvedNexusUrl, String ResolvedGroupId, String ResolvedArtifactId,
                                    String ResolvedVersion, String ResolvedRepository,
                                    String ResolvedType, String ResolvedClassifier,
                                    String ResolvedProtocol, String ResolvedNexusVersion,
                                    FilePath artifactPomFilePath) {
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
            if (artifactPomFilePath.getRemote() != null) {
                pomFile = new File(artifactPomFilePath.getRemote());
            }

            return Utils.uploadArtifact(artifactFile, listener, resolvedNexusUser, resolvedNexusPassword, resolvedNexusUrl,
                    resolvedGroupId, resolvedArtifactId, resolvedVersion, resolvedRepository,
                    resolvedType, resolvedClassifier, resolvedProtocol, resolvedNexusVersion, pomFile);
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {

        }
    }
}
