package com.zeywox.veyronixcore.util;

import org.springframework.data.redis.core.StringRedisTemplate;

public final class VersionLookup {
    private final StringRedisTemplate redis;

    public VersionLookup(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Product version: simple string counter bumped on upsert/setStock.
     * Returns null if the key is missing (EtagPolicy can handle null).
     */
    public String productVersion(String productId) {
        return redis.opsForValue().get(Keys.verProduct(productId));
    }

    /**
     * Category (or bucket) version keyed by the *normalized* category.
     * This MUST normalize to match what the Lua scripts bump.
     *
     * @param category raw category as provided upstream (may contain spaces/case)
     * @param inStock  Optional:
     *                 - empty -> whole-category version (any change)
     *                 - true  -> "in-stock" bucket version
     *                 - false -> "out-of-stock" bucket version
     */
    public String categoryVersion(String category, java.util.Optional<Boolean> inStock) {
        String norm = Keys.normalize(category);

        if (inStock.isEmpty()) {
            return redis.opsForValue().get(Keys.verCategory(norm));
        }
        return inStock.get()
                ? redis.opsForValue().get(Keys.verCategoryIn(norm))
                : redis.opsForValue().get(Keys.verCategoryOut(norm));
    }

}
