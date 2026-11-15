package dev.segually.sbamodloader.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ModSettingsTest {
    private static ModSettings parse(String json) throws Exception {
        return ModSettings.parse(new JSONObject(json), "My Mod.sbapack");
    }

    private static final String SAMPLE = "{\"id\":\"mymod\",\"guid\":\"dev.me.mymod\",\"settings\":["
            + "{\"key\":\"enabled\",\"type\":\"bool\",\"label\":\"Turn it on\",\"default\":true},"
            + "{\"key\":\"speed\",\"type\":\"float\",\"label\":\"Speed\",\"default\":1.5,\"min\":0.5,\"max\":3,\"step\":0.1},"
            + "{\"key\":\"count\",\"type\":\"int\",\"label\":\"Count\",\"default\":4,\"min\":1,\"max\":10},"
            + "{\"key\":\"mode\",\"type\":\"choice\",\"label\":\"Mode\",\"default\":\"slow\",\"choices\":[\"fast\",\"slow\"]},"
            + "{\"key\":\"name\",\"type\":\"string\",\"label\":\"Name\",\"default\":\"bear\",\"section\":\"Player\"}]}";

    @Test
    public void aDeclarationIsRead() throws Exception {
        ModSettings s = parse(SAMPLE);
        assertEquals("mymod", s.id);
        assertEquals("dev.me.mymod", s.guid);
        assertEquals(5, s.fields.size());
        assertEquals("true", s.field("enabled").defaultValue);
        assertEquals("1.5", s.field("speed").defaultValue);
        assertEquals("4", s.field("count").defaultValue);
        assertEquals("slow", s.field("mode").defaultValue);
        assertEquals("bear", s.field("name").defaultValue);
    }

    @Test
    public void noSettingsMeansNull() throws Exception {
        assertNull(parse("{\"name\":\"x\"}"));
        assertNull(parse("{\"settings\":[]}"));
    }

    @Test
    public void theIdFallsBackToTheFileName() throws Exception {
        ModSettings s = parse("{\"settings\":[{\"key\":\"a\",\"type\":\"bool\"}]}");
        assertEquals("My_Mod.sbapack", s.id);
    }

    @Test
    public void malformedFieldsAreSkippedNotFatal() throws Exception {
        ModSettings s = parse("{\"settings\":["
                + "{\"key\":\"bad key!\",\"type\":\"bool\"},"
                + "{\"key\":\"nope\",\"type\":\"banana\"},"
                + "{\"key\":\"range\",\"type\":\"int\",\"min\":5,\"max\":1},"
                + "{\"key\":\"empty\",\"type\":\"choice\",\"choices\":[]},"
                + "{\"key\":\"ok\",\"type\":\"bool\"}]}");
        assertEquals(1, s.fields.size());
        assertEquals("ok", s.fields.get(0).key);
    }

    @Test
    public void duplicateKeysKeepTheFirst() throws Exception {
        ModSettings s = parse("{\"settings\":[{\"key\":\"a\",\"type\":\"bool\",\"label\":\"first\"},{\"key\":\"a\",\"type\":\"int\"}]}");
        assertEquals(1, s.fields.size());
        assertEquals("first", s.fields.get(0).label);
    }

    @Test
    public void numbersAreClampedToTheirRange() throws Exception {
        ModSettings s = parse(SAMPLE);
        assertEquals("3", s.field("speed").coerce("99"));
        assertEquals("0.5", s.field("speed").coerce("-4"));
        assertEquals("10", s.field("count").coerce("12.4"));
        assertEquals("1", s.field("count").coerce("0"));
        assertNull(s.field("count").coerce("abc"));
        assertNull(s.field("speed").coerce("NaN"));
    }

    @Test
    public void aChoiceMustExistAndABoolMustBeTrueOrFalse() throws Exception {
        ModSettings s = parse(SAMPLE);
        assertEquals("fast", s.field("mode").coerce("fast"));
        assertNull(s.field("mode").coerce("turbo"));
        assertEquals("false", s.field("enabled").coerce("false"));
        assertNull(s.field("enabled").coerce("yes"));
    }

    @Test
    public void textIsCutToItsLength() throws Exception {
        ModSettings s = parse("{\"settings\":[{\"key\":\"t\",\"type\":\"string\",\"maxLength\":5}]}");
        assertEquals("abcde", s.field("t").coerce("abcdefgh"));
    }

    @Test
    public void tooManyFieldsAreCut() throws Exception {
        StringBuilder sb = new StringBuilder("{\"settings\":[");
        for (int i = 0; i < 50; i++) sb.append(i == 0 ? "" : ",").append("{\"key\":\"k").append(i).append("\",\"type\":\"bool\"}");
        assertEquals(ModSettings.MAX_FIELDS, parse(sb.append("]}").toString()).fields.size());
    }

    @Test
    public void aDefaultOutsideTheRangeIsPulledIn() throws Exception {
        ModSettings s = parse("{\"settings\":[{\"key\":\"n\",\"type\":\"int\",\"min\":1,\"max\":5,\"default\":50}]}");
        assertEquals("5", s.field("n").defaultValue);
    }

    @Test
    public void nativeTextHasEveryFieldWithDefaultsForTheRest() throws Exception {
        ModSettings s = parse(SAMPLE);
        Map<String, String> values = new HashMap<>();
        values.put("speed", "2");
        values.put("mode", "bogus"); // not valid, so the default is written
        values.put("name", "a\\b\nc");
        String text = s.toNativeText(values);
        assertTrue(text.contains("enabled=true\n"));
        assertTrue(text.contains("speed=2\n"));
        assertTrue(text.contains("count=4\n"));
        assertTrue(text.contains("mode=slow\n"));
        assertTrue(text.contains("name=a\\\\b\\nc\n"));
    }

    @Test
    public void aBepInExConfigNeedsAGuid() throws Exception {
        assertEquals("", parse("{\"settings\":[{\"key\":\"a\",\"type\":\"bool\"}]}").toBepInExConfig(new HashMap<>()));
        String cfg = parse(SAMPLE).toBepInExConfig(new HashMap<>());
        assertTrue(cfg.contains("[General]"));
        assertTrue(cfg.contains("[Player]"));
        assertTrue(cfg.contains("enabled = true"));
        assertTrue(cfg.contains("name = bear"));
        assertFalse(cfg.contains("[General]\n\nname"));
    }

    @Test
    public void sanitizeMakesAUsableId() {
        assertEquals("m_x", ModSettings.sanitize("_x"));
        assertEquals("A_B.c", ModSettings.sanitize("A B.c"));
        assertNotNull(ModSettings.sanitize(""));
    }
}
