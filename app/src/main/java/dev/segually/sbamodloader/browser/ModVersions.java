package dev.segually.sbamodloader.browser;

import dev.segually.sbamodloader.browser.BrowserModels.Dependency;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Version;

/**
 * Ordering and matching of mod versions. Plain Java, no Android classes, so it is tested on a desktop JVM.
 *
 * <p>Mod versions look like {@code 1.2}, {@code 1.2.3} or {@code 1.2.3-beta} (the repository enforces this). Numbers
 * compare numerically, a missing number counts as 0, and a {@code -suffix} sorts before the same version without one.
 * This is not the loader's own scheme ({@code AppUpdater}), which also has letter suffixes like 0.0.1c.
 */
public final class ModVersions {
    private ModVersions() {
    }

    /** Negative when {@code a} is older than {@code b}, positive when newer, 0 when they are the same version. */
    public static int compare(String a, String b) {
        String[] pa = split(a);
        String[] pb = split(b);
        String[] na = pa[0].split("\\.");
        String[] nb = pb[0].split("\\.");
        for (int i = 0; i < Math.max(na.length, nb.length); i++) {
            int c = Long.compare(number(na, i), number(nb, i));
            if (c != 0) return c;
        }
        boolean preA = pa[1] != null && pa[1].equals("-");
        boolean preB = pb[1] != null && pb[1].equals("-");
        if (preA != preB) return preA ? -1 : 1;
        return pa[2].compareTo(pb[2]);
    }

    /** {numbers, separator or null, suffix}. Anything unreadable becomes version 0. */
    private static String[] split(String v) {
        int i = 0;
        while (i < v.length() && (Character.isDigit(v.charAt(i)) || v.charAt(i) == '.')) i++;
        String nums = v.substring(0, i);
        if (nums.isEmpty()) nums = "0";
        if (i < v.length() && (v.charAt(i) == '-' || v.charAt(i) == '+')) {
            return new String[]{nums, String.valueOf(v.charAt(i)), v.substring(i + 1)};
        }
        return new String[]{nums, null, v.substring(i)};
    }

    private static long number(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) return 0;
        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Whether {@code version} is inside the dependency's bounds. A missing bound is open. */
    public static boolean satisfies(Dependency d, String version) {
        if (d.min != null && compare(version, d.min) < 0) return false;
        return d.max == null || compare(version, d.max) <= 0;
    }

    /** The newest release made for {@code gameVersion}, or null when none was (or the game version is unknown). */
    public static Version bestFor(Entry entry, String gameVersion) {
        Version best = null;
        for (Version v : entry.versions) {
            if (v.supportsGame(gameVersion) && (best == null || compare(v.version, best.version) > 0)) best = v;
        }
        return best;
    }

    /** The release to offer first: the newest one made for this game, otherwise the newest. */
    public static Version defaultFor(Entry entry, String gameVersion) {
        Version best = bestFor(entry, gameVersion);
        return best != null ? best : entry.latest();
    }
}
