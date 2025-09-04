package com.zeywox.veyronixcore.util;

import com.zeywox.veyronixcore.dto.Meta;
import com.zeywox.veyronixcore.util.HttpCaching;
import org.springframework.http.HttpHeaders;

public final class ResponseHeaders {

    public static HttpHeaders ok(Meta meta, long ttlSecs) {
        HttpHeaders h = new HttpHeaders();
        HttpCaching.applyCacheHeaders(h, meta.contentType, meta.etag, meta.lastModifiedEpochMillis, ttlSecs);
        h.set(HttpHeaders.CONTENT_ENCODING, "gzip");
        h.add(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
        return HttpHeaders.readOnlyHttpHeaders(h);
    }

    public static HttpHeaders notModifiedFrom(HttpHeaders from) {
        HttpHeaders h = new HttpHeaders();
        if (from.getETag() != null) h.setETag(from.getETag());
        var lm = from.get(HttpHeaders.LAST_MODIFIED);
        if (lm != null) h.put(HttpHeaders.LAST_MODIFIED, lm);
        var cc = from.get(HttpHeaders.CACHE_CONTROL);
        if (cc != null) h.put(HttpHeaders.CACHE_CONTROL, cc);
        h.set(HttpHeaders.CONTENT_ENCODING, "gzip");
        h.add(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
        return h;
    }

}
