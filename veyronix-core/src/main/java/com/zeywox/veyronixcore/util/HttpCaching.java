package com.zeywox.veyronixcore.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class HttpCaching {
    private HttpCaching() {}
    private static final DateTimeFormatter RFC1123 =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    public static boolean acceptsGzip(HttpServletRequest req) {
        String ae = req.getHeader(HttpHeaders.ACCEPT_ENCODING);
        return ae != null && ae.toLowerCase().contains("gzip");
    }

    public static boolean isNotModified(HttpServletRequest req, String etag, long lastModifiedMillis) {
        String inm = trim(req.getHeader(HttpHeaders.IF_NONE_MATCH));
        if (inm != null && Etags.weakEquals(inm, etag)) return true;

        String ims = trim(req.getHeader(HttpHeaders.IF_MODIFIED_SINCE));
        if (ims != null) {
            try {
                var t = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(ims));
                if (lastModifiedMillis <= t.toEpochMilli()) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static void applyCacheHeaders(HttpHeaders h, String contentType, String etag,
                                         long lastModifiedMillis, long maxAgeSeconds) {
        h.set(HttpHeaders.CONTENT_TYPE, contentType);
        h.set(HttpHeaders.CACHE_CONTROL, "public, max-age=" + maxAgeSeconds);
        h.set(HttpHeaders.ETAG, etag);
        h.set(HttpHeaders.LAST_MODIFIED, RFC1123.format(Instant.ofEpochMilli(lastModifiedMillis)));
        h.set(HttpHeaders.VARY, "Accept-Encoding");
    }

    private static String trim(String s) { return s == null ? null : s.trim(); }
}

