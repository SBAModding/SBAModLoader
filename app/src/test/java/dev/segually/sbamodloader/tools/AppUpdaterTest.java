package dev.segually.sbamodloader.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppUpdaterTest {
    @Test
    public void ordersLettersAndNumbersAsBefore() {
        assertTrue(AppUpdater.compareVersions("0.0.4.2b", "0.0.4.2a") > 0);
        assertTrue(AppUpdater.compareVersions("0.0.4.2", "0.0.4.2b") > 0);
        assertTrue(AppUpdater.compareVersions("0.0.5a", "0.0.4.2") > 0);
        assertTrue(AppUpdater.compareVersions("0.0.5", "0.0.5a") > 0);
    }

    @Test
    public void aMarkerIsNotPartOfTheOrder() {
        assertEquals(0, AppUpdater.compareVersions("0.0.5a-compose", "0.0.5a"));
        assertTrue(AppUpdater.compareVersions("0.0.5b-compose", "0.0.5a-compose") > 0);
        assertTrue(AppUpdater.compareVersions("0.0.5-compose", "0.0.5a-compose") > 0);
    }

    @Test
    public void readsAnyMarker() {
        assertEquals("compose", AppUpdater.marker("0.0.5a-compose"));
        assertEquals("beta.2", AppUpdater.marker("v0.0.6-beta.2"));
        assertEquals("multi-platform", AppUpdater.marker("0.1.0a-multi-platform"));
        assertEquals("", AppUpdater.marker("0.0.4.2"));
        assertEquals("", AppUpdater.marker("not a version"));
    }

    @Test
    public void aMainBuildNeverSeesAMarkedRelease() {
        assertFalse(AppUpdater.isOffered("0.0.5a-compose", "0.0.4.2"));
        assertFalse(AppUpdater.isOffered("0.0.9-anything", "0.0.4.2"));
        assertTrue(AppUpdater.isOffered("0.0.4.3", "0.0.4.2"));
        assertTrue(AppUpdater.isOffered("0.0.5a", "0.0.4.2"));
    }

    @Test
    public void aMarkedBuildSeesItsOwnTrackAndNewerMainReleases() {
        String running = "0.0.5a-compose";
        assertTrue(AppUpdater.isOffered("0.0.5b-compose", running));
        assertTrue(AppUpdater.isOffered("0.0.5", running));
        assertTrue(AppUpdater.isOffered("0.0.6", running));
        assertFalse(AppUpdater.isOffered("0.0.5a-compose", running));
        assertFalse(AppUpdater.isOffered("0.0.4.3", running));
        assertFalse(AppUpdater.isOffered("0.0.4.2", running));
    }

    @Test
    public void aMarkedBuildIgnoresOtherMarkers() {
        assertFalse(AppUpdater.isOffered("0.0.6a-multiplatform", "0.0.5a-compose"));
        assertFalse(AppUpdater.isOffered("0.0.5b-beta", "0.0.5a-compose"));
    }

    @Test
    public void unreadableCandidatesAreNeverOffered() {
        assertFalse(AppUpdater.isOffered("garbage", "0.0.4.2"));
        assertFalse(AppUpdater.isOffered("", "0.0.5a-compose"));
    }
}
