package app.linkedin.extension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.function.Predicate;

/** Limits background requests to their intended services, with normal platform TLS validation. */
final class NetworkSafety {
    private NetworkSafety() {
    }

    static String normalizeShortLink(String text) {
        URI uri = secureUri(text);
        if (uri == null || !"lnkd.in".equalsIgnoreCase(uri.getHost())) return null;
        String path = uri.getRawPath();
        if (path == null || !path.matches("/[A-Za-z0-9_-]+/?")) return null;
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return "https://lnkd.in" + path;
    }

    static boolean isMediaUrl(String text) {
        URI uri = secureUri(text);
        return uri != null && hostWithin(uri.getHost(), "licdn.com");
    }

    static boolean isLinkedInUrl(URL url) {
        URI uri = secureUri(url.toString());
        return uri != null && ("lnkd.in".equalsIgnoreCase(uri.getHost())
                || hostWithin(uri.getHost(), "linkedin.com"));
    }

    static boolean isUpdateUrl(URL url) {
        URI uri = secureUri(url.toString());
        return uri != null && "api.github.com".equalsIgnoreCase(uri.getHost())
                && "/repos/Kratapand26/Gunshootr/releases/latest".equals(uri.getPath())
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
    }

    private static URI secureUri(String text) {
        if (text == null) return null;
        try {
            URI uri = new URI(text);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    ? uri : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean hostWithin(String host, String domain) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals(domain) || normalized.endsWith("." + domain);
    }

    /** Validate each redirect before contacting it; never follow a background request to an arbitrary host. */
    static URL redirectTarget(URL current, String location, Predicate<URL> allowed) throws IOException {
        if (location == null) throw new IOException("Missing redirect location");
        URL target = new URL(current, location);
        if (!allowed.test(target)) throw new IOException("Unexpected redirect destination");
        return target;
    }

    static HttpURLConnection open(String text, Predicate<URL> allowed) throws IOException {
        URL current = new URL(text);
        for (int redirects = 0; redirects <= 4; redirects++) {
            if (!allowed.test(current)) throw new IOException("Unexpected request destination");
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            try {
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(false);
                int status = connection.getResponseCode();
                if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) {
                    return connection;
                }
                current = redirectTarget(current, connection.getHeaderField("Location"), allowed);
            } catch (IOException | RuntimeException exception) {
                connection.disconnect();
                throw exception;
            }
            connection.disconnect();
        }
        throw new IOException("Too many redirects");
    }

    static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > limit - output.size()) throw new IOException("Response exceeds the size limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
