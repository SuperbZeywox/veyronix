package com.zeywox.veyronixcore.dto;

public final class EtagPolicy {
    private EtagPolicy() {}

    /**
     * Prefer a precomputed version (from Redis) when available; otherwise use the weak body hash.
     */
    public static String choose(String preferredVersion, String weakBodyHash) {
        return (preferredVersion != null ? preferredVersion : weakBodyHash);
    }
}