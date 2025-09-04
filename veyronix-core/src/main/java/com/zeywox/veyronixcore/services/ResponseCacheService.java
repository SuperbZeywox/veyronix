package com.zeywox.veyronixcore.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import com.zeywox.veyronixcore.dto.*;
import com.zeywox.veyronixcore.models.Product;
import com.zeywox.veyronixcore.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.zeywox.veyronixcore.util.HttpResponses.notModified;
import static com.zeywox.veyronixcore.util.HttpResponses.serve;

@Service
public class ResponseCacheService {
    private static final Logger log = LoggerFactory.getLogger(ResponseCacheService.class);

    private static final long PRODUCT_FRESH_JOIN_TIMEOUT_MS = 2000L; // same as before (2s)

    private final long ttlSeconds;      // L1 hard TTL
    private final long softTtlMillis;   // refresh-after-write window (SWR)
    private final ExecutorService cacheExecutor;

    private final VersionLookup versions;
    private final JsonGzipEncoder encoder;

    private final ConcurrentHashMap<String, ListQueryContext> ctxs = new ConcurrentHashMap<>();
    private final AsyncLoadingCache<String, CachedResponse> cache;

    private final ConcurrentHashMap<String, CompletableFuture<CachedResponse>> inflightProduct = new ConcurrentHashMap<>();

    public ResponseCacheService(ObjectMapper om,
                                com.zeywox.veyronixcore.config.cache.ResponseCacheProperties props,
                                @Qualifier("cacheFillExecutor") ExecutorService cacheFillExecutor,
                                @Qualifier("storeStringRedisTemplate") StringRedisTemplate storeRedis) {
        this.ttlSeconds    = props.hardTtlSeconds();
        this.softTtlMillis = props.l1SoftTtlMillis();
        this.cacheExecutor = cacheFillExecutor;
        this.versions      = new VersionLookup(storeRedis);
        this.encoder       = new JsonGzipEncoder(om);

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .refreshAfterWrite(softTtlMillis, TimeUnit.MILLISECONDS)
                .maximumWeight(256 * 1024 * 1024)
                .weigher((String k, CachedResponse e) -> e.gz().length)
                .recordStats()
                .buildAsync(new CacheLoader());
    }

    // ------------------------------- public API -------------------------------

    /** Fresh, no cache: concurrent calls coalesce into one Redis read */
    public ResponseEntity<byte[]> getProductFresh(String id, Supplier<Product> fetcher, HttpServletRequest req) {
        CompletableFuture<CachedResponse> cf = inflightProduct.computeIfAbsent(id, k ->
                CompletableFuture.supplyAsync(() -> computeProductFresh(id, fetcher), cacheExecutor)
        );
        cf.whenComplete((__, ___) -> inflightProduct.remove(id, cf));

        CachedResponse e;
        try {
            e = cf.get(PRODUCT_FRESH_JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            // Timed out or failed: compute just for this caller; do not cache here.
            if (!(ex instanceof TimeoutException)) {
                log.warn("product fresh compute failed ({}): {}", id, ex.toString());
            }
            e = computeProductFresh(id, fetcher);
        }

        if (HttpCaching.isNotModified(req, e.meta().etag, e.meta().lastModifiedEpochMillis)) {
            return notModified(e);
        }
        return serve(e);
    }

    public ResponseEntity<byte[]> getProductsListResponse(String category, Optional<Boolean> inStock, int page, int size,
                                                          Supplier<List<Product>> fetcher, HttpServletRequest req) {
        final String base = CacheKeys.base(category, inStock, page, size);
        ctxs.putIfAbsent(base, new ListQueryContext(category, inStock, page, size, fetcher));

        try {
            CachedResponse e = cache.get(base).join();
            if (HttpCaching.isNotModified(req, e.meta().etag, e.meta().lastModifiedEpochMillis)) {
                return notModified(e);
            }
            return serve(e);
        } catch (CompletionException ce) {
            log.warn("cache load failed for {}: {}", base,
                    (ce.getCause() != null ? ce.getCause().toString() : ce.toString()));
            // Fallback: compute only for THIS caller. Do NOT put it into cache here.
            CachedResponse e = computeNow(base, ctxs.get(base), null);
            if (HttpCaching.isNotModified(req, e.meta().etag, e.meta().lastModifiedEpochMillis)) {
                return notModified(e);
            }
            return serve(e);
        }
    }

    // --------------------------- cache loader/refresh -------------------------

    private final class CacheLoader implements com.github.benmanes.caffeine.cache.CacheLoader<String, CachedResponse> {
        @Override
        public CachedResponse load(String base) {
            ListQueryContext ctx = ctxs.get(base);
            if (ctx == null) throw new IllegalStateException("No ListQueryContext for " + base);
            return computeNow(base, ctx, null);
        }
        @Override
        public CachedResponse reload(String base, CachedResponse old) {
            ListQueryContext ctx = ctxs.get(base);
            if (ctx == null) return old;
            String newVer = versions.categoryVersion(ctx.category(), ctx.inStock());
            if (newVer != null && newVer.equals(old.meta().etag)) {
                return old; // unchanged, cheap
            }
            return computeNow(base, ctx, old);
        }
        @Override
        public CompletableFuture<CachedResponse> asyncLoad(String key, Executor executor) {
            return CompletableFuture.supplyAsync(() -> load(key), cacheExecutor);
        }
        @Override
        public CompletableFuture<CachedResponse> asyncReload(String key, CachedResponse oldValue, Executor executor) {
            return CompletableFuture.supplyAsync(() -> reload(key, oldValue), cacheExecutor);
        }
    }

    // ------------------------------- computes --------------------------------

    private CachedResponse computeProductFresh(String id, Supplier<Product> fetcher) {
        Product p = fetcher.get();
        Encoded enc = encoder.encodeProduct(p);
        String ver = versions.productVersion(id); // cheap GET
        String etag = EtagPolicy.choose(ver, enc.weakHash);
        long lastMod = System.currentTimeMillis();
        Meta meta = new Meta(etag, lastMod, "application/json");
        HttpHeaders h = ResponseHeaders.ok(meta, ttlSeconds);
        return new CachedResponse(enc.gz, meta, h);
    }

    private CachedResponse computeNow(String base, ListQueryContext ctx, CachedResponse old) {
        String preferredVersion = versions.categoryVersion(ctx.category(), ctx.inStock());
        List<Product> data = ctx.fetcher().get();
        return buildEntryFromData(base, preferredVersion, data, old);
    }

    private CachedResponse buildEntryFromData(String base, String preferredVersion, List<Product> data, CachedResponse old) {
        Encoded enc = encoder.encodeList(data);
        long lastMod = System.currentTimeMillis();
        String etag = EtagPolicy.choose(preferredVersion, enc.weakHash);
        Meta meta = new Meta(etag, lastMod, "application/json");
        HttpHeaders headers = ResponseHeaders.ok(meta, ttlSeconds);
        return new CachedResponse(enc.gz, meta, headers);
    }
}






