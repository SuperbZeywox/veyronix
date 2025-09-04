# Low Latency Product API

A tiny Spring Boot service that ingests a product feed into Redis (in-memory) and serves **low-latency** product APIs.  
Designed to be simple, fast, and resilient to imperfect client input.

---

## Highlights

- **Startup ingest** from `feed.url` with validation/normalization.
- **Low-latency reads** from Redis `HASH` + category indexes (`SET` + `ZSET`).
- **Stable IDs** via a natural-key registry *(name|category → id)* with remapping on updates.
- **L1 list cache (Caffeine) with SWR**
    - In-process entries hold **gzipped JSON + meta (ETag, Last-Modified, Content-Type)**.
    - `expireAfterWrite = 30s` (hard TTL), `refreshAfterWrite = 3s` (soft TTL / background revalidate).
    - **Version-aware refresh**: checks Redis **`ver:*`** keys to skip rebuild when unchanged.
    - **Single-flight** for list loads via async cache loader.
- **Coalesced “get-one” path**: concurrent `GET /products/{id}` calls join a single fresh compute (join window ≈ **2s**).
- **Cheap validators & payloads**: weak **ETag** (CRC32C) and **gzip-once-per-refresh** to minimize CPU on hot paths.

---

## Endpoints

- `GET /products/{id}` — fetch one product by ID.
- `GET /products?category=…[&inStock][&page][&size]` — list by category.
    - `category` *(required)*: case/space-insensitive (normalized).
    - `inStock` *(optional)*: `true` → only `stock > 0`; `false` → only `stock == 0`; omitted → all.
    - `page` *(default: 1)*: `>= 1`.
    - `size` *(fixed: 30)*: **controller forces `30`** (any input is normalized to `30`).
- `PATCH /products/{id}` — update any of: `name`, `category`, `price`, `description`, `stock`.
- `PUT /products/{id}/stock` — dedicated stock setter.

### Caching behavior (list endpoint)

- **L1 cache key (deterministic):**  
  `products:category={norm(category)}:inStock={null|true|false}:page={page}:size={size}`

- **Entry & TTLs (in-process):**  
  Keeps **gzipped body + meta** in Caffeine. **Hard TTL = 30s**, **soft TTL (SWR) = 3s**.  
  Response bytes are kept **in-process** (not stored in Redis).

- **When cached?** All pages are cacheable.

- **Gzip:** Responses are served **gzip-encoded** with `Content-Encoding: gzip` and `Vary: Accept-Encoding`.

- **Validators:**
    - `If-None-Match` (ETag) or `If-Modified-Since` → may return **304** (no body).

- **Always sets:**  
  `Content-Type: application/json`, `Cache-Control: public, max-age=30`, `ETag`, `Last-Modified`, `Vary: Accept-Encoding`.

---

## Input Hardening

- Ingest rejects rows with **blank `name`** or **invalid/negative `price`**.
- Blank/whitespace `category` → **`uncategorized`**.
- `description` defaults to `""`; `stock` initialized to `0`.
- `PATCH` validates types/ranges; unknown fields → **400 Bad Request**.

---

## Architecture

### Redis Key Schema

- `product:{id}` → **HASH**  
  fields: `id`, `name`, `category`, `price`, `description`, `stock`.

- **Category membership (compat)**
    - `idx:category:{normalizedCategory}` → **SET** of IDs (all).
    - `idx:category:in:{normalizedCategory}` → **SET** (stock > 0).
    - `idx:category:out:{normalizedCategory}` → **SET** (stock == 0).

- **Category pagination (preferred for listing):**
    - `zidx:category:{normalizedCategory}` → **ZSET** (score `0.0`, lex order).
    - `zidx:category:in:{normalizedCategory}` → **ZSET** (stock > 0).
    - `zidx:category:out:{normalizedCategory}` → **ZSET** (stock == 0).

- **Natural key → id registry:**
    - `idx:nk:product` (**HASH**)
        - field = `sha256(normalize(name) + "|" + normalize(category))` (first 32 hex chars).
        - value = stable `id` (UUID).
        - On `name`/`category` change, mapping is **remapped** to the new NK.

- **Change detectors (for revalidation):**
    - `ver:category:{cat}`
    - `ver:category:in:{cat}`
    - `ver:category:out:{cat}`
    - `ver:product:{id}`

> **Normalization** (for keys): lower-case, trim, collapse spaces to `-`; blank → `uncategorized`.

### Listing Flow (fast path)

1. Build **L1 key** from `(category, inStock, page, size)` using **normalized** params.
2. **Check validators/meta** → respond `304` if matched.
3. **Try L1 cache**. If hit → serve **immediately** (gz body + headers).
4. Miss → fetch IDs + `HGETALL` (pipelined), serialize once, gzip once, store in L1.  
   On soft-ttl refresh, compare **`ver:*`**; if unchanged, **reuse** prior entry; else rebuild.

---

## Performance

> Results captured from an automated plan per endpoint: warm-up(100) → [repeat ×5: sequential(100) → burst(1000) → sleep 20s]  
> **Note:** Tables below use only the endpoint **number**. See the mapping just above.

### Run Metadata

| Field | Value                   |
|-------|-------------------------|
| Run   | `2025-09-04T09:18:25Z`  |
| Host  | `zeywox`                |
| Type  | `TTFB`                  |
| Base  | `http://localhost:8080` |


### Endpoints
| # | Endpoint |
|---:|---|
| 1 | `/products?category=Computer` |
| 2 | `/products?category=Computer&inStock=true` |
| 3 | `/products/015b3d42-b1c0-40e1-ba7c-98157c269fe5` |

### Table A — Sequential pooled results (100 per run; all combined)

| # | total | min (ms) | median (ms) | avg (ms) | max (ms) | mode (ms) | p90 (ms) | p95 (ms) | p99 (ms) |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 500 | 0.29 | 0.52 | 0.61 | 3.31 | 0.42 | 1.00 | 1.12 | 1.41 |
| 2 | 500 | 0.28 | 0.53 | 0.60 | 1.84 | 0.40 | 0.92 | 1.09 | 1.37 |
| 3 | 500 | 0.48 | 0.66 | 0.72 | 3.19 | 0.67 | 0.87 | 1.06 | 1.93 |

### Table B — Burst (1000) run-averaged results (mean of per-run metrics; n=5)

| # | total/run | min (ms) | median (ms) | avg (ms) | max (ms) | mode (ms) | p90 (ms) | p95 (ms) | p99 (ms) |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1000 | 0.37 | 0.57 | 0.69 | 32.17 | 0.55 | 0.77 | 0.96 | 2.07 |
| 2 | 1000 | 0.39 | 0.57 | 0.74 | 36.50 | 0.54 | 0.81 | 0.99 | 2.32 |
| 3 | 1000 | 0.53 | 0.89 | 1.14 | 45.55 | 0.87 | 1.13 | 1.60 | 4.25 |

### Table C — Variability (burst run-avg “avg (ms)” ± SD over 5 runs)

| # | avg (ms) ± SD |
|---:|---:|
| 1 | 0.69 ± 0.04 |
| 2 | 0.74 ± 0.09 |
| 3 | 1.14 ± 0.19 |

---

## Important — Environment tuning before stress test

The service’s read path (Undertow + virtual threads + Redis pipelining + L1 cache) is tuned for very low latency.  
To sustain that under bursty load, the host must provide enough sockets, backlog, and ephemeral ports.  
Apply these **preferred** runtime settings **before** launching the JVM and running the benchmark:

### Environment tuning before stress test

```bash
# Preferred (more headroom under bursts)
ulimit -n 200000                     # run in the SAME shell that will start `java -jar`
sudo sysctl -w net.core.somaxconn=65535
sudo sysctl -w net.ipv4.tcp_max_syn_backlog=16384
sudo sysctl -w net.ipv4.tcp_fin_timeout=15
sudo sysctl -w net.ipv4.tcp_tw_reuse=1
sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535"
```
---

## Running

### Prereqs

- **Java 24+**, Maven, docker

### Commands

From the **repository root**:

```bash
# 1) Start infra and build artifacts
docker compose up -d
mvn clean package

# 2) Start the service (module: veyronix-core) with tuned Undertow threads
cd veyronix-core
java -Dserver.undertow.io-threads=16 \
     -Dserver.undertow.worker-threads=64 \
     -jar target/veyronix-core-1.0.jar

# 3) Get an in-stock productId (prints one id to stdout)
cd veyronix-core
mvn -q exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.zeywox.veyronixcore.pre_test.Test1 \
  -Dbench.timeoutSec=10 \
  -Dbench.stockValue=7 \
  2>/dev/null

# 4) Make the benchmark script executable
chmod +x ./src/test/java/com/zeywox/veyronixcore/http/benchmark/main.sh

# 5) Run the benchmark using the printed <productId>
./src/test/java/com/zeywox/veyronix/http/benchmark/main.sh <productId>

# 6) Example: productId: 015b3d42-b1c0-40e1-ba7c-98157c269fe5
# we use following command:
./src/test/java/com/zeywox/veyronixcore/http/benchmark/main.sh 015b3d42-b1c0-40e1-ba7c-98157c269fe5

```

---

## Project Structure:
```

├── config
│   ├── bootstrap
│   │   └── FeedLoader.java
│   ├── cache
│   │   ├── CacheFillExecutorConfig.java
│   │   ├── RedisConfig.java
│   │   ├── RedisLuaConfig.java
│   │   └── ResponseCacheProperties.java
│   ├── controller
│   │   └── GlobalExceptionHandler.java
│   ├── general
│   │   ├── AsyncExecConfig.java
│   │   └── UndertowServerConfig.java
│   └── jackson
│       └── JacksonConfig.java
├── controllers
│   └── ProductController.java
├── deserialization
│   ├── DoublePriceDeserializer.java
│   └── PatchProductNormalizationModule.java
├── dto
│   ├── CachedResponse.java
│   ├── Encoded.java
│   ├── EtagPolicy.java
│   ├── ListQueryContext.java
│   ├── Meta.java
│   ├── PatchProductRequest.java
│   ├── ProductIn.java
│   └── Requests.java
├── models
│   └── Product.java
├── repos
│   ├── ProductRepository.java
│   └── RedisProductRepository.java
├── services
│   ├── FeedIngestionService.java
│   ├── ProductService.java
│   └── ResponseCacheService.java
├── util
│   ├── CacheKeys.java
│   ├── Compression.java
│   ├── Etags.java
│   ├── HttpCaching.java
│   ├── HttpResponses.java
│   ├── IdRegistry.java
│   ├── JsonGzipEncoder.java
│   ├── Keys.java
│   ├── ResponseHeaders.java
│   ├── ValueCoercions.java
│   └── VersionLookup.java
└── VeyronixCoreApplication.java

```
