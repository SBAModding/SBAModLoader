package dev.segually.sbamodloader.browser;

import static dev.segually.sbamodloader.browser.Fixtures.dep;
import static dev.segually.sbamodloader.browser.Fixtures.index;
import static dev.segually.sbamodloader.browser.Fixtures.installed;
import static dev.segually.sbamodloader.browser.Fixtures.kinds;
import static dev.segually.sbamodloader.browser.Fixtures.mod;
import static dev.segually.sbamodloader.browser.Fixtures.release;
import static dev.segually.sbamodloader.browser.Fixtures.steps;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static java.util.Arrays.asList;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.InstallPlanner.Kind;
import dev.segually.sbamodloader.browser.InstallPlanner.Plan;

public class RepositoriesTest {
    private static String sample() throws Exception {
        try (InputStream in = RepositoriesTest.class.getResourceAsStream("/index-sample.json")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void theOfficialRepositoryKeepsPlainIds() throws Exception {
        List<Entry> mods = ModIndexParser.parse(sample());
        for (Entry e : mods) {
            assertTrue(e.official());
            assertEquals(e.id, e.rawId());
        }
    }

    @Test
    public void anotherRepositoryPrefixesIdsAndDependencies() throws Exception {
        List<Entry> official = ModIndexParser.parse(sample());
        List<Entry> other = ModIndexParser.parse(sample(), "rabc123");
        assertEquals(official.size(), other.size());
        for (int i = 0; i < other.size(); i++) {
            Entry o = other.get(i);
            assertFalse(o.official());
            assertEquals("rabc123:" + official.get(i).id, o.id);
            assertEquals(official.get(i).id, o.rawId());
            for (BrowserModels.Version v : o.versions) for (BrowserModels.Dependency d : v.dependencies) {
                assertTrue(d.id.startsWith("rabc123:"));
            }
        }
    }

    @Test
    public void theSameIdInTwoRepositoriesDoesNotCollide() throws Exception {
        List<Entry> a = ModIndexParser.parse(sample());
        List<Entry> b = ModIndexParser.parse(sample(), "rabc123");
        assertNotEquals(a.get(0).id, b.get(0).id);
    }

    @Test
    public void aRepositoryNameComesFromTheIndexWhenItHasOne() throws Exception {
        JSONObject root = new JSONObject(sample());
        assertNull(ModIndexParser.indexName(root.toString()));
        root.put("name", "  Friends' mods ");
        assertEquals("Friends' mods", ModIndexParser.indexName(root.toString()));
        assertNull(ModIndexParser.indexName("not json"));
    }

    @Test
    public void everyAddressHasItsOwnShortId() {
        RepoSource a = new RepoSource("a", "https://example.com/a/index.json", true);
        RepoSource b = new RepoSource("b", "https://example.com/b/index.json", true);
        assertTrue(a.id().matches("r[0-9a-f]{6}"));
        assertNotEquals(a.id(), b.id());
        assertEquals(a.id(), new RepoSource("other name", a.url, false).id());
        assertNull(RepoSource.official().id());
        assertTrue(RepoSource.official().isOfficial());
        assertEquals("https://example.com/a/", a.baseUrl());
    }

    @Test
    public void onlyHttpsJsonAddressesAreAccepted() {
        assertTrue(RepoSource.validUrl("https://example.com/mods/index.json", false));
        assertFalse(RepoSource.validUrl("http://example.com/index.json", false));
        assertFalse(RepoSource.validUrl("https://example.com/index.html", false));
        assertFalse(RepoSource.validUrl("https://exa mple.com/index.json", false));
        assertFalse(RepoSource.validUrl("file:///sdcard/index.json", false));
        assertFalse(RepoSource.validUrl("http://127.0.0.1:8765/index.json", false));
        assertTrue(RepoSource.validUrl("http://127.0.0.1:8765/index.json", true));
        assertFalse(RepoSource.validUrl("", false));
        assertFalse(RepoSource.validUrl(null, false));
    }

    @Test
    public void imagesAreOnlyFetchedFromAnAcceptedFolder() {
        assertEquals("https://example.com/a/images/x/icon.png", ImageLoader.urlFor("https://example.com/a/", "images/x/icon.png"));
        assertNull(ImageLoader.urlFor("https://example.com/a/", "../secret.png"));
        assertNull(ImageLoader.urlFor("ftp://example.com/a/", "images/x/icon.png"));
        assertNull(ImageLoader.urlFor("https://example.com/a", "images/x/icon.png"));
        assertNull(ImageLoader.urlFor(null, "images/x/icon.png"));
    }

    @Test
    public void aModFromAnotherRepositoryIsFlaggedAsUnverified() {
        Entry m = mod("rabc123:m", release("1.0", "13.0.8"));
        m.repoId = "rabc123";
        m.repoName = "Friends";
        Plan p = InstallPlanner.plan(m, m.latest(), index(m), installed(), "13.0.8");
        assertEquals(asList(Kind.UNVERIFIED_SOURCE), kinds(p));
        assertFalse(p.blocked());
        assertTrue(p.needsConfirmation());
        assertEquals("Friends", p.issues.get(0).other);
    }

    @Test
    public void aModFromTheOfficialRepositoryIsNotFlagged() {
        Entry m = mod("m", release("1.0", "13.0.8"));
        assertFalse(kinds(InstallPlanner.plan(m, m.latest(), index(m), installed(), "13.0.8")).contains(Kind.UNVERIFIED_SOURCE));
    }

    @Test
    public void aDependencyFallsBackToTheOfficialRepository() {
        Entry official = mod("y", release("1.0", "13.0.8"));
        Entry other = mod("rabc123:x", release("1.0", "13.0.8", dep("rabc123:y", "1.0", null)));
        other.repoId = "rabc123";
        other.repoName = "Friends";
        Plan p = InstallPlanner.plan(other, other.latest(), index(official, other), installed(), "13.0.8");
        assertEquals(asList("y@1.0", "rabc123:x@1.0"), steps(p));
        assertFalse(p.blocked());
    }

    @Test
    public void aDependencyInTheSameRepositoryIsPreferred() {
        Entry official = mod("y", release("1.0", "13.0.8"));
        Entry own = mod("rabc123:y", release("1.0", "13.0.8"));
        own.repoId = "rabc123";
        Entry other = mod("rabc123:x", release("1.0", "13.0.8", dep("rabc123:y", "1.0", null)));
        other.repoId = "rabc123";
        Plan p = InstallPlanner.plan(other, other.latest(), index(official, own, other), installed(), "13.0.8");
        assertEquals(asList("rabc123:y@1.0", "rabc123:x@1.0"), steps(p));
    }

    @Test
    public void aMissingDependencyIsNamedWithoutThePrefix() {
        Entry other = mod("rabc123:x", release("1.0", "13.0.8", dep("rabc123:gone", "1.0", null)));
        other.repoId = "rabc123";
        Plan p = InstallPlanner.plan(other, other.latest(), index(other), installed(), "13.0.8");
        assertTrue(kinds(p).contains(Kind.DEP_MISSING));
        for (InstallPlanner.Issue i : p.issues) if (i.kind == Kind.DEP_MISSING) assertEquals("gone", i.mod);
    }
}
