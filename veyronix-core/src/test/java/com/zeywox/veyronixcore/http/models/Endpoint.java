package com.zeywox.veyronixcore.http.models;

import java.util.Map;


public record Endpoint(String url, Map<String, String> headers) {
    public Endpoint {
        headers = (headers == null) ? Map.of() : headers;
    }
}
