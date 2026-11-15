package dev.segually.sbamodloader.browser;

import static dev.segually.sbamodloader.browser.Fixtures.mod;
import static dev.segually.sbamodloader.browser.Fixtures.release;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.segually.sbamodloader.browser.BrowserModels.Audit;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Version;
import dev.segually.sbamodloader.browser.SafetyReasons.Reason;
import dev.segually.sbamodloader.browser.SafetyReasons.Type;

public class SafetyReasonsTest {
    private static Version audited(String version, String... contains) {
        Version v = release(version, "13.0.8");
        v.audit = new Audit(version, "2026-11-14", "the team", new ArrayList<>());
        v.contains = new ArrayList<>(Arrays.asList(contains));
        return v;
    }

    private static List<Type> types(List<Reason> reasons) {
        List<Type> out = new ArrayList<>();
        for (Reason r : reasons) out.add(r.type);
        return out;
    }

    private static Entry entry(boolean openSource, String author, Version... versions) {
        Entry e = mod("m", versions);
        e.openSource = openSource;
        e.license = openSource ? "MIT" : "All rights reserved";
        e.author = author;
        return e;
    }

    @Test
    public void theAuditIsAlwaysFirst() {
        Version v = audited("1.0");
        Entry e = entry(false, "someone", v);
        assertEquals(Type.AUDITED, SafetyReasons.build(e, v, "owner").get(0).type);
    }

    @Test
    public void reviewerNotesComeRightAfterTheAudit() {
        Version v = audited("1.0");
        v.audit.notes.add("Reads only art assets");
        Entry e = entry(false, "someone", v);
        List<Reason> r = SafetyReasons.build(e, v, "owner");
        assertEquals(Type.NOTE, r.get(1).type);
        assertEquals("Reads only art assets", r.get(1).a);
    }

    @Test
    public void aPackOfOnlyAssetsHasNoCode() {
        Version v = audited("1.0", "assets");
        List<Type> t = types(SafetyReasons.build(entry(false, "x", v), v, "owner"));
        assertTrue(t.contains(Type.NO_CODE));
        assertFalse(t.contains(Type.NATIVE_CODE));
    }

    @Test
    public void codeIsReportedAsAFactNotAsSafe() {
        Version v = audited("1.0", "assets", "native", "java");
        List<Reason> r = SafetyReasons.build(entry(false, "x", v), v, "owner");
        List<Type> t = types(r);
        assertFalse(t.contains(Type.NO_CODE));
        assertTrue(t.contains(Type.NATIVE_CODE));
        assertTrue(t.contains(Type.JAVA_CODE));
        for (Reason x : r) if (x.type == Type.NATIVE_CODE || x.type == Type.JAVA_CODE) assertFalse(x.positive());
    }

    @Test
    public void unknownContentsSayNothingAboutCode() {
        Version v = audited("1.0");
        List<Type> t = types(SafetyReasons.build(entry(false, "x", v), v, "owner"));
        assertFalse(t.contains(Type.NO_CODE));
        assertFalse(t.contains(Type.NATIVE_CODE));
    }

    @Test
    public void openSourceNamesTheLicenceAndTheRelease() {
        Version v = audited("1.0");
        Entry e = entry(true, "x", v);
        Reason open = null;
        for (Reason r : SafetyReasons.build(e, v, "owner")) if (r.type == Type.OPEN_SOURCE) open = r;
        assertEquals("MIT", open.a);
    }

    @Test
    public void closedSourceSaysWhereTheFileComesFrom() {
        Version v = audited("1.0");
        Entry e = entry(false, "x", v);
        Reason closed = null;
        for (Reason r : SafetyReasons.build(e, v, "owner")) if (r.type == Type.CLOSED_SOURCE) closed = r;
        assertEquals("example.com", closed.b);
    }

    @Test
    public void theOwnersModsSaySo() {
        Version v = audited("1.0");
        assertTrue(types(SafetyReasons.build(entry(false, "Segually", v), v, "segually")).contains(Type.OWNER));
        assertFalse(types(SafetyReasons.build(entry(false, "someone", v), v, "segually")).contains(Type.OWNER));
    }

    @Test
    public void earlierCheckedVersionsAreCounted() {
        Version newest = audited("1.2");
        Version mid = audited("1.1");
        Version old = audited("1.0");
        Entry e = entry(false, "x", newest, mid, old);
        Reason history = null;
        for (Reason r : SafetyReasons.build(e, newest, "owner")) if (r.type == Type.HISTORY) history = r;
        assertEquals("2", history.a);
        assertFalse(types(SafetyReasons.build(e, old, "owner")).contains(Type.HISTORY));
    }
}
