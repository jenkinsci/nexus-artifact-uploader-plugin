package sp.sd.nexusartifactuploader.dsl;

import groovy.lang.Closure;
import javaposse.jobdsl.dsl.Context;
import javaposse.jobdsl.dsl.DslContext;
import javaposse.jobdsl.plugin.DslExtensionMethod;
import sp.sd.nexusartifactuploader.Artifact;

import java.util.ArrayList;
import java.util.List;

import static javaposse.jobdsl.plugin.ContextExtensionPoint.executeInContext;

/**
 * Created by suresh on 9/29/2016.
 */
public class NexusArtifactUploaderJobDslContext implements Context {
    String nexusVersion;
    String protocol;
    String nexusUrl;
    String groupId;
    String version;
    String repository;
    String credentialsId;
    List<Artifact> artifactList = new ArrayList<>();

    void nexusVersion(String nexusVersion) {
        this.nexusVersion = nexusVersion;
    }

    void protocol(String protocol) {
        this.protocol = protocol;
    }

    void nexusUrl(String nexusUrl) {
        this.nexusUrl = nexusUrl;
    }

    void groupId(String groupId) {
        this.groupId = groupId;
    }

    void version(String version) {
        this.version = version;
    }

    void repository(String repository) {
        this.repository = repository;
    }

    void credentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    void artifact(@DslContext(ArtifactJobDslContext.class) Closure artifactClosure) {
        ArtifactJobDslContext context = new ArtifactJobDslContext();
        executeInContext(artifactClosure, context);
        Artifact artifact = new Artifact(context.artifactId, context.type, context.classifier, context.file, context.pomFile);
        artifactList.add(artifact);
    }
}
