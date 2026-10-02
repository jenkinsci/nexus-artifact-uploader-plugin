package sp.sd.nexusartifactuploader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import hudson.model.FreeStyleBuild;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTableRow;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class NexusUploadBuildActionTest {

    private static final String RELEASES = "https://nexus.example.com/repository/releases";
    private static final String RAW = "https://nexus.example.com/repository/raw";

    private static final UploadedArtifact APP = artifact(RELEASES, "releases", "app", "", "jar", true);
    private static final UploadedArtifact APP_SOURCES = artifact(RELEASES, "releases", "app", "sources", "jar", true);
    private static final UploadedArtifact FIRMWARE = artifact(RAW, "raw", "fw", "", "bin", false);

    /**
     * Two uploads in one build: one side-bar entry with every artifact in its table, and one flat
     * list of all URLs on the build page.
     */
    @Test
    void severalUploadsShareOneSideBarEntryTableAndUrlList(JenkinsRule j) throws Exception {
        FreeStyleBuild build = j.buildAndAssertSuccess(j.createFreeStyleProject());
        build.addAction(new NexusUploadBuildAction(RELEASES, Arrays.asList(APP, APP_SOURCES)));
        build.addAction(new NexusUploadBuildAction(RAW, Collections.singletonList(FIRMWARE)));

        List<NexusUploadBuildAction> actions = NexusUploadBuildAction.getAll(build);
        assertThat(actions.get(1).getIconFileName(), nullValue());

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage buildPage = wc.getPage(build);

        assertThat(buildPage.getByXPath("//*[@id='side-panel']//a[contains(@href,'nexus-artifacts')]"), hasSize(1));

        // One summary block, holding exactly the URLs as links and nothing else.
        assertThat(buildPage.getByXPath("//*[@id='main-panel']//a[@href='nexus-artifacts']"), hasSize(1));
        List<DomElement> items =
                buildPage.getByXPath("//*[@id='main-panel']//li[a[contains(@href,'nexus.example.com')]]");
        assertThat(texts(items), contains(APP.getUrl(), APP_SOURCES.getUrl(), FIRMWARE.getUrl()));
        for (UploadedArtifact artifact : Arrays.asList(APP, APP_SOURCES, FIRMWARE)) {
            assertThat(buildPage.getByXPath("//li/a[@href='" + artifact.getUrl() + "']"), hasSize(1));
        }

        HtmlPage table = wc.getPage(build, "nexus-artifacts");
        assertThat(table.getByXPath("//table//tbody/tr"), hasSize(3));
        assertThat(
                cells(table, APP_SOURCES),
                contains("releases", "com.example:app", "1.0", "sources", "jar", APP_SOURCES.getFileName()));
        assertThat(
                cells(table, FIRMWARE),
                contains("raw", "com.example:fw", "1.0", "", "bin", FIRMWARE.getFileName() + " (unconfirmed)"));
    }

    /** A URL that is not http(s), e.g. from a compromised agent, is shown but never linked. */
    @Test
    void nonHttpUrlIsRenderedAsText(JenkinsRule j) throws Exception {
        UploadedArtifact evil = new UploadedArtifact(
                "com.example", "evil", "1.0", "", "jar", "evil-1.0.jar", "releases", "javascript:alert(1)", true);
        FreeStyleBuild build = j.buildAndAssertSuccess(j.createFreeStyleProject());
        build.addAction(new NexusUploadBuildAction(RELEASES, Collections.singletonList(evil)));

        JenkinsRule.WebClient wc = j.createWebClient();
        for (HtmlPage page : Arrays.asList(wc.getPage(build), wc.getPage(build, "nexus-artifacts"))) {
            assertThat(page.getByXPath("//a[starts-with(@href,'javascript')]"), hasSize(0));
        }
        assertThat(wc.getPage(build).asNormalizedText(), containsString("javascript:alert(1)"));
    }

    @Test
    void artifactsAreExposedThroughTheRestApi(JenkinsRule j) throws Exception {
        FreeStyleBuild build = j.buildAndAssertSuccess(j.createFreeStyleProject());
        build.addAction(new NexusUploadBuildAction(RELEASES, Arrays.asList(APP, APP_SOURCES)));

        String json = j.createWebClient()
                .goTo(build.getUrl() + "api/json?tree=actions[artifacts[url,classifier,verified]]", "application/json")
                .getWebResponse()
                .getContentAsString();

        assertThat(json, containsString(APP.getUrl()));
        assertThat(json, containsString(APP_SOURCES.getUrl()));
        assertThat(json, containsString("\"classifier\":\"sources\""));

        String plain = j.createWebClient()
                .goTo(build.getUrl() + "api/json", "application/json")
                .getWebResponse()
                .getContentAsString();
        assertThat(plain, containsString(APP.getUrl()));
    }

    private static List<String> texts(List<DomElement> elements) {
        return elements.stream().map(e -> e.asNormalizedText().trim()).collect(Collectors.toList());
    }

    /** The cell texts of the table row that links to the given artifact. */
    private static List<String> cells(HtmlPage table, UploadedArtifact artifact) {
        HtmlTableRow row = table.getFirstByXPath("//table//tbody/tr[td/a[@href='" + artifact.getUrl() + "']]");
        return row.getCells().stream().map(c -> c.asNormalizedText().trim()).collect(Collectors.toList());
    }

    private static UploadedArtifact artifact(
            String base, String repository, String id, String classifier, String type, boolean verified) {
        String fileName = id + "-1.0" + (classifier.isEmpty() ? "" : "-" + classifier) + "." + type;
        return new UploadedArtifact(
                "com.example",
                id,
                "1.0",
                classifier,
                type,
                fileName,
                repository,
                base + "/com/example/" + id + "/1.0/" + fileName,
                verified);
    }
}
