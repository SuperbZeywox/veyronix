package com.zeywox.veyronixcore.util;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Component
public class IdRegistry {
    private final StringRedisTemplate redis;

    public IdRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** We lookup existing ID for the natural key (name|category); we create once if absent. */
    public String lookupOrCreateId(String name, String category) {
        String field = naturalKeyField(name, category);
        String mapKey = Keys.idxNaturalKey();

        String id = (String) redis.opsForHash().get(mapKey, field);
        if (id == null || id.isBlank()) {
            String candidate = UUID.randomUUID().toString();
            Boolean created = redis.opsForHash().putIfAbsent(mapKey, field, candidate);
            if (Boolean.TRUE.equals(created)) return candidate;           // won the race
            return (String) redis.opsForHash().get(mapKey, field);        // someone set it
        }
        return id;
    }

    /** If the NK changed (name/category changed), we move mapping to the new NK. */
    public void remapIfChanged(String oldName, String oldCategory,
                               String newName, String newCategory,
                               String id) {
        String oldField = naturalKeyField(oldName, oldCategory);
        String newField = naturalKeyField(newName, newCategory);
        if (Objects.equals(oldField, newField)) return;

        Object mapped = redis.opsForHash().get(Keys.idxNaturalKey(), oldField);
        if (Objects.equals(mapped, id)) {
            redis.opsForHash().delete(Keys.idxNaturalKey(), oldField);
        }
        // Write/overwrite the new mapping for this id
        redis.opsForHash().put(Keys.idxNaturalKey(), newField, id);
    }

    // stable, compact NK field from (name|category) ---
    private static String naturalKeyField(String name, String category) {
        String canonical = normalize(name) + "|" + normalize(category);
        return sha256Hex(canonical).substring(0, 32); // shorter field
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return Normalizer.normalize(t, Normalizer.Form.NFC);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
