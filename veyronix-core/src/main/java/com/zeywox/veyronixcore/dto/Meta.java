package com.zeywox.veyronixcore.dto;


public final class Meta {
    public final String etag;
    public final long lastModifiedEpochMillis;
    public final String contentType;
    public Meta(String etag, long lastModifiedEpochMillis, String contentType) {
        this.etag = etag; this.lastModifiedEpochMillis = lastModifiedEpochMillis; this.contentType = contentType;
    }
}

