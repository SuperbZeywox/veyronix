package com.zeywox.veyronixcore.util;

public class ValueCoercions {

    // ---- mapping / helpers ----

    public static final java.util.regex.Pattern CANON_PRICE =
            java.util.regex.Pattern.compile("^\\d+(?:\\.\\d{2})?$");

    public static String str(Object o) {
        if (o == null) return null;
        if (o instanceof byte[] b) return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        return String.valueOf(o);
    }
    public static String trimToNull(String s){ if (s==null) return null; s=s.trim(); return s.isEmpty()?null:s; }
    public static String defaultEmpty(String s){ return s==null? "":s; }
    public static String defaultCategory(String c){ return (c==null||c.isBlank())? "uncategorized":c; }

    public static Integer parseIntSafe(Object o){
        if (o==null) return 0;
        if (o instanceof Number n) return n.intValue();
        String s = str(o);
        if (s==null || s.isBlank()) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    public static Double parseRedisPrice(Object o){
        if (o==null) return null;
        if (o instanceof Number n){
            double d = n.doubleValue();
            return Double.isFinite(d) && d >= 0 ? round2(d) : null;
        }
        String s = str(o);
        if (s==null || s.isBlank()) return null;
        s = s.trim();
        if (!CANON_PRICE.matcher(s).matches()) {
            // different layer, different rule: reject/non-canonical → treat as null or log
            return null;
        }
        return new java.math.BigDecimal(s).doubleValue();
    }
    public static double round2(double v){
        return java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    public static String s(Object o) { return o == null ? null : String.valueOf(o); }
    public static String ns(String v) { return v == null ? "" : v; }

    public static int parseInt(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
    public static boolean isBlank(String s) { return s == null || s.isBlank(); }


}
