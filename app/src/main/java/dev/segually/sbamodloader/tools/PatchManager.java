package dev.segually.sbamodloader.tools;

/**
 * Mandatory patches are asset overlays (replacement native libraries and classes) that the
 * game's protection requires. They will be published as releases of a patch repository and
 * downloaded from there. No source exists yet, so nothing is required or installed.
 */
public final class PatchManager {
    /** Releases API endpoint of the patch repository. Empty until the repository exists. */
    public static final String RELEASES_URL = "";

    public enum Status {
        /** No patch source is configured, so no patches are required. */
        NO_SOURCE,
        /** Patches are required but not installed. */
        MISSING,
        INSTALLED
    }

    private PatchManager() {
    }

    public static Status status() {
        return RELEASES_URL.isEmpty() ? Status.NO_SOURCE : Status.MISSING;
    }
}
