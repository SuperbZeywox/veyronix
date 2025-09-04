package com.zeywox.veyronixcore.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Compression {
    private Compression() {}
    public static byte[] gzip(byte[] raw) {
        try (var bos = new ByteArrayOutputStream(raw.length);
             var gz = new GZIPOutputStream(bos)) {
            gz.write(raw);
            gz.finish();
            return bos.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }
    public static byte[] gunzip(byte[] gz) {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(gz));
             var bos = new ByteArrayOutputStream(Math.max(1024, gz.length * 2))) {
            in.transferTo(bos);
            return bos.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }
}

