package app.linkedin.extension;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Gunshootr log, kept in the app's private storage for manually requested bug reports.
 * Errors are kept with repeat suppression; extra details require "Diagnostic logging" to be on.
 * Sensitive fields are redacted before writing to either Android logs or the private file.
 */
final class Diagnostics {
    private static final String FILE_NAME = "gunshootr_linkedin_log.txt";
    /** The file is cut to the newest half when it grows past this size. */
    static final int MAX_BYTES = 300 * 1024;
    static final int REPORT_LINES = 200;
    static final int MAX_MESSAGE_CHARS = 4096;

    private static final long REPEAT_WINDOW_MS = 5000;
    private static final Map<String, Long> LAST_WRITTEN = new HashMap<>();
    private static final Map<String, Integer> REPEATS = new HashMap<>();

    private static final SimpleDateFormat TIME = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT);

    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern URN = Pattern.compile("urn:li:[A-Za-z_]+:[^\\s,)\\]]+");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    /** LinkedIn member and media IDs, such as ACoAAB1x2y... or D4E22AQH.... */
    private static final Pattern TOKEN = Pattern.compile("\\b(?=[A-Za-z0-9_-]*\\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{16,}\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d{6,}\\b");
    private static final Pattern SECRET = Pattern.compile(
            "\\b(?:authorization|cookie|set-cookie|access_token|refresh_token|session_id|password|secret|token)"
                    + "\\s*[:=]\\s*[^\\r\\n]+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> BOOLEAN_SETTINGS = new HashSet<>(Arrays.asList(
            Settings.HIDE_ADS, Settings.HIDE_PROMOTED_JOBS, Settings.HIDE_SUGGESTED, Settings.HIDE_PREMIUM,
            Settings.DOWNLOAD_MEDIA, Settings.FOCUS_MODE, Settings.HIDE_CELEBRATIONS, Settings.HIDE_FEED_JOBS,
            Settings.HIDE_REPOSTS, Settings.HIDE_VIDEO_POSTS, Settings.HIDE_NEW_POSTS_PILL, Settings.HIDE_TRANSLATION,
            Settings.OPEN_LINKS_DIRECTLY, Settings.HIDE_SPONSORED_MESSAGES, Settings.GHOST_MODE,
            Settings.BLOCK_TRACKING, Settings.DISABLE_DOUBLE_TAP_LIKE, Settings.SANITIZE_SHARE_LINKS,
            Settings.RESOLVE_SHORT_LINKS, Settings.DOWNLOAD_SPLIT_BY_TYPE, Settings.DEBUG_LOGGING));

    private Diagnostics() {
    }

    /** Details, only kept while "Diagnostic logging" is on. */
    static void debug(String message) {
        try {
            if (!Settings.debug()) return;
            String line = repeatedEntry(prepareEntry(message));
            if (line == null) return;
            Log.d(Settings.TAG, line);
            append("D", line);
        } catch (Throwable ignored) {
            // Diagnostic backends must never affect LinkedIn's hooks.
        }
    }

    private static String repeatedEntry(String message) {
        String line = message;
        // Patches can log the same detail for every feed item. Keep one line per message every few seconds so
        // repeats don't push the rest of the log out of the report.
        synchronized (REPEATS) {
            long now = System.currentTimeMillis();
            Long last = LAST_WRITTEN.get(message);
            if (last != null && now - last < REPEAT_WINDOW_MS) {
                Integer count = REPEATS.get(message);
                REPEATS.put(message, count == null ? 1 : count + 1);
                return null;
            }
            Integer skipped = REPEATS.remove(message);
            if (skipped != null) line = message + " (+" + skipped + " more)";
            LAST_WRITTEN.put(message, now);
            if (LAST_WRITTEN.size() > 200) {
                LAST_WRITTEN.clear();
                REPEATS.clear();
            }
        }
        return line;
    }

    /** Errors are always kept, so that a report can show what failed even if logging was off. */
    static void error(String message, Throwable t) {
        try {
            String line = repeatedEntry(prepareEntry(t == null ? message : message + "\n" + stackTrace(t)));
            if (line == null) return;
            // Passing the original Throwable would expose its unredacted message to logcat.
            Log.e(Settings.TAG, line);
            append("E", line);
        } catch (Throwable ignored) {
            // Keep the original app behavior even if logging is unavailable.
        }
    }

    private static synchronized void append(String level, String message) {
        try {
            File file = file();
            if (file == null) return;
            appendToFile(file, TIME.format(new Date()) + " " + level + " " + message);
        } catch (Throwable ignored) {
            // Logging must never break the app.
        }
    }

    static String prepareEntry(String text) {
        String safe = redact(text);
        if (safe.length() <= MAX_MESSAGE_CHARS) return safe;
        int end = MAX_MESSAGE_CHARS;
        if (Character.isHighSurrogate(safe.charAt(end - 1))) end--;
        return safe.substring(0, end) + " [truncated]";
    }

    static synchronized void appendToFile(File file, String message) throws java.io.IOException {
        if (file.length() > MAX_BYTES) Files.write(file.toPath(), readTail(file, MAX_BYTES / 2));
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write((prepareEntry(message) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        if (file.length() > MAX_BYTES) Files.write(file.toPath(), readTail(file, MAX_BYTES / 2));
    }

    /** Bounded allocation even if an old or damaged log is unexpectedly large. */
    private static byte[] readTail(File file, int maxBytes) throws java.io.IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, input.length() - maxBytes);
            byte[] bytes = new byte[(int) (input.length() - start)];
            input.seek(start);
            input.readFully(bytes);
            if (start == 0) return bytes;
            int from = 0;
            while (from < bytes.length && bytes[from] != '\n') from++;
            return from == bytes.length ? new byte[0] : Arrays.copyOfRange(bytes, from + 1, bytes.length);
        }
    }

    static synchronized void clear() {
        File file = file();
        if (file != null) file.delete();
        synchronized (REPEATS) {
            LAST_WRITTEN.clear();
            REPEATS.clear();
        }
    }

    /** The report to paste into a bug report: app, device, patches, settings, and the newest log lines. */
    static String report(Context context) {
        StringBuilder out = new StringBuilder();
        out.append("### Gunshootr Patches diagnostic report\n");
        out.append("Links, IDs, and e-mail addresses were removed. Please check it before posting.\n\n");
        out.append("```\n");
        out.append("Gunshootr Patches ").append(Settings.patchesVersion())
                .append(" • LinkedIn ").append(appVersion(context)).append('\n');
        out.append("LinkedIn patch source: Michii Patches ").append(Settings.UPSTREAM_VERSION).append('\n');
        out.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT)
                .append(") • ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        out.append("Language: ").append(I18n.language()).append(" (device ")
                .append(Locale.getDefault().toLanguageTag()).append(")\n");
        out.append("Diagnostic logging: ").append(Settings.debug() ? "on" : "off").append("\n\n");

        out.append("Patches:\n");
        for (Object[] patch : SettingsActivity.patches()) {
            out.append("  ").append((Boolean) patch[1] ? "[x] " : "[ ] ").append(patch[0]).append('\n');
        }

        out.append("\nSettings:\n");
        SharedPreferences prefs = Settings.prefs();
        if (prefs != null) {
            out.append(settingsSummary(prefs.getAll()));
        }

        out.append("\nLog (newest ").append(REPORT_LINES).append(" lines):\n");
        List<String> lines = lastLines(REPORT_LINES);
        if (lines.isEmpty()) out.append("  (empty)\n");
        for (String line : lines) out.append(line).append('\n');
        out.append("```\n");
        return redact(out.toString());
    }

    /** Removes links, LinkedIn IDs, e-mail addresses, and long numbers. */
    static String redact(String text) {
        if (text == null) return "";
        text = SECRET.matcher(text).replaceAll("[secret]");
        text = URL.matcher(text).replaceAll("[link]");
        text = URN.matcher(text).replaceAll("urn:li:[id]");
        text = EMAIL.matcher(text).replaceAll("[email]");
        text = TOKEN.matcher(text).replaceAll("[id]");
        return NUMBER.matcher(text).replaceAll("[number]");
    }

    /** Only patch preferences belong in the report; imported unknown keys and personal folder names do not. */
    static String settingsSummary(Map<String, ?> preferences) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, ?> entry : new TreeMap<>(preferences).entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (BOOLEAN_SETTINGS.contains(key) && value instanceof Boolean) {
                out.append("  ").append(key).append(" = ").append(value).append('\n');
            } else if (Settings.LANGUAGE.equals(key)) {
                String language = String.valueOf(value);
                out.append("  language = ").append(I18n.LANGUAGE_AUTO.equals(language)
                        || Translations.LANGUAGES.containsKey(language) ? language : "[invalid]").append('\n');
            } else if (Settings.DOWNLOAD_BASE_DIR.equals(key)) {
                out.append("  download_base_dir = ").append(Arrays.asList(Settings.DOWNLOAD_BASE_DIRS).contains(value)
                        ? value : "[invalid]").append('\n');
            } else if (Settings.DOWNLOAD_FOLDER.equals(key)) {
                out.append("  download_folder = ").append(Settings.DEFAULT_DOWNLOAD_FOLDER.equals(value)
                        ? Settings.DEFAULT_DOWNLOAD_FOLDER : "[custom folder]").append('\n');
            }
        }
        return out.toString();
    }

    private static List<String> lastLines(int count) {
        try {
            File file = file();
            return file == null ? new ArrayList<>() : lastLines(file, count);
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    static synchronized List<String> lastLines(File file, int count) throws java.io.IOException {
        List<String> lines = new ArrayList<>();
        count = Math.min(REPORT_LINES, Math.max(0, count));
        if (count == 0 || !file.exists()) return lines;
        String tail = new String(readTail(file, MAX_BYTES), StandardCharsets.UTF_8);
        if (tail.isEmpty()) return lines;
        List<String> all = Arrays.asList(tail.split("\n"));
        lines.addAll(all.subList(Math.max(0, all.size() - count), all.size()));
        return lines;
    }

    private static File file() {
        Context context = Settings.app();
        return context == null ? null : new File(context.getFilesDir(), FILE_NAME);
    }

    private static String stackTrace(Throwable t) {
        StringWriter writer = new StringWriter();
        t.printStackTrace(new PrintWriter(writer));
        // The top of the trace is enough to find the failing patch.
        String[] lines = writer.toString().split("\n");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 12); i++) out.append("    ").append(lines[i].trim()).append('\n');
        return out.toString().trim();
    }

    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (Exception e) {
            return "?";
        }
    }
}
