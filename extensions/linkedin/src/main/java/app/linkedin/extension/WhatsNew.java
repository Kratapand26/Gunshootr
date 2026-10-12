package app.linkedin.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;

/** Shows the changes of the installed patch bundle once, the first time LinkedIn opens after patching. */
final class WhatsNew {
    /** Newest first. Add an entry for every release that users should read about. */
    static final String[][] CHANGELOG = {
            {"1.1.0", "LinkedIn features updated from Michii Patches 1.1.0.\n\n"
                    + "• Supports LinkedIn 4.1.1259 and 4.1.1255.1\n"
                    + "• Gunshootr Patches settings shortcut is published from the main screen, at the top\n"
                    + "• English is the default; choose another language under Other → Language\n"
                    + "• Copy a diagnostic report under Other; sensitive details are redacted before logging\n"
                    + "• Dialog buttons remain visible over LinkedIn screens\n\n"
                    + "Open settings from Me → Gunshootr Patches, or long press the LinkedIn icon → Gunshootr Patches."},
            {"1.0.0", "LinkedIn features ported from Michii Patches 1.0.0.\n\n"
                    + "• Hide ads, promoted jobs, suggested posts, and Premium upsells\n"
                    + "• Download photos, videos, profile photos, and banners to a chosen folder\n"
                    + "• Feed filters: focus mode, celebrations, jobs, reposts, and videos\n"
                    + "• Chat: hide sponsored messages and optional ghost mode\n"
                    + "• Open links directly (including lnkd.in) and clean shared links\n"
                    + "• Optional tracking block and disable double-tap like\n\n"
                    + "Open settings from the Me panel → Gunshootr Patches."},
    };

    private static boolean checked;

    private WhatsNew() {
    }

    /** Text for a version, or null. Pre-release builds (1.1.0-dev.2) use their base version's entry. */
    static String notesFor(String version) {
        String base = version.split("-", 2)[0];
        for (String[] entry : CHANGELOG) {
            if (entry[0].equals(base)) return entry[1];
        }
        return null;
    }

    static void maybeShow(Activity activity) {
        if (checked) return;
        checked = true;
        try {
            String version = Settings.patchesVersion();
            if (version.equals(Settings.getString(Settings.LAST_SEEN_VERSION, null))) return;
            Settings.setString(Settings.LAST_SEEN_VERSION, version);

            String notes = notesFor(Settings.UPSTREAM_VERSION);
            if (notes == null) return;
            boolean dark = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            AlertDialog dialog = new AlertDialog.Builder(activity, dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                    : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle("LinkedIn features in " + SettingsActivity.BRAND + " " + version)
                    .setMessage(notes)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Settings", (d, which) -> SettingsActivity.open(activity))
                    .show();
            SettingsActivity.tintButtons(dialog, activity);
        } catch (Throwable t) {
            Diagnostics.error("WhatsNew failed", t);
        }
    }
}
