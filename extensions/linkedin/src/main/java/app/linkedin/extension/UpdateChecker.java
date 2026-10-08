package app.linkedin.extension;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Compares the installed patch bundle with the latest stable GitHub release. */
final class UpdateChecker {
    static final String REPO = "Kratapand26/Gunshootr";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + REPO + "/releases/latest";

    enum Status {
        /** A newer stable release exists. */
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        /** The repository has no stable release yet (GitHub answers 404). */
        NO_RELEASE,
        /** Offline, timeout, or another HTTP error. */
        FAILED
    }

    interface Callback {
        /** latestVersion is only set for UPDATE_AVAILABLE and UP_TO_DATE. */
        void onResult(Status status, String latestVersion);
    }

    private UpdateChecker() {
    }

    static void check(Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String[] result = fetchLatestVersion();
            Status status;
            String latest = null;
            if (result == null) {
                status = Status.FAILED;
            } else if (result.length == 0) {
                status = Status.NO_RELEASE;
            } else {
                latest = result[0];
                status = isNewer(latest, Settings.patchesVersion()) ? Status.UPDATE_AVAILABLE : Status.UP_TO_DATE;
            }
            Status finalStatus = status;
            String finalLatest = latest;
            main.post(() -> callback.onResult(finalStatus, finalLatest));
        }, "GunshootrUpdateCheck").start();
    }

    /** {version} on success, {} when there is no stable release, null on failure. */
    private static String[] fetchLatestVersion() {
        HttpURLConnection connection = null;
        try {
            connection = NetworkSafety.open(LATEST_RELEASE_API, NetworkSafety::isUpdateUrl);
            int code = connection.getResponseCode();
            if (code == 404) return new String[0];
            if (code != 200) return null;
            try (InputStream in = connection.getInputStream()) {
                String tag = new JSONObject(new String(NetworkSafety.readBounded(in, 128 * 1024),
                        StandardCharsets.UTF_8)).optString("tag_name", "");
                if (tag.isEmpty()) return null;
                return new String[]{tag.startsWith("v") ? tag.substring(1) : tag};
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * True if candidate's major.minor.patch is greater than current's, or equal while current is a
     * pre-release of it (1.1.0 is newer than 1.1.0-dev.3).
     */
    static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        if (a == null || b == null) return false;
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] > b[i];
        }
        return current.contains("-") && !candidate.contains("-");
    }

    private static int[] parse(String version) {
        if (version == null) return null;
        String core = version.split("-", 2)[0];
        String[] parts = core.split("\\.");
        if (parts.length < 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
