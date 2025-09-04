package com.zeywox.veyronixcore.util;

import java.util.Locale;

public final class Keys {
    private Keys() {}

    public static String productHash(String id) { return "product:" + id; }
    public static String idxAll()               { return "idx:all"; }

    // SET indexes (legacy + useful for membership/debug)
    public static String idxCategory(String category) {
        return "idx:category:" + normalize(category);
    }
    public static String idxCategoryInStock(String category) {
        return "idx:category:in:" + normalize(category);
    }
    public static String idxCategoryOutOfStock(String category) {
        return "idx:category:out:" + normalize(category);
    }

    // ZSET indexes for stable, efficient pagination (score=0 → lexicographic by member)
    public static String idxCategoryZ(String category) {
        return "zidx:category:" + normalize(category);
    }
    public static String idxCategoryInStockZ(String category) {
        return "zidx:category:in:" + normalize(category);
    }
    public static String idxCategoryOutOfStockZ(String category) {
        return "zidx:category:out:" + normalize(category);
    }

    // Natural key registry (unchanged)
    public static String idxNaturalKey() { return "idx:nk:product"; } // hash: field=sha256(nk), value=id

    // cheap change detectors for revalidation ----------
    public static String verCategory(String category) {
        return "ver:category:" + normalize(category);
    }
    public static String verCategoryIn(String category) {
        return "ver:category:in:" + normalize(category);
    }
    public static String verCategoryOut(String category) {
        return "ver:category:out:" + normalize(category);
    }

    // bump whenever a product changes
    public static String verProduct(String id) { return "ver:product:" + id; }


    public static String normalize(String s) {
        if (s == null || s.isBlank()) return "uncategorized";
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }





}




