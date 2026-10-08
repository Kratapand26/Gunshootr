package app.linkedin.extension;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves lnkd.in short links in the background. lnkd.in answers with a LinkedIn warning page
 * whose "continue" button (data-tracking-control-name="external_url_click") holds the target.
 * Results are cached so a tap can use the target without waiting for the network.
 * Plain Java (no Android types) so it can be tested off device.
 */
final class ShortLinkResolver {
    static final String PREFIX = "https://lnkd.in/";
    private static final Pattern SHORT_LINK = Pattern.compile("https://lnkd\\.in/[A-Za-z0-9_-]+");
    private static final Pattern TARGET = Pattern.compile(
            "<a[^>]*data-tracking-control-name=\"external_url_click\"[^>]*href=\"([^\"]+)\"");
    private static final int MAX_LINKS = 200;

    private static final Map<String, String> resolved = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> started = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    private ShortLinkResolver() {
    }

    /** The cached target of a short link, or null if it is not resolved (yet). */
    static String cached(String shortLink) {
        String key = NetworkSafety.normalizeShortLink(shortLink);
        return key == null ? null : resolved.get(key);
    }

    /** Starts resolving every lnkd.in link found in the text. */
    static void prefetchAll(String text) {
        if (text == null) return;
        Matcher matcher = SHORT_LINK.matcher(text);
        while (matcher.find()) prefetch(matcher.group());
    }

    static void prefetch(String shortLink) {
        String key = NetworkSafety.normalizeShortLink(shortLink);
        if (key == null) return;
        if (started.size() >= MAX_LINKS || started.putIfAbsent(key, Boolean.TRUE) != null) return;
        executor.execute(() -> {
            String target = resolve(key);
            if (target != null) resolved.put(key, target);
        });
    }

    /** Fetches the warning page and returns the target URL, or null. Blocking. */
    static String resolve(String shortLink) {
        String key = NetworkSafety.normalizeShortLink(shortLink);
        if (key == null) return null;
        HttpURLConnection connection = null;
        try {
            connection = NetworkSafety.open(key, NetworkSafety::isLinkedInUrl);
            if (connection.getResponseCode() != 200) return null;
            try (InputStream in = connection.getInputStream()) {
                return targetFromPage(new String(NetworkSafety.readBounded(in, 512 * 1024), StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static String targetFromPage(String html) {
        Matcher matcher = TARGET.matcher(html);
        if (!matcher.find()) return null;
        String target = matcher.group(1)
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x3D;", "=");
        return target.startsWith("https://") || target.startsWith("http://") ? target : null;
    }

}
