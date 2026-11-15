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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import org.junit.Test;

import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.InstallPlanner.Kind;
import dev.segually.sbamodloader.browser.InstallPlanner.Plan;

public class InstallPlannerTest {
    private static Plan plan(Entry target, String version, java.util.Map<String, Entry> index,
                             java.util.Map<String, String> installed, String game) {
        return InstallPlanner.plan(target, target.version(version), index, installed, game);
    }

    @Test
    public void aPlainInstallOfTheLatestMatchingReleaseNeedsNoConfirmation() {
        Entry m = mod("m", release("1.0", "13.0.8"));
        Plan p = plan(m, "1.0", index(m), installed(), "13.0.8");
        assertEquals(asList("m@1.0"), steps(p));
        assertEquals(emptyList(), kinds(p));
        assertFalse(p.needsConfirmation());
        assertFalse(p.blocked());
    }

    @Test
    public void aReleaseForAnotherGameVersionIsWarnedAbout() {
        Entry m = mod("m", release("1.0", "12.0.0"));
        Plan p = plan(m, "1.0", index(m), installed(), "13.0.8");
        assertEquals(asList(Kind.GAME_MISMATCH), kinds(p));
        assertEquals("12.0.0", p.issues.get(0).other);
        assertTrue(p.needsConfirmation());
        assertFalse("a mismatch warns, it does not block", p.blocked());
    }

    @Test
    public void anUnknownGameVersionIsNotAMismatch() {
        Entry m = mod("m", release("1.0", "12.0.0"));
        assertEquals(emptyList(), kinds(plan(m, "1.0", index(m), installed(), null)));
    }

    @Test
    public void anOlderReleaseIsFlaggedAsOutdated() {
        Entry m = mod("m", release("2.0", "13.0.8"), release("1.0", "13.0.8"));
        Plan p = plan(m, "1.0", index(m), installed(), "13.0.8");
        assertEquals(asList(Kind.MOD_OUTDATED), kinds(p));
        assertEquals("2.0", p.issues.get(0).other);
    }

    @Test
    public void aMissingDependencyIsInstalledFirst() {
        Entry lib = mod("lib", release("1.4", "13.0.8"), release("1.0", "13.0.8"));
        Entry m = mod("m", release("1.0", "13.0.8", dep("lib", "1.0", null)));
        Plan p = plan(m, "1.0", index(m, lib), installed(), "13.0.8");
        assertEquals(asList("lib@1.4", "m@1.0"), steps(p));
        assertTrue(p.steps.get(0).dependency);
        assertFalse(p.steps.get(1).dependency);
        assertEquals(emptyList(), kinds(p));
        assertTrue("two downloads need a summary", p.needsConfirmation());
        assertEquals(2000, p.totalBytes());
    }

    @Test
    public void aDependencyThatFitsIsLeftAlone() {
        Entry lib = mod("lib", release("1.4", "13.0.8"));
        Entry m = mod("m", release("1.0", "13.0.8", dep("lib", "1.0", "2.0")));
        Plan p = plan(m, "1.0", index(m, lib), installed("lib", "1.2"), "13.0.8");
        assertEquals(asList("m@1.0"), steps(p));
        assertEquals(emptyList(), kinds(p));
    }

    @Test
    public void theNewestDependencyInsideTheBoundsIsChosen() {
        Entry lib = mod("lib", release("2.0", "13.0.8"), release("1.5", "13.0.8"), release("1.0", "13.0.8"));
        Entry m = mod("m", release("1.0", "13.0.8", dep("lib", null, "1.9")));
        assertEquals(asList("lib@1.5", "m@1.0"), steps(plan(m, "1.0", index(m, lib), installed(), "13.0.8")));
    }

    @Test
    public void aDependencyMadeForTheGameBeatsANewerOneThatIsNot() {
        Entry lib = mod("lib", release("2.0", "14.0.0"), release("1.0", "13.0.8"));
        Entry m = mod("m", release("1.0", "13.0.8", dep("lib", null, null)));
        Plan p = plan(m, "1.0", index(m, lib), installed(), "13.0.8");
        assertEquals(asList("lib@1.0", "m@1.0"), steps(p));
        assertEquals(emptyList(), kinds(p));
    }

    @Test
    public void aDependencyOnlyMadeForOtherGamesIsWarnedAbout() {
        Entry lib = mod("lib", release("1.0", "12.0.0"));
        Entry m = mod("m", release("1.0", "13.0.8", dep("lib", null, null)));
        Plan p = plan(m, "1.0", index(m, lib), installed(), "13.0.8");
        assertEquals(asList(Kind.DEP_GAME_MISMATCH), kinds(p));
        assertEquals(asList("lib@1.0", "m@1.0"), steps(p));
        assertFalse(p.blocked());
    }

    @Test
    public void anInstalledDependencyThatIsTooOldIsUpdated() {
        Entry lib = mod("lib", release("1.4", "13.0.8"), release("1.0", "13.0.8"));
        Entry m = mod("m", release("1.0", "13.0.8", dep("lib", "1.2", null)));
        Plan p = plan(m, "1.0", index(m, lib), installed("lib", "1.0"), "13.0.8");
        assertEquals(asList(Kind.DEP_OUTDATED), kinds(p));
        assertEquals("1.0", p.issues.get(0).version);
        assertEquals("1.0", p.steps.get(0).replaces);
        assertEquals(asList("lib@1.4", "m@1.0"), steps(p));
    }

    @Test
    public void anInstalledDependencyThatIsTooNewIsReplacedByAnOlderOne() {
        Entry lib = mod("lib", release("2.0", "13.0.8"), release("1.5", "13.0.8"), release("1.0", "13.0.8"));
        Entry m = mod("m", release("1.0", "13.0.8", dep("lib", null, "1.5")));
        Plan p = plan(m, "1.0", index(m, lib), installed("lib", "2.0"), "13.0.8");
        assertEquals(asList(Kind.DEP_NEEDS_OLDER), kinds(p));
        assertEquals("2.0", p.issues.get(0).version);
        assertEquals(asList("lib@1.5", "m@1.0"), steps(p));
        assertEquals("2.0", p.steps.get(0).replaces);
        assertFalse(p.blocked());
    }

    @Test
    public void aDependencyThatIsNotInTheRepositoryBlocks() {
        Entry m = mod("m", release("1.0", "13.0.8", dep("ghost", null, null)));
        Plan p = plan(m, "1.0", index(m), installed(), "13.0.8");
        assertEquals(asList(Kind.DEP_MISSING), kinds(p));
        assertTrue(p.blocked());
    }

    @Test
    public void noReleaseInsideTheBoundsBlocks() {
        Entry lib = mod("lib", release("1.0", "13.0.8"));
        Entry m = mod("m", release("1.0", "13.0.8", dep("lib", "2.0", null)));
        Plan p = plan(m, "1.0", index(m, lib), installed(), "13.0.8");
        assertEquals(asList(Kind.DEP_NO_VERSION), kinds(p));
        assertTrue(p.blocked());
    }

    @Test
    public void chainsAreInstalledDependenciesFirst() {
        Entry c = mod("c", release("1.0", "13.0.8"));
        Entry b = mod("b", release("1.0", "13.0.8", dep("c", null, null)));
        Entry a = mod("a", release("1.0", "13.0.8", dep("b", null, null)));
        Plan p = plan(a, "1.0", index(a, b, c), installed(), "13.0.8");
        assertEquals(asList("c@1.0", "b@1.0", "a@1.0"), steps(p));
        assertEquals(3000, p.totalBytes());
    }

    @Test
    public void aSharedDependencyIsInstalledOnce() {
        Entry lib = mod("lib", release("1.0", "13.0.8"));
        Entry b = mod("b", release("1.0", "13.0.8", dep("lib", null, null)));
        Entry a = mod("a", release("1.0", "13.0.8", dep("b", null, null), dep("lib", null, null)));
        Plan p = plan(a, "1.0", index(a, b, lib), installed(), "13.0.8");
        assertEquals(asList("lib@1.0", "b@1.0", "a@1.0"), steps(p));
    }

    @Test
    public void incompatibleDemandsOnTheSameModConflict() {
        Entry lib = mod("lib", release("2.0", "13.0.8"), release("1.0", "13.0.8"));
        Entry b = mod("b", release("1.0", "13.0.8", dep("lib", "2.0", null)));
        Entry a = mod("a", release("1.0", "13.0.8", dep("lib", null, "1.5"), dep("b", null, null)));
        Plan p = plan(a, "1.0", index(a, b, lib), installed(), "13.0.8");
        assertTrue(kinds(p).contains(Kind.DEP_CONFLICT));
        assertTrue(p.blocked());
    }

    @Test
    public void aCycleTerminatesAndInstallsEachModOnce() {
        Entry b = mod("b", release("1.0", "13.0.8", dep("a", null, null)));
        Entry a = mod("a", release("1.0", "13.0.8", dep("b", null, null)));
        Plan p = plan(a, "1.0", index(a, b), installed(), "13.0.8");
        assertEquals(asList("b@1.0", "a@1.0"), steps(p));
        assertEquals(emptyList(), kinds(p));
    }

    @Test
    public void aCycleWhoseBoundsCannotBeMetIsAConflictNotALoop() {
        Entry b = mod("b", release("1.0", "13.0.8", dep("a", "2.0", null)));
        Entry a = mod("a", release("1.0", "13.0.8", dep("b", null, null)));
        Plan p = plan(a, "1.0", index(a, b), installed(), "13.0.8");
        assertTrue(p.blocked());
    }

    @Test
    public void theReplacedVersionOfTheTargetIsRecorded() {
        Entry m = mod("m", release("2.0", "13.0.8"), release("1.0", "13.0.8"));
        Plan p = plan(m, "2.0", index(m), installed("m", "1.0"), "13.0.8");
        assertEquals("1.0", p.steps.get(0).replaces);
        assertNull(plan(m, "2.0", index(m), installed(), "13.0.8").steps.get(0).replaces);
    }
}
