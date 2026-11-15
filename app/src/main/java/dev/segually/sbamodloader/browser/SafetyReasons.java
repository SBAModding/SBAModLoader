package dev.segually.sbamodloader.browser;

import java.util.ArrayList;
import java.util.List;

import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Version;

/**
 * Why a release is rated safe, as a list the page can show. Plain Java, no Android classes, so it is tested on a
 * desktop JVM.
 *
 * <p>Nothing here is a verdict the app makes up: every reason is a fact from the repository (the audit, the reviewer's
 * notes, what the file is made of, where it is hosted, who made it). The first reason is always the audit itself; a
 * release without one is never listed at all.
 */
public final class SafetyReasons {
    private SafetyReasons() {
    }

    public enum Type {
        /** Checked by the project's reviewers: {@code a} is the version, {@code b} the date, {@code c} who. */
        AUDITED,
        /** A note the reviewer wrote: {@code a} is the text. */
        NOTE,
        /** Contains only images, sounds or other replaced files, no code. */
        NO_CODE,
        /** Contains native code. */
        NATIVE_CODE,
        /** Contains Java code. */
        JAVA_CODE,
        /** Is a plugin (.NET code). */
        PLUGIN_CODE,
        /** Open source: {@code a} is the licence, {@code b} the repository, {@code c} the tag. */
        OPEN_SOURCE,
        /** Closed source: {@code a} is the licence or terms, {@code b} the host the file comes from. */
        CLOSED_SOURCE,
        /** From a repository the SBAModLoader team does not run: {@code a} is its name. Nothing else is claimed. */
        UNVERIFIED,
        /** Made by the owner of the project. */
        OWNER,
        /** Earlier versions were checked too: {@code a} is how many. */
        HISTORY
    }

    /** One reason. Which of the strings are set depends on {@link #type}. */
    public static final class Reason {
        public final Type type;
        public final String a;
        public final String b;
        public final String c;

        Reason(Type type, String a, String b, String c) {
            this.type = type;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        /** Whether this reason counts in the mod's favour (as opposed to a neutral fact the player should know). */
        public boolean positive() {
            switch (type) {
                case AUDITED:
                case NOTE:
                case NO_CODE:
                case OPEN_SOURCE:
                case OWNER:
                case HISTORY:
                    return true;
                default:
                    return false;
            }
        }
    }

    /**
     * @param owner the name the project's owner publishes under; mods by that author get the OWNER reason
     */
    public static List<Reason> build(Entry entry, Version release, String owner) {
        List<Reason> out = new ArrayList<>();
        if (!entry.official()) {
            // Another repository's audits, notes and contents are only its word: none of it is shown as a reason.
            out.add(new Reason(Type.UNVERIFIED, entry.repoName, null, null));
            if (entry.openSource) {
                out.add(new Reason(Type.OPEN_SOURCE, entry.license, release.source.repo, release.source.tag));
            } else {
                out.add(new Reason(Type.CLOSED_SOURCE, entry.license, release.source.host(), null));
            }
            return out;
        }
        out.add(new Reason(Type.AUDITED, release.audit.version, release.audit.date, release.audit.by));
        for (String note : release.audit.notes) out.add(new Reason(Type.NOTE, note, null, null));

        List<String> contains = release.contains;
        if (!contains.isEmpty()) {
            if (contains.size() == 1 && contains.get(0).equals("assets")) {
                out.add(new Reason(Type.NO_CODE, null, null, null));
            } else {
                if (contains.contains("native")) out.add(new Reason(Type.NATIVE_CODE, null, null, null));
                if (contains.contains("java")) out.add(new Reason(Type.JAVA_CODE, null, null, null));
                if (contains.contains("fusion")) out.add(new Reason(Type.PLUGIN_CODE, null, null, null));
            }
        }

        if (entry.openSource) {
            out.add(new Reason(Type.OPEN_SOURCE, entry.license, release.source.repo, release.source.tag));
        } else {
            out.add(new Reason(Type.CLOSED_SOURCE, entry.license, release.source.host(), null));
        }

        if (owner != null && entry.author != null && entry.author.trim().equalsIgnoreCase(owner)) {
            out.add(new Reason(Type.OWNER, owner, null, null));
        }

        int earlier = 0;
        for (Version v : entry.versions) {
            if (v != release && v.audit != null && ModVersions.compare(v.version, release.version) < 0) earlier++;
        }
        if (earlier > 0) out.add(new Reason(Type.HISTORY, Integer.toString(earlier), null, null));
        return out;
    }
}
