package dev.segually.sbamodloader.browser;

import static dev.segually.sbamodloader.browser.Fixtures.mod;
import static dev.segually.sbamodloader.browser.Fixtures.release;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.GameUpdate.Action;
import dev.segually.sbamodloader.browser.GameUpdate.Fit;

public class GameUpdateTest {
    @Test
    public void aReleaseThatListsTheGameFits() {
        Entry m = mod("m", release("1.1", "13.0.8,13.1.0"), release("1.0", "13.0.8"));
        assertEquals(Fit.COMPATIBLE, GameUpdate.fit(m, "1.0", "13.0.8"));
        assertEquals(Fit.INCOMPATIBLE, GameUpdate.fit(m, "1.0", "13.1.0"));
        assertEquals(Fit.COMPATIBLE, GameUpdate.fit(m, "1.1", "13.1.0"));
    }

    @Test
    public void aReleaseTheRepositoryDoesNotListCannotBeJudged() {
        Entry m = mod("m", release("1.0", "13.0.8"));
        assertEquals(Fit.UNKNOWN, GameUpdate.fit(m, "2.0", "13.1.0"));
        assertEquals(Fit.UNKNOWN, GameUpdate.fit(null, "1.0", "13.1.0"));
        assertEquals(Fit.UNKNOWN, GameUpdate.fit(m, "1.0", null));
    }

    @Test
    public void anIncompatibleModIsUpdatedWhenThereIsAFittingRelease() {
        assertEquals(Action.UPDATE, GameUpdate.decide(Fit.INCOMPATIBLE, true, false, false));
    }

    @Test
    public void anIncompatibleModWithoutAnUpdateIsSwitchedOff() {
        assertEquals(Action.DISABLE, GameUpdate.decide(Fit.INCOMPATIBLE, false, false, false));
    }

    @Test
    public void aModTheyChoseToKeepIsLeftAlone() {
        assertEquals(Action.NOTHING, GameUpdate.decide(Fit.INCOMPATIBLE, false, false, true));
        assertEquals(Action.NOTHING, GameUpdate.decide(Fit.INCOMPATIBLE, true, false, true));
    }

    @Test
    public void aModSwitchedOffByUsComesBackWhenItFits() {
        assertEquals(Action.ENABLE, GameUpdate.decide(Fit.COMPATIBLE, false, true, false));
        assertEquals(Action.NOTHING, GameUpdate.decide(Fit.COMPATIBLE, false, false, false));
    }

    @Test
    public void aModNobodyCanJudgeIsNeverTouched() {
        assertEquals(Action.NOTHING, GameUpdate.decide(Fit.UNKNOWN, true, false, false));
        assertEquals(Action.NOTHING, GameUpdate.decide(Fit.UNKNOWN, false, true, false));
    }
}
