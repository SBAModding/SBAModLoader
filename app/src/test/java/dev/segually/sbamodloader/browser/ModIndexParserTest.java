package dev.segually.sbamodloader.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.segually.sbamodloader.browser.BrowserModels.Entry;

public class ModIndexParserTest {
    private static String sample() throws IOException {
        try (InputStream in = ModIndexParserTest.class.getResourceAsStream("/index-sample.json")) {
            assertNotNull("index-sample.json", in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Entry byId(List<Entry> mods, String id) {
        for (Entry e : mods) if (e.id.equals(id)) return e;
        return null;
    }

    /** The sample index with {@code edit} applied to the hive-reborn object. */
    private static List<Entry> parseEdited(java.util.function.Consumer<JSONObject> edit) throws Exception {
        JSONObject root = new JSONObject(sample());
        JSONArray mods = root.getJSONArray("mods");
        for (int i = 0; i < mods.length(); i++) {
            if (mods.getJSONObject(i).getString("id").equals("hive-reborn")) edit.accept(mods.getJSONObject(i));
        }
        return ModIndexParser.parse(root.toString());
    }

    @Test
    public void readsTheRealIndexWithVersionHistory() throws Exception {
        List<Entry> mods = ModIndexParser.parse(sample());
        assertEquals(3, mods.size());
        Entry hive = byId(mods, "hive-reborn");
        assertEquals(2, hive.versions.size());
        assertEquals("1.0.1", hive.latest().version);
        assertEquals("1.0.0", hive.versions.get(1).version);
        assertEquals(355595950L, hive.version("1.0.0").size);
        assertEquals("the flat fields still describe the newest release", "1.0.1", hive.version);
    }

    @Test
    public void theFlatFieldsStillDescribeTheNewestRelease() throws Exception {
        Entry hive = byId(ModIndexParser.parse(sample()), "hive-reborn");
        assertEquals(hive.latest().sha256, hive.sha256);
        assertEquals(hive.latest().size, hive.size);
        assertEquals(hive.latest().gameVersions, hive.gameVersions);
        assertTrue(hive.downloadUrls().get(0).endsWith("/hive-reborn-1.0.1/HiveReborn.sbapack"));
    }

    @Test
    public void aMalformedOlderVersionIsSkippedAndTheModIsKept() throws Exception {
        List<Entry> mods = parseEdited(o -> {
            try {
                o.getJSONArray("previousVersions").getJSONObject(0).put("sha256", "short");
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        });
        Entry hive = byId(mods, "hive-reborn");
        assertNotNull(hive);
        assertEquals(1, hive.versions.size());
    }

    @Test
    public void anOlderVersionThatWasNotAuditedIsSkipped() throws Exception {
        Entry hive = byId(parseEdited(o -> {
            try {
                o.getJSONArray("previousVersions").getJSONObject(0).getJSONObject("audited").put("version", "0.9");
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }), "hive-reborn");
        assertEquals(1, hive.versions.size());
    }

    @Test
    public void aMalformedNewestReleaseDropsTheMod() throws Exception {
        List<Entry> mods = parseEdited(o -> {
            try {
                o.put("sha256", "short");
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        });
        assertNull(byId(mods, "hive-reborn"));
        assertEquals(2, mods.size());
    }

    @Test
    public void versionsAreSortedNewestFirstWhateverTheFileOrder() throws Exception {
        Entry hive = byId(parseEdited(o -> {
            try {
                // An older release listed twice is kept once.
                JSONArray prev = o.getJSONArray("previousVersions");
                prev.put(new JSONObject(prev.getJSONObject(0).toString()));
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }), "hive-reborn");
        assertEquals(2, hive.versions.size());
        assertEquals("1.0.1", hive.versions.get(0).version);
    }

    @Test
    public void readsDependenciesAndKeepsRequiresForOldCode() throws Exception {
        Entry hive = byId(parseEdited(o -> {
            try {
                o.remove("requires");
                o.put("dependencies", new JSONArray().put(new JSONObject().put("id", "segualsba-client").put("min", "1.0.1")));
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }), "hive-reborn");
        assertEquals(1, hive.latest().dependencies.size());
        assertEquals("1.0.1", hive.latest().dependencies.get(0).min);
        assertNull(hive.latest().dependencies.get(0).max);
        assertEquals(java.util.Arrays.asList("segualsba-client"), hive.requires);
    }

    @Test
    public void onlyRepositoryImagePathsAreKept() throws Exception {
        Entry hive = byId(parseEdited(o -> {
            try {
                o.put("icon", "images/hive-reborn/icon.png");
                o.put("screenshots", new JSONArray()
                        .put("images/hive-reborn/1.png")
                        .put("https://evil.example/x.png")
                        .put("images/../secret.png")
                        .put("images/hive-reborn/readme.txt")
                        .put("/images/hive-reborn/2.png"));
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }), "hive-reborn");
        assertEquals("images/hive-reborn/icon.png", hive.icon);
        assertEquals(java.util.Arrays.asList("images/hive-reborn/1.png"), hive.screenshots);
    }

    @Test
    public void imageUrlsOnlyComeFromTheRepository() {
        assertNull(ImageLoader.urlFor("https://evil.example/x.png"));
        assertNull(ImageLoader.urlFor("images/../x.png"));
        assertNull(ImageLoader.urlFor(null));
        assertTrue(ImageLoader.urlFor("images/a/b.png").startsWith("https://raw.githubusercontent.com/SBAModding/ModRepository/"));
    }

    @Test(expected = JSONException.class)
    public void anUnknownSchemaVersionIsRefused() throws Exception {
        ModIndexParser.parse(new JSONObject(sample()).put("schemaVersion", 2).toString());
    }

    @Test
    public void auditNotesAndContentsAreRead() throws Exception {
        Entry e = byId(parseEdited(o -> {
            try {
                o.getJSONObject("audited").put("notes", new JSONArray().put("Reads only art assets").put("  ").put("Second"));
                o.put("contains", new JSONArray().put("assets").put("bogus").put("assets"));
            } catch (JSONException ex) {
                throw new IllegalStateException(ex);
            }
        }), "hive-reborn");
        assertEquals(java.util.Arrays.asList("Reads only art assets", "Second"), e.latest().audit.notes);
        assertEquals(java.util.Collections.singletonList("assets"), e.latest().contains);
    }

    @Test
    public void tooManyOrTooLongNotesAreCut() throws Exception {
        Entry e = byId(parseEdited(o -> {
            try {
                JSONArray notes = new JSONArray();
                for (int i = 0; i < 9; i++) notes.put("n" + i);
                notes.put(0, "x".repeat(300));
                o.getJSONObject("audited").put("notes", notes);
            } catch (JSONException ex) {
                throw new IllegalStateException(ex);
            }
        }), "hive-reborn");
        assertEquals(6, e.latest().audit.notes.size());
        assertEquals(120, e.latest().audit.notes.get(0).length());
    }

    @Test
    public void aReleaseWithoutNotesOrContentsStillParses() throws Exception {
        Entry e = byId(ModIndexParser.parse(sample()), "hive-reborn");
        assertTrue(e.latest().audit.notes.isEmpty());
        assertTrue(e.latest().contains.isEmpty());
    }
}
