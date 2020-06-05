package sp.sd.nexusartifactuploader;

import hudson.*;
import hudson.model.*;
import hudson.remoting.Callable;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public void perform(Run build, FilePath workspace, Launcher launcher, final TaskListener listener) throws IOException, InterruptedException {
        final EnvVars envVars = build.getEnvironment(listener);
        Item project = build.getParent();
        final String username = getUsername(envVars, project);
        final String password = getPassword(envVars, project);
        final String nexusUrl = envVars.expand(getNexusUrl());
        final String repository = envVars.expand(getRepository());
        final String expandedVersion = envVars.expand(getVersion());
        final String expandedGroupId = envVars.expand(getGroupId());

        if (artifacts == null || artifacts.size() == 0) {
            throw new IOException("No artifacts defined. Artifacts must be defined in addition to group id. See https://plugins.jenkins.io/nexus-artifact-uploader");
        }

        final Map<Artifact, String> artifacts = new LinkedHashMap<Artifact, String>();
        for (Artifact artifact : this.artifacts) {
            FilePath artifactFilePath = new FilePath(workspace, envVars.expand(artifact.getFile()));
            artifacts.put(artifact, artifactFilePath.getRemote());
        }

        workspace.act(new Callable<Boolean, IOException>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Boolean call() throws IOException {
                final List<org.sonatype.aether.artifact.Artifact> nexusArtifacts = new ArrayList<org.sonatype.aether.artifact.Artifact>(artifacts.size());
                for (final Map.Entry<Artifact, String> entry : artifacts.entrySet()) {
                    Artifact artifact = entry.getKey();
                    File file = new File(entry.getValue());
                    if (!file.exists()) {
                        listener.getLogger().println(file.getName() + " file doesn't exists");
                        throw new IOException(file.getName() + " file doesn't exists");
                    } else {
                        nexusArtifacts.add(Utils.toArtifact(artifact.expandVars(envVars), expandedGroupId, expandedVersion, file));
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
            return "Nexus artifact uploader";
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

