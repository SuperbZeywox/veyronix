package com.zeywox.veyronixcore.dto;

/** Simple value object for encoded payload. */
public final class Encoded {
    final byte[] raw;
    public final byte[] gz;
    public final String weakHash;
    public Encoded(byte[] raw, byte[] gz, String weakHash) { this.raw = raw; this.gz = gz; this.weakHash = weakHash; }
}
