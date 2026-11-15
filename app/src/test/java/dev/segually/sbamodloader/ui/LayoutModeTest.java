package dev.segually.sbamodloader.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LayoutModeTest {
    private static LayoutMode.Signals phone() {
        LayoutMode.Signals s = new LayoutMode.Signals();
        s.smallestWidthDp = 400;
        s.widthDp = 400;
        return s;
    }

    @Test
    public void aPhoneIsNotADesktop() {
        assertFalse(LayoutMode.decide(phone()));
    }

    @Test
    public void aDeviceThatSaysItIsAPcIsADesktop() {
        LayoutMode.Signals s = phone();
        s.pcFeature = true;
        assertTrue(LayoutMode.decide(s));
    }

    @Test
    public void aChromebookAndADeskModeAreDesktops() {
        LayoutMode.Signals a = phone();
        a.chromeOs = true;
        assertTrue(LayoutMode.decide(a));
        LayoutMode.Signals b = phone();
        b.deskUiMode = true;
        assertTrue(LayoutMode.decide(b));
    }

    @Test
    public void aBigTouchOnlyTabletKeepsThePhoneLayout() {
        LayoutMode.Signals s = new LayoutMode.Signals();
        s.smallestWidthDp = 800;
        s.widthDp = 1280;
        assertFalse(LayoutMode.decide(s));
    }

    @Test
    public void aBigScreenWithAMouseIsADesktop() {
        LayoutMode.Signals s = new LayoutMode.Signals();
        s.smallestWidthDp = 800;
        s.widthDp = 1280;
        s.pointerOrKeyboard = true;
        assertTrue(LayoutMode.decide(s));
    }

    @Test
    public void waydroidOnABigWindowIsADesktopEvenWithoutAMouse() {
        LayoutMode.Signals s = new LayoutMode.Signals();
        s.smallestWidthDp = 700;
        s.widthDp = 1256;
        s.desktopProduct = true;
        assertTrue(LayoutMode.decide(s));
    }

    @Test
    public void aMouseOnASmallScreenIsNotEnough() {
        LayoutMode.Signals s = phone();
        s.pointerOrKeyboard = true;
        assertFalse(LayoutMode.decide(s));
    }

    @Test
    public void aSmallEmulatorWindowIsNotADesktop() {
        LayoutMode.Signals s = phone();
        s.desktopProduct = true;
        assertFalse(LayoutMode.decide(s));
    }

    @Test
    public void aWideFreeformWindowIsADesktop() {
        LayoutMode.Signals s = new LayoutMode.Signals();
        s.smallestWidthDp = 500;
        s.widthDp = 1100;
        s.freeform = true;
        assertTrue(LayoutMode.decide(s));
    }
}
