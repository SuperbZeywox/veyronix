package com.zeywox.veyronixcore.dto;

import com.zeywox.veyronixcore.dto.Meta;
import org.springframework.http.HttpHeaders;



public record CachedResponse(
        byte[] gz,         // gzipped body
        Meta meta,         // ETag, last-modified, content-type
        HttpHeaders headers // read-only headers (immutable wrapper)
) {}
