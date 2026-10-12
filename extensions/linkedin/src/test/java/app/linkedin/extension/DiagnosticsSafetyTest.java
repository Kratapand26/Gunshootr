package app.linkedin.extension;

import org.junit.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DiagnosticsSafetyTest {
    @Test
    public void unavailableAndroidLoggingDoesNotEscapeIntoHooks() {
        // Android methods throw in plain JVM tests; hooks must remain usable when a backend fails.
        Diagnostics.error("backend unavailable", new IllegalStateException("diagnostic failure"));
        Diagnostics.debug("backend unavailable");
    }

    @Test
    public void reportsRemoveUrlsEmailsLinkedInIdsAndLongNumbers() {
        String raw = "HTTPS://media.licdn.com/image?token=secret person@example.com "
                + "urn:li:member:987654321 ACoAAB1234567890xyz 987654321";
        String safe = Diagnostics.redact(raw);
        assertFalse(safe.contains("licdn.com"));
        assertFalse(safe.contains("person@example.com"));
        assertFalse(safe.contains("987654321"));
        assertFalse(safe.contains("ACoAAB1234567890xyz"));
        assertTrue(safe.contains("[link]"));
    }

    @Test
    public void credentialsAreRemovedFromExceptionMessages() {
        String safe = Diagnostics.prepareEntry("fetch failed\nAuthorization: Bearer abc\n"
                + "Cookie=session=private\npassword: do not export this\naccess_token=private-value");
        assertFalse(safe.contains("abc"));
        assertFalse(safe.contains("private"));
        assertFalse(safe.contains("do not export"));
        assertTrue(safe.startsWith("fetch failed"));
    }

    @Test
    public void entriesAreBoundedWithoutSplittingUnicodeCharacters() {
        String safe = Diagnostics.prepareEntry("x".repeat(Diagnostics.MAX_MESSAGE_CHARS - 1) + "😀tail");
        assertTrue(safe.endsWith(" [truncated]"));
        String prefix = safe.substring(0, safe.indexOf(" [truncated]"));
        assertFalse(Character.isHighSurrogate(prefix.charAt(prefix.length() - 1)));
        assertTrue(safe.length() <= Diagnostics.MAX_MESSAGE_CHARS + " [truncated]".length());
        assertEquals("", Diagnostics.prepareEntry(null));
    }

    @Test
    public void privateLogNeverStoresRawSensitiveData() throws Exception {
        File file = File.createTempFile("gunshootr-diagnostics-", ".txt");
        try {
            Diagnostics.appendToFile(file, "download https://media.licdn.com/private person@example.com");
            String stored = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            assertTrue(stored.contains("[link]"));
            assertFalse(stored.contains("private"));
            assertFalse(stored.contains("person@example.com"));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    public void logRotationKeepsRecentEntriesWithinTheSizeLimit() throws Exception {
        File file = File.createTempFile("gunshootr-diagnostics-", ".txt");
        try {
            for (int i = 0; i < 100; i++) {
                Diagnostics.appendToFile(file, "entry " + i + " " + "x".repeat(4000));
                assertTrue(file.length() <= Diagnostics.MAX_BYTES);
            }
            List<String> lines = Diagnostics.lastLines(file, Diagnostics.REPORT_LINES);
            assertTrue(lines.get(lines.size() - 1).startsWith("entry 99 "));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    public void reportCapsLinesAndHandlesAnEmptyLog() throws Exception {
        File file = File.createTempFile("gunshootr-diagnostics-", ".txt");
        try {
            assertTrue(Diagnostics.lastLines(file, 200).isEmpty());
            for (int i = 0; i < 250; i++) Diagnostics.appendToFile(file, "line " + i);
            List<String> lines = Diagnostics.lastLines(file, 1000);
            assertEquals(200, lines.size());
            assertEquals("line 50", lines.get(0));
            assertEquals("line 249", lines.get(199));
            assertTrue(Diagnostics.lastLines(file, 0).isEmpty());
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    public void damagedOversizedLogsAreRepairedBeforeAppending() throws Exception {
        File file = File.createTempFile("gunshootr-diagnostics-", ".txt");
        try {
            try (RandomAccessFile damaged = new RandomAccessFile(file, "rw")) {
                damaged.setLength(4L * Diagnostics.MAX_BYTES);
            }
            assertTrue(Diagnostics.lastLines(file, 200).isEmpty());
            Diagnostics.appendToFile(file, "recovered");
            assertTrue(file.length() <= Diagnostics.MAX_BYTES);
            assertEquals(List.of("recovered"), Diagnostics.lastLines(file, 200));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    public void reportsExcludeUnknownPreferencesAndPersonalFolderNames() {
        Map<String, Object> values = new HashMap<>();
        values.put(Settings.HIDE_ADS, true);
        values.put(Settings.DOWNLOAD_FOLDER, "Jane Doe/Private Media");
        values.put(Settings.LANGUAGE, "en");
        values.put("auth_cookie", "private-cookie");
        values.put("email", "person@example.com");
        String safe = Diagnostics.settingsSummary(values);
        assertTrue(safe.contains("hide_ads = true"));
        assertTrue(safe.contains("download_folder = [custom folder]"));
        assertTrue(safe.contains("language = en"));
        assertFalse(safe.contains("Jane Doe"));
        assertFalse(safe.contains("cookie"));
        assertFalse(safe.contains("email"));
    }

    @Test
    public void malformedPreferenceValuesAreNotCopiedIntoReports() {
        Map<String, Object> values = new HashMap<>();
        values.put(Settings.LANGUAGE, "person@example.com");
        values.put(Settings.DOWNLOAD_BASE_DIR, "Jane Doe/Private");
        values.put(Settings.HIDE_ADS, "private-value");
        String safe = Diagnostics.settingsSummary(values);
        assertFalse(safe.contains("person"));
        assertFalse(safe.contains("Jane Doe"));
        assertFalse(safe.contains("private-value"));
        assertTrue(safe.contains("[invalid]"));
    }
}
