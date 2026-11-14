package dev.segually.sbamodloader.tools;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a mod's file name into something a person would write: "PackMod.dll" becomes "Pack Mod",
 * "super_cars-adventure-1.2.0.sbapack" becomes "Super Cars Adventure" with the version "1.2.0".
 * Plain Java, no Android classes, so it can be tested on a desktop JVM.
 */
public final class ModNames {
    /** A humanized name and the version found at the end of the file name, or null. */
    public static final class Parsed {
        public final String name;
        public final String version;

        Parsed(String name, String version) {
            this.name = name;
            this.version = version;
        }
    }

    private static final String DISABLED = ".disabled"; // same suffix as ModPacks.DISABLED
    private static final String[] EXTENSIONS = {".sbapack", ".dll", ".so", ".zip", ".apk", ".jar"};
    // "-1.2.0", "_v2.1", " 1.0b": a version separated from the name, with at least one dot so "Mod2" stays a name.
    private static final Pattern TRAILING_VERSION = Pattern.compile("(?i)[\\s._-]+v?(\\d+(?:\\.\\d+)+[a-z]?)$");

    private ModNames() {
    }

    public static Parsed parse(String fileName) {
        String s = fileName == null ? "" : fileName.trim();
        if (s.endsWith(DISABLED)) s = s.substring(0, s.length() - DISABLED.length());
        String lower = s.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) {
                s = s.substring(0, s.length() - ext.length());
                // A native library is named like libgalazo.so; the "lib" is not part of the name.
                if (ext.equals(".so") && s.length() > 3 && s.startsWith("lib") && Character.isLetter(s.charAt(3))) {
                    s = s.substring(3);
                }
                break;
            }
        }

        String version = null;
        Matcher m = TRAILING_VERSION.matcher(s);
        if (m.find() && m.start() > 0) {
            version = m.group(1);
            s = s.substring(0, m.start());
        }

        return new Parsed(titleCase(splitWords(s)), version);
    }

    /** Just the humanized name. */
    public static String humanize(String fileName) {
        return parse(fileName).name;
    }

    private static String splitWords(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_' || c == '-' || c == '.' || c == '+') {
                out.append(' ');
                continue;
            }
            if (i > 0 && Character.isUpperCase(c)) {
                char prev = s.charAt(i - 1);
                boolean afterLowerOrDigit = Character.isLowerCase(prev) || Character.isDigit(prev);
                // "HTTPServer": split before the last capital of a run that is followed by a lowercase letter.
                boolean endOfAcronym = Character.isUpperCase(prev) && i + 1 < s.length() && Character.isLowerCase(s.charAt(i + 1));
                if (afterLowerOrDigit || endOfAcronym) out.append(' ');
            }
            out.append(c);
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    /** Capitalizes words that are all lowercase; leaves ones with capitals alone, so "HTTP" stays "HTTP". */
    private static String titleCase(String s) {
        if (s.isEmpty()) return s;
        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (out.length() > 0) out.append(' ');
            if (word.equals(word.toLowerCase(Locale.ROOT)) && !word.isEmpty()) {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            } else {
                out.append(word);
            }
        }
        return out.toString();
    }
}
