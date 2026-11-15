package dev.segually.sbamodloader.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The mod repository's data model. */
public final class BrowserModels {
    private BrowserModels() {
    }

    /** What the mod is, which decides how it is installed. */
    public enum Kind {
        FUSION("fusion"), NATIVE("native"), PACK("sbapack");

        public final String key;

        Kind(String key) {
            this.key = key;
        }

        public static Kind of(String key) {
            for (Kind k : values()) if (k.key.equals(key)) return k;
            throw new IllegalArgumentException("Unknown mod kind: " + key);
        }
    }

    /** Two levels, as in the repository: a category and one of its subcategories. */
    public enum Category {
        TEXTURE_PACK("texture_pack"), MOD("mod");

        public final String key;

        Category(String key) {
            this.key = key;
        }

        public static Category of(String key) {
            for (Category c : values()) if (c.key.equals(key)) return c;
            throw new IllegalArgumentException("Unknown category: " + key);
        }
    }

    public enum Sub {
        FUN(Category.TEXTURE_PACK, "fun"),
        EXTRA_CONTENT(Category.TEXTURE_PACK, "extra_content"),
        REPLACEMENT(Category.TEXTURE_PACK, "replacement"),
        QUALITY_OF_LIFE(Category.MOD, "quality_of_life"),
        EXPANSION(Category.MOD, "expansion"),
        CHANGES(Category.MOD, "changes"),
        CHEATS(Category.MOD, "cheats"),
        PORT(Category.MOD, "port");

        public final Category parent;
        public final String key;

        Sub(Category parent, String key) {
            this.parent = parent;
            this.key = key;
        }

        public static Sub of(Category parent, String key) {
            for (Sub s : values()) if (s.parent == parent && s.key.equals(key)) return s;
            throw new IllegalArgumentException("Unknown subcategory: " + parent.key + "/" + key);
        }

        public static List<Sub> of(Category parent) {
            List<Sub> out = new ArrayList<>();
            for (Sub s : values()) if (s.parent == parent) out.add(s);
            return out;
        }
    }

    /**
     * Where the file comes from. The hash in the repository is the source of truth either way: a download is
     * only installed if it matches it.
     */
    public static final class Source {
        public final boolean github;
        /** GitHub: "owner/repo". */
        public final String repo;
        public final String tag;
        public final String asset;
        /** External: the full URL. */
        public final String url;

        public Source(boolean github, String repo, String tag, String asset, String url) {
            this.github = github;
            this.repo = repo;
            this.tag = tag;
            this.asset = asset;
            this.url = url;
        }

        public String downloadUrl() {
            return github ? "https://github.com/" + repo + "/releases/download/" + tag + "/" + asset : url;
        }

        public String host() {
            String u = downloadUrl();
            int start = u.indexOf("://");
            start = start < 0 ? 0 : start + 3;
            int end = u.indexOf('/', start);
            return end < 0 ? u.substring(start) : u.substring(start, end);
        }
    }

    public static final class Audit {
        public final String version;
        public final String date;
        public final String by;

        public Audit(String version, String date, String by) {
            this.version = version;
            this.date = date;
            this.by = by;
        }
    }

    /** A mod this release needs, and the versions of it that it works with (both bounds inclusive, either may be null). */
    public static final class Dependency {
        public final String id;
        public final String min;
        public final String max;

        public Dependency(String id, String min, String max) {
            this.id = id;
            this.min = min;
            this.max = max;
        }
    }

    /**
     * One release of a mod: what to download and what it was made for. An {@link Entry} lists every release it
     * still offers, newest first.
     */
    public static final class Version {
        public String version, sha256, changelog;
        public List<String> gameVersions = new ArrayList<>();
        public List<String> mirrors = new ArrayList<>();
        public List<Dependency> dependencies = new ArrayList<>();
        public long size, updatedMillis;
        public Source source;
        public Audit audit;

        /** The primary download URL followed by the mirrors; every one must serve exactly the pinned file. */
        public List<String> downloadUrls() {
            List<String> urls = new ArrayList<>();
            urls.add(source.downloadUrl());
            urls.addAll(mirrors);
            return urls;
        }

        /** The file name the mod ships as, taken from the end of its URL. */
        public String assetName() {
            String u = source.downloadUrl();
            int q = u.indexOf('?');
            if (q >= 0) u = u.substring(0, q);
            return u.substring(u.lastIndexOf('/') + 1);
        }

        public boolean supportsGame(String gameVersion) {
            return gameVersion != null && gameVersions.contains(gameVersion);
        }
    }

    public static final class Entry {
        public String id, name, author, summary, description, version, license, sha256, changelog;
        /** Every release still offered, newest first. The first one is the same release as the fields below. */
        public List<Version> versions = new ArrayList<>();
        /** Repository-relative image paths (under images/&lt;id&gt;/), or null / empty when there are none. */
        public String icon;
        public List<String> screenshots = new ArrayList<>();
        public Kind kind;
        public List<Sub> categories = new ArrayList<>();
        public List<String> gameVersions = new ArrayList<>();
        public List<String> tags = new ArrayList<>();
        public List<String> requires = new ArrayList<>();
        public List<String> mirrors = new ArrayList<>();
        public long size, downloads, updatedMillis;
        public float rating;
        public int ratingCount;
        public boolean openSource;
        public Source source;
        public Audit audit;

        /** The newest release. */
        public Version latest() {
            return versions.get(0);
        }

        /** The release with this version number, or null when it is not offered any more. */
        public Version version(String number) {
            for (Version v : versions) if (v.version.equals(number)) return v;
            return null;
        }

        /** The primary download URL followed by the mirrors; every one must serve exactly the pinned file. */
        public List<String> downloadUrls() {
            return latest().downloadUrls();
        }

        /** The file name the newest release ships as, taken from the end of its URL. */
        public String assetName() {
            return latest().assetName();
        }

        public boolean inCategory(Category c) {
            for (Sub s : categories) if (s.parent == c) return true;
            return false;
        }

        public String searchText() {
            StringBuilder sb = new StringBuilder(name).append(' ').append(author).append(' ').append(summary);
            for (String t : tags) sb.append(' ').append(t);
            return sb.toString().toLowerCase(Locale.ROOT);
        }
    }
}
