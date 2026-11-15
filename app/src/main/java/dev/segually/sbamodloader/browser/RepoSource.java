package dev.segually.sbamodloader.browser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * A place mods are listed: the official repository, or one the player added by its index.json address. Only the
 * official one carries the project team's audits.
 */
public final class RepoSource {
    public final String name;
    public final String url;
    public final boolean enabled;

    public RepoSource(String name, String url, boolean enabled) {
        this.name = name;
        this.url = url;
        this.enabled = enabled;
    }

    public static RepoSource official() {
        return new RepoSource("SBAModding", RemoteModRepository.INDEX_URL, true);
    }

    public boolean isOfficial() {
        return RemoteModRepository.INDEX_URL.equals(url);
    }

    /** A short id for this address ("r" and 6 hex digits), put in front of the ids of its mods. Null for the official one. */
    public String id() {
        if (isOfficial()) return null;
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("r");
            for (int i = 0; i < 3; i++) sb.append(String.format(Locale.ROOT, "%02x", d[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The folder the index is in, with a trailing slash: where its images and stats.json are. */
    public String baseUrl() {
        return url.substring(0, url.lastIndexOf('/') + 1);
    }

    /** Whether this is an address the app will read: https, and no spaces. (Debug builds also accept localhost.) */
    public static boolean validUrl(String url, boolean allowLocalhost) {
        if (url == null || url.length() > 300 || url.matches(".*\\s.*")) return false;
        if (url.startsWith("https://") && url.length() > "https://x.y/".length()) return url.endsWith(".json");
        return allowLocalhost && url.startsWith("http://127.0.0.1") && url.endsWith(".json");
    }
}
