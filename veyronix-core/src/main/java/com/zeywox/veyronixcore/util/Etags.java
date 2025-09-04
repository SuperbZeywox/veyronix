package com.zeywox.veyronixcore.util;


import java.util.zip.CRC32C;

public final class Etags {
    private Etags() {}
    public static String weakCrc32c(byte[] data) {
        var crc = new CRC32C();
        crc.update(data, 0, data.length);
        return "W/\"" + Long.toHexString(crc.getValue()) + "\"";
    }
    public static boolean weakEquals(String clientList, String server) {
        if (clientList == null) return false;
        for (String token : clientList.split(",")) {
            String t = token.trim();
            t = t.startsWith("W/") ? t.substring(2) : t;
            if (t.equals(server.startsWith("W/") ? server.substring(2) : server)) return true;
        }
        return false;
    }
}


