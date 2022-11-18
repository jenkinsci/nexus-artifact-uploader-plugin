package sp.sd.nexusartifactuploader.steps;

/*
 * Created by suresh on 5/19/2016.
 */

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Strings;
import hudson.*;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;

import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NexusArtifactUploaderStep extends AbstractStepImpl {

    private final String nexusVersion;
    private final String protocol;
    private final String nexusUrl;
    private final String groupId;
    private final String version;
    private final String repository;
    private final List<Artifact> artifacts;

    @CheckForNull
    private final String credentialsId;

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

    @Nullable
    public String getCredentialsId() {
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
                        .lookupCredentials(StandardUsernameCredentials.class, project, ACL.SYSTEM, Collections.emptyList()),
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
            return new StandardUsernameListBoxModel().withEmptySelection().withAll(CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, owner, ACL.SYSTEM, Collections.emptyList()));
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
        private static final long serialVersionUID = 1L;

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
            final Item project = build.getParent();
            final EnvVars envVars = build.getEnvironment(listener);
            final String username = step.getUsername(envVars, project);
            final String password = step.getPassword(envVars, project);
            final String nexusUrl = envVars.expand(step.getNexusUrl());
            final String repository = envVars.expand(step.getRepository());
            final String groupId = envVars.expand(step.getGroupId());
            final String version = envVars.expand(step.getVersion());
            // These do not need expansion, as they are chosen from a finite list
            final String protocol = step.getProtocol();
            final String nexusVersion = step.getNexusVersion();
            final TaskListener listener = this.listener;

            final Map<Artifact, File> artifactToFile = new LinkedHashMap<>();
            for (Artifact artifact : step.artifacts) {
                FilePath artifactFilePath = new FilePath(ws, envVars.expand(artifact.getFile()));
                artifactToFile.put(artifact.expandVars(envVars), new File(artifactFilePath.getRemote()));
            }

            return ws.act(new Callable<Boolean, Exception>() {
                private static final long serialVersionUID = 1L;

                @Override
                public Boolean call() throws Exception {
                    final List<org.sonatype.aether.artifact.Artifact> nexusArtifacts = new ArrayList<>(artifactToFile.size());
                    for (final Map.Entry<Artifact, File> entry : artifactToFile.entrySet()) {
                        Artifact artifact = entry.getKey();
                        File file = entry.getValue();
                        if (!file.exists()) {
                            listener.getLogger().println(file.getName() + " file doesn't exist");
                            throw new IOException(file.getName() + " file doesn't exist");
                        } else {
                            nexusArtifacts.add(Utils.toArtifact(artifact, groupId, version, file));
                        }
                    }
                    return Utils.uploadArtifacts(listener, username, password,
                            nexusUrl, repository, protocol, nexusVersion,
                            nexusArtifacts.toArray(new org.sonatype.aether.artifact.Artifact[0]));
                }

                @Override
                public void checkRoles(RoleChecker checker) throws SecurityException {

                }
            });
        }
    }
}
