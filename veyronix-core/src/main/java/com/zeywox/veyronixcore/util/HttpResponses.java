package com.zeywox.veyronixcore.util;

import com.zeywox.veyronixcore.dto.CachedResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class HttpResponses {


    public static ResponseEntity<byte[]> serve(CachedResponse e) {
        return new ResponseEntity<>(e.gz(), e.headers(), HttpStatus.OK);
    }

    public static ResponseEntity<byte[]> notModified(CachedResponse e) {
        HttpHeaders h = HttpHeaders.readOnlyHttpHeaders(ResponseHeaders.notModifiedFrom(e.headers()));
        return new ResponseEntity<>(null, h, HttpStatus.NOT_MODIFIED);
    }


}
