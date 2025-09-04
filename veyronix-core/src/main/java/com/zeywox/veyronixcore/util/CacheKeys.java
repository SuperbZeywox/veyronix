package com.zeywox.veyronixcore.util;


import java.util.Optional;


public final class CacheKeys {
    private CacheKeys() {}

    public static String base(String category, Optional<Boolean> inStock, int page, int size) {
        return "products:category=" + category +
                inStock.map(b -> ":inStock=" + b).orElse("") +
                ":page=" + page + ":size=" + size;
    }

    public static String gz(String base)   { return base + ":gz"; }
    public static String meta(String base) { return base + ":meta"; }
    public static String hash(String base) { return base + ":bodyHash"; } // <— NEW
}




