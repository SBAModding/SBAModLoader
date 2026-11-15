package dev.segually.sbamodloader.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.os.Build;
import android.view.InputDevice;

import dev.segually.sbamodloader.SBASettings;

/**
 * Whether the app is running on a desktop (an Android PC, an emulator such as Waydroid or BlissOS, a Chromebook, a
 * tablet with a desk setup) and so should use the desktop layout: a navigation rail at the side and wide, two-pane pages.
 */
public final class LayoutMode {
    /** What MainActivity decided last, so pages can pad for the right navigation without asking the device again. */
    public static volatile boolean current;

    private LayoutMode() {
    }

    /** What the device says about itself; {@link #decide} turns it into an answer. */
    public static final class Signals {
        public boolean pcFeature;
        public boolean chromeOs;
        public boolean freeform;
        public boolean deskUiMode;
        public boolean pointerOrKeyboard;
        public boolean desktopProduct;
        public int smallestWidthDp;
        public int widthDp;
    }

    /** Pure logic, tested without a device. */
    public static boolean decide(Signals s) {
        // Devices that say they are a PC or a desk.
        if (s.pcFeature || s.chromeOs || s.deskUiMode) return true;
        boolean big = s.smallestWidthDp >= 720 || s.widthDp >= 1000;
        if (!big) return false;
        // A big screen with a mouse or keyboard, or one of the known Android-on-PC systems, is a desktop. A big
        // touch-only tablet is not: it keeps the phone layout (which is already two-pane when it is wide).
        return s.pointerOrKeyboard || s.desktopProduct || (s.freeform && s.widthDp >= 1000);
    }

    /** True when the desktop layout should be used, honouring the player's choice in Settings. */
    public static boolean isDesktop(Context context) {
        int mode = SBASettings.getLayoutMode(context);
        if (mode == SBASettings.LAYOUT_DESKTOP) return true;
        if (mode == SBASettings.LAYOUT_PHONE) return false;
        return decide(signals(context));
    }

    public static Signals signals(Context context) {
        Signals s = new Signals();
        PackageManager pm = context.getPackageManager();
        s.pcFeature = pm.hasSystemFeature("android.hardware.type.pc");
        s.chromeOs = pm.hasSystemFeature("org.chromium.arc") || pm.hasSystemFeature("org.chromium.arc.device_management");
        s.freeform = pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT);
        Configuration c = context.getResources().getConfiguration();
        s.deskUiMode = (c.uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK;
        s.smallestWidthDp = c.smallestScreenWidthDp;
        s.widthDp = c.screenWidthDp;
        s.pointerOrKeyboard = hasPointerOrKeyboard();
        s.desktopProduct = looksLikeDesktopProduct();
        return s;
    }

    private static boolean hasPointerOrKeyboard() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual() && (d.getSources() & InputDevice.SOURCE_MOUSE) == 0) continue;
            if ((d.getSources() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) return true;
            if ((d.getSources() & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                    && d.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) return true;
        }
        return false;
    }

    private static boolean looksLikeDesktopProduct() {
        String all = (Build.BRAND + " " + Build.MODEL + " " + Build.DEVICE + " " + Build.PRODUCT + " " + Build.HARDWARE
                + " " + Build.MANUFACTURER).toLowerCase(java.util.Locale.ROOT);
        for (String known : new String[]{"waydroid", "bliss", "redroid", "windows", "genymotion", "bluestacks", "anbox"}) {
            if (all.contains(known)) return true;
        }
        return false;
    }
}
