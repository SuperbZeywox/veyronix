package com.zeywox.veyronixcore.repos;

import com.zeywox.veyronixcore.models.Product;
import com.zeywox.veyronixcore.util.Keys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

import static com.zeywox.veyronixcore.util.ValueCoercions.*;

@Repository
public class RedisProductRepository implements ProductRepository {

    private final StringRedisTemplate redis;

    private final DefaultRedisScript<Long> upsertScript;
    private final DefaultRedisScript<Long> setStockScript;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> seedAndRangeScript;


    public RedisProductRepository(
            @Qualifier("storeStringRedisTemplate") StringRedisTemplate redis,
            DefaultRedisScript<Long> productUpsertScript,
            DefaultRedisScript<Long> productSetStockScript,
            @SuppressWarnings("rawtypes") DefaultRedisScript<List> zidxSeedAndRangeScript

    ) {
        this.redis = redis;
        this.upsertScript = productUpsertScript;
        this.setStockScript = productSetStockScript;
        this.seedAndRangeScript = zidxSeedAndRangeScript;

    }

    /** Bump the general category version (any content change within the category). */
    private void bumpCategoryVersion(String category) {
        if (isBlank(category)) return;
        redis.opsForValue().increment(Keys.verCategory(category));
    }

    /** Bump the specific in/out bucket version for a category. */
    private void bumpBucketVersion(String category, int stock) {
        if (isBlank(category)) return;
        if (stock > 0) {
            redis.opsForValue().increment(Keys.verCategoryIn(category));
        } else {
            redis.opsForValue().increment(Keys.verCategoryOut(category));
        }
    }

    @Override
    public Optional<Product> getOne(String id) {
        Map<Object, Object> m = redis.opsForHash().entries(Keys.productHash(id));
        if (m == null || m.isEmpty()) return Optional.empty();
        return Optional.of(new Product(m));
    }

    @Override
    public List<Product> getMany(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        List<Object> piped = redis.executePipelined((RedisCallback<Object>) connection -> {
            var str = redis.getStringSerializer();
            for (String id : ids) {
                connection.hashCommands().hGetAll(str.serialize(Keys.productHash(id)));
            }
            return null;
        });

        return piped.stream()
                .map(o -> (Map<Object, Object>) o)
                .filter(m -> m != null && !m.isEmpty())
                .map(Product::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listIdsByCategory(String category, Optional<Boolean> inStockFilter, int page, int size) {
        String norm = Keys.normalize(category);
        String zkey = inStockFilter
                .map(b -> b ? Keys.idxCategoryInStockZ(norm) : Keys.idxCategoryOutOfStockZ(norm))
                .orElse(Keys.idxCategoryZ(norm));
        String skey = inStockFilter
                .map(b -> b ? Keys.idxCategoryInStock(norm) : Keys.idxCategoryOutOfStock(norm))
                .orElse(Keys.idxCategory(norm));

        long start = (long) (page - 1) * size;
        long end   = start + size - 1;

        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) (List<?>) redis.execute(
                seedAndRangeScript,
                List.of(zkey, skey),
                String.valueOf(start), String.valueOf(end)
        );
        if (ids == null || ids.isEmpty()) return List.of();
        return new ArrayList<>(ids);
    }

    @Override
    public void upsert(Product p) {
        String id = p.id();
        String catRaw = p.category();
        String norm = Keys.normalize(catRaw);
        int stock = p.stock() == null ? 0 : p.stock();

        Map<String,String> m = p.toRedis();
        List<String> fv = new ArrayList<>(m.size() * 2);
        m.forEach((k,v) -> { fv.add(k); fv.add(v); });

        List<String> keys = List.of(
                Keys.productHash(id),
                Keys.idxAll(),
                Keys.idxCategory(norm),
                Keys.idxCategoryZ(norm),
                Keys.idxCategoryInStock(norm),
                Keys.idxCategoryInStockZ(norm),
                Keys.idxCategoryOutOfStock(norm),
                Keys.idxCategoryOutOfStockZ(norm),
                Keys.verProduct(id),
                Keys.verCategory(norm),
                Keys.verCategoryIn(norm),
                Keys.verCategoryOut(norm)
        );

        List<String> args = new ArrayList<>();
        args.add(id);
        args.add(catRaw == null ? "" : catRaw);
        args.add(String.valueOf(stock));
        args.addAll(fv);

        Long ok = redis.execute(upsertScript, keys, args.toArray());
        if (ok == null || ok != 1L) {
            throw new IllegalStateException("Lua upsert failed for " + id);
        }
    }


    @Override
    public int setStock(String id, int stock) {
        String prodKey = Keys.productHash(id);

        // Keep the early NOT_FOUND guard so callers get a fast, friendly exception.
        // (The script also guards with EXISTS to be race-safe.)
        String catRaw = redis.<String, String>opsForHash().get(prodKey, "category");
        if (catRaw == null) {
            throw new org.springframework.dao.EmptyResultDataAccessException("Product not found: " + id, 1);
        }

        // NEW: only pass product hash and ver:product keys (the script derives category keys itself)
        List<String> keys = List.of(
                prodKey,
                Keys.verProduct(id)
        );

        try {
            Long res = redis.execute(setStockScript, keys, id, String.valueOf(stock));
            if (res == null) throw new IllegalStateException("Lua setStock returned null for " + id);
            return res.intValue();
        } catch (org.springframework.dao.DataAccessException e) {
            if (e.getMessage() != null && e.getMessage().contains("NOT_FOUND")) {
                throw new org.springframework.dao.EmptyResultDataAccessException("Product not found: " + id, 1);
            }
            throw e;
        }
    }





}


