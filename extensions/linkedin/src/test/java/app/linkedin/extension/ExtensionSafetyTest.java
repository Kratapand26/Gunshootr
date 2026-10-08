package app.linkedin.extension;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class ExtensionSafetyTest {
    @Test
    public void shortLinksKeepTheirCaseAndDiscardTracking() {
        assertEquals("https://lnkd.in/AbC_-9", NetworkSafety.normalizeShortLink("https://lnkd.in/AbC_-9/?trk=feed#tail"));
        assertEquals("https://lnkd.in/AbC", NetworkSafety.normalizeShortLink("https://LNKD.IN:443/AbC"));
    }

    @Test
    public void malformedAndSpoofedShortLinksAreRejected() {
        for (String url : new String[]{null, "http://lnkd.in/abc", "https://lnkd.in.evil.example/abc",
                "https://evil.example/?https://lnkd.in/abc", "https://user@lnkd.in/abc",
                "https://lnkd.in:8443/abc", "https://lnkd.in/abc/other", "file://lnkd.in/abc"}) {
            assertNull(url, NetworkSafety.normalizeShortLink(url));
            assertNull(ShortLinkResolver.cached(url));
        }
    }

    @Test
    public void mediaDownloadsAcceptLinkedInCdnOnly() {
        assertTrue(NetworkSafety.isMediaUrl("https://media.licdn.com/dms/image/v2/abc?token=signed"));
        assertTrue(NetworkSafety.isMediaUrl("https://dms.licdn.com/playlist/vid/v2/abc/video.mp4"));
        assertTrue(NetworkSafety.isMediaUrl("https://MEDIA.LICDN.COM:443/image.jpg"));
        for (String url : new String[]{"http://media.licdn.com/image", "https://media.licdn.com.evil.example/image",
                "https://licdn.com@evil.example/image", "https://user@media.licdn.com/image",
                "https://127.0.0.1/image", "https://media.licdn.com:8443/image", "content://media/image", null}) {
            assertFalse(url, NetworkSafety.isMediaUrl(url));
        }
    }

    @Test
    public void shortLinkRedirectsStayWithinLinkedIn() throws Exception {
        URL initial = new URL("https://lnkd.in/abc");
        URL expected = new URL("https://www.linkedin.com/safety/go/?url=https%3A%2F%2Fexample.com");
        assertEquals(expected, NetworkSafety.redirectTarget(initial, expected.toString(), NetworkSafety::isLinkedInUrl));
        assertEquals(new URL("https://lnkd.in/next"),
                NetworkSafety.redirectTarget(initial, "/next", NetworkSafety::isLinkedInUrl));
    }

    @Test
    public void backgroundRedirectsRejectOtherHostsAndTlsDowngrades() throws Exception {
        URL initial = new URL("https://lnkd.in/abc");
        for (String location : new String[]{"https://127.0.0.1/private", "https://evil.example/path",
                "http://www.linkedin.com/safety/go", "https://linkedin.com.evil.example/", null}) {
            assertThrows(IOException.class,
                    () -> NetworkSafety.redirectTarget(initial, location, NetworkSafety::isLinkedInUrl));
        }
    }

    @Test
    public void updatesAreRestrictedToGunshootrReleaseMetadata() throws Exception {
        assertEquals("Kratapand26/Gunshootr", UpdateChecker.REPO);
        assertTrue(NetworkSafety.isUpdateUrl(new URL("https://api.github.com/repos/Kratapand26/Gunshootr/releases/latest")));
        for (String url : new String[]{"https://api.github.com/repos/heyymichii/michii-patches/releases/latest",
                "https://api.github.com/repos/Kratapand26/Gunshootr/releases/latest?token=secret",
                "https://evil.example/repos/Kratapand26/Gunshootr/releases/latest",
                "http://api.github.com/repos/Kratapand26/Gunshootr/releases/latest"}) {
            assertFalse(url, NetworkSafety.isUpdateUrl(new URL(url)));
        }
    }

    @Test
    public void boundedReadsAcceptTheExactLimit() throws Exception {
        byte[] payload = "bounded response".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(payload, NetworkSafety.readBounded(new ByteArrayInputStream(payload), payload.length));
    }

    @Test
    public void oversizedResponsesAreRejectedInsteadOfTruncated() {
        assertThrows(IOException.class,
                () -> NetworkSafety.readBounded(new ByteArrayInputStream(new byte[8193]), 8192));
    }

    @Test
    public void warningPageTargetsRejectExecutableSchemes() {
        assertNull(ShortLinkResolver.targetFromPage("<a data-tracking-control-name=\"external_url_click\" href=\"javascript:alert(1)\">"));
        assertNull(ShortLinkResolver.targetFromPage("<a data-tracking-control-name=\"external_url_click\" href=\"intent://open\">"));
    }

    @Test
    public void warningPageTargetsPreserveQueryParameters() {
        assertEquals("https://example.com/?a=1&b=2", ShortLinkResolver.targetFromPage(
                "<a data-tracking-control-name=\"external_url_click\" href=\"https://example.com/?a=1&amp;b=2\">"));
    }

    @Test
    public void sharedLinksPreserveDestinationsAndNonTrackingParameters() {
        String original = "https://www.linkedin.com/posts/abc?trk=feed&utm_source=share&id=42#details";
        assertEquals("https://www.linkedin.com/posts/abc?id=42#details", LinkCleaner.cleanUrl(original));
    }

    @Test
    public void sharingDoesNotRewriteOtherDomains() {
        String original = "Text https://example.com/?utm_source=required and https://linkedin.com.evil.example/?trk=keep";
        assertSame(original, LinkCleaner.clean(original));
        assertNull(LinkCleaner.clean(null));
    }

    @Test
    public void downloadFoldersCannotEscapeTheChosenPublicDirectory() {
        assertEquals("LinkedIn/Media", Settings.sanitizeFolder("../../LinkedIn/../Media"));
        assertEquals("Pictures/Subfolder", Settings.sanitizeFolder("/Pictures/./Subfolder"));
        assertEquals("", Settings.sanitizeFolder(null));
    }

    @Test
    public void truncatedPayloadsDoNotInventFeedMarkers() {
        assertFalse(ProtoScanner.hasExactString(new byte[]{0x0a, 0x7f, 'S'}, "Suggested"));
        assertTrue(ProtoScanner.scan(new byte[]{0x0a, 0x7f, 'S'}).strings.isEmpty());
        assertFalse(ProtoScanner.hasExactString(null, "Promoted"));
    }

    @Test
    public void feedLabelsMatchWholeFieldsInBothOriginalLanguages() {
        byte[] english = new byte[]{0x0a, 9, 'S', 'u', 'g', 'g', 'e', 's', 't', 'e', 'd'};
        byte[] indonesian = new byte[]{0x0a, 10, 'D', 'i', 's', 'a', 'r', 'a', 'n', 'k', 'a', 'n'};
        assertTrue(ProtoScanner.hasExactString(english, "Suggested", "Disarankan"));
        assertTrue(ProtoScanner.hasExactString(indonesian, "Suggested", "Disarankan"));
        assertFalse(ProtoScanner.hasExactString(english, "Suggest"));
    }

    @Test
    public void patchSourceVersionIsPreservedAndReleaseChecksCompareGunshootrVersions() {
        assertEquals("1.0.0", Settings.UPSTREAM_VERSION);
        assertTrue(UpdateChecker.isNewer("1.0.6", "1.0.5"));
        assertTrue(UpdateChecker.isNewer("1.0.5", "1.0.5-dev.1"));
        assertFalse(UpdateChecker.isNewer("1.0.4", "1.0.5"));
        assertFalse(UpdateChecker.isNewer("invalid", "1.0.5"));
    }
}
