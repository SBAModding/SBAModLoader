package dev.segually.sbamodloader.tools;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/** Looks up the newest released version. Disabled until a releases endpoint is configured. */
public final class UpdateChecker {
    /** JSON endpoint returning {"tag_name": "x.y.z"}, e.g. a GitHub "latest release" API URL. */
    public static final String LATEST_RELEASE_URL = "";

    public enum State { UP_TO_DATE, OUTDATED, UNKNOWN }

    private UpdateChecker() {
    }

    public static State check(String currentVersion) {
        if (LATEST_RELEASE_URL.isEmpty()) return State.UNKNOWN;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            try (InputStream in = c.getInputStream(); Scanner s = new Scanner(in).useDelimiter("\\A")) {
                String latest = new JSONObject(s.hasNext() ? s.next() : "{}").optString("tag_name", "");
                if (latest.isEmpty()) return State.UNKNOWN;
                return latest.replaceFirst("^v", "").equals(currentVersion) ? State.UP_TO_DATE : State.OUTDATED;
            }
        } catch (Exception e) {
            return State.UNKNOWN;
        }
    }
}
