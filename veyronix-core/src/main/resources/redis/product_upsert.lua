-- What this script does:
--   - Writes the product hash with provided fields (HMSET via HSET loop)
--   - Ensures membership in "all products" and the new category (SET + ZSET)
--   - Places the product into the correct in/out bucket for the new stock
--   - If the category changed (by *normalized* value), removes from old category indexes
--   - Bumps version counters with the same semantics as RedisVersionMutator:
--       * Always bump ver:product:<id>
--       * If new raw category not blank, bump ver:category:<newNorm>
--       * If moved categories (RAW changed): bump old category + its old bucket,
--         and bump the new bucket for the new category
--       * If same category (RAW equal):
--             - if stock changed: bump BOTH in/out buckets
--             - else: bump the ACTIVE bucket
--
-- KEYS:
--   1) product:<id>                   (HASH)
--   2) idx:all                        (SET)
--   3) idx:category:<newNorm>         (SET)
--   4) zidx:category:<newNorm>        (ZSET)
--   5) idx:category:in:<newNorm>      (SET)
--   6) zidx:category:in:<newNorm>     (ZSET)
--   7) idx:category:out:<newNorm>     (SET)
--   8) zidx:category:out:<newNorm>    (ZSET)
--   9) ver:product:<id>               (STRING)
--  10) ver:category:<newNorm>         (STRING)
--  11) ver:category:in:<newNorm>      (STRING)
--  12) ver:category:out:<newNorm>     (STRING)
--
-- ARGV:
--   1) id
--   2) new category (RAW; may be blank)
--   3) new stock (string; nil/'' => "0")
--   4..N) flat: field, value, field, value...
--
-- NOTE ON KEY NAMES:
--   - New-category keys are passed as KEYS[3..8] so they stay in sync with Java.
--   - Old-category keys are constructed here as strings. If you introduce a global
--     prefix/namespace in Java, consider passing a prefix in ARGV and prepend here.

local function trim(s) if not s then return "" end return (s:gsub("^%s*(.-)%s*$","%1")) end
local function isBlank(s) return trim(s) == "" end
local function normalize(cat)
    cat = trim(cat)
    if cat == "" then return "uncategorized" end
    cat = string.lower(cat)
    cat = (cat:gsub("%s+","-"))
    return cat
end

local id          = ARGV[1]
local newCatRaw   = ARGV[2]
local newStockStr = ARGV[3]; if not newStockStr or newStockStr == "" then newStockStr = "0" end
local newStock    = tonumber(newStockStr) or 0
local newCatNorm  = normalize(newCatRaw)

local productKey  = KEYS[1]

-- 1) Read previous state
local oldCatRaw   = redis.call("HGET", productKey, "category")
local oldStockStr = redis.call("HGET", productKey, "stock")
local oldStock    = tonumber(oldStockStr or "0") or 0
local oldCatNorm  = normalize(oldCatRaw)

-- 2) Write product hash fields
local i = 4
while i <= #ARGV do
    redis.call("HSET", productKey, ARGV[i], ARGV[i+1])
    i = i + 2
end

-- 3) Always index in "all products"
redis.call("SADD", KEYS[2], id)

-- 4) If normalized category changed, remove from old category indexes
--    (This also removes from "uncategorized" if that was the old normalized value.)
if oldCatNorm ~= newCatNorm then
    redis.call("SREM", "idx:category:"      .. oldCatNorm, id)
    redis.call("SREM", "idx:category:in:"   .. oldCatNorm, id)
    redis.call("SREM", "idx:category:out:"  .. oldCatNorm, id)
    redis.call("ZREM", "zidx:category:"     .. oldCatNorm, id)
    redis.call("ZREM", "zidx:category:in:"  .. oldCatNorm, id)
    redis.call("ZREM", "zidx:category:out:" .. oldCatNorm, id)
end

-- 5) Ensure membership in NEW category (normalized), including bucket
redis.call("SADD", KEYS[3], id)
redis.call("ZADD", KEYS[4], 0, id)
if newStock > 0 then
    redis.call("SADD", KEYS[5], id); redis.call("ZADD", KEYS[6], 0, id)
    redis.call("SREM", KEYS[7], id); redis.call("ZREM", KEYS[8],   id)
else
    redis.call("SADD", KEYS[7], id); redis.call("ZADD", KEYS[8], 0, id)
    redis.call("SREM", KEYS[5], id); redis.call("ZREM", KEYS[6],   id)
end

-- 6) Version bumps

-- Always bump product version
redis.call("INCR", KEYS[9])

-- If new raw category not blank, bump its category version
if not isBlank(newCatRaw) then
    redis.call("INCR", KEYS[10])
end

--if (oldCatRaw or "") ~= (newCatRaw or "") then
if oldCatRaw ~= newCatRaw then
    -- bump old category + its *old* bucket, if old raw wasn't blank
    if not isBlank(oldCatRaw) then
        redis.call("INCR", "ver:category:" .. oldCatNorm)
        if oldStock > 0 then
            redis.call("INCR", "ver:category:in:"  .. oldCatNorm)
        else
            redis.call("INCR", "ver:category:out:" .. oldCatNorm)
        end
    end
    -- bump *new* bucket for the new category if new raw isn't blank
    if not isBlank(newCatRaw) then
        if newStock > 0 then
            redis.call("INCR", KEYS[11])
        else
            redis.call("INCR", KEYS[12])
        end
    end
else
    -- Same category (RAW). Bucket rules:
    --  - if stock changed: bump BOTH buckets
    --  - else: bump ACTIVE bucket
    if not isBlank(newCatRaw) then
        if oldStock ~= newStock then
            redis.call("INCR", KEYS[11]); redis.call("INCR", KEYS[12])
        else
            if newStock > 0 then
                redis.call("INCR", KEYS[11])
            else
                redis.call("INCR", KEYS[12])
            end
        end
    end
end

return 1
