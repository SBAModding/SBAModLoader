package dev.segually.sbamodloader.browser;

import static dev.segually.sbamodloader.browser.Fixtures.index;
import static dev.segually.sbamodloader.browser.Fixtures.installed;
import static dev.segually.sbamodloader.browser.Fixtures.mod;
import static dev.segually.sbamodloader.browser.Fixtures.release;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import dev.segually.sbamodloader.browser.BrowserModels.Entry;

public class ModUpdatesTest {
    private static List<ModUpdates.Update> find(java.util.Map<String, Entry> index, java.util.Map<String, String> installed, String game) {
        return ModUpdates.find(index, installed, game);
    }

    @Test
    public void aNewerReleaseIsAnUpdate() {
        Entry m = mod("m", release("1.2", "13.0.8"), release("1.0", "13.0.8"));
        List<ModUpdates.Update> u = find(index(m), installed("m", "1.0"), "13.0.8");
        assertEquals(1, u.size());
        assertEquals("1.2", u.get(0).target.version);
        assertEquals("1.0", u.get(0).installed);
    }

    @Test
    public void theNewestReleaseIsNoUpdate() {
        Entry m = mod("m", release("1.2", "13.0.8"));
        assertTrue(find(index(m), installed("m", "1.2"), "13.0.8").isEmpty());
    }

    @Test
    public void aCopyNewerThanTheRepositoryListsIsNoUpdate() {
        Entry m = mod("m", release("1.2", "13.0.8"));
        assertTrue(find(index(m), installed("m", "2.0"), "13.0.8").isEmpty());
    }

    @Test
    public void aNewerReleaseForAnotherGameIsNotOffered() {
        Entry m = mod("m", release("2.0", "14.0.0"), release("1.0", "13.0.8"));
        assertTrue(find(index(m), installed("m", "1.0"), "13.0.8").isEmpty());
    }

    @Test
    public void theNewestReleaseThatFitsTheGameIsChosen() {
        Entry m = mod("m", release("3.0", "14.0.0"), release("2.0", "13.0.8"), release("1.0", "13.0.8"));
        assertEquals("2.0", find(index(m), installed("m", "1.0"), "13.0.8").get(0).target.version);
    }

    @Test
    public void withoutAGameVersionTheNewestCounts() {
        Entry m = mod("m", release("3.0", "14.0.0"), release("1.0", "13.0.8"));
        assertEquals("3.0", find(index(m), installed("m", "1.0"), null).get(0).target.version);
    }

    @Test
    public void modsThatAreNotInTheRepositoryAreIgnored() {
        Entry m = mod("m", release("2.0", "13.0.8"));
        assertTrue(find(index(m), installed("other", "1.0"), "13.0.8").isEmpty());
    }

    @Test
    public void updatesAreOrderedByName() {
        Entry b = mod("b", release("2.0", "13.0.8"));
        Entry a = mod("a", release("2.0", "13.0.8"));
        List<ModUpdates.Update> u = find(index(a, b), installed("b", "1.0", "a", "1.0"), "13.0.8");
        assertEquals("a", u.get(0).entry.id);
        assertEquals("b", u.get(1).entry.id);
    }
}
