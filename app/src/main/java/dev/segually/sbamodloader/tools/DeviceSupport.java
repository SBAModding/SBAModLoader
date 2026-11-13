package dev.segually.sbamodloader.tools;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import dev.segually.sbamodloader.R;

/**
 * Judges whether a device can be expected to run Fusion (the .NET runtime and BepInEx next to the game).
 * Fusion needs a lot of memory, so Android Go devices, devices with little RAM and very old Android versions
 * are treated as unsupported. The verdict only sets defaults; the user can still turn Fusion on.
 */
public final class DeviceSupport {
    /** Below this much total RAM Fusion is unsupported. Phones sold as 4 GB report about 3.6 GB. */
    private static final long MIN_TOTAL_RAM_BYTES = 3_500_000_000L;
    private static final int MIN_SDK = 28;

    private DeviceSupport() {
    }

    /** Human readable reasons Fusion is unsupported here; empty when the device is fine. */
    public static List<String> fusionProblems(Context context) {
        List<String> reasons = new ArrayList<>();
        ActivityManager am = context.getSystemService(ActivityManager.class);
        boolean go = am != null && am.isLowRamDevice();
        if (!go) {
            try {
                go = context.getPackageManager().hasSystemFeature("android.hardware.ram.low");
            } catch (RuntimeException ignored) {
            }
        }
        if (go) reasons.add(context.getString(R.string.fusion_reason_go));
        if (am != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            if (info.totalMem > 0 && info.totalMem < MIN_TOTAL_RAM_BYTES) {
                reasons.add(context.getString(R.string.fusion_reason_ram, info.totalMem / 1_000_000_000.0));
            }
        }
        if (Build.VERSION.SDK_INT < MIN_SDK) {
            reasons.add(context.getString(R.string.fusion_reason_os, Build.VERSION.RELEASE));
        }
        return reasons;
    }

    public static boolean fusionSupported(Context context) {
        return fusionProblems(context).isEmpty();
    }
}
