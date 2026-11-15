package dev.segually.sbamodloader.browser;

import static dev.segually.sbamodloader.browser.Fixtures.dep;
import static dev.segually.sbamodloader.browser.Fixtures.mod;
import static dev.segually.sbamodloader.browser.Fixtures.release;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dev.segually.sbamodloader.browser.BrowserModels.Entry;

public class ModVersionsTest {
    @Test
    public void ordersNumbersNumerically() {
        assertTrue(ModVersions.compare("1.0.1", "1.0.0") > 0);
        assertTrue(ModVersions.compare("1.10", "1.9") > 0);
        assertTrue(ModVersions.compare("2.0", "1.99.99") > 0);
        assertEquals(0, ModVersions.compare("1.2", "1.2.0"));
        assertEquals(0, ModVersions.compare("1.2.0.0", "1.2"));
    }

    @Test
    public void prereleaseSortsBeforeTheRelease() {
        assertTrue(ModVersions.compare("1.2.0-beta", "1.2.0") < 0);
        assertTrue(ModVersions.compare("1.2.0", "1.2.0-beta") > 0);
        assertTrue(ModVersions.compare("1.2.0-beta", "1.1.9") > 0);
        assertTrue(ModVersions.compare("1.2.0-alpha", "1.2.0-beta") < 0);
    }

    @Test
    public void unreadableVersionIsZero() {
        assertTrue(ModVersions.compare("garbage", "0.1") < 0);
        assertEquals(0, ModVersions.compare("", "0"));
    }

    @Test
    public void dependencyBoundsAreInclusiveAndOptional() {
        assertTrue(ModVersions.satisfies(dep("x", "1.2", "1.9"), "1.2"));
        assertTrue(ModVersions.satisfies(dep("x", "1.2", "1.9"), "1.9"));
        assertFalse(ModVersions.satisfies(dep("x", "1.2", "1.9"), "1.1.9"));
        assertFalse(ModVersions.satisfies(dep("x", "1.2", "1.9"), "1.9.1"));
        assertTrue(ModVersions.satisfies(dep("x", "1.2", null), "9.0"));
        assertTrue(ModVersions.satisfies(dep("x", null, "1.9"), "0.1"));
        assertTrue(ModVersions.satisfies(dep("x", null, null), "0.0.1"));
    }

    @Test
    public void bestForPicksTheNewestReleaseMadeForTheGame() {
        Entry e = mod("m", release("2.0", "14.0.0"), release("1.5", "13.0.8"), release("1.0", "13.0.8"));
        assertEquals("1.5", ModVersions.bestFor(e, "13.0.8").version);
        assertEquals("2.0", ModVersions.bestFor(e, "14.0.0").version);
        assertNull(ModVersions.bestFor(e, "12.0.0"));
        assertNull(ModVersions.bestFor(e, null));
    }

    @Test
    public void defaultForFallsBackToTheNewest() {
        Entry e = mod("m", release("2.0", "14.0.0"), release("1.0", "13.0.8"));
        assertEquals("1.0", ModVersions.defaultFor(e, "13.0.8").version);
        assertEquals("2.0", ModVersions.defaultFor(e, "12.0.0").version);
        assertEquals("2.0", ModVersions.defaultFor(e, null).version);
    }
}
